package com.dreamer.ao.phase;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * 阶段定义（静态配置）。
 * <p>
 * 对应 config/advancementoverhaul/phases/*.json 中的单个阶段对象：
 * <pre>
 * {
 *   "id": "overworld_dawn",
 *   "name": "黎明",
 *   "tier": 1,
 *   "scope": "world" | "dimension" | "player",
 *   "effects": { ... },                 // 见 {@link PhaseEffectSet}
 *   "unlockMilestone": "m_first_spawn", // 完成该里程碑后自动解锁；为空表示默认解锁/手动
 *   "transitions": [ { "to": "overworld_day", "condition": "..." } ]
 * }
 * </pre>
 */
public final class PhaseDefinition {

    private String id;
    private String name;
    private int tier;
    private String scope = "world"; // world / dimension / player
    private String dimension;       // scope=dimension 时有效（如 minecraft:overworld）
    private PhaseEffectSet effects = new PhaseEffectSet();
    private String unlockMilestone;
    private final List<Transition> transitions = new ArrayList<>();

    /** 运行态：每个定义对应一个运行态实例，保存当前进展 */
    private PhaseState state;

    public String getId() {
        return id;
    }

    public String getName() {
        return name != null ? name : id;
    }

    public int getTier() {
        return tier;
    }

    public String getScope() {
        return scope;
    }

    public String getDimension() {
        return dimension;
    }

    public PhaseEffectSet getEffects() {
        return effects;
    }

    public String getUnlockMilestone() {
        return unlockMilestone;
    }

    public List<Transition> getTransitions() {
        return transitions;
    }

    public PhaseState getState() {
        return state;
    }

    public void setState(PhaseState state) {
        this.state = state;
    }

    public boolean isDefaultUnlocked() {
        return unlockMilestone == null;
    }

    /** 序列化为 config 目录下的阶段定义 JSON（与 fromJson 读取格式一致） */
    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("id", id);
        root.addProperty("name", name != null ? name : id);
        root.addProperty("tier", tier);
        root.addProperty("scope", scope);
        if (dimension != null) {
            root.addProperty("dimension", dimension);
        }
        if (unlockMilestone != null) {
            root.addProperty("unlockMilestone", unlockMilestone);
        }
        root.add("effects", effects.toJson());
        JsonArray trans = new JsonArray();
        for (Transition t : transitions) {
            JsonObject to = new JsonObject();
            to.addProperty("to", t.to());
            if (t.condition() != null) {
                to.addProperty("condition", t.condition());
            }
            trans.add(to);
        }
        root.add("transitions", trans);
        return root;
    }

    /** 解析单个阶段定义 */
    public static PhaseDefinition fromJson(JsonObject obj) {
        PhaseDefinition def = new PhaseDefinition();
        def.id = obj.get("id").getAsString();
        def.name = obj.has("name") ? obj.get("name").getAsString() : def.id;
        def.tier = obj.has("tier") ? obj.get("tier").getAsInt() : 0;
        if (obj.has("scope")) {
            def.scope = obj.get("scope").getAsString();
        }
        if (obj.has("dimension")) {
            def.dimension = obj.get("dimension").getAsString();
        }
        def.effects = PhaseEffectSet.fromJson(obj.has("effects") ? obj.getAsJsonObject("effects") : null);
        if (obj.has("unlockMilestone")) {
            def.unlockMilestone = obj.get("unlockMilestone").getAsString();
        }
        if (obj.has("transitions")) {
            for (JsonElement e : obj.getAsJsonArray("transitions")) {
                def.transitions.add(Transition.fromJson(e.getAsJsonObject()));
            }
        }
        def.state = new PhaseState(def.id);
        return def;
    }

    /** 过渡条件 */
    public record Transition(String to, String condition) {
        public static Transition fromJson(JsonObject obj) {
            return new Transition(
                    obj.has("to") ? obj.get("to").getAsString() : null,
                    obj.has("condition") ? obj.get("condition").getAsString() : null
            );
        }
    }
}
