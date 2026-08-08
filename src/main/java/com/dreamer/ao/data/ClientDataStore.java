package com.dreamer.ao.data;

import com.dreamer.ao.Config;
import com.dreamer.ao.data.DataStore.*;
import com.dreamer.ao.data.model.CustomAdvancement;
import com.dreamer.ao.data.model.VanillaAdvMeta;
import com.dreamer.ao.network.payload.PhaseSyncPayload;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 客户端数据缓存（单例）。
 *
 * <h2>线程模型</h2>
 * <p>Minecraft 客户端数据来自两个线程：</p>
 * <ul>
 *   <li><b>网络主线程（Render thread）</b> — 接收服务端发来的同步数据包，调用
 *       {@link #setCompletedAdvancements}、{@link #updateCompletion} 等方法</li>
 *   <li><b>渲染线程</b> — 读取数据用于 GUI 渲染、tooltip 判断等</li>
 * </ul>
 * <p>两个线程之间通过以下机制保证数据一致性：</p>
 * <ul>
 *   <li><b>volatile 引用</b> — 批量更新方法（如 {@link #setAdvancements}）直接替换整个 Map 引用，
 *       利用 volatile 的 happens-before 语义保证可见性</li>
 *   <li><b>ConcurrentHashMap</b> — 用于 {@link #completedAdvancements}，
 *       因为增量更新 {@link #updateCompletion} 需要 put/get 操作本身线程安全，
 *       而普通的 volatile HashMap 的 get/put 没有 happens-before 保证</li>
 *   <li><b>AtomicInteger</b> — 用于 {@link #completedCount}，
 *       因为计数需要先读后写（read-modify-write），volatile int 无法保证原子性</li>
 * </ul>
 * <p>增量方法（如 {@link #updateCompletion}）从 Minecraft 网络主线程调用，
 * 但它们对 ConcurrentHashMap 的 put 操作对渲染线程立即可见。</p>
 *
 * <h2>标签页缓存</h2>
 * 标签页列表和标签页索引使用脏标记延迟重建（{@link #tabsDirty} / {@link #tabIndexDirty}），
 * 避免每次查询时重建。
 */
public class ClientDataStore {

    private static final ClientDataStore INSTANCE = new ClientDataStore();
    public static ClientDataStore getInstance() { return INSTANCE; }

    private ClientDataStore() {}

    // ═══════════════ 数据记录类型 ═══════════════

    /**
     * 原版进度条目（客户端渲染用）。
     * <p>
     * 包含显示名、描述、隐藏状态、翻译键、根标签页、画布位置和图标信息。
     */
    public record VanillaAdvEntry(
            String id, String name, String desc, boolean hidden,
            String nameKey, String descKey, String rootTab, int x, int y, String icon
    ) {
        /** 简化构造器（无布局/图标信息） */
        public VanillaAdvEntry(String id, String name, String desc, boolean hidden) {
            this(id, name, desc, hidden, null, null, null, 0, 0, null);
        }
    }

    // ═══════════════ 数据字段（volatile 可见性） ═══════════════

    private volatile Map<String, CustomAdvancement> advancements = new HashMap<>();
    private volatile Map<String, DimensionLock> dimensionLocks = new HashMap<>();
    /**
     * 完成状态映射。
     * <p>
     * <b>为什么用 ConcurrentHashMap 而不是普通的 volatile HashMap？</b>
     * <ul>
     *   <li>{@code setCompletedAdvancements(Map)} 替换整个引用 → volatile 可见性足够</li>
     *   <li>{@code updateCompletion(String, boolean)} 做增量 get + put，
     *       这是两次独立操作，在普通 HashMap 上没有 happens-before 保证。
     *       渲染线程可能看到 put 的结果但 get 到旧值。</li>
     *   <li>ConcurrentHashMap 的 get/put 各自保证可见性，且免锁并发。
     *       不需要用 synchronized 或 ReentrantLock 让两个线程互斥等待。</li>
     * </ul>
     * <p>
     * 注意：变量声明仍然是 {@code Map<String, Boolean>} 接口类型（而非 {@code ConcurrentHashMap}），
     * 因为 {@link #setCompletedAdvancements} 会替换引用，外部传入的可能不是 ConcurrentHashMap。
     */
    private volatile Map<String, Boolean> completedAdvancements = new ConcurrentHashMap<>();

    /**
     * 完成计数。
     * <p>
     * <b>为什么用 AtomicInteger 而不是 volatile int？</b>
     * {@link #updateCompletion} 中的 {@code count++} 和 {@code count--} 是
     * 读-改-写复合操作，volatile 只保证可见性不保证原子性。
     * AtomicInteger 的 incrementAndGet/decrementAndGet 使用 CAS 保证原子递增递减。
     */
    private final AtomicInteger completedCount = new AtomicInteger(0);

    private volatile Map<String, Integer> advancementProgress = new HashMap<>();
    private volatile List<String> customTabs = new ArrayList<>();
    private volatile Set<String> disabledVanillaAdvancements = new HashSet<>();
    private volatile Set<String> enabledVanillaAdvancements = new HashSet<>();
    private volatile Set<String> pendingAdvancements = new HashSet<>();
    private volatile List<VanillaAdvEntry> vanillaAdvancements = new ArrayList<>();
    private volatile Map<String, VanillaAdvEntry> vanillaAdvEntryMap = new HashMap<>();
    private volatile Map<String, VanillaAdvMeta> vanillaMeta = new HashMap<>();
    private volatile Map<String, String> vanillaParentMap = new HashMap<>();
    private volatile List<String> tabOrder = new ArrayList<>();

    /** 玩家叙事统计数据（来自服务端同步） */
    private volatile PlayerStats playerStats = new PlayerStats();
    /** PlayerStats 版本计数器（每次 setPlayerStats 递增，供 UI 检测刷新） */
    private final AtomicInteger statsVersion = new AtomicInteger(0);

    // ═══════════════ 标签页缓存（脏标记 + 延迟重建） ═══════════════

    private volatile boolean tabsDirty = true;
    private volatile List<String> cachedTabs = Collections.emptyList();
    private volatile boolean tabIndexDirty = true;
    private volatile Map<String, List<CustomAdvancement>> cachedTabIndex = Collections.emptyMap();

    /** 标记标签页缓存为脏（成就/标签页数据变更时调用） */
    public void markTabsDirty() { tabsDirty = true; tabIndexDirty = true; }

    // ═══════════════ 成就数据 ═══════════════

    /**
     * 本地拖动（已修改、尚未被服务端同步确认）的自定义卡片 id -> 拖动结束时间戳。
     * <p>
     * syncAll 全量覆盖 {@link #setAdvancements(Map)} 时，对这些卡片保留客户端坐标，
     * 避免"拖完父成就后，拖动其子成就触发 syncAll 把父拉回原位"。
     * 条目超过 {@link #LOCAL_ADV_DIRTY_TTL_MS} 自动失效（下次 setAdvancements 时清理），
     * 不长期偏离服务端权威数据。
     */
    private static final long LOCAL_ADV_DIRTY_TTL_MS = 4000;
    private final Map<String, Long> localAdvDirty = new java.util.concurrent.ConcurrentHashMap<>();

    /** 标记一张卡片为"本地拖动未同步"，记录当前时间戳。 */
    public void markLocalAdvDirty(String id) {
        if (id != null) localAdvDirty.put(id, System.currentTimeMillis());
    }

    public void setAdvancements(Map<String, CustomAdvancement> map) {
        long now = System.currentTimeMillis();
        // 清理过期的本地脏标记
        localAdvDirty.values().removeIf(ts -> now - ts > LOCAL_ADV_DIRTY_TTL_MS);
        Map<String, CustomAdvancement> result = map;
        if (!localAdvDirty.isEmpty()) {
            result = new java.util.HashMap<>(map); // 浅拷贝，避免修改服务端传入的 Map
            for (String id : localAdvDirty.keySet()) {
                CustomAdvancement local = this.advancements.get(id); // 客户端当前（拖动后）坐标
                if (local == null) continue;
                CustomAdvancement incoming = result.get(id);
                if (incoming != null) {
                    incoming.setX(local.getX());
                    incoming.setY(local.getY());
                } else {
                    result.put(id, local);
                }
            }
        }
        this.advancements = result;
        markTabsDirty();
    }
    public Map<String, CustomAdvancement> getAdvancements() { return Collections.unmodifiableMap(advancements); }
    public CustomAdvancement getAdvancement(String id) { return advancements.get(id); }

    // ═══════════════ 维度锁 ═══════════════

    public void setDimensionLocks(Map<String, DimensionLock> map) { this.dimensionLocks = map; }
    public Map<String, DimensionLock> getDimensionLocks() { return Collections.unmodifiableMap(dimensionLocks); }

    // ═══════════════ 阶段系统 ═══════════════

    /** 服务端推送的阶段态摘要（来自 PhaseSyncPayload） */
    private volatile String phaseWorldPhase;
    private volatile Map<String, String> phaseDimensionPhases = new HashMap<>();
    private volatile String phasePlayerPhase;
    private volatile String phaseTempPhase;
    private volatile List<String> phaseUnlocked = new ArrayList<>();
    private volatile List<String> phaseDefBriefs = new ArrayList<>();
    /** 阶段数据版本（每次 setPhaseData 递增，供面板检测刷新） */
    private final AtomicInteger phaseVersion = new AtomicInteger(0);

    public void setPhaseData(PhaseSyncPayload payload) {
        this.phaseWorldPhase = payload.worldPhase();
        this.phaseDimensionPhases = payload.dimensionPhases() != null
                ? new HashMap<>(payload.dimensionPhases()) : new HashMap<>();
        this.phasePlayerPhase = payload.playerPhase();
        this.phaseTempPhase = payload.tempPhase();
        this.phaseUnlocked = payload.unlockedPhases() != null
                ? new ArrayList<>(payload.unlockedPhases()) : new ArrayList<>();
        this.phaseDefBriefs = payload.defBriefs() != null
                ? new ArrayList<>(payload.defBriefs()) : new ArrayList<>();
        this.phaseVersion.incrementAndGet();
    }
    public int getPhaseVersion() { return phaseVersion.get(); }
    public String getPhaseWorldPhase() { return phaseWorldPhase; }
    public Map<String, String> getPhaseDimensionPhases() { return Collections.unmodifiableMap(phaseDimensionPhases); }
    public String getPhasePlayerPhase() { return phasePlayerPhase; }
    public String getPhaseTempPhase() { return phaseTempPhase; }
    public List<String> getPhaseUnlocked() { return Collections.unmodifiableList(phaseUnlocked); }
    public List<String> getPhaseDefBriefs() { return Collections.unmodifiableList(phaseDefBriefs); }
    public boolean isPhaseUnlocked(String id) { return phaseUnlocked.contains(id); }

    // ═══════════════ 完成状态 ═══════════════

    /** 全量设置完成状态，同时更新完成计数 */
    public void setCompletedAdvancements(Map<String, Boolean> map) {
        this.completedAdvancements = map;
        int count = 0;
        for (Boolean v : map.values()) {
            if (Boolean.TRUE.equals(v)) count++;
        }
        completedCount.set(count);
    }
    public boolean isCompleted(String advId) {
        return Boolean.TRUE.equals(completedAdvancements.get(advId));
    }

    /**
     * 增量更新完成状态（网络主线程调用）。
     * <p>
     * 操作顺序：先 get 旧值 → put 新值 → 更新计数。
     * 三个操作之间没有锁保护，但这里不需要完美一致性：
     * <ul>
     *   <li>如果另一个线程在 get 和 put 之间也做了 put，最坏情况是计数短暂偏差一帧</li>
     *   <li>Minecraft 客户端不会同时对同一个 advancement 发送两次更新</li>
     *   <li>计数用 AtomicInteger 保证至少不会出现数据损坏</li>
     * </ul>
     */
    public void updateCompletion(String advId, boolean completed) {
        Boolean old = completedAdvancements.get(advId);
        completedAdvancements.put(advId, completed);
        if (completed && !Boolean.TRUE.equals(old)) completedCount.incrementAndGet();
        else if (!completed && Boolean.TRUE.equals(old)) completedCount.decrementAndGet();
    }

    // ═══════════════ 进度 ═══════════════

    public void setAdvancementProgress(Map<String, Integer> map) { this.advancementProgress = map; }
    public int getProgress(String advId) { return advancementProgress.getOrDefault(advId, 0); }
    public void updateProgress(String advId, int progress) { advancementProgress.put(advId, progress); }

    // ═══════════════ 自定义标签页 ═══════════════

    public void setCustomTabs(List<String> tabs) {
        this.customTabs = tabs != null ? tabs : new ArrayList<>();
        markTabsDirty();
    }
    public List<String> getCustomTabs() { return Collections.unmodifiableList(customTabs); }

    // ═══════════════ 标签页顺序 ═══════════════

    public void setTabOrder(List<String> order) {
        this.tabOrder = order != null ? order : new ArrayList<>();
        markTabsDirty();
    }
    public List<String> getTabOrder() { return Collections.unmodifiableList(tabOrder); }

    // ═══════════════ 原版进度启用/禁用 ═══════════════

    /**
     * 判断原版进度是否启用（客户端侧判断）。
     * 优先级：enabled > disabled > 服务端默认配置。
     */
    public boolean isVanillaEnabled(String id) {
        if (enabledVanillaAdvancements.contains(id)) return true;
        if (disabledVanillaAdvancements.contains(id)) return false;
        try {
            return Config.VANILLA_DEFAULT_ENABLED.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }
    public void setDisabledVanilla(Set<String> set) {
        this.disabledVanillaAdvancements = set != null ? set : new HashSet<>();
    }
    public Set<String> getDisabledVanilla() { return Collections.unmodifiableSet(disabledVanillaAdvancements); }
    public void setEnabledVanilla(Set<String> set) {
        this.enabledVanillaAdvancements = set != null ? set : new HashSet<>();
    }
    public Set<String> getEnabledVanilla() { return Collections.unmodifiableSet(enabledVanillaAdvancements); }

    // ═══════════════ Pending ═══════════════

    public void setPendingAdvancements(Set<String> set) {
        this.pendingAdvancements = set != null ? new HashSet<>(set) : new HashSet<>();
    }
    public Set<String> getPendingAdvancements() { return Collections.unmodifiableSet(pendingAdvancements); }
    public boolean isPending(String advId) { return pendingAdvancements.contains(advId); }
    public void updatePending(String advId, boolean pending) {
        if (pending) pendingAdvancements.add(advId);
        else pendingAdvancements.remove(advId);
    }

    // ═══════════════ 原版进度 ═══════════════

    /** 设置原版进度列表并重建 ID→Entry 映射表 */
    public void setVanillaAdvancements(List<VanillaAdvEntry> list) {
        this.vanillaAdvancements = list != null ? list : new ArrayList<>();
        Map<String, VanillaAdvEntry> newMap = new HashMap<>();
        for (VanillaAdvEntry e : vanillaAdvancements) newMap.put(e.id(), e);
        vanillaAdvEntryMap = newMap;
        markTabsDirty();
    }
    public List<VanillaAdvEntry> getVanillaAdvancements() { return Collections.unmodifiableList(vanillaAdvancements); }
    public VanillaAdvEntry getVanillaAdvEntry(String id) { return vanillaAdvEntryMap.get(id); }

    // ═══════════════ 原版元数据 ═══════════════

    public void setVanillaMeta(Map<String, VanillaAdvMeta> meta) {
        this.vanillaMeta = meta != null ? meta : new HashMap<>();
    }
    public Map<String, VanillaAdvMeta> getVanillaMeta() { return Collections.unmodifiableMap(vanillaMeta); }
    public VanillaAdvMeta getVanillaMeta(String id) { return vanillaMeta.get(id); }

    public void setVanillaParentMap(Map<String, String> map) {
        this.vanillaParentMap = map != null ? map : new HashMap<>();
    }
    public Map<String, String> getVanillaParentMap() { return Collections.unmodifiableMap(vanillaParentMap); }

    // ═══════════════ 玩家叙事统计 ═══════════════

    /** 设置玩家叙事统计数据（来自服务端全量同步） */
    public void setPlayerStats(PlayerStats stats) {
        this.playerStats = stats != null ? stats : new PlayerStats();
        this.statsVersion.incrementAndGet();
    }
    public PlayerStats getPlayerStats() { return playerStats; }
    public int getStatsVersion() { return statsVersion.get(); }

    // ═══════════════ Timeline 数据 ═══════════════

    private volatile String timelineData = "[]";
    private volatile int timelineVersion = 0;

    public void setTimelineData(String json) {
        this.timelineData = json != null ? json : "[]";
        this.timelineVersion++;
    }
    public String getTimelineData() { return timelineData; }
    public int getTimelineVersion() { return timelineVersion; }

    /**
     * 获取原版进度所属的显示标签页。
     * 优先使用元数据中配置的 tab，无配置时默认使用 TAB_VANILLA。
     */
    public String getVanillaDisplayTab(String vanillaId) {
        VanillaAdvMeta meta = vanillaMeta.get(vanillaId);
        if (meta != null && meta.getTab() != null && !meta.getTab().isEmpty()) {
            return meta.getTab();
        }
        return DataStore.TAB_VANILLA;
    }

    /**
     * 获取原版进度在画布上的位置。
     * 返回 {@code int[]{x, y}} 或 null（未设置位置时）。
     */
    public int[] getVanillaPosition(String vanillaId) {
        VanillaAdvMeta meta = vanillaMeta.get(vanillaId);
        if (meta != null && meta.hasPosition()) {
            return new int[]{meta.getX(), meta.getY()};
        }
        return null;
    }

    // ═══════════════ 标签页查询 ═══════════════

    /**
     * 获取所有标签页列表。
     * <p>
     * 排序规则：TAB_VANILLA → TAB_DEFAULT → tabOrder → 未排序的内置标签页 → customTabs → 其他使用中的标签页。
     * 结果被缓存直到 {@link #markTabsDirty} 被调用。
     */
    public List<String> getTabs() {
        if (!tabsDirty) return cachedTabs;

        Set<String> usedTabs = new HashSet<>();
        boolean hasDefaultContent = false;
        for (CustomAdvancement adv : advancements.values()) {
            String tab = adv.getTab();
            if (tab != null && !tab.isEmpty()) {
                usedTabs.add(tab);
            } else {
                hasDefaultContent = true;
            }
        }
        for (VanillaAdvMeta meta : vanillaMeta.values()) {
            if (meta.getTab() != null && !meta.getTab().isEmpty()) usedTabs.add(meta.getTab());
        }

        LinkedHashSet<String> all = new LinkedHashSet<>();
        all.add(DataStore.TAB_VANILLA);
        // 默认分类（"默认"）始终显示
        all.add(DataStore.TAB_DEFAULT);

        for (String t : tabOrder) {
            if (t.equals(DataStore.TAB_VANILLA) || t.equals(DataStore.TAB_DEFAULT)) continue;
            if (DataStore.isBuiltinTab(t) && !usedTabs.contains(t)) continue;
            all.add(t);
        }

        for (String t : DataStore.BUILTIN_TABS) {
            if (t.equals(DataStore.TAB_VANILLA) || t.equals(DataStore.TAB_DEFAULT)) continue;
            if (all.contains(t)) continue;
            if (usedTabs.contains(t)) all.add(t);
        }

        all.addAll(customTabs);

        for (String t : usedTabs) {
            if (!all.contains(t)) all.add(t);
        }

        cachedTabs = new ArrayList<>(all);
        tabsDirty = false;
        return cachedTabs;
    }

    public boolean isBuiltinTab(String tab) { return DataStore.isBuiltinTab(tab); }

    /** 获取指定标签页下的自定义进度列表（结果已复制，外部修改安全） */
    public List<CustomAdvancement> getAdvancementsByTab(String tab) {
        if (tab == null || tab.isEmpty()) return new ArrayList<>(advancements.values());
        if (tabIndexDirty) rebuildTabIndex();
        List<CustomAdvancement> result = cachedTabIndex.get(tab);
        return result != null ? new ArrayList<>(result) : Collections.emptyList();
    }

    /** 获取指定标签页下的原版进度 ID 列表 */
    public List<String> getVanillaIdsByTab(String tab) {
        List<String> ids = new ArrayList<>();
        for (var entry : vanillaMeta.entrySet()) {
            if (tab.equals(entry.getValue().getTab())) ids.add(entry.getKey());
        }
        return ids;
    }

    /** 获取指定标签页下的所有进度 ID（自定义 + 原版） */
    public List<String> getAllIdsByTab(String tab) {
        List<String> ids = new ArrayList<>();
        for (CustomAdvancement adv : getAdvancementsByTab(tab)) ids.add(adv.getId());
        ids.addAll(getVanillaIdsByTab(tab));
        return ids;
    }

    /** 重建标签页索引（advancements 变更时通过脏标记触发） */
    private void rebuildTabIndex() {
        Map<String, List<CustomAdvancement>> index = new HashMap<>();
        for (CustomAdvancement adv : advancements.values()) {
            String tab = adv.getTab();
            if (tab == null || tab.isEmpty()) {
                tab = DataStore.TAB_DEFAULT;
            }
            index.computeIfAbsent(tab, k -> new ArrayList<>()).add(adv);
        }
        cachedTabIndex = index;
        tabIndexDirty = false;
    }

    /** 获取所有隐藏的进度 */
    public List<CustomAdvancement> getHiddenAdvancements() {
        List<CustomAdvancement> list = new ArrayList<>();
        for (CustomAdvancement adv : advancements.values()) {
            if (adv.isHidden()) list.add(adv);
        }
        return list;
    }

    // ═══════════════ 统计 ═══════════════

    public int getTotalCount() { return advancements.size(); }
    public int getCompletedCount() { return completedCount.get(); }

    /** 计算指定标签页的总进度数（自定义 + 原版） */
    public int getTabTotalCount(String tab) {
        if (tabIndexDirty) rebuildTabIndex();
        int count = 0;
        List<CustomAdvancement> list = cachedTabIndex.get(tab);
        if (list != null) count += list.size();
        if (DataStore.TAB_VANILLA.equals(tab)) {
            for (VanillaAdvEntry e : vanillaAdvancements) {
                VanillaAdvMeta meta = vanillaMeta.get(e.id());
                if (meta == null || meta.getTab() == null || meta.getTab().isEmpty()
                        || DataStore.TAB_VANILLA.equals(meta.getTab())) {
                    count++;
                }
            }
        }
        return count;
    }

    /** 计算指定标签页的已完成进度数 */
    public int getTabCompletedCount(String tab) {
        if (tabIndexDirty) rebuildTabIndex();
        int count = 0;
        List<CustomAdvancement> list = cachedTabIndex.get(tab);
        if (list != null) {
            for (CustomAdvancement a : list) {
                if (isCompleted(a.getId())) count++;
            }
        }
        if (DataStore.TAB_VANILLA.equals(tab)) {
            for (VanillaAdvEntry e : vanillaAdvancements) {
                VanillaAdvMeta meta = vanillaMeta.get(e.id());
                if (meta == null || meta.getTab() == null || meta.getTab().isEmpty()
                        || DataStore.TAB_VANILLA.equals(meta.getTab())) {
                    if (isCompleted(e.id())) count++;
                }
            }
        }
        return count;
    }
}
