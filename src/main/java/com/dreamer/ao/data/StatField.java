package com.dreamer.ao.data;

/**
 * 玩家统计字段标识，用于类型安全的字段引用。
 */
public enum StatField {
    SUNRISES_VIEWED("sunrisesViewed", long.class),
    SUNSETS_VIEWED("sunsetsViewed", long.class),
    ANIMALS_TAMED("animalsTamed", long.class),
    NAME_TAGS_USED("nameTagsUsed", long.class),
    BLOCKS_PLACED("blocksPlaced", long.class),
    BLOCKS_BROKEN("blocksBroken", long.class),
    WANDERING_TRADER_TRADES("wanderingTraderTrades", long.class),
    LIGHTNING_STRIKES("lightningStrikes", long.class),
    RAIN_TICKS("rainTicks", long.class),
    SNOW_TICKS("snowTicks", long.class),
    CROPS_PLANTED("cropsPlanted", long.class),
    ITEMS_CRAFTED("itemsCrafted", long.class),
    FALL_DAMAGE_EVENTS("fallDamageEvents", long.class),
    TORCHES_PLACED("torchesPlaced", long.class),
    BLOCKS_PLACED_IN_WATER("blocksPlacedInWater", long.class),
    ANIMALS_FED("animalsFed", long.class);

    private final String jsonName;
    private final Class<?> valueType;

    StatField(String jsonName, Class<?> valueType) {
        this.jsonName = jsonName;
        this.valueType = valueType;
    }

    public String jsonName() { return jsonName; }
    public Class<?> valueType() { return valueType; }

    /** 根据 JSON 字段名查找枚举，未找到返回 null。 */
    public static StatField fromJsonName(String name) {
        for (StatField f : values()) {
            if (f.jsonName.equals(name)) return f;
        }
        return null;
    }
}
