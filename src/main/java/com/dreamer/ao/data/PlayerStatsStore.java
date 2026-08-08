package com.dreamer.ao.data;

import com.dreamer.ao.util.AtomicFileWriter;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerStatsStore.class);
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
                AtomicFileWriter.writeString(target, DataStore.GSON_PRETTY.toJson(obj), 1);
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
        obj.addProperty("sunrisesViewed", s.getSunrisesViewed());
        obj.addProperty("sunsetsViewed", s.getSunsetsViewed());
        obj.addProperty("animalsTamed", s.getAnimalsTamed());
        obj.addProperty("nameTagsUsed", s.getNameTagsUsed());
        obj.addProperty("blocksPlaced", s.getBlocksPlaced());
        obj.addProperty("blocksBroken", s.getBlocksBroken());
        obj.addProperty("wanderingTraderTrades", s.getWanderingTraderTrades());
        obj.addProperty("lightningStrikes", s.getLightningStrikes());
        obj.addProperty("rainTicks", s.getRainTicks());
        obj.addProperty("snowTicks", s.getSnowTicks());
        obj.addProperty("cropsPlanted", s.getCropsPlanted());
        obj.addProperty("itemsCrafted", s.getItemsCrafted());
        obj.addProperty("fallDamageEvents", s.getFallDamageEvents());
        obj.addProperty("torchesPlaced", s.getTorchesPlaced());
        obj.addProperty("blocksPlacedInWater", s.getBlocksPlacedInWater());
        obj.addProperty("animalsFed", s.getAnimalsFed());
        obj.addProperty("firstDeathDay", s.getFirstDeathDay());
        obj.addProperty("firstDeathX", s.getFirstDeathX());
        obj.addProperty("firstDeathY", s.getFirstDeathY());
        obj.addProperty("firstDeathZ", s.getFirstDeathZ());
        obj.addProperty("firstDeathRecorded", s.isFirstDeathRecorded());
        obj.addProperty("latestDeathX", s.getLatestDeathX());
        obj.addProperty("latestDeathY", s.getLatestDeathY());
        obj.addProperty("latestDeathZ", s.getLatestDeathZ());
        obj.addProperty("firstNetherDay", s.getFirstNetherDay());
        obj.addProperty("firstEndDay", s.getFirstEndDay());
        obj.addProperty("firstDiamondDay", s.getFirstDiamondDay());
        obj.addProperty("firstEnchantDay", s.getFirstEnchantDay());
        obj.addProperty("firstTameDay", s.getFirstTameDay());
        obj.addProperty("firstRainSleepDay", s.getFirstRainSleepDay());
        obj.addProperty("firstBlockPlacedX", s.getFirstBlockPlacedX());
        obj.addProperty("firstBlockPlacedY", s.getFirstBlockPlacedY());
        obj.addProperty("firstBlockPlacedZ", s.getFirstBlockPlacedZ());
        obj.addProperty("firstBlockPlacedRecorded", s.isFirstBlockPlacedRecorded());
        if (s.getMostFrequentBiome() != null) {
            obj.addProperty("mostFrequentBiome", s.getMostFrequentBiome());
        }
        obj.addProperty("furthestDistance", s.getFurthestDistance());
        obj.addProperty("lowestY", s.getLowestY());
        obj.addProperty("highestY", s.getHighestY());
        if (!s.getBiomeTimes().isEmpty()) {
            JsonObject btObj = new JsonObject();
            for (Map.Entry<String, Long> e : s.getBiomeTimes().entrySet()) {
                btObj.addProperty(e.getKey(), e.getValue());
            }
            obj.add("biomeTimes", btObj);
        }
        return obj;
    }

    private PlayerStats statsFromJson(JsonObject obj) {
        PlayerStats s = new PlayerStats();
        s.setSunrisesViewed(getInt(obj, "sunrisesViewed"));
        s.setSunsetsViewed(getInt(obj, "sunsetsViewed"));
        s.setAnimalsTamed(getInt(obj, "animalsTamed"));
        s.setNameTagsUsed(getInt(obj, "nameTagsUsed"));
        s.setBlocksPlaced(getInt(obj, "blocksPlaced"));
        s.setBlocksBroken(getInt(obj, "blocksBroken"));
        s.setWanderingTraderTrades(getInt(obj, "wanderingTraderTrades"));
        s.setLightningStrikes(getInt(obj, "lightningStrikes"));
        s.setRainTicks(getLong(obj, "rainTicks"));
        s.setSnowTicks(getLong(obj, "snowTicks"));
        s.setCropsPlanted(getInt(obj, "cropsPlanted"));
        s.setItemsCrafted(getInt(obj, "itemsCrafted"));
        s.setFallDamageEvents(getInt(obj, "fallDamageEvents"));
        s.setTorchesPlaced(getInt(obj, "torchesPlaced"));
        s.setBlocksPlacedInWater(getInt(obj, "blocksPlacedInWater"));
        s.setAnimalsFed(getInt(obj, "animalsFed"));
        s.setFirstDeathDay(getInt(obj, "firstDeathDay", -1));
        s.setFirstDeathX(getInt(obj, "firstDeathX"));
        s.setFirstDeathY(getInt(obj, "firstDeathY"));
        s.setFirstDeathZ(getInt(obj, "firstDeathZ"));
        s.setFirstDeathRecorded(getBool(obj, "firstDeathRecorded"));
        s.setLatestDeathX(getInt(obj, "latestDeathX"));
        s.setLatestDeathY(getInt(obj, "latestDeathY"));
        s.setLatestDeathZ(getInt(obj, "latestDeathZ"));
        s.setFirstNetherDay(getInt(obj, "firstNetherDay", -1));
        s.setFirstEndDay(getInt(obj, "firstEndDay", -1));
        s.setFirstDiamondDay(getInt(obj, "firstDiamondDay", -1));
        s.setFirstEnchantDay(getInt(obj, "firstEnchantDay", -1));
        s.setFirstTameDay(getInt(obj, "firstTameDay", -1));
        s.setFirstRainSleepDay(getInt(obj, "firstRainSleepDay", -1));
        s.setFirstBlockPlacedX(getInt(obj, "firstBlockPlacedX"));
        s.setFirstBlockPlacedY(getInt(obj, "firstBlockPlacedY"));
        s.setFirstBlockPlacedZ(getInt(obj, "firstBlockPlacedZ"));
        s.setFirstBlockPlacedRecorded(getBool(obj, "firstBlockPlacedRecorded"));
        if (obj.has("mostFrequentBiome")) {
            s.setMostFrequentBiome(obj.get("mostFrequentBiome").getAsString());
        }
        s.setFurthestDistance(getDouble(obj, "furthestDistance"));
        s.setLowestY(obj.has("lowestY") ? obj.get("lowestY").getAsInt() : Integer.MAX_VALUE);
        s.setHighestY(obj.has("highestY") ? obj.get("highestY").getAsInt() : Integer.MIN_VALUE);
        if (obj.has("biomeTimes") && obj.get("biomeTimes").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject("biomeTimes").entrySet()) {
                s.putBiomeTime(e.getKey(), e.getValue().getAsLong());
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
