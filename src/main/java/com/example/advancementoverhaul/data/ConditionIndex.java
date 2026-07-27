package com.example.advancementoverhaul.data;

import com.example.advancementoverhaul.data.DataStore.ConditionType;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 条件索引：按条件类型和目标 ID 的高速查询结构。
 * <p>
 * 索引的构建由 ServerDataStore 负责，
 * 本类仅管理索引的两个 Map 并提供线程安全的查询方法。
 * <p>
 * <b>预计算优化：</b>{@link #mergedTargetIndex} 在索引重建时将通配符条件
 * 预先合并到对应类型的目标索引中，避免每次查询时重新去重合并。
 */
final class ConditionIndex {

    private volatile Map<ConditionType, List<String>> typeIndex = new HashMap<>();
    private volatile Map<String, List<String>> targetIndex = new HashMap<>();
    /** 预计算通配符合并后的目标索引：key = ConditionType:targetId → 合并后列表 */
    private volatile Map<String, List<String>> mergedTargetIndex = new HashMap<>();

    void setIndexes(Map<ConditionType, List<String>> typeIndex,
                    Map<String, List<String>> targetIndex) {
        this.typeIndex = typeIndex != null ? typeIndex : new HashMap<>();
        this.targetIndex = targetIndex != null ? targetIndex : new HashMap<>();
    }

    void setMergedTargetIndex(Map<String, List<String>> merged) {
        this.mergedTargetIndex = merged != null ? merged : new HashMap<>();
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
        // 回退：合并索引中无条目时使用原始索引
        return Collections.unmodifiableList(targetIndex.getOrDefault(key, List.of()));
    }

}
