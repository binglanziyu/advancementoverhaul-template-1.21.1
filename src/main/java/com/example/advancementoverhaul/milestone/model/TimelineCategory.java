package com.example.advancementoverhaul.milestone.model;

public record TimelineCategory(String id, String nameKey, String icon, int color, int sortOrder) {
    public static final TimelineCategory JOURNEY = new TimelineCategory("journey", "milestone.advancementoverhaul.cat_journey", "\ud83d\uddfa\ufe0f", -7624772, 0);
    public static final TimelineCategory BUILDING = new TimelineCategory("building", "milestone.advancementoverhaul.cat_building", "\ud83c\udfd7\ufe0f", -6576216, 1);
    public static final TimelineCategory COMBAT = new TimelineCategory("combat", "milestone.advancementoverhaul.cat_combat", "\u2694\ufe0f", -4679014, 2);
    public static final TimelineCategory SURVIVAL = new TimelineCategory("survival", "milestone.advancementoverhaul.cat_survival", "\ud83c\udf3e", -6509172, 3);
    public static final TimelineCategory CRAFTING = new TimelineCategory("crafting", "milestone.advancementoverhaul.cat_crafting", "\ud83d\udce6", -6645320, 4);
    public static final TimelineCategory EXPLORE = new TimelineCategory("explore", "milestone.advancementoverhaul.cat_explore", "\ud83e\udded", -7559008, 5);
    public static final TimelineCategory[] BUILTIN = new TimelineCategory[]{JOURNEY, BUILDING, COMBAT, SURVIVAL, CRAFTING, EXPLORE};

    public static TimelineCategory byId(String id) {
        for (TimelineCategory cat : BUILTIN) {
            if (!cat.id.equals(id)) continue;
            return cat;
        }
        return JOURNEY;
    }
}
