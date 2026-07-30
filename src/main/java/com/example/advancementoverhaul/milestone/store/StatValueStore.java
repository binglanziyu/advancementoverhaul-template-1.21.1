package com.example.advancementoverhaul.milestone.store;

import com.example.advancementoverhaul.data.PlayerStats;
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
        if (old.sunrisesViewed > 0) {
            store.setCounter("sunrises_viewed", old.sunrisesViewed);
        }
        if (old.sunsetsViewed > 0) {
            store.setCounter("sunsets_viewed", old.sunsetsViewed);
        }
        if (old.animalsTamed > 0) {
            store.setCounter("animals_tamed", old.animalsTamed);
        }
        if (old.nameTagsUsed > 0) {
            store.setCounter("name_tags_used", old.nameTagsUsed);
        }
        if (old.blocksPlaced > 0) {
            store.setCounter("blocks_placed", old.blocksPlaced);
        }
        if (old.blocksBroken > 0) {
            store.setCounter("blocks_broken", old.blocksBroken);
        }
        if (old.wanderingTraderTrades > 0) {
            store.setCounter("wandering_trader_trades", old.wanderingTraderTrades);
        }
        if (old.lightningStrikes > 0) {
            store.setCounter("lightning_strikes", old.lightningStrikes);
        }
        if (old.rainTicks > 0L) {
            store.setCounter("rain_ticks", old.rainTicks);
        }
        if (old.snowTicks > 0L) {
            store.setCounter("snow_ticks", old.snowTicks);
        }
        if (old.cropsPlanted > 0) {
            store.setCounter("crops_planted", old.cropsPlanted);
        }
        if (old.itemsCrafted > 0) {
            store.setCounter("items_crafted", old.itemsCrafted);
        }
        if (old.fallDamageEvents > 0) {
            store.setCounter("fall_damage_events", old.fallDamageEvents);
        }
        if (old.torchesPlaced > 0) {
            store.setCounter("torches_placed", old.torchesPlaced);
        }
        if (old.blocksPlacedInWater > 0) {
            store.setCounter("blocks_placed_in_water", old.blocksPlacedInWater);
        }
        if (old.animalsFed > 0) {
            store.setCounter("animals_fed", old.animalsFed);
        }
        if (old.firstDeathRecorded) {
            store.setFirstDay("first_death", old.firstDeathDay);
        }
        if (old.firstNetherDay > 0) {
            store.setFirstDay("first_nether", old.firstNetherDay);
        }
        if (old.firstEndDay > 0) {
            store.setFirstDay("first_end", old.firstEndDay);
        }
        if (old.firstDiamondDay > 0) {
            store.setFirstDay("first_diamond", old.firstDiamondDay);
        }
        if (old.firstEnchantDay > 0) {
            store.setFirstDay("first_enchant", old.firstEnchantDay);
        }
        if (old.firstTameDay > 0) {
            store.setFirstDay("first_tame", old.firstTameDay);
        }
        if (old.firstRainSleepDay > 0) {
            store.setFirstDay("first_rain_sleep", old.firstRainSleepDay);
        }
        if (old.firstBlockPlacedRecorded) {
            store.setFirstDay("first_block_placed", 1);
        }
        if (old.furthestDistance > 0.0) {
            store.setCounter("furthest_distance", (long)old.furthestDistance);
        }
        if (old.hasLowestY()) {
            store.setCounter("lowest_y", old.lowestY);
        }
        if (old.hasHighestY()) {
            store.setCounter("highest_y", old.highestY);
        }
        if (old.mostFrequentBiome != null && !old.mostFrequentBiome.isEmpty()) {
            JsonObject biomeRecord = new JsonObject();
            biomeRecord.addProperty("biome", old.mostFrequentBiome);
            store.setRecord("most_frequent_biome", biomeRecord);
        }
        return store;
    }
}
