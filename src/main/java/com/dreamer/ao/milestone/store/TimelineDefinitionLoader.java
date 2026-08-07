package com.dreamer.ao.milestone.store;

import com.dreamer.ao.milestone.model.MilestoneDefinition;
import com.dreamer.ao.milestone.model.TimeMilestone;
import com.dreamer.ao.milestone.model.TimelineCategory;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimelineDefinitionLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineDefinitionLoader.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final TimelineDefinitionLoader INSTANCE = new TimelineDefinitionLoader();
    private final Map<String, MilestoneDefinition> milestonesById = new LinkedHashMap<>();
    private final List<MilestoneDefinition> milestonesOrdered = new ArrayList<>();
    private final Map<String, TimelineCategory> categories = new LinkedHashMap<>();
    private final Map<String, TimeMilestone> customMilestones = new LinkedHashMap<>();
    private Path milestonesDir;
    private Path customMilestonesFile;
    private static final String DEFAULT_NORMAL = "{\n  \"category\": \"normal\",\n  \"milestones\": [\n    {\n      \"id\": \"first_awaken\",\n      \"name_key\": \"milestone.advancementoverhaul.first_awaken\",\n      \"description_key\": \"milestone.advancementoverhaul.first_awaken.desc\",\n      \"icon\": \"minecraft:grass_block\",\n      \"trigger\": \"WORLD_JOIN\"\n    },\n    {\n      \"id\": \"first_mining\",\n      \"name_key\": \"milestone.advancementoverhaul.first_mining\",\n      \"description_key\": \"milestone.advancementoverhaul.first_mining.desc\",\n      \"icon\": \"minecraft:stone_pickaxe\",\n      \"trigger\": \"FIRST_OBTAIN\",\n      \"trigger_param\": \"minecraft:cobblestone\"\n    },\n    {\n      \"id\": \"first_diamond\",\n      \"name_key\": \"milestone.advancementoverhaul.first_diamond\",\n      \"description_key\": \"milestone.advancementoverhaul.first_diamond.desc\",\n      \"icon\": \"minecraft:diamond\",\n      \"trigger\": \"FIRST_OBTAIN\",\n      \"trigger_param\": \"minecraft:diamond\",\n      \"achievement\": {\n        \"auto_generate\": true\n      }\n    },\n    {\n      \"id\": \"first_death\",\n      \"name_key\": \"milestone.advancementoverhaul.first_death\",\n      \"description_key\": \"milestone.advancementoverhaul.first_death.desc\",\n      \"icon\": \"minecraft:bone\",\n      \"trigger\": \"FIRST_DEATH\"\n    },\n    {\n      \"id\": \"first_nether\",\n      \"name_key\": \"milestone.advancementoverhaul.first_nether\",\n      \"description_key\": \"milestone.advancementoverhaul.first_nether.desc\",\n      \"icon\": \"minecraft:obsidian\",\n      \"trigger\": \"FIRST_DIMENSION\",\n      \"trigger_param\": \"minecraft:the_nether\"\n    },\n    {\n      \"id\": \"first_end\",\n      \"name_key\": \"milestone.advancementoverhaul.first_end\",\n      \"description_key\": \"milestone.advancementoverhaul.first_end.desc\",\n      \"icon\": \"minecraft:end_stone\",\n      \"trigger\": \"FIRST_DIMENSION\",\n      \"trigger_param\": \"minecraft:the_end\"\n    },\n    {\n      \"id\": \"first_sunrise\",\n      \"name_key\": \"milestone.advancementoverhaul.first_sunrise\",\n      \"description_key\": \"milestone.advancementoverhaul.first_sunrise.desc\",\n      \"icon\": \"minecraft:sunflower\",\n      \"trigger\": \"SUNRISE_VIEWED\"\n    },\n    {\n      \"id\": \"first_sunset\",\n      \"name_key\": \"milestone.advancementoverhaul.first_sunset\",\n      \"description_key\": \"milestone.advancementoverhaul.first_sunset.desc\",\n      \"icon\": \"minecraft:clock\",\n      \"trigger\": \"SUNSET_VIEWED\"\n    },\n    {\n      \"id\": \"first_block_placed\",\n      \"name_key\": \"milestone.advancementoverhaul.first_block_placed\",\n      \"description_key\": \"milestone.advancementoverhaul.first_block_placed.desc\",\n      \"icon\": \"minecraft:oak_planks\",\n      \"trigger\": \"FIRST_BLOCK_PLACE\"\n    },\n    {\n      \"id\": \"first_tame\",\n      \"name_key\": \"milestone.advancementoverhaul.first_tame\",\n      \"description_key\": \"milestone.advancementoverhaul.first_tame.desc\",\n      \"icon\": \"minecraft:bone\",\n      \"trigger\": \"FIRST_TAME\"\n    },\n    {\n      \"id\": \"first_rain_sleep\",\n      \"name_key\": \"milestone.advancementoverhaul.first_rain_sleep\",\n      \"description_key\": \"milestone.advancementoverhaul.first_rain_sleep.desc\",\n      \"icon\": \"minecraft:light_blue_bed\",\n      \"trigger\": \"FIRST_RAIN_SLEEP\"\n    },\n    {\n      \"id\": \"first_enchant\",\n      \"name_key\": \"milestone.advancementoverhaul.first_enchant\",\n      \"description_key\": \"milestone.advancementoverhaul.first_enchant.desc\",\n      \"icon\": \"minecraft:enchanting_table\",\n      \"trigger\": \"FIRST_ENCHANT\"\n    },\n    {\n      \"id\": \"items_crafted_100\",\n      \"name_key\": \"milestone.advancementoverhaul.items_crafted_100\",\n      \"description_key\": \"milestone.advancementoverhaul.items_crafted_100.desc\",\n      \"icon\": \"minecraft:crafting_table\",\n      \"trigger\": \"COUNTER_REACH\",\n      \"trigger_param\": \"items_crafted\",\n      \"trigger_threshold\": 100\n    },\n    {\n      \"id\": \"blocks_placed_100\",\n      \"name_key\": \"milestone.advancementoverhaul.blocks_placed_100\",\n      \"description_key\": \"milestone.advancementoverhaul.blocks_placed_100.desc\",\n      \"icon\": \"minecraft:bricks\",\n      \"trigger\": \"COUNTER_REACH\",\n      \"trigger_param\": \"blocks_placed\",\n      \"trigger_threshold\": 100\n    },\n    {\n      \"id\": \"distance_500\",\n      \"name_key\": \"milestone.advancementoverhaul.distance_500\",\n      \"description_key\": \"milestone.advancementoverhaul.distance_500.desc\",\n      \"icon\": \"minecraft:leather_boots\",\n      \"trigger\": \"DISTANCE_REACH\",\n      \"trigger_threshold\": 500\n    }\n  ]\n}\n";
    private static final String DEFAULT_UNIQUE = "{\n  \"category\": \"unique\",\n  \"milestones\": [\n    {\n      \"id\": \"rainy_night_travel\",\n      \"name_key\": \"milestone.advancementoverhaul.rainy_night_travel\",\n      \"description_key\": \"milestone.advancementoverhaul.rainy_night_travel.desc\",\n      \"icon\": \"minecraft:water_bucket\",\n      \"trigger\": \"RAIN_NIGHT_TRAVEL\",\n      \"trigger_threshold\": 500\n    },\n    {\n      \"id\": \"blocks_placed_1000\",\n      \"name_key\": \"milestone.advancementoverhaul.blocks_placed_1000\",\n      \"description_key\": \"milestone.advancementoverhaul.blocks_placed_1000.desc\",\n      \"icon\": \"minecraft:chiseled_stone_bricks\",\n      \"trigger\": \"COUNTER_REACH\",\n      \"trigger_param\": \"blocks_placed\",\n      \"trigger_threshold\": 1000\n    },\n    {\n      \"id\": \"first_lightning\",\n      \"name_key\": \"milestone.advancementoverhaul.first_lightning\",\n      \"description_key\": \"milestone.advancementoverhaul.first_lightning.desc\",\n      \"icon\": \"minecraft:lightning_rod\",\n      \"trigger\": \"FIRST_LIGHTNING\"\n    },\n    {\n      \"id\": \"fall_damage_10\",\n      \"name_key\": \"milestone.advancementoverhaul.fall_damage_10\",\n      \"description_key\": \"milestone.advancementoverhaul.fall_damage_10.desc\",\n      \"icon\": \"minecraft:feather\",\n      \"trigger\": \"COUNTER_REACH\",\n      \"trigger_param\": \"fall_damage_events\",\n      \"trigger_threshold\": 10\n    },\n    {\n      \"id\": \"distance_2000\",\n      \"name_key\": \"milestone.advancementoverhaul.distance_2000\",\n      \"description_key\": \"milestone.advancementoverhaul.distance_2000.desc\",\n      \"icon\": \"minecraft:iron_boots\",\n      \"trigger\": \"DISTANCE_REACH\",\n      \"trigger_threshold\": 2000\n    },\n    {\n      \"id\": \"distance_10000\",\n      \"name_key\": \"milestone.advancementoverhaul.distance_10000\",\n      \"description_key\": \"milestone.advancementoverhaul.distance_10000.desc\",\n      \"icon\": \"minecraft:diamond_boots\",\n      \"trigger\": \"DISTANCE_REACH\",\n      \"trigger_threshold\": 10000\n    }\n  ]\n}\n";

    public static TimelineDefinitionLoader getInstance() {
        return INSTANCE;
    }

    private TimelineDefinitionLoader() {
    }

    public void init(Path configDir) {
        Path baseDir = configDir.resolve("advancementoverhaul").resolve("timeline");
        this.milestonesDir = baseDir.resolve("milestones");
        this.customMilestonesFile = baseDir.resolve("custom_milestones.json");
        try {
            Files.createDirectories(this.milestonesDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create timeline config directory", e);
            return;
        }
        this.generateDefaults();
        this.loadCategories(baseDir.resolve("categories.json"));
        this.loadMilestones(this.milestonesDir);
        this.loadCustomMilestones();
        LOGGER.info("Loaded {} milestone definitions + {} custom across {} categories",
                this.milestonesById.size(), this.customMilestones.size(), this.categories.size());
    }

    public MilestoneDefinition getMilestone(String id) {
        return this.milestonesById.get(id);
    }

    public List<MilestoneDefinition> getAllMilestones() {
        return Collections.unmodifiableList(this.milestonesOrdered);
    }

    public List<MilestoneDefinition> getMilestonesByCategory(String category) {
        return this.milestonesOrdered.stream().filter(m -> m.getCategory().equals(category)).toList();
    }

    public List<String> getCategories() {
        return this.categories.keySet().stream().toList();
    }

    public TimelineCategory getCategoryDef(String categoryId) {
        return this.categories.getOrDefault(categoryId, TimelineCategory.NORMAL);
    }

    public List<TimeMilestone> getCustomMilestones() {
        return List.copyOf(this.customMilestones.values());
    }

    public void addCustomMilestone(TimeMilestone tm) {
        this.customMilestones.put(tm.id(), tm);
        this.saveCustomMilestones();
    }

    public void updateCustomMilestone(TimeMilestone tm) {
        this.customMilestones.put(tm.id(), tm);
        this.saveCustomMilestones();
    }

    public void removeCustomMilestone(String id) {
        this.customMilestones.remove(id);
        this.saveCustomMilestones();
    }

    private void loadCustomMilestones() {
        this.customMilestones.clear();
        if (this.customMilestonesFile == null || !Files.exists(this.customMilestonesFile)) {
            return;
        }
        try {
            String content = Files.readString(this.customMilestonesFile, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();
            if (root.has("custom_milestones")) {
                JsonArray arr = root.getAsJsonArray("custom_milestones");
                for (JsonElement elem : arr) {
                    TimeMilestone tm = TimeMilestone.fromJson(elem.getAsJsonObject());
                    this.customMilestones.put(tm.id(), tm);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load custom milestones: {}", e.getMessage());
        }
    }

    private void saveCustomMilestones() {
        if (this.customMilestonesFile == null) {
            return;
        }
        try {
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (TimeMilestone tm : this.customMilestones.values()) {
                arr.add(tm.toJson());
            }
            root.add("custom_milestones", arr);
            Files.writeString(this.customMilestonesFile, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Failed to save custom milestones: {}", e.getMessage());
        }
    }

    private void loadCategories(Path categoriesFile) {
        for (TimelineCategory cat : TimelineCategory.BUILTIN) {
            this.categories.put(cat.id(), cat);
        }
        if (!Files.exists(categoriesFile)) {
            return;
        }
        try {
            String content = Files.readString(categoriesFile, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();
            if (root.has("categories")) {
                JsonArray arr = root.getAsJsonArray("categories");
                for (JsonElement elem : arr) {
                    JsonObject obj = elem.getAsJsonObject();
                    String id = obj.get("id").getAsString();
                    String nameKey = obj.has("name_key") ? obj.get("name_key").getAsString() : id;
                    String icon = obj.has("icon") ? obj.get("icon").getAsString() : "\ud83d\udccc";
                    int color = obj.has("color") ? parseHexColor(obj.get("color").getAsString()) : -7624772;
                    int order = obj.has("order") ? obj.get("order").getAsInt() : this.categories.size();
                    this.categories.put(id, new TimelineCategory(id, nameKey, icon, color, order));
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load categories.json: {}", e.getMessage());
        }
    }

    private void loadMilestones(Path dir) {
        this.milestonesById.clear();
        this.milestonesOrdered.clear();
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> jsonFiles = files.filter(p -> p.toString().endsWith(".json"))
                    .sorted(Comparator.comparing(Path::getFileName, Comparator.naturalOrder()))
                    .toList();
            Map<String, List<MilestoneDefinition>> byCategory = new LinkedHashMap<>();
            for (Path path : jsonFiles) {
                try {
                    String content = Files.readString(path, StandardCharsets.UTF_8);
                    JsonObject root = JsonParser.parseString(content).getAsJsonObject();
                    String category = root.has("category") ? root.get("category").getAsString()
                            : path.getFileName().toString().replace(".json", "");
                    if (!root.has("milestones")) continue;
                    JsonArray arr = root.getAsJsonArray("milestones");
                    for (JsonElement elem : arr) {
                        JsonObject obj = elem.getAsJsonObject();
                        if (!obj.has("category")) {
                            obj.addProperty("category", category);
                        }
                        MilestoneDefinition def = MilestoneDefinition.fromJson(obj);
                        this.milestonesById.put(def.getId(), def);
                        byCategory.computeIfAbsent(def.getCategory(), k -> new ArrayList<>()).add(def);
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to load milestone file {}: {}", path.getFileName(), e.getMessage());
                }
            }
            for (String id : this.getOrderedCategoryIds()) {
                List<MilestoneDefinition> list = byCategory.get(id);
                if (list == null) continue;
                this.milestonesOrdered.addAll(list);
            }
            for (Map.Entry<String, List<MilestoneDefinition>> entry : byCategory.entrySet()) {
                if (this.categories.containsKey(entry.getKey())) continue;
                this.milestonesOrdered.addAll(entry.getValue());
            }
        } catch (IOException e) {
            LOGGER.error("Failed to list milestone files", e);
        }
    }

    private List<String> getOrderedCategoryIds() {
        return Arrays.stream(TimelineCategory.BUILTIN).map(TimelineCategory::id).toList();
    }

    private void generateDefaults() {
        Path normalFile = this.milestonesDir.resolve("normal.json");
        Path uniqueFile = this.milestonesDir.resolve("unique.json");

        if (!Files.exists(normalFile)) this.writeDefaultFile(normalFile, DEFAULT_NORMAL);
        if (!Files.exists(uniqueFile)) this.writeDefaultFile(uniqueFile, DEFAULT_UNIQUE);
    }

    private void writeDefaultFile(Path file, String content) {
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
            LOGGER.info("Generated default milestone config: {}", file.getFileName());
        } catch (IOException e) {
            LOGGER.error("Failed to write default config: {}", file, e);
        }
    }

    private static int parseHexColor(String hex) {
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        try {
            return (int) (Long.parseLong(hex, 16) | 0xFF000000L);
        } catch (NumberFormatException e) {
            return -7624772;
        }
    }
}
