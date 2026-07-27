package com.example.advancementoverhaul.data;

import com.example.advancementoverhaul.Config;
import com.example.advancementoverhaul.data.DataStore.ConditionType;
import com.example.advancementoverhaul.data.model.CustomAdvancement;
import com.example.advancementoverhaul.data.model.VanillaAdvMeta;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 服务端数据存储协调器（单例）。
 * <p>
 * <b>一句话作用：</b>统一管理模组的所有持久化数据，协调四个内部模块完成数据的增删改查和文件读写。
 * <p>
 * 本类本身不直接操作数据，而是将请求委托给四个专门模块：
 * <ul>
 *   <li>{@link AdvancementStore} — 成就 CRUD、条件索引</li>
 *   <li>{@link PlayerDataStore} — 玩家完成状态、进度、pending 标记</li>
 *   <li>{@link TabStore} — 自定义标签页、排序</li>
 *   <li>{@link VanillaStateStore} — 原版进度启用/禁用、元数据、缓存</li>
 * </ul>
 * <p>
 * <b>维度锁定</b>数据较简单，直接在本类中管理。
 * <p>
 * <b>线程安全：</b>异步文件写入通过单线程 {@code saveExecutor} 完成，
 * 复合写操作通过各模块内部的写锁保护。
 *
 * @see AdvancementStore 成就存储
 * @see PlayerDataStore 玩家数据存储
 * @see TabStore 标签页存储
 * @see VanillaStateStore 原版状态存储
 */
