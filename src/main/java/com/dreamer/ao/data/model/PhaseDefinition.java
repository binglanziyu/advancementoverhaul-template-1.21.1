package com.dreamer.ao.data.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.*;

/**
 * 阶段定义 — 描述一个游戏阶段的属性效果和装备配置。
 * <p>
 * 阶段是全局/维度级别的状态，影响该范围内所有怪物的属性和装备。
 * 效果以倍率叠加：最终值 = 基础值 × (全局倍率 + 维度倍率 + 玩家倍率)。
 *
 * @param id          阶段唯一 ID
 * @param nameKey     显示名翻译键
 * @param descriptionKey 描述翻译键
 * @param effects     属性效果列表（attribute → multiplier）
 * @param equipment   装备配置（slot → 候选列表）
 * @param priority    优先级（同维度多阶段时，高优先级覆盖低优先级的效果）
 * @param isDefault   是否为默认阶段（世界初始阶段）
 */
public record PhaseDefinition(
        String id,
        String nameKey,
        String descriptionKey,
        List<PhaseEffect> effects,
        Map<EquipmentSlot, PhaseEquipmentSlot> equipment,
        int priority,
        boolean isDefault
) {
    public PhaseDefinition {
        if (effects == null) effects = List.of();
        if (equipment == null) equipment = Map.of();
    }

    /**
     * 获取指定属性的效果倍率。
     * @return 倍率，无效果时返回 1.0
     */
    public double getEffectMultiplier(String attributeId) {
        for (PhaseEffect pe : effects) {
            if (pe.attributeId().equals(attributeId)) {
                return pe.multiplier();
            }
        }
        return 1.0;
    }

    /**
     * 获取指定槽位的装备配置。
     */
    public PhaseEquipmentSlot getEquipmentSlot(EquipmentSlot slot) {
        return equipment.get(slot);
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        obj.addProperty("name_key", nameKey);
        obj.addProperty("description_key", descriptionKey);
        obj.addProperty("priority", priority);
        obj.addProperty("is_default", isDefault);

        JsonArray effArr = new JsonArray();
        for (PhaseEffect pe : effects) effArr.add(pe.toJson());
        obj.add("effects", effArr);

        JsonObject equipObj = new JsonObject();
        for (var entry : equipment.entrySet()) {
            equipObj.add(entry.getKey().getName(), entry.getValue().toJson());
        }
        obj.add("equipment", equipObj);

        return obj;
    }

    public static PhaseDefinition fromJson(JsonObject obj) {
        String id = getString(obj, "id", "");
        String nameKey = getString(obj, "name_key", "");
        String descKey = getString(obj, "description_key", "");
        int priority = obj.has("priority") ? obj.get("priority").getAsInt() : 0;
        boolean isDefault = obj.has("is_default") && obj.get("is_default").getAsBoolean();

        List<PhaseEffect> effects = new ArrayList<>();
        if (obj.has("effects") && obj.get("effects").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("effects")) {
                if (el.isJsonObject()) effects.add(PhaseEffect.fromJson(el.getAsJsonObject()));
            }
        }

        Map<EquipmentSlot, PhaseEquipmentSlot> equipment = new EnumMap<>(EquipmentSlot.class);
        if (obj.has("equipment") && obj.get("equipment").isJsonObject()) {
            for (var entry : obj.getAsJsonObject("equipment").entrySet()) {
                EquipmentSlot slot = parseSlot(entry.getKey());
                if (slot != null && entry.getValue().isJsonObject()) {
                    equipment.put(slot, PhaseEquipmentSlot.fromJson(entry.getValue().getAsJsonObject()));
                }
            }
        }

        return new PhaseDefinition(id, nameKey, descKey, effects, equipment, priority, isDefault);
    }

    private static EquipmentSlot parseSlot(String name) {
        return switch (name.toLowerCase()) {
            case "head"     -> EquipmentSlot.HEAD;
            case "chest"    -> EquipmentSlot.CHEST;
            case "legs"     -> EquipmentSlot.LEGS;
            case "feet"     -> EquipmentSlot.FEET;
            case "mainhand" -> EquipmentSlot.MAINHAND;
            case "offhand"  -> EquipmentSlot.OFFHAND;
            default -> null;
        };
    }

    private static String getString(JsonObject obj, String key, String def) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : def;
    }
}
