package com.dreamer.ao;

/**
 * 冒险日志/叙事统计相关的本地化键常量。
 * <p>
 * 包含 {@code NARR_} 前缀的所有叙事统计消息键。
 * 这些常量从 {@link LangKeys} 中分离出来，便于叙事系统独立引用。
 *
 * @see LangKeys 主本地化键类（保持向后兼容引用）
 */
public final class NarrativeLangKeys {
    private NarrativeLangKeys() {}

    public static final String NARR_TITLE          = "advancementoverhaul.narrative.title";
    public static final String NARR_CAT_JOURNEY    = "advancementoverhaul.narrative.cat_journey";
    public static final String NARR_CAT_BUILDING   = "advancementoverhaul.narrative.cat_building";
    public static final String NARR_CAT_COMBAT     = "advancementoverhaul.narrative.cat_combat";
    public static final String NARR_CAT_SURVIVAL   = "advancementoverhaul.narrative.cat_survival";
    public static final String NARR_CAT_CRAFTING   = "advancementoverhaul.narrative.cat_crafting";
    public static final String NARR_CAT_EXPLORE    = "advancementoverhaul.narrative.cat_explore";
    public static final String NARR_DAY            = "advancementoverhaul.narrative.day";

    public static final String NARR_SUNRISES_VIEWED       = "advancementoverhaul.narrative.stat_sunrisesViewed";
    public static final String NARR_SUNSETS_VIEWED        = "advancementoverhaul.narrative.stat_sunsetsViewed";
    public static final String NARR_RAIN_TICKS            = "advancementoverhaul.narrative.stat_rainTicks";
    public static final String NARR_SNOW_TICKS            = "advancementoverhaul.narrative.stat_snowTicks";
    public static final String NARR_BLOCKS_PLACED         = "advancementoverhaul.narrative.stat_blocksPlaced";
    public static final String NARR_BLOCKS_BROKEN         = "advancementoverhaul.narrative.stat_blocksBroken";
    public static final String NARR_TORCHES_PLACED        = "advancementoverhaul.narrative.stat_torchesPlaced";
    public static final String NARR_BLOCKS_PLACED_IN_WATER = "advancementoverhaul.narrative.stat_blocksPlacedInWater";
    public static final String NARR_LIGHTNING_STRIKES     = "advancementoverhaul.narrative.stat_lightningStrikes";
    public static final String NARR_FALL_DAMAGE_EVENTS    = "advancementoverhaul.narrative.stat_fallDamageEvents";
    public static final String NARR_ANIMALS_TAMED         = "advancementoverhaul.narrative.stat_animalsTamed";
    public static final String NARR_ANIMALS_FED           = "advancementoverhaul.narrative.stat_animalsFed";
    public static final String NARR_CROPS_PLANTED         = "advancementoverhaul.narrative.stat_cropsPlanted";
    public static final String NARR_NAME_TAGS_USED        = "advancementoverhaul.narrative.stat_nameTagsUsed";
    public static final String NARR_WANDERING_TRADER_TRADES = "advancementoverhaul.narrative.stat_wanderingTraderTrades";
    public static final String NARR_ITEMS_CRAFTED         = "advancementoverhaul.narrative.stat_itemsCrafted";
    public static final String NARR_FURTHEST_DISTANCE     = "advancementoverhaul.narrative.stat_furthestDistance";
    public static final String NARR_MOST_FREQUENT_BIOME   = "advancementoverhaul.narrative.stat_mostFrequentBiome";

    public static final String NARR_FIRST_NETHER_DAY      = "advancementoverhaul.narrative.stat_firstNetherDay";
    public static final String NARR_FIRST_END_DAY         = "advancementoverhaul.narrative.stat_firstEndDay";
    public static final String NARR_FIRST_DIAMOND_DAY     = "advancementoverhaul.narrative.stat_firstDiamondDay";
    public static final String NARR_FIRST_ENCHANT_DAY     = "advancementoverhaul.narrative.stat_firstEnchantDay";
    public static final String NARR_FIRST_TAME_DAY        = "advancementoverhaul.narrative.stat_firstTameDay";
    public static final String NARR_FIRST_RAIN_SLEEP_DAY  = "advancementoverhaul.narrative.stat_firstRainSleepDay";
    public static final String NARR_FIRST_DEATH_DAY       = "advancementoverhaul.narrative.stat_firstDeathDay";
    public static final String NARR_LATEST_DEATH          = "advancementoverhaul.narrative.stat_latestDeath";
    public static final String NARR_FIRST_BLOCK_PLACED    = "advancementoverhaul.narrative.stat_firstBlockPlaced";
    public static final String NARR_LOWEST_Y              = "advancementoverhaul.narrative.stat_lowestY";
    public static final String NARR_HIGHEST_Y             = "advancementoverhaul.narrative.stat_highestY";

    public static final String NARR_DISTANCE_WALKED       = "advancementoverhaul.narrative.stat_distanceWalked";
    public static final String NARR_DISTANCE_SWUM         = "advancementoverhaul.narrative.stat_distanceSwum";
    public static final String NARR_DISTANCE_SPRINT       = "advancementoverhaul.narrative.stat_distanceSprint";
    public static final String NARR_DISTANCE_FLOWN        = "advancementoverhaul.narrative.stat_distanceFlown";
    public static final String NARR_JUMPS                 = "advancementoverhaul.narrative.stat_jumps";
    public static final String NARR_DAMAGE_DEALT          = "advancementoverhaul.narrative.stat_damageDealt";
    public static final String NARR_DAMAGE_TAKEN          = "advancementoverhaul.narrative.stat_damageTaken";
    public static final String NARR_MOB_KILLS             = "advancementoverhaul.narrative.stat_mobKills";
    public static final String NARR_PLAYER_KILLS          = "advancementoverhaul.narrative.stat_playerKills";
    public static final String NARR_DEATHS                = "advancementoverhaul.narrative.stat_deaths";
    public static final String NARR_FISH_CAUGHT           = "advancementoverhaul.narrative.stat_fishCaught";
    public static final String NARR_ANIMALS_BRED          = "advancementoverhaul.narrative.stat_animalsBred";
    public static final String NARR_CAKE_SLICES           = "advancementoverhaul.narrative.stat_cakeSlicesEaten";
    public static final String NARR_CRAFTING_TABLE_USES   = "advancementoverhaul.narrative.stat_craftingTableUses";
    public static final String NARR_ANVIL_USES            = "advancementoverhaul.narrative.stat_anvilUses";
    public static final String NARR_GRINDSTONE_USES       = "advancementoverhaul.narrative.stat_grindstoneUses";
    public static final String NARR_ITEMS_ENCHANTED       = "advancementoverhaul.narrative.stat_itemsEnchanted";
    public static final String NARR_BEACON_USES           = "advancementoverhaul.narrative.stat_beaconUses";
    public static final String NARR_VILLAGER_TRADES       = "advancementoverhaul.narrative.stat_villagerTrades";
    public static final String NARR_RAIDS_WON             = "advancementoverhaul.narrative.stat_raidsWon";
    public static final String NARR_TARGETS_HIT           = "advancementoverhaul.narrative.stat_targetsHit";
    public static final String NARR_BELLS_RUNG            = "advancementoverhaul.narrative.stat_bellsRung";
}
