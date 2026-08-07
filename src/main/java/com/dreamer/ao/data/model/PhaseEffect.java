package com.dreamer.ao.data.model;

import com.google.gson.JsonObject;

/**
 * 阶段属性效果条目。
 * <p>
 * 描述某个阶段对某个属性的倍率影响。
 * 最终值 = 原版基础值 × multiplier，然后 clamp 到 {@link com.dreamer.ao.data.PhaseConstants} 中的上限。
 *
 * @param attributeId  属性 ID（如 "generic.max_health"）
 * @param multiplier   倍率（1.0 = 不变，1.5 = +50%，0.5 = -50%）
 */
public record PhaseEffect(String attributeId, double multiplier) {

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("attribute", attributeId);
        obj.addProperty("multiplier", multiplier);
        return obj;
    }

    public static PhaseEffect fromJson(JsonObject obj) {
        String attr = obj.has("attribute") ? obj.get("attribute").getAsString() : "";
        double mult = obj.has("multiplier") ? obj.get("multiplier").getAsDouble() : 1.0;
        return new PhaseEffect(attr, mult);
    }
}
