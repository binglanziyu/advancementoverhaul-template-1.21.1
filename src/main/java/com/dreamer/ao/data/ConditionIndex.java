package com.dreamer.ao.data;

import com.dreamer.ao.data.DataStore.ConditionType;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 条件索引：按条件类型和目标 ID 的高速查询结构。
 * <p>
 * 索引的构建由 ServerDataStore 负责，
 * 本类仅管理索引的 Map 并提供线程安全的查询方法。
 * <p>
 * <b>预计算优化：</b>{@link #mergedTargetIndex} 在索引重建时将通配符条件
 * 预先合并到对应类型的目标索引中，避免每次查询时重新去重合并。
 * <p>
 * <b>条件级别索引：</b>{@link #condIndexTargetIndex} 精确记录每个成就中匹配的条件索引，
 * 使 {@code ConditionEvaluator.evaluate()} 可直接跳转到匹配条件，省去内层全量遍历。
 */
public final class ConditionIndex {

    private volatile Map<ConditionType, List<String>> typeIndex = new HashMap<>();
    private volatile Map<String, List<String>> targetIndex = new HashMap<>();
    /** 预计算通配符合并后的目标索引：key = ConditionType:targetId → 合并后列表 */
    private volatile Map<String, List<String>> mergedTargetIndex = new HashMap<>();
    /** 条件级别索引：key = ConditionType:targetId → (advId, condIndex) 列表，直接跳到匹配条件 */
    private volatile Map<String, List<AdvIdCondIndex>> condIndexTargetIndex = new HashMap<>();

    void setIndexes(Map<ConditionType, List<String>> typeIndex,
                    Map<String, List<String>> targetIndex) {
        this.typeIndex = typeIndex != null ? typeIndex : new HashMap<>();
        this.targetIndex = targetIndex != null ? targetIndex : new HashMap<>();
    }

    void setMergedTargetIndex(Map<String, List<String>> merged) {
        this.mergedTargetIndex = merged != null ? merged : new HashMap<>();
    }

    void setCondIndexTargetIndex(Map<String, List<AdvIdCondIndex>> idx) {
        this.condIndexTargetIndex = idx != null ? idx : new HashMap<>();
    }

    List<String> getByType(ConditionType type) {
        return Collections.unmodifiableList(typeIndex.getOrDefault(type, List.of()));
    }

    /**
     * 按类型和目标查询预计算合并列表（已包含通配符条件的成就）。
     * 当合并索引无对应条目时回退到原始目标索引。
     */
    List<String> getByTypeAndTarget(ConditionType type, String targetId) {
        String key = type.name() + ":" + (targetId != null ? targetId : "");
        List<String> merged = mergedTargetIndex.get(key);
        if (merged != null) return Collections.unmodifiableList(merged);
        return Collections.unmodifiableList(targetIndex.getOrDefault(key, List.of()));
    }

    /**
     * 按类型和目标查询条件级别索引，返回 (advId, condIndex) 对列表。
     * 用于 {@code ConditionEvaluator} 直接跳到匹配的条件，避免遍历所有条件。
     */
    List<AdvIdCondIndex> getAdvCondIndexesByCondition(ConditionType type, String targetId) {
        String key = type.name() + ":" + (targetId != null ? targetId : "");
        List<AdvIdCondIndex> idx = condIndexTargetIndex.get(key);
        return idx != null ? Collections.unmodifiableList(idx) : List.of();
    }

    /**
     * 条件索引条目：成就 ID 与条件索引的配对。
     * <p>
     * 用于在条件评估时直接跳转到匹配的条件，省去对该成就所有条件的内层遍历。
     *
     * @param advId     成就 ID
     * @param condIndex 条件在成就条件列表中的索引位置
     */
    public record AdvIdCondIndex(String advId, int condIndex) {
    }

}
