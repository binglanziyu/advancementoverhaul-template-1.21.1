package com.example.advancementoverhaul.milestone.store;

import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.PlayerStats;
import com.example.advancementoverhaul.milestone.model.MilestoneDefinition;
import com.example.advancementoverhaul.milestone.model.TimeMilestone;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimelineStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/TimelineStore");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final TimelineStore INSTANCE = new TimelineStore();
    private final Map<UUID, Map<String, Integer>> playerMilestones = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> playerMilestoneTicks = new ConcurrentHashMap<>();
    private final Map<UUID, StatValueStore> playerStats = new ConcurrentHashMap<>();
    private Path dataDir;

    public static TimelineStore getInstance() {
        return INSTANCE;
    }

    private TimelineStore() {
    }

    public void init(Path baseDir) {
        this.dataDir = baseDir.resolve("timeline");
        try {
            Files.createDirectories(this.dataDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create timeline data directory: {}", this.dataDir, e);
        }
    }

    public boolean isUnlocked(UUID uuid, String milestoneId) {
        Map<String, Integer> milestones = this.playerMilestones.get(uuid);
        return milestones != null && milestones.containsKey(milestoneId);
    }

    public boolean unlockMilestone(UUID uuid, String milestoneId, int gameDay, long gameTick) {
        Map<String, Integer> milestones = this.playerMilestones.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        Map<String, Long> ticks = this.playerMilestoneTicks.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        if (milestones.containsKey(milestoneId)) {
            return false;
        }
        milestones.put(milestoneId, gameDay);
        ticks.put(milestoneId, gameTick);
        LOGGER.info("Milestone unlocked: {} for {} on day {}", milestoneId, uuid, gameDay);
        return true;
    }

    public Map<String, Integer> getUnlockedMilestones(UUID uuid) {
        Map<String, Integer> map = this.playerMilestones.get(uuid);
        return map != null ? new LinkedHashMap<>(map) : Collections.emptyMap();
    }

    public long getUnlockTick(UUID uuid, String milestoneId) {
        Map<String, Long> ticks = this.playerMilestoneTicks.get(uuid);
        return ticks != null ? ticks.getOrDefault(milestoneId, 0L) : 0L;
    }

    public StatValueStore getOrCreateStats(UUID uuid) {
        return this.playerStats.computeIfAbsent(uuid, k -> new StatValueStore());
    }

    public StatValueStore getStats(UUID uuid) {
        return this.playerStats.get(uuid);
    }

    public void loadPlayer(UUID uuid) {
        if (this.dataDir == null) {
            return;
        }
        Path file = this.dataDir.resolve(uuid.toString() + ".json");
        if (!Files.exists(file)) {
            this.tryMigrateFromLegacy(uuid);
            return;
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
            var milestones = new ConcurrentHashMap<String, Integer>();
            var ticks = new ConcurrentHashMap<String, Long>();
            if (obj.has("milestones") && obj.get("milestones").isJsonObject()) {
                JsonObject milestonesObj = obj.getAsJsonObject("milestones");
                for (Map.Entry<String, JsonElement> entry : milestonesObj.entrySet()) {
                    if (!entry.getValue().isJsonObject()) continue;
                    JsonObject m = entry.getValue().getAsJsonObject();
                    milestones.put(entry.getKey(), m.get("day").getAsInt());
                    ticks.put(entry.getKey(), m.get("tick").getAsLong());
                }
            }
            this.playerMilestones.put(uuid, milestones);
            this.playerMilestoneTicks.put(uuid, ticks);
            if (obj.has("stats") && obj.get("stats").isJsonObject()) {
                this.playerStats.put(uuid, StatValueStore.fromJson(obj.getAsJsonObject("stats")));
            } else {
                this.playerStats.put(uuid, new StatValueStore());
            }
            LOGGER.debug("Loaded timeline data for {}: {} milestones", uuid, milestones.size());
        } catch (Exception e) {
            LOGGER.error("Failed to load timeline data for {}: {}", uuid, e.getMessage());
            this.playerMilestones.put(uuid, new ConcurrentHashMap<>());
            this.playerMilestoneTicks.put(uuid, new ConcurrentHashMap<>());
            this.playerStats.put(uuid, new StatValueStore());
        }
    }

    public void savePlayer(UUID uuid) {
        if (this.dataDir == null) {
            return;
        }
        Map<String, Integer> milestones = this.playerMilestones.get(uuid);
        Map<String, Long> ticks = this.playerMilestoneTicks.get(uuid);
        StatValueStore stats = this.playerStats.get(uuid);
        if (milestones == null && stats == null) {
            return;
        }
        try {
            JsonObject root = new JsonObject();
            if (milestones != null) {
                JsonObject milestonesObj = new JsonObject();
                for (Map.Entry<String, Integer> entry : milestones.entrySet()) {
                    JsonObject m = new JsonObject();
                    m.addProperty("day", entry.getValue());
                    m.addProperty("tick", ticks != null ? ticks.getOrDefault(entry.getKey(), 0L) : 0L);
                    milestonesObj.add(entry.getKey(), m);
                }
                root.add("milestones", milestonesObj);
            }
            if (stats != null) {
                root.add("stats", stats.toJson());
            }
            Path file = this.dataDir.resolve(uuid.toString() + ".json");
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Failed to save timeline data for {}: {}", uuid, e.getMessage());
        }
    }

    public JsonArray toSyncJson(UUID uuid) {
        JsonArray arr = new JsonArray();
        Map<String, Integer> unlocked = this.getUnlockedMilestones(uuid);
        TimelineDefinitionLoader loader = TimelineDefinitionLoader.getInstance();

        for (MilestoneDefinition def : loader.getAllMilestones()) {
            TimeMilestone tm = def.toPendingMilestone();
            if (unlocked.containsKey(def.getId())) {
                int day = unlocked.get(def.getId());
                long tick = this.getUnlockTick(uuid, def.getId());
                tm = tm.unlocked(day, tick);
            }
            arr.add(tm.toJson());
        }

        for (TimeMilestone ct : loader.getCustomMilestones()) {
            TimeMilestone tm;
            if (unlocked.containsKey(ct.id())) {
                int day = unlocked.get(ct.id());
                long tick = this.getUnlockTick(uuid, ct.id());
                tm = ct.unlocked(day, tick);
            } else {
                tm = ct;
            }
            arr.add(tm.toJson());
        }

        return arr;
    }

    private void tryMigrateFromLegacy(UUID uuid) {
        if (this.dataDir == null) {
            return;
        }
        Path legacyFile = this.dataDir.getParent().resolve("player_stats").resolve(uuid.toString() + ".json");
        if (!Files.exists(legacyFile)) {
            this.playerMilestones.put(uuid, new ConcurrentHashMap<>());
            this.playerMilestoneTicks.put(uuid, new ConcurrentHashMap<>());
            this.playerStats.put(uuid, new StatValueStore());
            return;
        }
        try {
            String content = Files.readString(legacyFile, StandardCharsets.UTF_8);
            PlayerStats oldStats = DataStore.GSON.fromJson(content, PlayerStats.class);
            StatValueStore newStats = StatValueStore.migrateFromPlayerStats(oldStats);
            this.playerStats.put(uuid, newStats);
            var milestones = new ConcurrentHashMap<String, Integer>();
            var ticks = new ConcurrentHashMap<String, Long>();
            if (oldStats.firstDeathRecorded) {
                milestones.put("first_death", oldStats.firstDeathDay);
                ticks.put("first_death", (long) oldStats.firstDeathDay * 24000L);
            }
            if (oldStats.firstNetherDay > 0) {
                milestones.put("first_nether", oldStats.firstNetherDay);
                ticks.put("first_nether", (long) oldStats.firstNetherDay * 24000L);
            }
            if (oldStats.firstEndDay > 0) {
                milestones.put("first_end", oldStats.firstEndDay);
                ticks.put("first_end", (long) oldStats.firstEndDay * 24000L);
            }
            if (oldStats.firstDiamondDay > 0) {
                milestones.put("first_diamond", oldStats.firstDiamondDay);
                ticks.put("first_diamond", (long) oldStats.firstDiamondDay * 24000L);
            }
            if (oldStats.firstEnchantDay > 0) {
                milestones.put("first_enchant", oldStats.firstEnchantDay);
                ticks.put("first_enchant", (long) oldStats.firstEnchantDay * 24000L);
            }
            if (oldStats.firstTameDay > 0) {
                milestones.put("first_tame", oldStats.firstTameDay);
                ticks.put("first_tame", (long) oldStats.firstTameDay * 24000L);
            }
            if (oldStats.firstRainSleepDay > 0) {
                milestones.put("first_rain_sleep", oldStats.firstRainSleepDay);
                ticks.put("first_rain_sleep", (long) oldStats.firstRainSleepDay * 24000L);
            }
            if (oldStats.firstBlockPlacedRecorded) {
                milestones.put("first_block_placed", 1);
                ticks.put("first_block_placed", 1000L);
            }
            this.playerMilestones.put(uuid, milestones);
            this.playerMilestoneTicks.put(uuid, ticks);
            this.savePlayer(uuid);
            LOGGER.info("Migrated legacy stats for {} to timeline format: {} milestones", uuid, milestones.size());
        } catch (Exception e) {
            LOGGER.error("Failed to migrate legacy stats for {}: {}", uuid, e.getMessage());
            this.playerMilestones.put(uuid, new ConcurrentHashMap<>());
            this.playerMilestoneTicks.put(uuid, new ConcurrentHashMap<>());
            this.playerStats.put(uuid, new StatValueStore());
        }
    }

    public void saveAll() {
        HashSet<UUID> allUuids = new HashSet<>();
        allUuids.addAll(this.playerMilestones.keySet());
        allUuids.addAll(this.playerStats.keySet());
        for (UUID uuid : allUuids) {
            this.savePlayer(uuid);
        }
        LOGGER.info("Saved all timeline data");
    }
}
