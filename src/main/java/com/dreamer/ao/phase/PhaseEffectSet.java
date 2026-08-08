package com.dreamer.ao.phase;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 阶段效果集合。
 * <p>
 * 包含三类效果：
 * <ul>
 *   <li><b>A 类 玩家属性</b> — 直接改玩家 Attribute（最大生命、护甲、韧性、击退抗性、移速、攻击伤害、攻击速度、幸运、体型缩放）</li>
 *   <li><b>B 类 怪物/维度</b> — 怪物生命/伤害/速度/生成速率/护甲倍率、Boss 伤害倍率、怪物概率穿戴装备</li>
 *   <li><b>D 类 状态效果</b> — 直接读取游戏现有 MobEffect（按 effect id + 等级 + 时长施加）</li>
 * </ul>
 * <p>
 * 最终效果 = 全局（世界）效果 + 维度效果 + 玩家效果三层叠加，叠加由 {@link PhaseEffectCalculator} 完成。
 */
public final class PhaseEffectSet {

    /** A 类属性 key（与服务端 Attribute 映射在 {@link PhaseEffectApplier} 中维护） */
    public static final List<String> PLAYER_ATTR_KEYS = List.of(
            "max_health", "armor", "armor_toughness", "knockback_resistance",
            "movement_speed", "attack_damage", "attack_speed", "luck", "scale"
    );

    /** B 类怪物倍率 key */
    public static final List<String> MOB_MULT_KEYS = List.of(
            "mob_health_mult", "mob_damage_mult", "mob_speed_mult",
            "mob_spawn_rate_mult", "mob_armor_mult", "boss_damage_mult"
    );

    /** 属性值（乘算/加算由 applier 决定，这里统一存数值） */
    private final Map<String, Double> attributes = new LinkedHashMap<>();
    /** 怪物倍率 */
    private final Map<String, Double> mobMults = new LinkedHashMap<>();
    /** D 类状态效果：effectId -> (level, seconds) */
    private final Map<String, MobEffectSpec> mobEffects = new LinkedHashMap<>();
    /** B 类怪物装备规则（可多个） */
    private final List<MobEquipmentRule> equipmentRules = new ArrayList<>();

    public Map<String, Double> getAttributes() {
        return attributes;
    }

    public Map<String, Double> getMobMults() {
        return mobMults;
    }

    public Map<String, MobEffectSpec> getMobEffects() {
        return mobEffects;
    }

    public List<MobEquipmentRule> getEquipmentRules() {
        return equipmentRules;
    }

    /** 清空所有效果 */
    public void clear() {
        attributes.clear();
        mobMults.clear();
        mobEffects.clear();
        equipmentRules.clear();
    }

    /** 从 JSON 的 "effects" 对象解析（effects 可含 attributes/mob_mults/mob_effects/equipment 四类） */
    public static PhaseEffectSet fromJson(JsonObject effects) {
        PhaseEffectSet set = new PhaseEffectSet();
        if (effects == null) {
            return set;
        }
        if (effects.has("attributes")) {
            JsonObject attrs = effects.getAsJsonObject("attributes");
            for (String key : PLAYER_ATTR_KEYS) {
                if (attrs.has(key)) {
                    set.attributes.put(key, attrs.get(key).getAsDouble());
                }
            }
        }
        if (effects.has("mob_mults")) {
            JsonObject mults = effects.getAsJsonObject("mob_mults");
            for (String key : MOB_MULT_KEYS) {
                if (mults.has(key)) {
                    set.mobMults.put(key, mults.get(key).getAsDouble());
                }
            }
        }
        if (effects.has("mob_effects")) {
            JsonArray arr = effects.getAsJsonArray("mob_effects");
            for (JsonElement elem : arr) {
                JsonObject obj = elem.getAsJsonObject();
                String id = obj.get("id").getAsString();
                int level = obj.has("level") ? obj.get("level").getAsInt() : 0;
                int seconds = obj.has("seconds") ? obj.get("seconds").getAsInt() : 30;
                set.mobEffects.put(id, new MobEffectSpec(id, level, seconds));
            }
        }
        if (effects.has("equipment")) {
            JsonArray arr = effects.getAsJsonArray("equipment");
            for (JsonElement elem : arr) {
                set.equipmentRules.add(MobEquipmentRule.fromJson(elem.getAsJsonObject()));
            }
        }
        return set;
    }

