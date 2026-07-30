package com.example.advancementoverhaul.milestone.model;

import com.google.gson.JsonObject;

/**
 * 里程碑定义 — 描述一个里程碑的触发条件与行为配置。
 * 作为 milestones/ JSON 配置的反序列化目标，也用于运行时匹配。
 */
public class MilestoneDefinition {
    private final String id;
    private final String nameKey;
    private final String descriptionKey;
    private final String iconItem;
    private final String category;
    private final MilestoneTrigger trigger;
    private final String triggerParam;
    private final long triggerThreshold;
    private final String linkedAdvancement;
    private final boolean autoAdvancement;
    private final String requiredAdvancement;

    public MilestoneDefinition(String id, String nameKey, String descriptionKey, String iconItem, String category, MilestoneTrigger trigger, String triggerParam, long triggerThreshold, String linkedAdvancement, boolean autoAdvancement, String requiredAdvancement) {
        this.id = id;
        this.nameKey = nameKey;
        this.descriptionKey = descriptionKey;
        this.iconItem = iconItem;
        this.category = category;
        this.trigger = trigger;
        this.triggerParam = triggerParam;
        this.triggerThreshold = triggerThreshold;
        this.linkedAdvancement = linkedAdvancement;
        this.autoAdvancement = autoAdvancement;
        this.requiredAdvancement = requiredAdvancement;
    }

    public String getId() {
        return this.id;
    }

    public String getNameKey() {
        return this.nameKey;
    }

    public String getDescriptionKey() {
        return this.descriptionKey;
    }

    public String getIconItem() {
        return this.iconItem;
    }

    public String getCategory() {
        return this.category;
    }

    public MilestoneTrigger getTrigger() {
        return this.trigger;
    }

    public String getTriggerParam() {
        return this.triggerParam;
    }

    public long getTriggerThreshold() {
        return this.triggerThreshold;
    }

    public String getLinkedAdvancement() {
        return this.linkedAdvancement;
    }

    public boolean isAutoAdvancement() {
        return this.autoAdvancement;
    }

    public String getRequiredAdvancement() {
        return this.requiredAdvancement;
    }

    public TimeMilestone toPendingMilestone() {
        return TimeMilestone.pending(this.id, this.nameKey, this.descriptionKey, this.iconItem, this.category, this.linkedAdvancement, this.autoAdvancement, this.requiredAdvancement);
    }

    public static MilestoneDefinition fromJson(JsonObject obj) {
        String id = MilestoneDefinition.getString(obj, "id", "");
        String nameKey = MilestoneDefinition.getString(obj, "name_key", "");
        String descKey = MilestoneDefinition.getString(obj, "description_key", "");
        String icon = MilestoneDefinition.getString(obj, "icon", "minecraft:paper");
        String category = MilestoneDefinition.getString(obj, "category", "journey");
        MilestoneTrigger trigger = MilestoneDefinition.parseTrigger(MilestoneDefinition.getString(obj, "trigger", "CUSTOM"));
        String triggerParam = MilestoneDefinition.getString(obj, "trigger_param", null);
        long threshold = obj.has("trigger_threshold") ? obj.get("trigger_threshold").getAsLong() : 1L;
        String linkedAdv = null;
        boolean autoAdv = false;
        String requiredAdv = null;
        if (obj.has("achievement") && obj.get("achievement").isJsonObject()) {
            JsonObject ach = obj.getAsJsonObject("achievement");
            linkedAdv = MilestoneDefinition.getString(ach, "linked", null);
            autoAdv = ach.has("auto_generate") && ach.get("auto_generate").getAsBoolean();
            requiredAdv = MilestoneDefinition.getString(ach, "required", null);
        }
        return new MilestoneDefinition(id, nameKey, descKey, icon, category, trigger, triggerParam, threshold, linkedAdv, autoAdv, requiredAdv);
    }

    private static MilestoneTrigger parseTrigger(String name) {
        try {
            return MilestoneTrigger.valueOf(name.toUpperCase());
        }
        catch (IllegalArgumentException e) {
            return MilestoneTrigger.CUSTOM;
        }
    }

    private static String getString(JsonObject obj, String key, String defaultValue) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        return obj.get(key).getAsString();
    }
}
