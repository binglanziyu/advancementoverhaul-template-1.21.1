package com.dreamer.ao.achievement;

import com.dreamer.ao.data.ConditionType;
import com.dreamer.ao.data.DataStore;
import com.dreamer.ao.data.model.AdvancementCondition;
import com.dreamer.ao.data.model.CustomAdvancement;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 成就（自定义进度）领域 CRUD 服务。
 *
 * <p>从命令层 {@code AdvCrudExecutor} 与桥接层 {@code AchievementBridgeImpl}
 * 中抽取出的、不依赖 Brigadier {@code CommandContext} 的纯领域逻辑，
 * 使两类调用方共用同一套 JSON → {@link CustomAdvancement} 解析规则，
 * 消除重复实现与“命令字符串 + Base64 往返”反模式。
 */
public final class AdvancementCrudService {

    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private AdvancementCrudService() {}

    /**
     * 将 JSON 字符串解析为 {@link CustomAdvancement} 字段映射。
     *
     * @throws JsonSyntaxException 当 JSON 语法非法时
     * @throws IllegalArgumentException 当 JSON 为空或缺少 id 字段时
     */
    public static Map<String, Object> parseJson(String json) {
        Map<String, Object> data = DataStore.GSON_PRETTY.fromJson(json, MAP_TYPE);
        if (data == null) {
            throw new IllegalArgumentException("empty json");
        }
        String id = data.get("id") instanceof String s ? s : null;
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("missing id");
        }
        return data;
    }

    /**
     * 将 JSON 数据字段应用到 {@link CustomAdvancement} 对象。
     * 支持所有字段：name, description, hidden, icon, prerequisites, tab, x, y, conditions。
     */
    @SuppressWarnings("unchecked")
    public static void applyJsonToAdvancement(CustomAdvancement adv, Map<String, Object> data) {
        if (data.get("name") instanceof String name) adv.setName(name);
        if (data.get("description") instanceof String desc) adv.setDescription(desc);
        if (data.get("hidden") instanceof Boolean h) adv.setHidden(h);
        if (data.get("icon") instanceof String icon) adv.setIcon(icon.isEmpty() ? null : icon);
        if (data.get("tab") instanceof String tab) adv.setTab(tab);
        if (data.get("x") instanceof Number x) adv.setX(x.intValue());
        if (data.get("y") instanceof Number y) adv.setY(y.intValue());

        // prerequisites: 支持数组和单字符串两种格式
        if (data.get("prerequisites") instanceof List<?> prereqs) {
            adv.setPrerequisites(parsePrereqs(prereqs));
        } else if (data.get("prerequisite") instanceof String p) {
            adv.setPrerequisites(p.isEmpty() ? new ArrayList<>()
                    : new ArrayList<>(List.of(p)));
        }

        // conditions 解析
        if (data.get("conditions") instanceof List<?> condRaw) {
            List<AdvancementCondition> newConds = new ArrayList<>();
            for (Object obj : condRaw) {
                if (!(obj instanceof Map<?, ?> cmRaw)) continue;
                try {
                    Map<String, Object> cm = (Map<String, Object>) cmRaw;
                    if (!(cm.get("type") instanceof String typeStr)) continue;
                    ConditionType ct = ConditionType.valueOf(typeStr.toUpperCase());
                    String targetId = cm.get("targetId") instanceof String tid ? tid : "";
                    int count = cm.get("count") instanceof Number n ? n.intValue() : 1;
                    AdvancementCondition cond = new AdvancementCondition(ct, targetId, count);
                    if (cm.get("nbtMatchMode") instanceof String mode) cond.setNbtMatchMode(mode);
                    if (cm.get("targetNbt") instanceof String nbt) cond.setTargetNbt(nbt);
                    newConds.add(cond);
                } catch (IllegalArgumentException ignored) {
                    // 非法条件类型，跳过
                }
            }
            adv.setConditions(newConds);
        }
    }

    /**
     * 从 JSON 解析前置条件列表。
     * 支持 {@code ["id1", "id2"]} 和 {@code "id1"} 两种格式。
     */
    @SuppressWarnings("unchecked")
    public static List<String> parsePrereqs(Object raw) {
        List<String> result = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof String s && !s.isEmpty()) result.add(s);
            }
        } else if (raw instanceof String s && !s.isEmpty()) {
            result.add(s);
        }
        return result;
    }

    /**
     * 判断 JSON 数据中是否包含属性级变更（非位置、非分类）。
     * 纯位置（x/y）或分类（tab）变更被视为“非属性变更”，不应触发 FTB Quests 同步。
     */
    public static boolean hasAttributeChanges(Map<String, Object> data) {
        if (data.containsKey("name") && data.get("name") instanceof String) return true;
        if (data.containsKey("description") && data.get("description") instanceof String) return true;
        if (data.containsKey("hidden") && data.get("hidden") instanceof Boolean) return true;
        if (data.containsKey("icon") && data.get("icon") instanceof String) return true;
        if (data.containsKey("prerequisites")) return true;
        if (data.containsKey("prerequisite")) return true;
        if (data.containsKey("conditions")) return true;
        // x, y, tab 是位置/分类变更，不算属性变更
        return false;
    }
}
