package com.dreamer.ao.milestone.store;

import com.dreamer.ao.data.PlayerStats;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StatValueStore {
    private final Map<String, Long> counters = new ConcurrentHashMap<String, Long>();
    private final Map<String, JsonObject> records = new ConcurrentHashMap<String, JsonObject>();
    private final Map<String, Integer> firstDay = new ConcurrentHashMap<String, Integer>();

    public long getCounter(String key) {
        return this.counters.getOrDefault(key, 0L);
    }

    public long incrementCounter(String key, long amount) {
        return this.counters.merge(key, amount, Long::sum);
    }

    public void setCounter(String key, long value) {
        this.counters.put(key, value);
    }

    public Map<String, Long> getCounters() {
        return this.counters;
    }

    public JsonObject getRecord(String key) {
        return this.records.get(key);
    }

    public void setRecord(String key, JsonObject value) {
        this.records.put(key, value);
    }

    public boolean hasRecord(String key) {
        return this.records.containsKey(key);
    }

    public int getFirstDay(String key) {
        return this.firstDay.getOrDefault(key, -1);
    }

    public void setFirstDay(String key, int day) {
        this.firstDay.putIfAbsent(key, day);
    }

    public boolean hasFirstDay(String key) {
        return this.firstDay.containsKey(key);
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        JsonObject countersObj = new JsonObject();
        this.counters.forEach((k, v) -> countersObj.addProperty(k, (Number)v));
        obj.add("counters", (JsonElement)countersObj);
        JsonObject recordsObj = new JsonObject();
        this.records.forEach((k, v) -> recordsObj.add(k, (JsonElement)v));
        obj.add("records", (JsonElement)recordsObj);
        JsonObject firstDayObj = new JsonObject();
        this.firstDay.forEach((k, v) -> firstDayObj.addProperty(k, (Number)v));
        obj.add("firstDay", (JsonElement)firstDayObj);
        return obj;
    }

    public static StatValueStore fromJson(JsonObject obj) {
        StatValueStore store = new StatValueStore();
        if (obj.has("counters")) {
            JsonObject countersObj = obj.getAsJsonObject("counters");
            for (Map.Entry entry : countersObj.entrySet()) {
                store.counters.put((String)entry.getKey(), ((JsonElement)entry.getValue()).getAsLong());
            }
        }
        if (obj.has("records")) {
            JsonObject recordsObj = obj.getAsJsonObject("records");
            for (Map.Entry entry : recordsObj.entrySet()) {
                store.records.put((String)entry.getKey(), ((JsonElement)entry.getValue()).getAsJsonObject());
            }
        }
        if (obj.has("firstDay")) {
            JsonObject firstDayObj = obj.getAsJsonObject("firstDay");
            for (Map.Entry entry : firstDayObj.entrySet()) {
                store.firstDay.put((String)entry.getKey(), ((JsonElement)entry.getValue()).getAsInt());
            }
        }
        return store;
    }

    public boolean hasAnyData() {
        return !this.counters.isEmpty() || !this.records.isEmpty() || !this.firstDay.isEmpty();
    }

    public static StatValueStore migrateFromPlayerStats(PlayerStats old) {
        StatValueStore store = new StatValueStore();
        if (old.getSunrisesViewed() > 0) {
            store.setCounter("sunrises_viewed", old.getSunrisesViewed());
        }
        if (old.getSunsetsViewed() > 0) {
            store.setCounter("sunsets_viewed", old.getSunsetsViewed());
        }
        if (old.getAnimalsTamed() > 0) {
            store.setCounter("animals_tamed", old.getAnimalsTamed());
        }
        if (old.getNameTagsUsed() > 0) {
            store.setCounter("name_tags_used", old.getNameTagsUsed());
        }
        if (old.getBlocksPlaced() > 0) {
            store.setCounter("blocks_placed", old.getBlocksPlaced());
        }
        if (old.getBlocksBroken() > 0) {
            store.setCounter("blocks_broken", old.getBlocksBroken());
        }
        if (old.getWanderingTraderTrades() > 0) {
            store.setCounter("wandering_trader_trades", old.getWanderingTraderTrades());
        }
        if (old.getLightningStrikes() > 0) {
            store.setCounter("lightning_strikes", old.getLightningStrikes());
        }
        if (old.getRainTicks() > 0L) {
            store.setCounter("rain_ticks", old.getRainTicks());
        }
        if (old.getSnowTicks() > 0L) {
            store.setCounter("snow_ticks", old.getSnowTicks());
        }
        if (old.getCropsPlanted() > 0) {
            store.setCounter("crops_planted", old.getCropsPlanted());
        }
        if (old.getItemsCrafted() > 0) {
            store.setCounter("items_crafted", old.getItemsCrafted());
        }
        if (old.getFallDamageEvents() > 0) {
            store.setCounter("fall_damage_events", old.getFallDamageEvents());
        }
        if (old.getTorchesPlaced() > 0) {
            store.setCounter("torches_placed", old.getTorchesPlaced());
        }
        if (old.getBlocksPlacedInWater() > 0) {
            store.setCounter("blocks_placed_in_water", old.getBlocksPlacedInWater());
        }
        if (old.getAnimalsFed() > 0) {
            store.setCounter("animals_fed", old.getAnimalsFed());
        }
        if (old.isFirstDeathRecorded()) {
            store.setFirstDay("first_death", old.getFirstDeathDay());
        }
        if (old.getFirstNetherDay() > 0) {
            store.setFirstDay("first_nether", old.getFirstNetherDay());
        }
        if (old.getFirstEndDay() > 0) {
            store.setFirstDay("first_end", old.getFirstEndDay());
        }
        if (old.getFirstDiamondDay() > 0) {
            store.setFirstDay("first_diamond", old.getFirstDiamondDay());
        }
        if (old.getFirstEnchantDay() > 0) {
            store.setFirstDay("first_enchant", old.getFirstEnchantDay());
        }
        if (old.getFirstTameDay() > 0) {
            store.setFirstDay("first_tame", old.getFirstTameDay());
        }
        if (old.getFirstRainSleepDay() > 0) {
            store.setFirstDay("first_rain_sleep", old.getFirstRainSleepDay());
        }
        if (old.isFirstBlockPlacedRecorded()) {
            store.setFirstDay("first_block_placed", 1);
        }
        if (old.getFurthestDistance() > 0.0) {
            store.setCounter("furthest_distance", (long)old.getFurthestDistance());
        }
        if (old.hasLowestY()) {
            store.setCounter("lowest_y", old.getLowestY());
        }
        if (old.hasHighestY()) {
            store.setCounter("highest_y", old.getHighestY());
        }
        if (old.getMostFrequentBiome() != null && !old.getMostFrequentBiome().isEmpty()) {
            JsonObject biomeRecord = new JsonObject();
            biomeRecord.addProperty("biome", old.getMostFrequentBiome());
            store.setRecord("most_frequent_biome", biomeRecord);
        }
        return store;
    }
}
