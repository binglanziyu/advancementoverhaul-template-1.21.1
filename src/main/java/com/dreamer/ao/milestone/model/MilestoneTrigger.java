package com.dreamer.ao.milestone.model;

public enum MilestoneTrigger {
    WORLD_JOIN          ("milestone.advancementoverhaul.trigger_world_join"),
    FIRST_OBTAIN        ("milestone.advancementoverhaul.trigger_first_obtain"),
    FIRST_DEATH         ("milestone.advancementoverhaul.trigger_first_death"),
    FIRST_DIMENSION     ("milestone.advancementoverhaul.trigger_first_dimension"),
    FIRST_TAME          ("milestone.advancementoverhaul.trigger_first_tame"),
    FIRST_BLOCK_PLACE   ("milestone.advancementoverhaul.trigger_first_block_place"),
    FIRST_ENCHANT       ("milestone.advancementoverhaul.trigger_first_enchant"),
    FIRST_RAIN_SLEEP    ("milestone.advancementoverhaul.trigger_first_rain_sleep"),
    FIRST_LIGHTNING     ("milestone.advancementoverhaul.trigger_first_lightning"),
    COUNTER_REACH       ("milestone.advancementoverhaul.trigger_counter_reach"),
    DISTANCE_REACH      ("milestone.advancementoverhaul.trigger_distance_reach"),
    RAIN_NIGHT_TRAVEL   ("milestone.advancementoverhaul.trigger_rain_night_travel"),
    SUNRISE_VIEWED      ("milestone.advancementoverhaul.trigger_sunrise_viewed"),
    SUNSET_VIEWED       ("milestone.advancementoverhaul.trigger_sunset_viewed"),
    CUSTOM              ("milestone.advancementoverhaul.trigger_custom");

    private final String nameKey;

    MilestoneTrigger(String nameKey) {
        this.nameKey = nameKey;
    }

    public String getNameKey() {
        return nameKey;
    }

    public String getDisplayName() {
        return net.minecraft.network.chat.Component.translatable(nameKey).getString();
    }
}