public class ServerDataStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul");

    // ═══════════════ 文件路径常量 ═══════════════

    private static final String ADV_FILE          = "advancements.json";
    private static final String VANILLA_FILE      = "vanilla_states.json";
    private static final String VANILLA_RAW_FILE  = "vanilla_raw_cache.json";
    private static final String VANILLA_META_FILE = "vanilla_meta.json";
    private static final String TAB_ORDER_FILE    = "tab_order.json";

    // ═══════════════ 单例 ═══════════════

    private static final ServerDataStore INSTANCE = new ServerDataStore();
    public static ServerDataStore getInstance() { return INSTANCE; }
    private ServerDataStore() {}

    // ═══════════════ 内部模块 ═══════════════

    private final AdvancementStore   advStore   = new AdvancementStore();
    private final PlayerDataStore    playerStore = new PlayerDataStore();
    private final TabStore           tabStore    = new TabStore();
    private final VanillaStateStore  vanillaStore = new VanillaStateStore();

    // ═══════════════ 维度锁（数据简单，直接管理） ═══════════════

    private final Map<String, DimensionLock> dimensionLocks = new ConcurrentHashMap<>();

    // ═══════════════ 基础设施 ═══════════════

    /** 异步文件写入线程（单线程守护线程，保证写入顺序） */
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AO-Save");
        t.setDaemon(true);
        return t;
    });

    private volatile MinecraftServer server;
    private volatile Path dataFolder;
    private volatile Path playerDataFolder;
    private long lastAutoSave = 0;

    // ══════════════════════════════════════════════════════════
    // 生命周期
    // ══════════════════════════════════════════════════════════

    /**
     * 初始化数据目录并加载所有持久化文件。
     * 只会执行一次（重复调用直接返回）。
     */
    public void init(Path configDir) {
        if (this.dataFolder != null) return;
        Path folder = configDir.resolve("advancement_overhaul");
        try { Files.createDirectories(folder); }
        catch (IOException e) { LOGGER.error("Failed to create config data folder", e); return; }
        this.dataFolder = folder;

        advStore.loadFromFile(folder.resolve(ADV_FILE), DataStore.GSON);
        vanillaStore.loadStates(folder.resolve(VANILLA_FILE));
        vanillaStore.loadMeta(folder.resolve(VANILLA_META_FILE));
        tabStore.loadOrder(folder.resolve(TAB_ORDER_FILE));
    }

    /**
     * 设置服务端引用并初始化玩家数据目录。
     * 首次调用时创建目录、迁移旧数据、加载玩家文件。
     * 同时缓存原版进度原始 JSON。
     */
    public void setServer(MinecraftServer server) {
        if (this.dataFolder == null) {
            LOGGER.error("ServerDataStore.setServer() called before init() — data folder not set");
            return;
        }
        this.server = server;
        if (this.playerDataFolder == null) {
            this.playerDataFolder = server.getWorldPath(LevelResource.ROOT)
                    .resolve("advancement_overhaul");
            try { Files.createDirectories(playerDataFolder); }
            catch (IOException e) { LOGGER.error("Failed to create player data folder", e); }
            Path playerDir = playerDataFolder.resolve("player_data");
            try { Files.createDirectories(playerDir); }
            catch (IOException e) { LOGGER.error("Failed to create player_data directory", e); }

            playerStore.migrateFromLegacy(playerDataFolder);
            playerStore.loadFromDir(playerDataFolder);
        }
        if (vanillaStore.getRawCache() == null) {
            vanillaStore.cacheFromServer(server);
            // 首次缓存后处理 enabledMods 配置
            processEnabledMods();
        }
    }

    /** 定期保存检查（由 ServerTickEvent 调用） */
    public void tick() {
        long intervalMs = Config.PLAYER_DATA_SAVE_INTERVAL.get() * 50L;
        if (System.currentTimeMillis() - lastAutoSave > intervalMs) {
            savePlayerDataIfDirty();
            lastAutoSave = System.currentTimeMillis();
        }
    }

    /**
     * 安全关闭：保存脏数据 → 停止异步写线程 → 等待最多 5 秒。
     */
    public void shutdown() {
        savePlayerDataIfDirty();
        saveExecutor.shutdown();
        try {
            if (!saveExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                saveExecutor.shutdownNow();
                LOGGER.warn("Save executor did not terminate in 5s, forcing shutdown");
            }
        } catch (InterruptedException e) {
            saveExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ═══════════════ 文件 I/O 辅助 ═══════════════

    /** 异步写入文件（先写临时文件再原子替换，防止崩溃时数据损坏）。
     * executor 已关闭时回退到同步写入。 */
    private void asyncWrite(Path path, String content) {
        if (saveExecutor.isShutdown()) {
            try {
                Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
                Files.writeString(tmp, content);
                Files.move(tmp, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) { LOGGER.error("Failed to save {} synchronously", path.getFileName(), e); }
            return;
        }
        saveExecutor.submit(() -> {
            try {
                Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
                Files.writeString(tmp, content);
                Files.move(tmp, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) { LOGGER.error("Failed to save {}", path.getFileName(), e); }
        });
    }

    // Getters
    public MinecraftServer getServer() { return server; }
    public Path getDataFolder() { return dataFolder; }

    // ═══════════════ 成就变更回调 ═══════════════

    /**
     * 注册成就变更回调（由 AdvancementOverhaul 在初始化时调用）。
     * 每次 addAdvancement / removeAdvancement 自动触发运行时增量更新（FTB Quests 兼容）。
     */
    public static void setOnAdvancementChanged(Consumer<String> callback) {
        INSTANCE.advStore.setOnChange(callback);
    }

    // ══════════════════════════════════════════════════════════
    // [区域 1] 成就数据 CRUD（委托给 AdvancementStore）
    // ══════════════════════════════════════════════════════════

    /** 获取所有成就的 Map（自定义 ID → 成就对象）。返回原始引用，只应读取不应修改。 */
    public Map<String, CustomAdvancement> getAdvancements() { return advStore.getAll(); }

    /** 按 ID 获取单个成就，可能返回 null */
    public CustomAdvancement getAdvancement(String id) { return advStore.get(id); }

    /** 添加或更新成就（触发回调 + 自动保存） */
    public void addAdvancement(CustomAdvancement adv) {
        addAdvancement(adv, true);
    }

    /**
     * 添加或更新成就，可选择是否触发属性变更回调。
     * 位置（x/y）或分类（tab）变更应传 triggerCallback = false，避免不必要的 FTB Quests 同步。
     */
    public void addAdvancement(CustomAdvancement adv, boolean triggerCallback) {
        advStore.add(adv, triggerCallback, this::saveAdvancements);
    }

    /**
     * 删除成就并级联清理（玩家数据、其他成就的前提条件、维度锁引用）。
     * 自动保存并触发变更回调。
     */
    public void removeAdvancement(String id) {
        advStore.remove(id, this::saveAdvancements,
                playerStore::removeAdvancementFromAll,
                this::cleanDimensionLockReferences);
    }

    /** 删除成就（不自动保存），用于批量操作。调用方需手动调用 saveAdvancements。 */
    public void removeAdvancementNoSave(String id) {
        advStore.removeNoSave(id,
                playerStore::removeAdvancementFromAll,
                this::cleanDimensionLockReferences);
    }

    /** 清理引用被删除成就的维度锁 */
    private void cleanDimensionLockReferences(String advId) {
        for (var entry : dimensionLocks.entrySet()) {
            DimensionLock lock = entry.getValue();
            if (advId.equals(lock.getUnlockAdvancementId())) {
                lock.setUnlockAdvancementId(null);
                lock.setDisabled(false);
                LOGGER.warn("Dimension lock '{}' referenced deleted advancement '{}', lock disabled",
                        entry.getKey(), advId);
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    // [区域 2] 玩家数据（委托给 PlayerDataStore）
    // ══════════════════════════════════════════════════════════

    public Map<String, Boolean> getPlayerCompletions(UUID uuid) { return playerStore.getCompletions(uuid); }
    public boolean isCompleted(UUID uuid, String advId) { return playerStore.isCompleted(uuid, advId); }
    public void setCompleted(UUID uuid, String advId, boolean v) { playerStore.setCompleted(uuid, advId, v); }

    public Map<String, Map<Integer, Integer>> getPlayerProgress(UUID uuid) { return playerStore.getProgress(uuid); }
    public int getConditionProgress(UUID uuid, String advId, int condIndex) { return playerStore.getConditionProgress(uuid, advId, condIndex); }
    public void setConditionProgress(UUID uuid, String advId, int condIndex, int value) { playerStore.setConditionProgress(uuid, advId, condIndex, value); }
    public void resetConditionProgress(UUID uuid, String advId) { playerStore.resetConditionProgress(uuid, advId); }

    /** 计算指定成就的总完成百分比（0-100） */
    public int getProgress(UUID uuid, String advId) {
        return playerStore.getProgressPercent(uuid, advId, getAdvancement(advId));
    }

    /** 获取玩家进度的快照（advId → 0-100 百分比），用于网络同步 */
    public Map<String, Integer> getPlayerProgressSnapshot(UUID uuid) {
        return playerStore.getProgressSnapshot(uuid, advStore.getAll());
    }

    public Set<String> getPendingAdvancements(UUID uuid) { return playerStore.getPending(uuid); }
    public boolean isPending(UUID uuid, String advId) { return playerStore.isPending(uuid, advId); }
    public void setPending(UUID uuid, String advId, boolean pending) { playerStore.setPending(uuid, advId, pending); }

    /** 仅在脏标记为 true 时保存玩家数据（CAS 原子操作，异步写入） */
    public void savePlayerDataIfDirty() {
        Path dir = playerDataFolder != null ? playerDataFolder : dataFolder;
        if (dir != null) playerStore.saveIfDirty(dir, saveExecutor);
    }

    // ══════════════════════════════════════════════════════════
    // [区域 3] 标签页（委托给 TabStore）
    // ══════════════════════════════════════════════════════════

    public List<String> getCustomTabs() { return tabStore.getCustomTabs(); }

    public void addCustomTab(String name) { tabStore.addCustomTab(name); }

    /** 删除自定义标签页并清理引用 */
    public void removeCustomTab(String name) {
        tabStore.removeCustomTab(name, advStore.getAll(),
                this::saveTabOrder, this::saveAdvancements);
    }

    public List<String> getTabOrder() { return tabStore.getTabOrder(); }

    public void setTabOrder(List<String> order) {
        tabStore.setTabOrder(order);
        saveTabOrder();
    }

    private void saveTabOrder() {
        if (dataFolder == null) return;
        asyncWrite(dataFolder.resolve(TAB_ORDER_FILE), tabStore.getOrderJson());
    }

    // ══════════════════════════════════════════════════════════
    // [区域 4] 维度锁（数据简单，直接管理）
    // ══════════════════════════════════════════════════════════

    public Map<String, DimensionLock> getDimensionLocks() { return dimensionLocks; }
    public DimensionLock getDimensionLock(String dim) { return dimensionLocks.get(dim); }
    public void setDimensionLock(String dim, DimensionLock dl) { dimensionLocks.put(dim, dl); }

    // ══════════════════════════════════════════════════════════
    // [区域 5] 原版进度状态（委托给 VanillaStateStore）
    // ══════════════════════════════════════════════════════════

    public Set<String> getDisabledVanilla() { return vanillaStore.getDisabled(); }
    public Set<String> getEnabledVanilla() { return vanillaStore.getEnabled(); }
    public boolean isVanillaEnabled(String id) { return vanillaStore.isEnabled(id); }

    public void setVanillaEnabled(String id, boolean enabled) {
        vanillaStore.setEnabled(id, enabled);
        saveVanillaStates();
    }

    public void setVanillaDisabledBatch(Set<String> ids) {
        vanillaStore.setDisabledBatch(ids);
        saveVanillaStates();
    }

    public void enableAllVanilla(Set<String> allIds) {
        vanillaStore.enableAll(allIds);
        saveVanillaStates();
    }

    /**
     * 为所有已启用的原版进度自动分配标签页。
     * 按照进度树结构：每个根节点（无 parent 或 parent 未启用）创建一个标签页，
     * 该子树下的所有进度都归入该标签页。
     * 已有标签页分配的进度会被跳过，不会覆盖手动分配。
     * <p>
     * 调用时机：{@code /adv vanilla enableall}、服务端首次加载时处理 {@code enabledMods} 配置。
     */
    public void autoAssignVanillaTabs() {
        Map<String, String> parentMap = vanillaStore.getParentMap();
        Map<String, VanillaAdvMeta> metaMap = vanillaStore.getMetaMap();
        Set<String> enabled = vanillaStore.getEnabled();
        Map<String, JsonElement> rawCache = vanillaStore.getRawCache();

        if (enabled.isEmpty()) return;

        // 1. 为每个已启用的 ID 找到其根节点
        Map<String, List<String>> rootChildren = new LinkedHashMap<>(); // rootId → [childIds]

        for (String id : enabled) {
            String current = id;
            String root = id;
            Set<String> visited = new HashSet<>();
            while (true) {
                String parent = parentMap.get(current);
                if (parent == null || !enabled.contains(parent)) break;
                if (!visited.add(current)) break; // 循环检测
                current = parent;
                root = current;
            }
            rootChildren.computeIfAbsent(root, k -> new ArrayList<>()).add(id);
        }

        // 2. 为每个根节点确定标签页名称并分配（跳过已有标签页的进度）
        int assigned = 0;
        for (Map.Entry<String, List<String>> entry : rootChildren.entrySet()) {
            String rootId = entry.getKey();
            List<String> children = entry.getValue();

            // 从 rawCache 获取根节点的显示名
            String tabName = getAdvDisplayName(rootId, rawCache);
            if (tabName == null || tabName.isEmpty()) {
                tabName = rootId.replace(':', '_');
            }

            boolean hasNewAssignments = false;
            for (String childId : children) {
                VanillaAdvMeta meta = metaMap.get(childId);
                // 已有标签页分配则跳过，不覆盖手动分配
                if (meta != null && meta.getTab() != null && !meta.getTab().isEmpty()) continue;
                hasNewAssignments = true;
                if (meta == null) meta = new VanillaAdvMeta();
                meta.setTab(tabName);
                metaMap.put(childId, meta);
                assigned++;
            }
            if (hasNewAssignments) tabStore.addCustomTab(tabName);
        }

        if (assigned > 0) {
            saveVanillaMeta();
            saveTabOrder();
            LOGGER.info("Auto-assigned {} vanilla advancements to {} tabs", assigned, rootChildren.size());
        }
    }

    /**
     * 从 rawCache 获取进度的显示名称（已翻译的文本）。
     */
    private String getAdvDisplayName(String id, Map<String, JsonElement> rawCache) {
        if (rawCache == null) return null;
        JsonElement elem = rawCache.get(id);
        if (elem == null || !elem.isJsonObject()) return null;
        JsonObject obj = elem.getAsJsonObject();
        if (!obj.has("display") || !obj.get("display").isJsonObject()) return null;
        JsonObject display = obj.getAsJsonObject("display");
        if (!display.has("title")) return null;
        JsonElement title = display.get("title");
        if (title.isJsonObject()) {
            JsonObject titleObj = title.getAsJsonObject();
            if (titleObj.has("translate")) {
                // 返回可翻译键的最后一段作为分类名
                String key = titleObj.get("translate").getAsString();
                String[] parts = key.split("\\.");
                return parts[parts.length - 1];
            }
            if (titleObj.has("text")) return titleObj.get("text").getAsString();
        }
        if (title.isJsonPrimitive()) return title.getAsString();
        return null;
    }

    /**
     * 处理 {@code enabledMods} 配置：自动启用指定模组的所有进度并分配标签页。
     * 仅在 rawCache 已加载后调用一次（首次玩家登录前）。
     * 增量启用——不覆盖已有手动启用/禁用的状态。
     */
    public void processEnabledMods() {
        List<? extends String> mods = Config.ENABLED_MODS.get();
        if (mods == null || mods.isEmpty()) return;

        Map<String, JsonElement> rawCache = vanillaStore.getRawCache();
        if (rawCache == null) return;

        Set<String> disabled = vanillaStore.getDisabled();
        Set<String> toEnable = new HashSet<>();
        for (String modId : mods) {
            String prefix = modId + ":";
            for (String id : rawCache.keySet()) {
                if (id.startsWith(prefix) && !disabled.contains(id)) {
                    toEnable.add(id);
                }
            }
        }

        if (toEnable.isEmpty()) {
            LOGGER.warn("enabledMods configured but no advancements matched: {}", mods);
            return;
        }

        for (String id : toEnable) {
            vanillaStore.setEnabled(id, true);
        }
        saveVanillaStates();
        autoAssignVanillaTabs();
        LOGGER.info("Enabled {} advancements from mods: {}", toEnable.size(), mods);
    }

    private void saveVanillaStates() {
        if (dataFolder == null) return;
        asyncWrite(dataFolder.resolve(VANILLA_FILE),
                DataStore.GSON_PRETTY.toJson(vanillaStore.statesToJson()));
    }

    // 原版元数据
    public Map<String, VanillaAdvMeta> getVanillaMetaMap() { return vanillaStore.getMetaMap(); }
    public VanillaAdvMeta getVanillaMeta(String id) { return vanillaStore.getMeta(id); }
    public void setVanillaMeta(String id, VanillaAdvMeta meta) {
        vanillaStore.setMeta(id, meta);
        saveVanillaMeta();
    }

    /** 保存原版元数据到文件（供内部和外部如 VanillaExecutor 调用） */
    public void saveVanillaMeta() {
        if (dataFolder == null) return;
        asyncWrite(dataFolder.resolve(VANILLA_META_FILE), vanillaStore.metaToJson());
    }

    // 原版原始缓存
    public Map<String, JsonElement> getVanillaAdvRawCache() { return vanillaStore.getRawCache(); }
    public void setVanillaAdvRawCache(Map<String, JsonElement> cache) { vanillaStore.setRawCache(cache); }
    public Map<String, String> getVanillaParentMap() { return vanillaStore.getParentMap(); }

    /** 从服务端 AdvancementManager 缓存所有非自定义进度的原始 JSON */
    public void cacheVanillaAdvancements() {
        vanillaStore.cacheFromServer(server);
        if (dataFolder != null && vanillaStore.getRawCache() != null) {
            asyncWrite(dataFolder.resolve(VANILLA_RAW_FILE), vanillaStore.rawCacheToJson());
        }
    }

    // ══════════════════════════════════════════════════════════
    // 条件索引（委托给 AdvancementStore）
    // ══════════════════════════════════════════════════════════

    public List<String> getAdvIdsByConditionType(ConditionType type) {
        return advStore.getAdvIdsByConditionType(type);
    }

    public List<String> getAdvIdsByCondition(ConditionType type, String targetId) {
        return advStore.getAdvIdsByCondition(type, targetId);
    }

    // ══════════════════════════════════════════════════════════
    // 文件持久化
    // ══════════════════════════════════════════════════════════

    /** 保存成就数据到文件 */
    public void saveAdvancements() {
        if (dataFolder == null) return;
        asyncWrite(dataFolder.resolve(ADV_FILE), advStore.toJson(DataStore.GSON_PRETTY));
    }

    /** 保存玩家数据（供外部调用，如 CommandHelper.syncTargetPlayer） */
    public void savePlayerData() {
        Path dir = playerDataFolder != null ? playerDataFolder : dataFolder;
        if (dir != null) playerStore.saveAll(dir);
    }

    /** 保存所有持久化数据 */
    public void saveAll() {
        saveAdvancements();
        savePlayerDataIfDirty();
        saveVanillaStates();
        saveVanillaMeta();
        saveTabOrder();
    }

    /** 从文件重新加载所有数据（不重置内存，增量更新） */
    public void forceReload() {
        advStore.loadFromFile(dataFolder.resolve(ADV_FILE), DataStore.GSON);
        vanillaStore.loadStates(dataFolder.resolve(VANILLA_FILE));
        vanillaStore.loadMeta(dataFolder.resolve(VANILLA_META_FILE));
        tabStore.loadOrder(dataFolder.resolve(TAB_ORDER_FILE));
        if (playerDataFolder != null) playerStore.loadFromDir(playerDataFolder);
        if (server != null && vanillaStore.getRawCache() == null) vanillaStore.cacheFromServer(server);
    }

    // ══════════════════════════════════════════════════════════
    // 导入/导出
    // ══════════════════════════════════════════════════════════

    /** 导出所有配置数据为 JSON（成就、标签页、维度锁、原版元数据、标签页顺序） */
    public JsonObject exportAll() {
        JsonObject root = new JsonObject();
        root.add("advancements", DataStore.GSON.toJsonTree(advStore.getAll()));
        root.add("customTabs", DataStore.GSON.toJsonTree(new ArrayList<>(tabStore.getCustomTabs())));
        root.add("dimensionLocks", DataStore.GSON.toJsonTree(dimensionLocks));
        root.add("vanillaMeta", DataStore.GSON.toJsonTree(vanillaStore.getMetaMap()));
        root.add("tabOrder", DataStore.GSON.toJsonTree(new ArrayList<>(tabStore.getTabOrder())));
        return root;
    }

    /** 从 JSON 导入配置数据（覆盖现有数据并自动保存）。
     * 导入前先创建备份，如果导入数据格式不完整则拒绝导入。 */
    public void importAll(JsonObject data) {
        // 1. 验证必需字段
        if (!data.has("advancements") && !data.has("customTabs")
                && !data.has("dimensionLocks") && !data.has("vanillaMeta")
                && !data.has("tabOrder")) {
            LOGGER.warn("Import rejected: JSON contains no recognized data sections");
            throw new IllegalArgumentException("Import data contains no recognized sections");
        }

        // 2. 创建备份（用于导入失败时回滚）
        JsonObject backup = exportAll();

        try {
            // 3. 验证并应用 각 部分
            if (data.has("advancements")) {
                Map<String, CustomAdvancement> advs = DataStore.GSON.fromJson(
                        data.get("advancements"),
                        new com.google.gson.reflect.TypeToken<Map<String, CustomAdvancement>>() {}.getType());
                if (advs == null || advs.isEmpty()) {
                    LOGGER.warn("Import: advancements section is empty or invalid");
                } else {
                    for (CustomAdvancement adv : advs.values()) {
                        // 验证每个成就的关键字段
                        if (adv.getId() == null || adv.getId().isEmpty()) {
                            throw new IllegalArgumentException("Import contains advancement with empty ID");
                        }
                        String tab = adv.getTab();
                        if (tab != null) adv.setTab(DataStore.normalizeTabName(tab));
                    }
                    advStore.replaceAll(advs);
                }
            }
            if (data.has("customTabs")) {
                List<String> tabs = DataStore.GSON.fromJson(data.get("customTabs"),
                        new com.google.gson.reflect.TypeToken<List<String>>() {}.getType());
                if (tabs != null) {
                    synchronized (tabStore.getCustomTabs()) {
                        tabStore.getCustomTabs().clear();
                        tabStore.getCustomTabs().addAll(tabs);
                    }
                }
            }
            if (data.has("dimensionLocks")) {
                Map<String, DimensionLock> locks = DataStore.GSON.fromJson(
                        data.get("dimensionLocks"),
                        new com.google.gson.reflect.TypeToken<Map<String, DimensionLock>>() {}.getType());
                if (locks != null) { dimensionLocks.clear(); dimensionLocks.putAll(locks); }
            }
            if (data.has("vanillaMeta")) {
                Map<String, VanillaAdvMeta> meta = DataStore.GSON.fromJson(
                        data.get("vanillaMeta"),
                        new com.google.gson.reflect.TypeToken<Map<String, VanillaAdvMeta>>() {}.getType());
                if (meta != null) { vanillaStore.getMetaMap().clear(); vanillaStore.getMetaMap().putAll(meta); }
            }
            if (data.has("tabOrder")) {
                List<String> order = DataStore.GSON.fromJson(data.get("tabOrder"),
                        new com.google.gson.reflect.TypeToken<List<String>>() {}.getType());
                if (order != null) tabStore.setTabOrder(order);
            }
            saveAll();
            LOGGER.info("Import successful: {} advancements, {} tabs, {} dimension locks",
                    advStore.getAll().size(),
                    tabStore.getCustomTabs().size(),
                    dimensionLocks.size());
        } catch (Exception e) {
            // 4. 导入失败时回滚到备份
            LOGGER.error("Import failed, rolling back to backup. Error: {}", e.getMessage());
            try {
                importAll(backup);
                LOGGER.info("Rollback successful");
            } catch (Exception rollbackError) {
                LOGGER.error("CRITICAL: Rollback also failed! Data may be inconsistent. Error: {}",
                        rollbackError.getMessage());
            }
            throw new RuntimeException("Import failed: " + e.getMessage(), e);
        }
    }
}