    /** 序列化为 JSON（供网络同步与调试） */
    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        if (!attributes.isEmpty()) {
            JsonObject a = new JsonObject();
            for (Map.Entry<String, Double> e : attributes.entrySet()) {
                a.addProperty(e.getKey(), e.getValue());
            }
            root.add("attributes", a);
        }
        if (!mobMults.isEmpty()) {
            JsonObject m = new JsonObject();
            for (Map.Entry<String, Double> e : mobMults.entrySet()) {
                m.addProperty(e.getKey(), e.getValue());
            }
            root.add("mob_mults", m);
        }
        if (!mobEffects.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (MobEffectSpec spec : mobEffects.values()) {
                arr.add(spec.toJson());
            }
            root.add("mob_effects", arr);
        }
        if (!equipmentRules.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (MobEquipmentRule rule : equipmentRules) {
                arr.add(rule.toJson());
            }
            root.add("equipment", arr);
        }
        return root;
    }

    /** D 类状态效果规格 */
    public record MobEffectSpec(String id, int level, int seconds) {
        public JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("level", level);
            o.addProperty("seconds", seconds);
            return o;
        }
    }

    /**
     * B 类怪物装备规则：按实体给生成怪物套装备（仅全局作用域生效）。
     * <p>
     * JSON 结构（按部位分组，单部位可多条目，每条目可带附魔与自身概率）：
     * <pre>
     * {
     *   "chance": 1.0,
     *   "entity": "minecraft:zombie",
     *   "slots": {
     *     "head": [ {"item": "...", "chance": 1.0, "enchants": {"minecraft:protection": 3}} ],
     *     "chest": [ ... ], ...
     *   }
     * }
     * </pre>
     */
    public static final class MobEquipmentRule {
        /** 整条规则触发概率 0~1 */
        private double chance = 1.0;
        /** 目标实体类型 id（如 minecraft:zombie） */
        private String entityFilter;
        /** 部位 -> 装备条目列表（head/chest/legs/feet/mainhand/offhand） */
        private final Map<String, List<EquipmentEntry>> slots = new LinkedHashMap<>();

        public double getChance() {
            return chance;
        }

        public String getEntityFilter() {
            return entityFilter;
        }

        public Map<String, List<EquipmentEntry>> getSlots() {
            return slots;
        }

        public static MobEquipmentRule fromJson(JsonObject obj) {
            MobEquipmentRule rule = new MobEquipmentRule();
            if (obj.has("chance")) {
                rule.chance = obj.get("chance").getAsDouble();
            }
            if (obj.has("entity")) {
                rule.entityFilter = obj.get("entity").getAsString();
            } else if (obj.has("entityFilter")) {
                rule.entityFilter = obj.get("entityFilter").getAsString();
            }
            if (obj.has("slots")) {
                JsonObject s = obj.getAsJsonObject("slots");
                for (Map.Entry<String, JsonElement> e : s.entrySet()) {
                    List<EquipmentEntry> list = new ArrayList<>();
                    if (e.getValue().isJsonArray()) {
                        for (JsonElement it : e.getValue().getAsJsonArray()) {
                            list.add(EquipmentEntry.fromJson(it));
                        }
                    } else {
                        list.add(EquipmentEntry.fromJson(e.getValue()));
                    }
                    rule.slots.put(e.getKey(), list);
                }
            }
            return rule;
        }

        public JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("chance", chance);
            if (entityFilter != null) {
                o.addProperty("entity", entityFilter);
            }
            JsonObject s = new JsonObject();
            for (Map.Entry<String, List<EquipmentEntry>> e : slots.entrySet()) {
                if (e.getValue().size() == 1) {
                    s.add(e.getKey(), e.getValue().get(0).toJson());
                } else {
                    JsonArray arr = new JsonArray();
                    for (EquipmentEntry en : e.getValue()) arr.add(en.toJson());
                    s.add(e.getKey(), arr);
                }
            }
            o.add("slots", s);
            return o;
        }
    }

    /** 单个装备条目：物品 + 自身概率 + 附魔（附魔 id -> 等级） */
    public static final class EquipmentEntry {
        private final String item;
        private final double chance;
        private final Map<String, Integer> enchants;

        public EquipmentEntry(String item, double chance, Map<String, Integer> enchants) {
            this.item = item;
            this.chance = chance;
            this.enchants = enchants == null ? new LinkedHashMap<>() : enchants;
        }

        public String getItem() {
            return item;
        }

        public double getChance() {
            return chance;
        }

        public Map<String, Integer> getEnchants() {
            return enchants;
        }

        /** 兼容旧结构（字符串或仅含 item 的对象） */
        public static EquipmentEntry fromJson(JsonElement el) {
            if (el.isJsonPrimitive()) {
                return new EquipmentEntry(el.getAsString(), 1.0, new LinkedHashMap<>());
            }
            JsonObject o = el.getAsJsonObject();
            String item = o.has("item") ? o.get("item").getAsString() : "";
            double chance = o.has("chance") ? o.get("chance").getAsDouble() : 1.0;
            Map<String, Integer> enchants = new LinkedHashMap<>();
            if (o.has("enchants")) {
                JsonObject eo = o.getAsJsonObject("enchants");
                for (Map.Entry<String, JsonElement> e : eo.entrySet()) {
                    enchants.put(e.getKey(), e.getValue().getAsInt());
                }
            }
            return new EquipmentEntry(item, chance, enchants);
        }

        public JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("item", item);
            o.addProperty("chance", chance);
            if (!enchants.isEmpty()) {
                JsonObject eo = new JsonObject();
                for (Map.Entry<String, Integer> e : enchants.entrySet()) {
                    eo.addProperty(e.getKey(), e.getValue());
                }
                o.add("enchants", eo);
            }
            return o;
        }
    }
}
