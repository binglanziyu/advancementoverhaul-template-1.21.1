package com.example.advancementoverhaul.milestone.model;

public record TimelineCategory(String id, String nameKey, String icon, int color, int sortOrder) {
    public static final TimelineCategory NORMAL = new TimelineCategory("normal", "milestone.advancementoverhaul.cat_normal", "\u2606", -7624772, 0);
    public static final TimelineCategory UNIQUE = new TimelineCategory("unique", "milestone.advancementoverhaul.cat_unique", "\u2605", -6509172, 1);
    public static final TimelineCategory[] BUILTIN = new TimelineCategory[]{NORMAL, UNIQUE};

    public static TimelineCategory byId(String id) {
        for (TimelineCategory cat : BUILTIN) {
            if (!cat.id.equals(id)) continue;
            return cat;
        }
        return NORMAL;
    }
}
