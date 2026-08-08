package com.dreamer.ao.phase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 阶段定义内存索引。
 * <p>
 * 保存所有已加载的 {@link PhaseDefinition}，并提供按 id、按 scope、按维度查询，以及过渡条件判定。
 */
public final class PhaseRegistry {

    private static PhaseRegistry INSTANCE;
    private final Map<String, PhaseDefinition> byId = new LinkedHashMap<>();

    private PhaseRegistry(List<PhaseDefinition> defs) {
        for (PhaseDefinition d : defs) {
            byId.put(d.getId(), d);
        }
    }

    public static void load() {
        INSTANCE = new PhaseRegistry(PhaseDefinitionLoader.loadAll());
    }

    public static PhaseRegistry get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public Optional<PhaseDefinition> getById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<PhaseDefinition> all() {
        return new ArrayList<>(byId.values());
    }

    public List<PhaseDefinition> byScope(String scope) {
        List<PhaseDefinition> out = new ArrayList<>();
        for (PhaseDefinition d : byId.values()) {
            if (d.getScope().equals(scope)) {
                out.add(d);
            }
        }
        return out;
    }

    /** 维度作用域的阶段（scope=dimension 且 dimension 匹配） */
    public List<PhaseDefinition> byDimension(String dimensionId) {
        List<PhaseDefinition> out = new ArrayList<>();
        for (PhaseDefinition d : byId.values()) {
            if ("dimension".equals(d.getScope()) && dimensionId.equals(d.getDimension())) {
                out.add(d);
            }
        }
        return out;
    }

    /** 根据已完成里程碑 id，返回应解锁的阶段定义列表 */
    public List<PhaseDefinition> phasesUnlockedByMilestone(String milestoneId) {
        List<PhaseDefinition> out = new ArrayList<>();
        if (milestoneId == null) {
            return out;
        }
        for (PhaseDefinition d : byId.values()) {
            if (milestoneId.equals(d.getUnlockMilestone())) {
                out.add(d);
            }
        }
        return out;
    }

    /** 重新加载配置 */
    public void reload() {
        load();
    }
}
