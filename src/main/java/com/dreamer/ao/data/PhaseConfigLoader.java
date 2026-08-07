package com.dreamer.ao.data;

import com.dreamer.ao.data.model.PhaseDefinition;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * 阶段配置加载器（单例）。
 * <p>
 * 从 {@code config/advancementoverhaul/phases/} 目录加载阶段 JSON 定义。
 * 每个 JSON 文件可包含一个或多个阶段定义。
 */
public class PhaseConfigLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(PhaseConfigLoader.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final PhaseConfigLoader INSTANCE = new PhaseConfigLoader();
    private static final String PHASES_DIR = "phases";

    public static PhaseConfigLoader getInstance() { return INSTANCE; }
    private PhaseConfigLoader() {}

    private volatile Map<String, PhaseDefinition> phases = new LinkedHashMap<>();
    private volatile boolean initialized;

    public void init(Path configDir) {
        if (initialized) return;
        initialized = true;
        Path base = configDir.resolve("advancementoverhaul").resolve(PHASES_DIR);
        ensureDir(base);
        generateDefaults(base);
        reload(base);
        LOGGER.info("Phase config loaded: {} phases", phases.size());
    }

    public void reload(Path configDir) {
        Path base = configDir.resolve("advancementoverhaul").resolve(PHASES_DIR);
        doReload(base);
    }

    public void reload() {
        if (initialized) {
            // Re-init from the known path
            doReload(phases.isEmpty() ? null : null);
        }
    }

    private void doReload(Path dir) {
        Map<String, PhaseDefinition> result = new LinkedHashMap<>();
        if (dir == null || !Files.exists(dir)) {
            this.phases = result;
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(f -> f.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(file -> {
                        try {
                            String content = Files.readString(file);
                            JsonObject root = JsonParser.parseString(content).getAsJsonObject();
                            if (root.has("phases")) {
                                for (JsonElement el : root.getAsJsonArray("phases")) {
                                    if (el.isJsonObject()) {
                                        PhaseDefinition def = PhaseDefinition.fromJson(el.getAsJsonObject());
                                        if (def.id() != null && !def.id().isEmpty()) {
                                            result.put(def.id(), def);
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.warn("Failed to load phase file {}: {}", file.getFileName(), e.getMessage());
                        }
                    });
        } catch (IOException e) {
            LOGGER.warn("Failed to list phases directory: {}", e.getMessage());
        }
        this.phases = result;
    }

    public PhaseDefinition getPhase(String id) {
        return phases.get(id);
    }

    public Collection<PhaseDefinition> getAllPhases() {
        return Collections.unmodifiableCollection(phases.values());
    }

    public List<String> getPhaseIds() {
        return new ArrayList<>(phases.keySet());
    }

    private void ensureDir(Path dir) {
        try { Files.createDirectories(dir); } catch (IOException e) {
            LOGGER.error("Failed to create phases directory: {}", dir, e);
        }
    }

    /**
     * 生成默认阶段配置文件（首次运行时）。
     */
    private void generateDefaults(Path base) {
        Path defaultFile = base.resolve("default_phases.json");
        if (Files.exists(defaultFile)) return;

        String json = """
            {
              "phases": [
                {
                  "id": "peaceful",
                  "name_key": "phase.advancementoverhaul.peaceful",
                  "description_key": "phase.advancementoverhaul.peaceful.desc",
                  "priority": 0,
                  "is_default": true,
                  "effects": [],
                  "equipment": {}
                },
                {
                  "id": "blood_moon",
                  "name_key": "phase.advancementoverhaul.blood_moon",
                  "description_key": "phase.advancementoverhaul.blood_moon.desc",
                  "priority": 10,
                  "is_default": false,
                  "effects": [
                    {"attribute": "generic.max_health", "multiplier": 1.5},
                    {"attribute": "generic.attack_damage", "multiplier": 1.3},
                    {"attribute": "generic.movement_speed", "multiplier": 1.1},
                    {"attribute": "generic.armor", "multiplier": 1.2}
                  ],
                  "equipment": {
                    "mainhand": {
                      "items": [
                        {"item": "minecraft:iron_sword", "weight": 5, "probability": 0.3, "drop_chance": 0.085},
                        {"item": "minecraft:diamond_sword", "weight": 1, "probability": 0.05, "drop_chance": 0.03}
                      ]
                    },
                    "head": {
                      "items": [
                        {"item": "minecraft:iron_helmet", "weight": 3, "probability": 0.2, "drop_chance": 0.085},
                        {"item": "minecraft:chainmail_helmet", "weight": 2, "probability": 0.15, "drop_chance": 0.05}
                      ]
                    }
                  }
                },
                {
                  "id": "hardened",
                  "name_key": "phase.advancementoverhaul.hardened",
                  "description_key": "phase.advancementoverhaul.hardened.desc",
                  "priority": 5,
                  "is_default": false,
                  "effects": [
                    {"attribute": "generic.max_health", "multiplier": 2.0},
                    {"attribute": "generic.attack_damage", "multiplier": 1.5},
                    {"attribute": "generic.armor", "multiplier": 1.5},
                    {"attribute": "generic.armor_toughness", "multiplier": 1.3},
                    {"attribute": "generic.knockback_resistance", "multiplier": 1.2},
                    {"attribute": "generic.attack_speed", "multiplier": 1.1}
                  ],
                  "equipment": {
                    "mainhand": {
                      "items": [
                        {"item": "minecraft:diamond_sword", "weight": 3, "probability": 0.25, "drop_chance": 0.03},
                        {"item": "minecraft:netherite_sword", "weight": 1, "probability": 0.03, "drop_chance": 0.01}
                      ]
                    },
                    "head": {
                      "items": [
                        {"item": "minecraft:diamond_helmet", "weight": 2, "probability": 0.15, "drop_chance": 0.03}
                      ]
                    },
                    "chest": {
                      "items": [
                        {"item": "minecraft:diamond_chestplate", "weight": 2, "probability": 0.12, "drop_chance": 0.03}
                      ]
                    }
                  }
                }
              ]
            }
            """;
        try {
            Files.writeString(defaultFile, json);
            LOGGER.info("Generated default phase config: {}", defaultFile);
        } catch (IOException e) {
            LOGGER.error("Failed to write default phase config", e);
        }
    }
}
