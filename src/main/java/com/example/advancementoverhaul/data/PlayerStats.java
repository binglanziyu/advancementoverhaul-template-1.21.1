package com.example.advancementoverhaul.data;

import java.util.LinkedHashMap;
import java.util.Map;

public class PlayerStats {
    public int sunrisesViewed;
    public int sunsetsViewed;
    public int animalsTamed;
    public int nameTagsUsed;
    public int blocksPlaced;
    public int blocksBroken;
    public int wanderingTraderTrades;
    public int lightningStrikes;
    public long rainTicks;
    public long snowTicks;
    public int cropsPlanted;
    public int itemsCrafted;
    public int fallDamageEvents;
    public int torchesPlaced;
    public int blocksPlacedInWater;
    public int animalsFed;
    public int firstDeathDay = -1;
    public int firstDeathX;
    public int firstDeathY;
    public int firstDeathZ;
    public boolean firstDeathRecorded;
    public int latestDeathX;
    public int latestDeathY;
    public int latestDeathZ;
    public int firstNetherDay = -1;
    public int firstEndDay = -1;
    public int firstDiamondDay = -1;
    public int firstEnchantDay = -1;
    public int firstTameDay = -1;
    public int firstRainSleepDay = -1;
    public int firstBlockPlacedX;
    public int firstBlockPlacedY;
    public int firstBlockPlacedZ;
    public boolean firstBlockPlacedRecorded;
    public String mostFrequentBiome;
    public double furthestDistance;
    public int lowestY = Integer.MAX_VALUE;
    public int highestY = Integer.MIN_VALUE;
    public Map<String, Long> biomeTimes = new LinkedHashMap<>();

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
}
