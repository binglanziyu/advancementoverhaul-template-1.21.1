/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonParser
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.example.advancementoverhaul.data;

import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.PlayerStats;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerStatsStore {
    private static final Logger LOGGER = LoggerFactory.getLogger((String)"AdvancementOverhaul/Stats");
    private static final PlayerStatsStore INSTANCE = new PlayerStatsStore();
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AO-StatsSave");
        t.setDaemon(true);
        return t;
    });
    private final Map<UUID, PlayerStats> statsMap = new ConcurrentHashMap<UUID, PlayerStats>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final Set<UUID> dirtyUuids = Collections.synchronizedSet(new HashSet());
    private volatile Path dataDir;
    private long lastAutoSave;

    public static PlayerStatsStore getInstance() {
        return INSTANCE;
    }

    private PlayerStatsStore() {
    }

    public void init(Path baseDir) {
        if (this.dataDir != null) {
            return;
        }
        this.dataDir = baseDir.resolve("player_stats");
        try {
            Files.createDirectories(this.dataDir, new FileAttribute[0]);
        }
        catch (IOException e) {
            LOGGER.error("Failed to create player_stats directory", (Throwable)e);
            return;
        }
        this.loadFromDir();
    }

    public PlayerStats getOrCreate(UUID uuid) {
        return this.statsMap.computeIfAbsent(uuid, k -> new PlayerStats());
    }

    public PlayerStats get(UUID uuid) {
        return this.statsMap.get(uuid);
    }

    public void markDirty(UUID uuid) {
        this.dirty.set(true);
        this.dirtyUuids.add(uuid);
    }

    public boolean isDirty(UUID uuid) {
        return this.dirtyUuids.contains(uuid);
    }

    public void tick() {
        long now = System.currentTimeMillis();
        if (now - this.lastAutoSave > 30000L) {
            this.saveIfDirty();
            this.lastAutoSave = now;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void saveIfDirty() {
        ArrayList<UUID> snapshot;
        if (!this.dirty.compareAndSet(true, false)) {
            return;
        }
        Set<UUID> set = this.dirtyUuids;
        synchronized (set) {
            snapshot = new ArrayList<UUID>(this.dirtyUuids);
            this.dirtyUuids.clear();
        }
        if (snapshot.isEmpty()) {
            return;
        }
        HashMap<UUID, PlayerStats> snapshotMap = new HashMap<UUID, PlayerStats>();
        for (UUID uuid : snapshot) {
            PlayerStats s = this.statsMap.get(uuid);
            if (s == null) continue;
            snapshotMap.put(uuid, s);
        }
        this.saveExecutor.submit(() -> {
            try {
                this.writeToFiles(snapshotMap);
            }
            catch (Exception e) {
                this.dirty.set(true);
                LOGGER.error("Failed to save player stats, will retry", (Throwable)e);
            }
        });
    }

    public void saveAll() {
        this.dirty.set(false);
        this.dirtyUuids.clear();
        HashMap<UUID, PlayerStats> snapshot = new HashMap<UUID, PlayerStats>(this.statsMap);
        this.saveExecutor.submit(() -> {
            try {
                this.writeToFiles(snapshot);
            }
            catch (Exception e) {
                LOGGER.error("Failed to save all player stats", (Throwable)e);
            }
        });
    }

    public void shutdown() {
        this.saveIfDirty();
        this.saveExecutor.shutdown();
        try {
            if (!this.saveExecutor.awaitTermination(5L, TimeUnit.SECONDS)) {
                this.saveExecutor.shutdownNow();
            }
        }
        catch (InterruptedException e) {
            this.saveExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void writeToFiles(Map<UUID, PlayerStats> map) {
        if (this.dataDir == null) {
            return;
        }
        for (Map.Entry<UUID, PlayerStats> entry : map.entrySet()) {
            UUID uuid = entry.getKey();
            PlayerStats stats = entry.getValue();
            JsonObject obj = this.statsToJson(stats);
            try {
                Path target = this.dataDir.resolve(uuid.toString() + ".json");
                Path tmp = this.dataDir.resolve(uuid.toString() + ".json.tmp");
                Files.writeString(tmp, (CharSequence)DataStore.GSON_PRETTY.toJson((JsonElement)obj), new OpenOption[0]);
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (Exception e) {
                LOGGER.warn("Failed to write stats for UUID {}: {}", (Object)uuid, (Object)e.getMessage());
            }
        }
    }

    private void loadFromDir() {
        if (this.dataDir == null || !Files.exists(this.dataDir, new LinkOption[0])) {
            return;
        }
        this.statsMap.clear();
        try (Stream<Path> stream = Files.list(this.dataDir);){
            stream.filter(f -> f.getFileName().toString().endsWith(".json")).forEach(file -> {
                try {
                    String name = file.getFileName().toString();
                    String uuidStr = name.substring(0, name.length() - 5);
                    UUID uuid = UUID.fromString(uuidStr);
                    JsonObject obj = JsonParser.parseString((String)Files.readString(file)).getAsJsonObject();
                    PlayerStats stats = this.statsFromJson(obj);
                    this.statsMap.put(uuid, stats);
                }
                catch (IllegalArgumentException name) {
                }
                catch (Exception e) {
                    LOGGER.warn("Failed to parse stats file {}: {}", (Object)file.getFileName(), (Object)e.getMessage());
                }
            });
        }
        catch (Exception e) {
            LOGGER.error("Failed to list player_stats directory", (Throwable)e);
        }
        LOGGER.info("Loaded stats for {} players", (Object)this.statsMap.size());
    }

    private JsonObject statsToJson(PlayerStats s) {
        JsonObject obj = new JsonObject();
        obj.addProperty("sunrisesViewed", (Number)s.sunrisesViewed);
        obj.addProperty("sunsetsViewed", (Number)s.sunsetsViewed);
        obj.addProperty("animalsTamed", (Number)s.animalsTamed);
        obj.addProperty("nameTagsUsed", (Number)s.nameTagsUsed);
        obj.addProperty("blocksPlaced", (Number)s.blocksPlaced);
        obj.addProperty("blocksBroken", (Number)s.blocksBroken);
        obj.addProperty("wanderingTraderTrades", (Number)s.wanderingTraderTrades);
        obj.addProperty("lightningStrikes", (Number)s.lightningStrikes);
        obj.addProperty("rainTicks", (Number)s.rainTicks);
        obj.addProperty("snowTicks", (Number)s.snowTicks);
        obj.addProperty("cropsPlanted", (Number)s.cropsPlanted);
        obj.addProperty("itemsCrafted", (Number)s.itemsCrafted);
        obj.addProperty("fallDamageEvents", (Number)s.fallDamageEvents);
        obj.addProperty("torchesPlaced", (Number)s.torchesPlaced);
        obj.addProperty("blocksPlacedInWater", (Number)s.blocksPlacedInWater);
        obj.addProperty("animalsFed", (Number)s.animalsFed);
        obj.addProperty("firstDeathDay", (Number)s.firstDeathDay);
        obj.addProperty("firstDeathX", (Number)s.firstDeathX);
        obj.addProperty("firstDeathY", (Number)s.firstDeathY);
        obj.addProperty("firstDeathZ", (Number)s.firstDeathZ);
        obj.addProperty("firstDeathRecorded", Boolean.valueOf(s.firstDeathRecorded));
        obj.addProperty("latestDeathX", (Number)s.latestDeathX);
        obj.addProperty("latestDeathY", (Number)s.latestDeathY);
        obj.addProperty("latestDeathZ", (Number)s.latestDeathZ);
        obj.addProperty("firstNetherDay", (Number)s.firstNetherDay);
        obj.addProperty("firstEndDay", (Number)s.firstEndDay);
        obj.addProperty("firstDiamondDay", (Number)s.firstDiamondDay);
        obj.addProperty("firstEnchantDay", (Number)s.firstEnchantDay);
        obj.addProperty("firstTameDay", (Number)s.firstTameDay);
        obj.addProperty("firstRainSleepDay", (Number)s.firstRainSleepDay);
        obj.addProperty("firstBlockPlacedX", (Number)s.firstBlockPlacedX);
        obj.addProperty("firstBlockPlacedY", (Number)s.firstBlockPlacedY);
        obj.addProperty("firstBlockPlacedZ", (Number)s.firstBlockPlacedZ);
        obj.addProperty("firstBlockPlacedRecorded", Boolean.valueOf(s.firstBlockPlacedRecorded));
        if (s.mostFrequentBiome != null) {
            obj.addProperty("mostFrequentBiome", s.mostFrequentBiome);
        }
        obj.addProperty("furthestDistance", (Number)s.furthestDistance);
        obj.addProperty("lowestY", (Number)s.lowestY);
        obj.addProperty("highestY", (Number)s.highestY);
        if (!s.biomeTimes.isEmpty()) {
            JsonObject btObj = new JsonObject();
            for (Map.Entry<String, Long> e : s.biomeTimes.entrySet()) {
                btObj.addProperty(e.getKey(), (Number)e.getValue());
            }
            obj.add("biomeTimes", (JsonElement)btObj);
        }
        return obj;
    }

    private PlayerStats statsFromJson(JsonObject obj) {
        PlayerStats s = new PlayerStats();
        s.sunrisesViewed = PlayerStatsStore.getInt(obj, "sunrisesViewed");
        s.sunsetsViewed = PlayerStatsStore.getInt(obj, "sunsetsViewed");
        s.animalsTamed = PlayerStatsStore.getInt(obj, "animalsTamed");
        s.nameTagsUsed = PlayerStatsStore.getInt(obj, "nameTagsUsed");
        s.blocksPlaced = PlayerStatsStore.getInt(obj, "blocksPlaced");
        s.blocksBroken = PlayerStatsStore.getInt(obj, "blocksBroken");
        s.wanderingTraderTrades = PlayerStatsStore.getInt(obj, "wanderingTraderTrades");
        s.lightningStrikes = PlayerStatsStore.getInt(obj, "lightningStrikes");
        s.rainTicks = PlayerStatsStore.getLong(obj, "rainTicks");
        s.snowTicks = PlayerStatsStore.getLong(obj, "snowTicks");
        s.cropsPlanted = PlayerStatsStore.getInt(obj, "cropsPlanted");
        s.itemsCrafted = PlayerStatsStore.getInt(obj, "itemsCrafted");
        s.fallDamageEvents = PlayerStatsStore.getInt(obj, "fallDamageEvents");
        s.torchesPlaced = PlayerStatsStore.getInt(obj, "torchesPlaced");
        s.blocksPlacedInWater = PlayerStatsStore.getInt(obj, "blocksPlacedInWater");
        s.animalsFed = PlayerStatsStore.getInt(obj, "animalsFed");
        s.firstDeathDay = PlayerStatsStore.getInt(obj, "firstDeathDay", -1);
        s.firstDeathX = PlayerStatsStore.getInt(obj, "firstDeathX");
        s.firstDeathY = PlayerStatsStore.getInt(obj, "firstDeathY");
        s.firstDeathZ = PlayerStatsStore.getInt(obj, "firstDeathZ");
        s.firstDeathRecorded = PlayerStatsStore.getBool(obj, "firstDeathRecorded");
        s.latestDeathX = PlayerStatsStore.getInt(obj, "latestDeathX");
        s.latestDeathY = PlayerStatsStore.getInt(obj, "latestDeathY");
        s.latestDeathZ = PlayerStatsStore.getInt(obj, "latestDeathZ");
        s.firstNetherDay = PlayerStatsStore.getInt(obj, "firstNetherDay", -1);
        s.firstEndDay = PlayerStatsStore.getInt(obj, "firstEndDay", -1);
        s.firstDiamondDay = PlayerStatsStore.getInt(obj, "firstDiamondDay", -1);
        s.firstEnchantDay = PlayerStatsStore.getInt(obj, "firstEnchantDay", -1);
        s.firstTameDay = PlayerStatsStore.getInt(obj, "firstTameDay", -1);
        s.firstRainSleepDay = PlayerStatsStore.getInt(obj, "firstRainSleepDay", -1);
        s.firstBlockPlacedX = PlayerStatsStore.getInt(obj, "firstBlockPlacedX");
        s.firstBlockPlacedY = PlayerStatsStore.getInt(obj, "firstBlockPlacedY");
        s.firstBlockPlacedZ = PlayerStatsStore.getInt(obj, "firstBlockPlacedZ");
        s.firstBlockPlacedRecorded = PlayerStatsStore.getBool(obj, "firstBlockPlacedRecorded");
        if (obj.has("mostFrequentBiome")) {
            s.mostFrequentBiome = obj.get("mostFrequentBiome").getAsString();
        }
        s.furthestDistance = PlayerStatsStore.getDouble(obj, "furthestDistance");
        s.lowestY = obj.has("lowestY") ? obj.get("lowestY").getAsInt() : Integer.MAX_VALUE;
        int n = s.highestY = obj.has("highestY") ? obj.get("highestY").getAsInt() : Integer.MIN_VALUE;
        if (obj.has("biomeTimes") && obj.get("biomeTimes").isJsonObject()) {
            for (Map.Entry e : obj.getAsJsonObject("biomeTimes").entrySet()) {
                s.biomeTimes.put((String)e.getKey(), ((JsonElement)e.getValue()).getAsLong());
            }
        }
        return s;
    }

    private static int getInt(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e != null && !e.isJsonNull() ? e.getAsInt() : 0;
    }

    private static int getInt(JsonObject obj, String key, int defaultVal) {
        JsonElement e = obj.get(key);
        return e != null && !e.isJsonNull() ? e.getAsInt() : defaultVal;
    }

    private static long getLong(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e != null && !e.isJsonNull() ? e.getAsLong() : 0L;
    }

    private static boolean getBool(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e != null && !e.isJsonNull() && e.getAsBoolean();
    }

    private static double getDouble(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e != null && !e.isJsonNull() ? e.getAsDouble() : 0.0;
    }
}

