package com.dreamer.ao.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 玩家统计数据模型。
 * <p>
 * 所有字段通过 getter/setter 访问，支持 {@link StatField} 枚举进行类型安全的字段引用。
 * biomeTimes 使用 LRU 访问顺序 LinkedHashMap，自动淘汰最少访问的条目。
 */
public class PlayerStats {

    // ═══════════════ 字段 ═══════════════

    private static final int MAX_BIOME_TIMES = 128;

    private int sunrisesViewed;
    private int sunsetsViewed;
    private int animalsTamed;
    private int nameTagsUsed;
    private int blocksPlaced;
    private int blocksBroken;
    private int wanderingTraderTrades;
    private int lightningStrikes;
    private long rainTicks;
    private long snowTicks;
    private int cropsPlanted;
    private int itemsCrafted;
    private int fallDamageEvents;
    private int torchesPlaced;
    private int blocksPlacedInWater;
    private int animalsFed;
    private int firstDeathDay = -1;
    private int firstDeathX;
    private int firstDeathY;
    private int firstDeathZ;
    private boolean firstDeathRecorded;
    private int latestDeathX;
    private int latestDeathY;
    private int latestDeathZ;
    private int firstNetherDay = -1;
    private int firstEndDay = -1;
    private int firstDiamondDay = -1;
    private int firstEnchantDay = -1;
    private int firstTameDay = -1;
    private int firstRainSleepDay = -1;
    private int firstBlockPlacedX;
    private int firstBlockPlacedY;
    private int firstBlockPlacedZ;
    private boolean firstBlockPlacedRecorded;
    private String mostFrequentBiome;
    private double furthestDistance;
    private int lowestY = Integer.MAX_VALUE;
    private int highestY = Integer.MIN_VALUE;
    private final Map<String, Long> biomeTimes = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > MAX_BIOME_TIMES;
        }
    };

    // ═══════════════ 工具方法 ═══════════════

    public static int gameDay(long gameTime) {
        return (int)(gameTime / 24000L) + 1;
    }

    public boolean hasLowestY() {
        return this.lowestY != Integer.MAX_VALUE;
    }

    public boolean hasHighestY() {
        return this.highestY != Integer.MIN_VALUE;
    }

    public boolean hasAnyData() {
        return this.sunrisesViewed > 0 || this.sunsetsViewed > 0 || this.animalsTamed > 0
                || this.nameTagsUsed > 0 || this.blocksPlaced > 0 || this.blocksBroken > 0
                || this.wanderingTraderTrades > 0 || this.lightningStrikes > 0
                || this.rainTicks > 0L || this.snowTicks > 0L || this.cropsPlanted > 0
                || this.itemsCrafted > 0 || this.fallDamageEvents > 0 || this.torchesPlaced > 0
                || this.blocksPlacedInWater > 0 || this.animalsFed > 0
                || this.firstDeathRecorded || this.firstBlockPlacedRecorded
                || this.firstNetherDay > 0 || this.firstEndDay > 0 || this.firstDiamondDay > 0
                || this.firstEnchantDay > 0 || this.firstTameDay > 0 || this.firstRainSleepDay > 0
                || this.furthestDistance > 0.0 || this.hasLowestY() || this.hasHighestY()
                || this.mostFrequentBiome != null && !this.mostFrequentBiome.isEmpty();
    }

    /**
     * 通过字段名获取整数值（用于条件评估）。
     * @deprecated 建议使用 {@link #getField(StatField)} 进行类型安全访问
     */
    @Deprecated
    public long getStatValue(String fieldName) {
        return switch (fieldName) {
            case "sunrisesViewed" -> this.sunrisesViewed;
            case "sunsetsViewed" -> this.sunsetsViewed;
            case "animalsTamed" -> this.animalsTamed;
            case "nameTagsUsed" -> this.nameTagsUsed;
            case "blocksPlaced" -> this.blocksPlaced;
            case "blocksBroken" -> this.blocksBroken;
            case "wanderingTraderTrades" -> this.wanderingTraderTrades;
            case "lightningStrikes" -> this.lightningStrikes;
            case "rainTicks" -> this.rainTicks;
            case "snowTicks" -> this.snowTicks;
            case "cropsPlanted" -> this.cropsPlanted;
            case "itemsCrafted" -> this.itemsCrafted;
            case "fallDamageEvents" -> this.fallDamageEvents;
            case "torchesPlaced" -> this.torchesPlaced;
            case "blocksPlacedInWater" -> this.blocksPlacedInWater;
            case "animalsFed" -> this.animalsFed;
            default -> 0L;
        };
    }

    /** 通过枚举获取整数值（类型安全）。 */
    public long getField(StatField field) {
        return switch (field) {
            case SUNRISES_VIEWED -> this.sunrisesViewed;
            case SUNSETS_VIEWED -> this.sunsetsViewed;
            case ANIMALS_TAMED -> this.animalsTamed;
            case NAME_TAGS_USED -> this.nameTagsUsed;
            case BLOCKS_PLACED -> this.blocksPlaced;
            case BLOCKS_BROKEN -> this.blocksBroken;
            case WANDERING_TRADER_TRADES -> this.wanderingTraderTrades;
            case LIGHTNING_STRIKES -> this.lightningStrikes;
            case RAIN_TICKS -> this.rainTicks;
            case SNOW_TICKS -> this.snowTicks;
            case CROPS_PLANTED -> this.cropsPlanted;
            case ITEMS_CRAFTED -> this.itemsCrafted;
            case FALL_DAMAGE_EVENTS -> this.fallDamageEvents;
            case TORCHES_PLACED -> this.torchesPlaced;
            case BLOCKS_PLACED_IN_WATER -> this.blocksPlacedInWater;
            case ANIMALS_FED -> this.animalsFed;
        };
    }

    /** 通过枚举设置整数值（类型安全）。 */
    public void setField(StatField field, long value) {
        switch (field) {
            case SUNRISES_VIEWED -> this.sunrisesViewed = (int) value;
            case SUNSETS_VIEWED -> this.sunsetsViewed = (int) value;
            case ANIMALS_TAMED -> this.animalsTamed = (int) value;
            case NAME_TAGS_USED -> this.nameTagsUsed = (int) value;
            case BLOCKS_PLACED -> this.blocksPlaced = (int) value;
            case BLOCKS_BROKEN -> this.blocksBroken = (int) value;
            case WANDERING_TRADER_TRADES -> this.wanderingTraderTrades = (int) value;
            case LIGHTNING_STRIKES -> this.lightningStrikes = (int) value;
            case RAIN_TICKS -> this.rainTicks = value;
            case SNOW_TICKS -> this.snowTicks = value;
            case CROPS_PLANTED -> this.cropsPlanted = (int) value;
            case ITEMS_CRAFTED -> this.itemsCrafted = (int) value;
            case FALL_DAMAGE_EVENTS -> this.fallDamageEvents = (int) value;
            case TORCHES_PLACED -> this.torchesPlaced = (int) value;
            case BLOCKS_PLACED_IN_WATER -> this.blocksPlacedInWater = (int) value;
            case ANIMALS_FED -> this.animalsFed = (int) value;
        }
    }

    // ═══════════════ Getters & Setters ═══════════════

    public int getSunrisesViewed() { return sunrisesViewed; }
    public void setSunrisesViewed(int v) { this.sunrisesViewed = v; }
    public int getSunsetsViewed() { return sunsetsViewed; }
    public void setSunsetsViewed(int v) { this.sunsetsViewed = v; }
    public int getAnimalsTamed() { return animalsTamed; }
    public void setAnimalsTamed(int v) { this.animalsTamed = v; }
    public int getNameTagsUsed() { return nameTagsUsed; }
    public void setNameTagsUsed(int v) { this.nameTagsUsed = v; }
    public int getBlocksPlaced() { return blocksPlaced; }
    public void setBlocksPlaced(int v) { this.blocksPlaced = v; }
    public int getBlocksBroken() { return blocksBroken; }
    public void setBlocksBroken(int v) { this.blocksBroken = v; }
    public int getWanderingTraderTrades() { return wanderingTraderTrades; }
    public void setWanderingTraderTrades(int v) { this.wanderingTraderTrades = v; }
    public int getLightningStrikes() { return lightningStrikes; }
    public void setLightningStrikes(int v) { this.lightningStrikes = v; }
    public long getRainTicks() { return rainTicks; }
    public void setRainTicks(long v) { this.rainTicks = v; }
    public long getSnowTicks() { return snowTicks; }
    public void setSnowTicks(long v) { this.snowTicks = v; }
    public int getCropsPlanted() { return cropsPlanted; }
    public void setCropsPlanted(int v) { this.cropsPlanted = v; }
    public int getItemsCrafted() { return itemsCrafted; }
    public void setItemsCrafted(int v) { this.itemsCrafted = v; }
    public int getFallDamageEvents() { return fallDamageEvents; }
    public void setFallDamageEvents(int v) { this.fallDamageEvents = v; }
    public int getTorchesPlaced() { return torchesPlaced; }
    public void setTorchesPlaced(int v) { this.torchesPlaced = v; }
    public int getBlocksPlacedInWater() { return blocksPlacedInWater; }
    public void setBlocksPlacedInWater(int v) { this.blocksPlacedInWater = v; }
    public int getAnimalsFed() { return animalsFed; }
    public void setAnimalsFed(int v) { this.animalsFed = v; }

    public int getFirstDeathDay() { return firstDeathDay; }
    public void setFirstDeathDay(int v) { this.firstDeathDay = v; }
    public int getFirstDeathX() { return firstDeathX; }
    public void setFirstDeathX(int v) { this.firstDeathX = v; }
    public int getFirstDeathY() { return firstDeathY; }
    public void setFirstDeathY(int v) { this.firstDeathY = v; }
    public int getFirstDeathZ() { return firstDeathZ; }
    public void setFirstDeathZ(int v) { this.firstDeathZ = v; }
    public boolean isFirstDeathRecorded() { return firstDeathRecorded; }
    public void setFirstDeathRecorded(boolean v) { this.firstDeathRecorded = v; }
    public int getLatestDeathX() { return latestDeathX; }
    public void setLatestDeathX(int v) { this.latestDeathX = v; }
    public int getLatestDeathY() { return latestDeathY; }
    public void setLatestDeathY(int v) { this.latestDeathY = v; }
    public int getLatestDeathZ() { return latestDeathZ; }
    public void setLatestDeathZ(int v) { this.latestDeathZ = v; }

    public int getFirstNetherDay() { return firstNetherDay; }
    public void setFirstNetherDay(int v) { this.firstNetherDay = v; }
    public int getFirstEndDay() { return firstEndDay; }
    public void setFirstEndDay(int v) { this.firstEndDay = v; }
    public int getFirstDiamondDay() { return firstDiamondDay; }
    public void setFirstDiamondDay(int v) { this.firstDiamondDay = v; }
    public int getFirstEnchantDay() { return firstEnchantDay; }
    public void setFirstEnchantDay(int v) { this.firstEnchantDay = v; }
    public int getFirstTameDay() { return firstTameDay; }
    public void setFirstTameDay(int v) { this.firstTameDay = v; }
    public int getFirstRainSleepDay() { return firstRainSleepDay; }
    public void setFirstRainSleepDay(int v) { this.firstRainSleepDay = v; }

    public int getFirstBlockPlacedX() { return firstBlockPlacedX; }
    public void setFirstBlockPlacedX(int v) { this.firstBlockPlacedX = v; }
    public int getFirstBlockPlacedY() { return firstBlockPlacedY; }
    public void setFirstBlockPlacedY(int v) { this.firstBlockPlacedY = v; }
    public int getFirstBlockPlacedZ() { return firstBlockPlacedZ; }
    public void setFirstBlockPlacedZ(int v) { this.firstBlockPlacedZ = v; }
    public boolean isFirstBlockPlacedRecorded() { return firstBlockPlacedRecorded; }
    public void setFirstBlockPlacedRecorded(boolean v) { this.firstBlockPlacedRecorded = v; }

    public String getMostFrequentBiome() { return mostFrequentBiome; }
    public void setMostFrequentBiome(String v) { this.mostFrequentBiome = v; }
    public double getFurthestDistance() { return furthestDistance; }
    public void setFurthestDistance(double v) { this.furthestDistance = v; }
    public int getLowestY() { return lowestY; }
    public void setLowestY(int v) { this.lowestY = v; }
    public int getHighestY() { return highestY; }
    public void setHighestY(int v) { this.highestY = v; }
    public Map<String, Long> getBiomeTimes() { return Collections.unmodifiableMap(biomeTimes); }

    /** 累加某群系的停留 tick 数（替代外部直接对 getBiomeTimes() 做 merge） */
    public void addBiomeTime(String biomeId, long ticks) {
        if (biomeId == null) return;
        biomeTimes.merge(biomeId, ticks, Long::sum);
    }

    /** 反序列化载入用（替代外部直接对 getBiomeTimes() 做 put） */
    public void putBiomeTime(String biomeId, long ticks) {
        if (biomeId != null) biomeTimes.put(biomeId, ticks);
    }

    /** 清空群系停留统计（替代外部直接对 getBiomeTimes() 做 clear） */
    public void clearBiomeTimes() { biomeTimes.clear(); }

    /** 选出停留时长最长的群系并清空统计，返回该群系 id（null 表示无数据） */
    public String pollTopBiome() {
        String top = null;
        long best = 0L;
        for (Map.Entry<String, Long> e : biomeTimes.entrySet()) {
            if (e.getValue() > best) {
                best = e.getValue();
                top = e.getKey();
            }
        }
        biomeTimes.clear();
        return top;
    }

    public boolean isBiomeTimesEmpty() { return biomeTimes.isEmpty(); }
}
