package com.example.advancementoverhaul.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/Stats");
    private static final PlayerStatsStore INSTANCE = new PlayerStatsStore();
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AO-StatsSave");
        t.setDaemon(true);
        return t;
    });
    private final Map<UUID, PlayerStats> statsMap = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final Set<UUID> dirtyUuids = Collections.synchronizedSet(new HashSet<>());
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
            Files.createDirectories(this.dataDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create player_stats directory", e);
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

    public void saveIfDirty() {
        if (!this.dirty.compareAndSet(true, false)) {
            return;
        }
        ArrayList<UUID> snapshot;
        synchronized (this.dirtyUuids) {
            snapshot = new ArrayList<>(this.dirtyUuids);
            this.dirtyUuids.clear();
        }
        if (snapshot.isEmpty()) {
            return;
        }
        HashMap<UUID, PlayerStats> snapshotMap = new HashMap<>();
        for (UUID uuid : snapshot) {
            PlayerStats s = this.statsMap.get(uuid);
            if (s == null) continue;
            snapshotMap.put(uuid, s);
        }
        this.saveExecutor.submit(() -> {
            try {
                this.writeToFiles(snapshotMap);
            } catch (Exception e) {
                this.dirty.set(true);
                LOGGER.error("Failed to save player stats, will retry", e);
            }
        });
    }

    public void saveAll() {
        this.dirty.set(false);
        this.dirtyUuids.clear();
        HashMap<UUID, PlayerStats> snapshot = new HashMap<>(this.statsMap);
        this.saveExecutor.submit(() -> {
            try {
                this.writeToFiles(snapshot);
            } catch (Exception e) {
                LOGGER.error("Failed to save all player stats", e);
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
        } catch (InterruptedException e) {
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
                Files.writeString(tmp, DataStore.GSON_PRETTY.toJson(obj));
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                LOGGER.warn("Failed to write stats for UUID {}: {}", uuid, e.getMessage());
            }
        }
    }

    private void loadFromDir() {
        if (this.dataDir == null || !Files.exists(this.dataDir)) {
            return;
        }
        this.statsMap.clear();
        try (Stream<Path> stream = Files.list(this.dataDir)) {
            stream.filter(f -> f.getFileName().toString().endsWith(".json")).forEach(file -> {
                try {
                    String name = file.getFileName().toString();
                    String uuidStr = name.substring(0, name.length() - 5);
                    UUID uuid = UUID.fromString(uuidStr);
                    JsonObject obj = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                    PlayerStats stats = this.statsFromJson(obj);
                    this.statsMap.put(uuid, stats);
                } catch (IllegalArgumentException ignored) {
                } catch (Exception e) {
                    LOGGER.warn("Failed to parse stats file {}: {}", file.getFileName(), e.getMessage());
                }
            });
        } catch (Exception e) {
            LOGGER.error("Failed to list player_stats directory", e);
        }
        LOGGER.info("Loaded stats for {} players", this.statsMap.size());
    }

    private JsonObject statsToJson(PlayerStats s) {
        JsonObject obj = new JsonObject();
        obj.addProperty("sunrisesViewed", s.sunrisesViewed);
        obj.addProperty("sunsetsViewed", s.sunsetsViewed);
        obj.addProperty("animalsTamed", s.animalsTamed);
        obj.addProperty("nameTagsUsed", s.nameTagsUsed);
        obj.addProperty("blocksPlaced", s.blocksPlaced);
        obj.addProperty("blocksBroken", s.blocksBroken);
        obj.addProperty("wanderingTraderTrades", s.wanderingTraderTrades);
        obj.addProperty("lightningStrikes", s.lightningStrikes);
        obj.addProperty("rainTicks", s.rainTicks);
        obj.addProperty("snowTicks", s.snowTicks);
        obj.addProperty("cropsPlanted", s.cropsPlanted);
        obj.addProperty("itemsCrafted", s.itemsCrafted);
        obj.addProperty("fallDamageEvents", s.fallDamageEvents);
        obj.addProperty("torchesPlaced", s.torchesPlaced);
        obj.addProperty("blocksPlacedInWater", s.blocksPlacedInWater);
        obj.addProperty("animalsFed", s.animalsFed);
        obj.addProperty("firstDeathDay", s.firstDeathDay);
        obj.addProperty("firstDeathX", s.firstDeathX);
        obj.addProperty("firstDeathY", s.firstDeathY);
        obj.addProperty("firstDeathZ", s.firstDeathZ);
        obj.addProperty("firstDeathRecorded", s.firstDeathRecorded);
        obj.addProperty("latestDeathX", s.latestDeathX);
        obj.addProperty("latestDeathY", s.latestDeathY);
        obj.addProperty("latestDeathZ", s.latestDeathZ);
        obj.addProperty("firstNetherDay", s.firstNetherDay);
        obj.addProperty("firstEndDay", s.firstEndDay);
        obj.addProperty("firstDiamondDay", s.firstDiamondDay);
        obj.addProperty("firstEnchantDay", s.firstEnchantDay);
        obj.addProperty("firstTameDay", s.firstTameDay);
        obj.addProperty("firstRainSleepDay", s.firstRainSleepDay);
        obj.addProperty("firstBlockPlacedX", s.firstBlockPlacedX);
        obj.addProperty("firstBlockPlacedY", s.firstBlockPlacedY);
        obj.addProperty("firstBlockPlacedZ", s.firstBlockPlacedZ);
        obj.addProperty("firstBlockPlacedRecorded", s.firstBlockPlacedRecorded);
        if (s.mostFrequentBiome != null) {
            obj.addProperty("mostFrequentBiome", s.mostFrequentBiome);
        }
        obj.addProperty("furthestDistance", s.furthestDistance);
        obj.addProperty("lowestY", s.lowestY);
        obj.addProperty("highestY", s.highestY);
        if (!s.biomeTimes.isEmpty()) {
            JsonObject btObj = new JsonObject();
            for (Map.Entry<String, Long> e : s.biomeTimes.entrySet()) {
                btObj.addProperty(e.getKey(), e.getValue());
            }
            obj.add("biomeTimes", btObj);
        }
        return obj;
    }

    private PlayerStats statsFromJson(JsonObject obj) {
        PlayerStats s = new PlayerStats();
        s.sunrisesViewed = getInt(obj, "sunrisesViewed");
        s.sunsetsViewed = getInt(obj, "sunsetsViewed");
        s.animalsTamed = getInt(obj, "animalsTamed");
        s.nameTagsUsed = getInt(obj, "nameTagsUsed");
        s.blocksPlaced = getInt(obj, "blocksPlaced");
        s.blocksBroken = getInt(obj, "blocksBroken");
        s.wanderingTraderTrades = getInt(obj, "wanderingTraderTrades");
        s.lightningStrikes = getInt(obj, "lightningStrikes");
        s.rainTicks = getLong(obj, "rainTicks");
        s.snowTicks = getLong(obj, "snowTicks");
        s.cropsPlanted = getInt(obj, "cropsPlanted");
        s.itemsCrafted = getInt(obj, "itemsCrafted");
        s.fallDamageEvents = getInt(obj, "fallDamageEvents");
        s.torchesPlaced = getInt(obj, "torchesPlaced");
        s.blocksPlacedInWater = getInt(obj, "blocksPlacedInWater");
        s.animalsFed = getInt(obj, "animalsFed");
        s.firstDeathDay = getInt(obj, "firstDeathDay", -1);
        s.firstDeathX = getInt(obj, "firstDeathX");
        s.firstDeathY = getInt(obj, "firstDeathY");
        s.firstDeathZ = getInt(obj, "firstDeathZ");
        s.firstDeathRecorded = getBool(obj, "firstDeathRecorded");
        s.latestDeathX = getInt(obj, "latestDeathX");
        s.latestDeathY = getInt(obj, "latestDeathY");
        s.latestDeathZ = getInt(obj, "latestDeathZ");
        s.firstNetherDay = getInt(obj, "firstNetherDay", -1);
        s.firstEndDay = getInt(obj, "firstEndDay", -1);
        s.firstDiamondDay = getInt(obj, "firstDiamondDay", -1);
        s.firstEnchantDay = getInt(obj, "firstEnchantDay", -1);
        s.firstTameDay = getInt(obj, "firstTameDay", -1);
        s.firstRainSleepDay = getInt(obj, "firstRainSleepDay", -1);
        s.firstBlockPlacedX = getInt(obj, "firstBlockPlacedX");
        s.firstBlockPlacedY = getInt(obj, "firstBlockPlacedY");
        s.firstBlockPlacedZ = getInt(obj, "firstBlockPlacedZ");
        s.firstBlockPlacedRecorded = getBool(obj, "firstBlockPlacedRecorded");
        if (obj.has("mostFrequentBiome")) {
            s.mostFrequentBiome = obj.get("mostFrequentBiome").getAsString();
        }
        s.furthestDistance = getDouble(obj, "furthestDistance");
        s.lowestY = obj.has("lowestY") ? obj.get("lowestY").getAsInt() : Integer.MAX_VALUE;
        s.highestY = obj.has("highestY") ? obj.get("highestY").getAsInt() : Integer.MIN_VALUE;
        if (obj.has("biomeTimes") && obj.get("biomeTimes").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject("biomeTimes").entrySet()) {
                s.biomeTimes.put(e.getKey(), e.getValue().getAsLong());
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
