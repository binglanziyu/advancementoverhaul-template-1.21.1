package com.dreamer.ao.data;

import com.dreamer.ao.data.ConditionType;
import com.dreamer.ao.data.model.AdvancementCondition;
import com.dreamer.ao.data.model.CustomAdvancement;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * 自定义成就（进度）的存储与查询模块。
 * <p>
 * 负责成就数据的增删改查、条件索引的构建与查询、以及成就文件的持久化。
 * 所有写操作通过 {@link #writeLock} 序列化，读操作直接操作 {@link ConcurrentHashMap}。
 * <p>
 * 本类不直接处理玩家数据、标签页、维度锁等职责——
 * 这些由 {@link PlayerDataStore}、{@link TabStore}、{@link VanillaStateStore} 分别管理。
 *
 * @see ServerDataStore 使用本类的单例协调器
 */
final class AdvancementStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdvancementStore.class);

    // ═══════════════ 成就数据 ═══════════════

    /** 所有自定义成就：自定义 ID → 成就对象（ConcurrentHashMap 保证读线程安全） */
    private final Map<String, CustomAdvancement> advancements = new ConcurrentHashMap<>();

    /** 写锁：保护复合操作（如删除时遍历并修改其他成就的前置条件） */
    private final Object writeLock = new Object();

    /** 反向前置索引：前置 ID → 依赖它的成就 ID 集合（用于 O(1) 级联清理） */
    private final Map<String, Set<String>> reversePrereqIndex = new ConcurrentHashMap<>();

    /** 条件索引读写锁：允许多个读线程并发访问，仅重建索引时独占 */
    private final ReadWriteLock indexRebuildLock = new ReentrantReadWriteLock();

    // ═══════════════ 条件索引 ═══════════════

    private final ConditionIndex conditionIndex = new ConditionIndex();
    private volatile boolean conditionIndexDirty = true;

    // ═══════════════ 变更回调 ═══════════════

    /**
     * 成就变更回调（由 AdvancementOverhaul 注册）。
     * 每次新增/更新/删除成就时调用，用于 FTB Quests 实时同步。
     */
    private volatile Consumer<String> onChangeCallback;

    void setOnChange(Consumer<String> callback) { this.onChangeCallback = callback; }

    // ═══════════════ CRUD ═══════════════

    /** 获取所有成就的只读视图（返回原始 Map 引用——调用方只应读取） */
    Map<String, CustomAdvancement> getAll() { return advancements; }

    /** 按 ID 获取单个成就（可能返回 null） */
    CustomAdvancement get(String id) { return advancements.get(id); }

    /**
     * 添加或更新成就。
     * @param adv             成就对象
     * @param triggerCallback 是否触发变更回调（位置/分类变更传 false）
     * @param saveFn          保存回调（由 ServerDataStore 提供文件写入逻辑）
     */
    void add(CustomAdvancement adv, boolean triggerCallback, Runnable saveFn) {
        String advId = adv.getId();
        List<String> newPrereqs = adv.getPrerequisites();
        synchronized (writeLock) {
            CustomAdvancement old = advancements.get(advId);
            advancements.put(advId, adv);
            conditionIndexDirty = true;

            // 维护反向前置索引
            if (old != null) {
                for (String oldPrereq : old.getPrerequisites()) {
                    removeFromReverseIndex(oldPrereq, advId);
                }
            }
            if (newPrereqs != null) {
                for (String prereq : newPrereqs) {
                    addToReverseIndex(prereq, advId);
                }
            }
        }
        saveFn.run();
        if (triggerCallback) {
            Consumer<String> cb = onChangeCallback;
            if (cb != null) cb.accept(advId);
        }
    }

    /**
     * 删除成就并级联清理（玩家数据、其他成就的前提条件、维度锁由调用方负责）。
     * @param id              成就 ID
     * @param saveFn          保存回调
     * @param cleanupPlayerFn 清理玩家数据的回调（传入成就 ID）
     * @param cleanupDimLockFn 清理维度锁引用的回调（传入成就 ID）
     */
    void remove(String id, Runnable saveFn,
                Consumer<String> cleanupPlayerFn,
                Consumer<String> cleanupDimLockFn) {
        synchronized (writeLock) {
            // 从成就 Map 中移除
            CustomAdvancement removed = advancements.remove(id);
            conditionIndexDirty = true;

            if (removed != null) {
                // 从反向索引清理自身的前置引用
                for (String prereq : removed.getPrerequisites()) {
                    removeFromReverseIndex(prereq, id);
                }
            }

            // 从依赖此成就的其他成就中清理前置条件（使用反向索引 O(1) 查找）
            Set<String> dependents = reversePrereqIndex.remove(id);
            if (dependents != null) {
                for (String depId : dependents) {
                    CustomAdvancement adv = advancements.get(depId);
                    if (adv != null) {
                        List<String> prereqs = new ArrayList<>(adv.getPrerequisites());
                        if (prereqs.remove(id)) {
                            adv.setPrerequisites(prereqs);
                        }
                    }
                }
            }
        }
        // 级联清理（在锁外执行）
        cleanupPlayerFn.accept(id);
        cleanupDimLockFn.accept(id);
        saveFn.run();

        Consumer<String> cb = onChangeCallback;
        if (cb != null) cb.accept(id);
    }

    /**
     * 删除成就（不自动保存，用于批量操作）。
     * 调用方需手动保存，并负责通知回调。
     */
    void removeNoSave(String id, Consumer<String> cleanupPlayerFn,
                      Consumer<String> cleanupDimLockFn) {
        synchronized (writeLock) {
            CustomAdvancement removed = advancements.remove(id);
            conditionIndexDirty = true;

            if (removed != null) {
                for (String prereq : removed.getPrerequisites()) {
                    removeFromReverseIndex(prereq, id);
                }
            }

            Set<String> dependents = reversePrereqIndex.remove(id);
            if (dependents != null) {
                for (String depId : dependents) {
                    CustomAdvancement adv = advancements.get(depId);
                    if (adv != null) {
                        List<String> prereqs = new ArrayList<>(adv.getPrerequisites());
                        if (prereqs.remove(id)) {
                            adv.setPrerequisites(prereqs);
                        }
                    }
                }
            }
        }
        cleanupPlayerFn.accept(id);
        cleanupDimLockFn.accept(id);
    }

    // ═══════════════ 条件索引 ═══════════════

    /** 按条件类型查询相关的成就 ID 列表 */
    List<String> getAdvIdsByConditionType(ConditionType type) {
        ensureIndexFresh();
        indexRebuildLock.readLock().lock();
        try {
            return conditionIndex.getByType(type);
        } finally {
            indexRebuildLock.readLock().unlock();
        }
    }

    /** 按条件类型+目标 ID 查询相关的成就 ID 列表 */
    List<String> getAdvIdsByCondition(ConditionType type, String targetId) {
        ensureIndexFresh();
        indexRebuildLock.readLock().lock();
        try {
            return conditionIndex.getByTypeAndTarget(type, targetId);
        } finally {
            indexRebuildLock.readLock().unlock();
        }
    }

    /** 按条件类型+目标 ID 查询条件级别索引，返回 (advId, condIndex) 对列表，直接跳转到匹配条件 */
    List<ConditionIndex.AdvIdCondIndex> getAdvCondIndexesByCondition(ConditionType type, String targetId) {
        ensureIndexFresh();
        indexRebuildLock.readLock().lock();
        try {
            return conditionIndex.getAdvCondIndexesByCondition(type, targetId);
        } finally {
            indexRebuildLock.readLock().unlock();
        }
    }

    private void ensureIndexFresh() {
        if (conditionIndexDirty) rebuildConditionIndex();
    }

    /** 重建条件索引（使用写锁，允许并发读）。
     *  同时预计算通配符合并索引和条件级别索引，避免每次查询时去重合并或全量遍历。 */
    void rebuildConditionIndex() {
        if (!conditionIndexDirty) return;

        // 先快照数据（在读锁外），避免在写锁内长时间持有 writeLock
        List<CustomAdvancement> snapshot;
        synchronized (writeLock) {
            snapshot = new ArrayList<>(advancements.values());
        }

        Map<ConditionType, List<String>> typeMap = new HashMap<>();
        Map<String, List<String>> targetMap = new HashMap<>();
        Map<ConditionType, Set<String>> wildcardMap = new EnumMap<>(ConditionType.class);
        /** 条件级别索引：key = ConditionType:targetId → (advId, condIndex) 列表 */
        Map<String, List<ConditionIndex.AdvIdCondIndex>> condIndexMap = new HashMap<>();

        for (CustomAdvancement adv : snapshot) {
            Set<ConditionType> seen = EnumSet.noneOf(ConditionType.class);
            List<? extends AdvancementCondition> conditions = adv.getConditions();
            for (int ci = 0; ci < conditions.size(); ci++) {
                var cond = conditions.get(ci);
                if (seen.add(cond.getType())) {
                    typeMap.computeIfAbsent(cond.getType(), k -> new ArrayList<>()).add(adv.getId());
                }
                if (cond.getTargetId() == null || cond.getTargetId().isEmpty()) {
                    wildcardMap.computeIfAbsent(cond.getType(), k -> new LinkedHashSet<>())
                            .add(adv.getId());
                } else {
                    String key = cond.getType().name() + ":" + cond.getTargetId();
                    targetMap.computeIfAbsent(key, k -> new ArrayList<>()).add(adv.getId());
                    // 条件级别索引：记录 (advId, condIndex) 直接跳到匹配条件
                    condIndexMap.computeIfAbsent(key, k -> new ArrayList<>())
                            .add(new ConditionIndex.AdvIdCondIndex(adv.getId(), ci));
                }
            }
        }

        // 通配符条件也匹配到所有同类型的目标条件
        // O(N×M×L) 优化：使用 Set 批量合并去重，消除逐元素 contains 检查
        for (var entry : wildcardMap.entrySet()) {
            ConditionType type = entry.getKey();
            Set<String> wildcardAdvIds = entry.getValue();
            String prefix = type.name() + ":";
            for (var targetEntry : targetMap.entrySet()) {
                if (targetEntry.getKey().startsWith(prefix)) {
                    Set<String> merged = new LinkedHashSet<>(targetEntry.getValue());
                    merged.addAll(wildcardAdvIds);
                    targetEntry.setValue(new ArrayList<>(merged));
                }
            }
        }

        // 预计算合并索引
        Map<String, List<String>> mergedMap = new HashMap<>(targetMap.size());
        for (var entry : targetMap.entrySet()) {
            String fullKey = entry.getKey();
            List<String> baseIds = entry.getValue();
            int colonIdx = fullKey.indexOf(':');
            if (colonIdx > 0) {
                String typeName = fullKey.substring(0, colonIdx);
                try {
                    ConditionType ct = ConditionType.valueOf(typeName);
                    Set<String> wildcardIds = wildcardMap.get(ct);
                    if (wildcardIds != null && !wildcardIds.isEmpty()) {
                        List<String> merged = new ArrayList<>(baseIds.size() + wildcardIds.size());
                        merged.addAll(baseIds);
                        for (String wid : wildcardIds) {
                            if (!baseIds.contains(wid)) merged.add(wid);
                        }
                        mergedMap.put(fullKey, merged);
                    } else {
                        mergedMap.put(fullKey, baseIds);
                    }
                } catch (IllegalArgumentException e) {
                    mergedMap.put(fullKey, baseIds);
                }
            } else {
                mergedMap.put(fullKey, baseIds);
            }
        }

        indexRebuildLock.writeLock().lock();
        try {
            if (!conditionIndexDirty) return; // 双重检查
            conditionIndex.setIndexes(typeMap, targetMap);
            conditionIndex.setMergedTargetIndex(mergedMap);
            conditionIndex.setCondIndexTargetIndex(condIndexMap);
            conditionIndexDirty = false;
        } finally {
            indexRebuildLock.writeLock().unlock();
        }
    }

    /** 标记索引需要重建（在批量加载等操作后调用） */
    void markIndexDirty() { conditionIndexDirty = true; }

    // ═══════════════ 持久化 ═══════════════

    /** 从 JSON 文件加载所有成就，加载后立即重建索引和反向前置索引 */
    void loadFromFile(Path file, Gson gson) {
        if (file == null || !Files.exists(file)) return;
        try {
            Map<String, CustomAdvancement> loaded = DataStore.mapFromJson(Files.readString(file));
            // 迁移旧版标签页名称（"vanilla:xxx" → 内置标签页常量）
            for (CustomAdvancement adv : loaded.values()) {
                String tab = adv.getTab();
                if (tab != null) {
                    adv.setTab(DataStore.normalizeTabName(tab));
                }
            }
            synchronized (writeLock) {
                advancements.putAll(loaded);
                advancements.keySet().removeIf(k -> !loaded.containsKey(k));
                conditionIndexDirty = true;

                // 重建反向前置索引
                reversePrereqIndex.clear();
                for (CustomAdvancement adv : advancements.values()) {
                    for (String prereq : adv.getPrerequisites()) {
                        addToReverseIndex(prereq, adv.getId());
                    }
                }
            }
            // 加载后立即重建条件索引（问题3修复）
            rebuildConditionIndex();
        } catch (Exception e) {
            LOGGER.error("Failed to load advancements", e);
        }
    }

    /** 将成就数据序列化为 JSON 字符串 */
    String toJson(Gson gson) {
        synchronized (writeLock) {
            return gson.toJson(advancements);
        }
    }

    /** 批量替换所有成就（导入操作） */
    void replaceAll(Map<String, CustomAdvancement> newAdvancements) {
        synchronized (writeLock) {
            advancements.clear();
            advancements.putAll(newAdvancements);
            conditionIndexDirty = true;

            // 重建反向前置索引
            reversePrereqIndex.clear();
            for (CustomAdvancement adv : newAdvancements.values()) {
                for (String prereq : adv.getPrerequisites()) {
                    addToReverseIndex(prereq, adv.getId());
                }
            }
        }
    }

    /** 清空所有数据（用于 forceReload 场景） */
    void clear() {
        synchronized (writeLock) {
            advancements.clear();
            reversePrereqIndex.clear();
            conditionIndexDirty = true;
        }
    }

    // ═══════════════ 反向前置索引辅助方法 ═══════════════

    private void addToReverseIndex(String prereqId, String dependentId) {
        if (prereqId == null || prereqId.isEmpty()) return;
        reversePrereqIndex.computeIfAbsent(prereqId, k -> ConcurrentHashMap.newKeySet())
                .add(dependentId);
    }

    private void removeFromReverseIndex(String prereqId, String dependentId) {
        if (prereqId == null || prereqId.isEmpty()) return;
        Set<String> deps = reversePrereqIndex.get(prereqId);
        if (deps != null) {
            deps.remove(dependentId);
            if (deps.isEmpty()) reversePrereqIndex.remove(prereqId);
        }
    }
}
