package com.example.advancementoverhaul.milestone.model;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;

public record TimeMilestone(String id, String nameKey, String descriptionKey, String iconItem, String category, int unlockDay, long unlockTick, boolean unlocked, String linkedAdvancement, boolean autoAdvancement, String requiredAdvancement, String source, String customName, String customDesc, String customTrigger, String customParam, long customThreshold) {
    public static TimeMilestone pending(String id, String nameKey, String descriptionKey, String iconItem, String category, String linkedAdvancement, boolean autoAdvancement, String requiredAdvancement) {
        return new TimeMilestone(id, nameKey, descriptionKey, iconItem, category, 0, 0L, false, linkedAdvancement, autoAdvancement, requiredAdvancement, "config", null, null, null, null, 1L);
    }

    public static TimeMilestone customPending(String id, String name, String description, String iconItem, String category, String trigger, String param, long threshold) {
        return new TimeMilestone(id, id, id, iconItem, category, 0, 0L, false, null, false, null, "custom", name, description, trigger, param != null ? param : "", threshold);
    }

    public TimeMilestone unlocked(int day, long tick) {
        return new TimeMilestone(this.id, this.nameKey, this.descriptionKey, this.iconItem, this.category, day, tick, true, this.linkedAdvancement, this.autoAdvancement, this.requiredAdvancement, this.source, this.customName, this.customDesc, this.customTrigger, this.customParam, this.customThreshold);
    }

    public String getDisplayName() {
        if (this.customName != null && !this.customName.isEmpty()) {
            return this.customName;
        }
        return Component.translatable((String)this.nameKey).getString();
    }

    public String getDisplayDesc() {
        if (this.customDesc != null && !this.customDesc.isEmpty()) {
            return this.customDesc;
        }
        return Component.translatable((String)this.descriptionKey).getString();
    }

    public boolean isCustom() {
        return "custom".equals(this.source);
    }

    public static TimeMilestone fromJson(JsonObject obj) {
        return new TimeMilestone(TimeMilestone.str(obj, "id", ""), TimeMilestone.str(obj, "nameKey", ""), TimeMilestone.str(obj, "descriptionKey", ""), TimeMilestone.str(obj, "iconItem", "minecraft:paper"), TimeMilestone.str(obj, "category", "normal"), obj.has("unlockDay") ? obj.get("unlockDay").getAsInt() : 0, obj.has("unlockTick") ? obj.get("unlockTick").getAsLong() : 0L, obj.has("unlocked") && obj.get("unlocked").getAsBoolean(), TimeMilestone.strOrNull(obj, "linkedAdvancement"), obj.has("autoAdvancement") && obj.get("autoAdvancement").getAsBoolean(), TimeMilestone.strOrNull(obj, "requiredAdvancement"), TimeMilestone.str(obj, "source", "config"), TimeMilestone.strOrNull(obj, "customName"), TimeMilestone.strOrNull(obj, "customDesc"), TimeMilestone.strOrNull(obj, "customTrigger"), TimeMilestone.strOrNull(obj, "customParam"), obj.has("customThreshold") ? obj.get("customThreshold").getAsLong() : 1L);
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", this.id);
        obj.addProperty("nameKey", this.nameKey);
        obj.addProperty("descriptionKey", this.descriptionKey);
        obj.addProperty("iconItem", this.iconItem);
        obj.addProperty("category", this.category);
        obj.addProperty("unlockDay", (Number)this.unlockDay);
        obj.addProperty("unlockTick", (Number)this.unlockTick);
        obj.addProperty("unlocked", Boolean.valueOf(this.unlocked));
        if (this.linkedAdvancement != null) {
            obj.addProperty("linkedAdvancement", this.linkedAdvancement);
        }
        obj.addProperty("autoAdvancement", Boolean.valueOf(this.autoAdvancement));
        if (this.requiredAdvancement != null) {
            obj.addProperty("requiredAdvancement", this.requiredAdvancement);
        }
        obj.addProperty("source", this.source != null ? this.source : "config");
        if (this.customName != null) {
            obj.addProperty("customName", this.customName);
        }
        if (this.customDesc != null) {
            obj.addProperty("customDesc", this.customDesc);
        }
        if (this.customTrigger != null) {
            obj.addProperty("customTrigger", this.customTrigger);
        }
        if (this.customParam != null) {
            obj.addProperty("customParam", this.customParam);
        }
        obj.addProperty("customThreshold", (Number)this.customThreshold);
        return obj;
    }

    private static String str(JsonObject obj, String key, String def) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : def;
    }

    private static String strOrNull(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }
}
