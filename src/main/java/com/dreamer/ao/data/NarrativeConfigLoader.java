package com.dreamer.ao.data;

import com.dreamer.ao.data.model.EchoEntry;
import com.dreamer.ao.data.model.MonologueCategory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NarrativeConfigLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(NarrativeConfigLoader.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final NarrativeConfigLoader INSTANCE = new NarrativeConfigLoader();
    private static final String NARRATIVES_DIR = "narratives";
    private static final String MONOLOGUES_DIR = "monologues";
    private static final String ECHOES_DIR = "echoes";
    private static final String STAT_TEMPLATES_FILE = "stat_templates.json";
    private static final String ADV_DESCRIPTIONS_FILE = "advancement_descriptions.json";
    private volatile Map<String, MonologueCategory> monologues = new LinkedHashMap<>();
    private volatile Map<String, EchoEntry> echoes = new LinkedHashMap<>();
    private volatile JsonElement statTemplates;
    private volatile JsonElement advDescriptions;
    private volatile boolean initialized;
    private volatile boolean defaultsGenerated;

    public static NarrativeConfigLoader getInstance() {
        return INSTANCE;
    }

    private NarrativeConfigLoader() {
    }

    public void init(Path configDir) {
        if (this.initialized) {
            return;
        }
        this.initialized = true;
        Path base = configDir.resolve("advancementoverhaul").resolve(NARRATIVES_DIR);
        this.ensureDir(base);
        this.ensureDir(base.resolve(MONOLOGUES_DIR));
        this.ensureDir(base.resolve(ECHOES_DIR));
        this.generateReadme(base);
        this.generateDefaultsIfNeeded(base);
        this.doReload(base);
        LOGGER.info("Narrative config loaded: {} monologue categories, {} echoes", this.monologues.size(), this.echoes.size());
    }

    public void reload(Path configDir) {
        Path base = configDir.resolve("advancementoverhaul").resolve(NARRATIVES_DIR);
        this.doReload(base);
    }

    private void doReload(Path base) {
        this.loadMonologues(base.resolve(MONOLOGUES_DIR));
        this.loadEchoes(base.resolve(ECHOES_DIR));
        this.loadJsonFile(base.resolve(STAT_TEMPLATES_FILE), json -> this.statTemplates = json);
        this.loadJsonFile(base.resolve(ADV_DESCRIPTIONS_FILE), json -> this.advDescriptions = json);
    }

    private void loadMonologues(Path dir) {
        LinkedHashMap<String, MonologueCategory> result = new LinkedHashMap<>();
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(f -> f.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(file -> {
                        try {
                            String content = Files.readString(file);
                            JsonObject root = JsonParser.parseString(content).getAsJsonObject();
                            if (root.has(MONOLOGUES_DIR)) {
                                Type mapType = new TypeToken<Map<String, MonologueCategory>>(){}.getType();
                                Map<String, MonologueCategory> loaded = GSON.fromJson(root.get(MONOLOGUES_DIR), mapType);
                                if (loaded != null) {
                                    result.putAll(loaded);
                                    LOGGER.debug("Loaded {} monologue categories from {}", loaded.size(), file.getFileName());
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.warn("Failed to load monologue file {}: {}", file.getFileName(), e.getMessage());
                        }
                    });
        } catch (IOException e) {
            LOGGER.warn("Failed to list monologues directory: {}", e.getMessage());
        }
        this.monologues = result;
    }

    private void loadEchoes(Path dir) {
        LinkedHashMap<String, EchoEntry> result = new LinkedHashMap<>();
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(f -> f.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(file -> {
                        try {
                            String content = Files.readString(file);
                            JsonObject root = JsonParser.parseString(content).getAsJsonObject();
                            if (root.has(ECHOES_DIR)) {
                                EchoEntry[] entries = GSON.fromJson(root.get(ECHOES_DIR), EchoEntry[].class);
                                if (entries != null) {
                                    for (EchoEntry entry : entries) {
                                        if (entry.getId() == null || entry.getId().isEmpty()) continue;
                                        result.put(entry.getId(), entry);
                                    }
                                    LOGGER.debug("Loaded {} echoes from {}", entries.length, file.getFileName());
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.warn("Failed to load echo file {}: {}", file.getFileName(), e.getMessage());
                        }
                    });
        } catch (IOException e) {
            LOGGER.warn("Failed to list echoes directory: {}", e.getMessage());
        }
        this.echoes = result;
    }

    private void loadJsonFile(Path file, JsonConsumer consumer) {
        if (!Files.exists(file)) {
            return;
        }
        try {
            String content = Files.readString(file);
            JsonElement json = JsonParser.parseString(content);
            consumer.accept(json);
        } catch (Exception e) {
            LOGGER.warn("Failed to load {}: {}", file.getFileName(), e.getMessage());
        }
    }

    public Map<String, MonologueCategory> getMonologues() {
        return Collections.unmodifiableMap(this.monologues);
    }

    public Map<String, EchoEntry> getEchoes() {
        return Collections.unmodifiableMap(this.echoes);
    }

    public JsonElement getStatTemplates() {
        return this.statTemplates;
    }

    public JsonElement getAdvDescriptions() {
        return this.advDescriptions;
    }

    public boolean isDefaultsGenerated() {
        return this.defaultsGenerated;
    }

    private void generateDefaultsIfNeeded(Path base) {
        boolean generated = false;
        if (!Files.exists(base.resolve(MONOLOGUES_DIR).resolve("default.json"))) {
            this.generateDefaultMonologues(base.resolve(MONOLOGUES_DIR).resolve("default.json"));
            generated = true;
        }
        if (!Files.exists(base.resolve(ECHOES_DIR).resolve("default.json"))) {
            this.generateDefaultEchoes(base.resolve(ECHOES_DIR).resolve("default.json"));
            generated = true;
        }
        if (!Files.exists(base.resolve(STAT_TEMPLATES_FILE))) {
            this.generateDefaultStatTemplates(base.resolve(STAT_TEMPLATES_FILE));
            generated = true;
        }
        if (!Files.exists(base.resolve(ADV_DESCRIPTIONS_FILE))) {
            this.generateDefaultDescriptions(base.resolve(ADV_DESCRIPTIONS_FILE));
            generated = true;
        }
        if (generated) {
            this.defaultsGenerated = true;
            LOGGER.info("Generated default narrative config files in {}", base);
        }
    }

    private void ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.warn("Failed to create directory: {}", e.getMessage());
        }
    }

    private void writeFile(Path path, String content) {
        try {
            Files.writeString(path, content);
        } catch (IOException e) {
            LOGGER.warn("Failed to write {}: {}", path.getFileName(), e.getMessage());
        }
    }

    private void generateDefaultMonologues(Path file) {
        String json = "{\n  \"version\": 1,\n  \"description\": \"\u9ed8\u8ba4\u5b9a\u5236\u72ec\u767d\u6587\u672c\u5e93 - \u6309\u7c7b\u522b\u7ec4\u7ec7\uff0c\u652f\u6301\u6743\u91cd\u548c\u968f\u673a\u9009\u62e9\",\n  \"settings\": {\n    \"global_cooldown_ms\": 60000,\n    \"category_cooldown_ms\": 300000\n  },\n  \"monologues\": {\n    \"sunrise\": {\n      \"weight\": 1.0,\n      \"texts\": [\n        {\"text\": \"\u00a76\u2726 \u00a7o\u6668\u66e6\u8f7b\u629a\u5927\u5730\uff0c\u65b0\u7684\u4e00\u5929\u5f00\u59cb\u4e86\u00a7r\", \"weight\": 1.0},\n        {\"text\": \"\u00a7e\u263c \u00a7o\u9633\u5149\u7a7f\u900f\u8584\u96fe\uff0c\u4e07\u7269\u82cf\u9192\u00a7r\", \"weight\": 1.0},\n        {\"text\": \"\u00a7b\u2728 \u00a7o\u7b2c\u4e00\u7f15\u5149\u6d12\u5728\u4f60\u7684\u80a9\u5934\uff0c\u4e16\u754c\u5728\u547c\u5438\u00a7r\", \"weight\": 1.0},\n        {\"text\": \"\u00a76\u2605 \u00a7o\u9ece\u660e\u7834\u6653\uff0c\u4f60\u7684\u65c5\u7a0b\u8fd8\u5728\u7ee7\u7eed\u00a7r\", \"weight\": 1.0},\n        {\"text\": \"\u00a7e\u00a7o\u4e1c\u65b9\u65e2\u767d\uff0c\u53c8\u662f\u5145\u6ee1\u53ef\u80fd\u7684\u4e00\u5929\u00a7r\", \"weight\": 1.0}\n      ]\n    },\n    \"sunset\": {\n      \"weight\": 1.0,\n      \"texts\": [\n        {\"text\": \"\u00a7d\u2601 \u00a7o\u66ae\u8272\u6e10\u6c89\uff0c\u5929\u8fb9\u67d3\u4e0a\u6700\u540e\u4e00\u62b9\u7eef\u7ea2\u00a7r\", \"weight\": 1.0},\n        {\"text\": \"\u00a75\u263e \u00a7o\u5915\u9633\u897f\u4e0b\uff0c\u591c\u665a\u5373\u5c06\u964d\u4e34\u00a7r\", \"weight\": 1.0},\n        {\"text\": \"\u00a76\u00a7o\u4f59\u6656\u6d12\u6ee1\u5927\u5730\uff0c\u662f\u65f6\u5019\u5bfb\u627e\u5e87\u62a4\u4e86\u00a7r\", \"weight\": 1.0},\n        {\"text\": \"\u00a7d\u2728 \u00a7o\u665a\u971e\u5982\u753b\uff0c\u767d\u663c\u6084\u7136\u8c22\u5e55\u00a7r\", \"weight\": 1.0}\n      ]\n    },\n    \"nether\": {\n      \"weight\": 1.0,\n      \"texts\": [\n        {\"text\": \"\u00a7c\u2620 \u00a7o\u707c\u70ed\u7684\u98ce\u6251\u9762\u800c\u6765\uff0c\u4f60\u8e0f\u5165\u4e86\u53e6\u4e00\u4e2a\u4e16\u754c\u00a7r\", \"weight\": 1.0},\n        {\"text\": \"\u00a74\u2666 \u00a7o\u706b\u7130\u4e0e\u7194\u5ca9\u7684\u56fd\u5ea6\uff0c\u8fd9\u91cc\u6ca1\u6709\u5f52\u9014\u00a7r\", \"weight\": 1.0},\n        {\"text\": \"\u00a7c\u00a7o\u8840\u7ea2\u7684\u5929\u5e55\u4e0b\uff0c\u53e4\u8001\u7684\u4f4e\u8bed\u5728\u8033\u8fb9\u56de\u54cd\u00a7r\", \"weight\": 1.0}\n      ]\n    },\n    \"end\": {\n      \"weight\": 1.0,\n      \"texts\": [\n        {\"text\": \"\u00a7d\u2606 \u00a7o\u65e0\u5c3d\u7684\u865a\u7a7a\u4e4b\u4e2d\uff0c\u661f\u8fb0\u5728\u9759\u9759\u6ce8\u89c6\u7740\u4f60\u00a7r\", \"weight\": 1.0},\n        {\"text\": \"\u00a75\u00a7o\u4f60\u6f02\u6d6e\u5728\u865a\u65e0\u7684\u8fb9\u7f18\uff0c\u8fd9\u91cc\u662f\u7ec8\u7ed3\u4ea6\u662f\u5f00\u59cb\u00a7r\", \"weight\": 1.0},\n        {\"text\": \"\u00a7d\u2726 \u00a7o\u9ed1\u6697\u4e2d\uff0c\u53ea\u6709\u4f60\u7684\u5fc3\u8df3\u58f0\u56de\u8361\u00a7r\", \"weight\": 1.0}\n      ]\n    },\n    \"death\": {\n      \"weight\": 1.0,\n      \"texts\": [\n        {\"text\": \"\u00a7c\u271d \u00a7o\u6b7b\u4ea1\u4e0d\u662f\u7ec8\u70b9\uff0c\u662f\u53e6\u4e00\u6bb5\u65c5\u7a0b\u7684\u5f00\u59cb\u00a7r\", \"weight\": 1.0},\n        {\"text\": \"\u00a78\u00a7o\u9ed1\u6697\u7b3c\u7f69\u4e86\u4f60\uff0c\u4f46\u4f60\u7ec8\u5c06\u5f52\u6765\u00a7r\", \"weight\": 1.0},\n        {\"text\": \"\u00a7c\u00a7o\u4f60\u7684\u8eab\u8eaf\u6d88\u6563\uff0c\u4f46\u610f\u5fd7\u6c38\u4e0d\u6d88\u4ea1\u00a7r\", \"weight\": 1.0}\n      ]\n    },\n    \"diamond\": {\n      \"weight\": 1.0,\n      \"texts\": [\n        {\"text\": \"\u00a7b\u25c7 \u00a7o\u84dd\u8272\u5149\u8292\u5728\u624b\u4e2d\u95ea\u8000\u2014\u2014\u8fd9\u662f\u5927\u5730\u7684\u9988\u8d60\u00a7r\", \"weight\": 1.0},\n        {\"text\": \"\u00a7b\u2726 \u00a7o\u4f60\u627e\u5230\u4e86\u5b83\uff0c\u4e16\u754c\u4e0a\u6700\u575a\u786c\u7684\u73cd\u5b9d\u00a7r\", \"weight\": 1.0}\n      ]\n    },\n    \"enchant\": {\n      \"weight\": 1.0,\n      \"texts\": [\n        {\"text\": \"\u00a7d\u2736 \u00a7o\u5965\u672f\u7684\u80fd\u91cf\u5728\u7a7a\u6c14\u4e2d\u6d41\u6dcc\uff0c\u77e5\u8bc6\u5373\u662f\u529b\u91cf\u00a7r\", \"weight\": 1.0},\n        {\"text\": \"\u00a75\u00a7o\u53e4\u8001\u7684\u7b26\u6587\u5728\u4f60\u773c\u524d\u4eae\u8d77\uff0c\u9b54\u6cd5\u4e3a\u4f60\u6240\u7528\u00a7r\", \"weight\": 1.0}\n      ]\n    },\n    \"distance\": {\n      \"weight\": 1.0,\n      \"texts\": [\n        {\"text\": \"\u00a7a\u279c \u00a7o\u4f60\u8d70\u5f97\u66f4\u8fdc\u4e86\uff0c\u4e16\u754c\u6bd4\u4f60\u60f3\u8c61\u7684\u66f4\u52a0\u8fbd\u9614\u00a7r\", \"weight\": 1.0},\n        {\"text\": \"\u00a72\u00a7o\u672a\u77e5\u7684\u5730\u5e73\u7ebf\u5728\u524d\u65b9\u53ec\u5524\uff0c\u7ee7\u7eed\u524d\u884c\u5427\u00a7r\", \"weight\": 1.0}\n      ]\n    },\n    \"depth\": {\n      \"weight\": 1.0,\n      \"texts\": [\n        {\"text\": \"\u00a78\u25bc \u00a7o\u4f60\u8d8a\u6f5c\u8d8a\u6df1\uff0c\u5730\u5e95\u7684\u79d8\u5bc6\u6b63\u5728\u63ed\u5f00\u00a7r\", \"weight\": 1.0},\n        {\"text\": \"\u00a78\u00a7o\u5ca9\u77f3\u4e0e\u9ed1\u6697\u5305\u56f4\u7740\u4f60\uff0c\u4f46\u4f60\u7684\u5149\u8292\u4e0d\u706d\u00a7r\", \"weight\": 1.0}\n      ]\n    },\n    \"height\": {\n      \"weight\": 1.0,\n      \"texts\": [\n        {\"text\": \"\u00a7f\u25b2 \u00a7o\u4f60\u6500\u4e0a\u65b0\u7684\u9ad8\u5cf0\uff0c\u5929\u9645\u7ebf\u5728\u811a\u4e0b\u5ef6\u5c55\u00a7r\", \"weight\": 1.0},\n        {\"text\": \"\u00a77\u00a7o\u9ad8\u5904\u7684\u98ce\u5f88\u51b7\uff0c\u4f46\u89c6\u91ce\u65e0\u4e0e\u4f26\u6bd4\u00a7r\", \"weight\": 1.0}\n      ]\n    }\n  }\n}";
        this.writeFile(file, json);
    }

    private void generateDefaultEchoes(Path file) {
        String json = "{\n  \"version\": 1,\n  \"description\": \"\u9ed8\u8ba4\u6545\u5730\u56de\u58f0 - \u5f53\u73a9\u5bb6\u5230\u8fbe\u7279\u5b9a\u5730\u70b9\u65f6\u89e6\u53d1\u7684\u53d9\u4e8b\u6587\u672c\",\n  \"echoes\": [\n    {\n      \"id\": \"echo_deep_underground\",\n      \"condition\": {\n        \"type\": \"Y_BELOW\",\n        \"y\": 0\n      },\n      \"texts\": [\n        \"\u00a78\u25bc \u00a7o\u4f60\u811a\u4e0b\u7684\u5ca9\u77f3\u6c89\u9ed8\u5982\u8c1c\uff0c\u4ebf\u4e07\u5e74\u7684\u5bc2\u9759\u5c06\u4f60\u5305\u56f4\u00a7r\",\n        \"\u00a78\u00a7o\u8fd9\u91cc\u662f\u9633\u5149\u4ece\u672a\u89e6\u53ca\u7684\u9886\u57df\uff0c\u53ea\u6709\u4f60\u7684\u706f\u706b\u5b9a\u4e49\u4e86\u5b58\u5728\u00a7r\",\n        \"\u00a77\u00a7o\u6df1\u9083\u7684\u9ed1\u6697\u4e2d\uff0c\u4f60\u542c\u89c1\u4e86\u5927\u5730\u7f13\u6162\u7684\u5fc3\u8df3\u00a7r\"\n      ],\n      \"weight\": 0.8,\n      \"cooldownSeconds\": 600\n    },\n    {\n      \"id\": \"echo_high_mountain\",\n      \"condition\": {\n        \"type\": \"Y_ABOVE\",\n        \"y\": 150\n      },\n      \"texts\": [\n        \"\u00a7f\u25b2 \u00a7o\u4e91\u5c42\u5728\u4f60\u7684\u811a\u4e0b\u7ffb\u6d8c\uff0c\u4e16\u754c\u4ece\u672a\u5982\u6b64\u8fbd\u9614\u00a7r\",\n        \"\u00a77\u00a7o\u51db\u51bd\u7684\u98ce\u5439\u8fc7\u5c71\u5dc5\uff0c\u4f60\u4eff\u4f5b\u53ef\u4ee5\u89e6\u6478\u5230\u5929\u7a7a\u00a7r\",\n        \"\u00a7f\u00a7o\u7ad9\u5728\u4e16\u754c\u7684\u9876\u7aef\uff0c\u6bcf\u4e00\u53e3\u547c\u5438\u90fd\u5e26\u7740\u81ea\u7531\u7684\u6ecb\u5473\u00a7r\"\n      ],\n      \"weight\": 0.7,\n      \"cooldownSeconds\": 600\n    },\n    {\n      \"id\": \"echo_first_ocean\",\n      \"condition\": {\n        \"type\": \"BIOME\",\n        \"biome\": \"minecraft:ocean\"\n      },\n      \"texts\": [\n        \"\u00a7b~ \u00a7o\u65e0\u8fb9\u65e0\u9645\u7684\u84dd\u8272\u5728\u4f60\u773c\u524d\u5c55\u5f00\uff0c\u6d77\u6d6a\u8f7b\u629a\u7740\u6d77\u5cb8\u00a7r\",\n        \"\u00a73\u00a7o\u6df1\u4e0d\u89c1\u5e95\u7684\u6c34\u57df\u4e2d\uff0c\u9690\u85cf\u7740\u53e4\u8001\u7684\u79d8\u5bc6\u00a7r\"\n      ],\n      \"weight\": 0.9,\n      \"cooldownSeconds\": 600,\n      \"onceOnly\": true\n    },\n    {\n      \"id\": \"echo_desert\",\n      \"condition\": {\n        \"type\": \"BIOME\",\n        \"biome\": \"minecraft:desert\"\n      },\n      \"texts\": [\n        \"\u00a76~ \u00a7o\u91d1\u9ec4\u7684\u6c99\u4e18\u4e00\u671b\u65e0\u9645\uff0c\u70ed\u6d6a\u626d\u66f2\u7740\u8fdc\u65b9\u7684\u5929\u9645\u7ebf\u00a7r\",\n        \"\u00a7e\u00a7o\u5728\u8fd9\u7247\u5e72\u6db8\u7684\u571f\u5730\u4e0a\uff0c\u6bcf\u4e00\u7c92\u6c99\u783e\u90fd\u8bc9\u8bf4\u7740\u88ab\u9057\u5fd8\u7684\u6545\u4e8b\u00a7r\"\n      ],\n      \"weight\": 0.8,\n      \"cooldownSeconds\": 900,\n      \"onceOnly\": true\n    },\n    {\n      \"id\": \"echo_dark_forest\",\n      \"condition\": {\n        \"type\": \"BIOME\",\n        \"biome\": \"minecraft:dark_forest\"\n      },\n      \"texts\": [\n        \"\u00a72\u25b2 \u00a7o\u5de8\u5927\u7684\u8611\u83c7\u4f1e\u76d6\u906e\u5929\u853d\u65e5\uff0c\u6797\u95f4\u5f25\u6f2b\u7740\u53e4\u8001\u7684\u9759\u8c27\u00a7r\",\n        \"\u00a70\u00a7o\u9ed1\u6697\u7684\u6811\u6797\u4e2d\uff0c\u4f60\u611f\u89c9\u6709\u4ec0\u4e48\u4e1c\u897f\u5728\u6697\u5904\u6ce8\u89c6\u7740\u4f60\u00a7r\"\n      ],\n      \"weight\": 0.9,\n      \"cooldownSeconds\": 900,\n      \"onceOnly\": true\n    },\n    {\n      \"id\": \"echo_lush_cave\",\n      \"condition\": {\n        \"type\": \"BIOME\",\n        \"biome\": \"minecraft:lush_caves\"\n      },\n      \"texts\": [\n        \"\u00a7a\u2726 \u00a7o\u5730\u4e0b\u6d1e\u7a74\u91cc\u751f\u673a\u76ce\u7136\uff0c\u53d1\u5149\u7684\u6d46\u679c\u5982\u661f\u8fb0\u822c\u70b9\u7f00\u7740\u7a79\u9876\u00a7r\",\n        \"\u00a72\u00a7o\u85e4\u8513\u5782\u843d\u5982\u5e18\uff0c\u8fd9\u662f\u5927\u5730\u6df1\u5904\u9690\u85cf\u7684\u82b1\u56ed\u00a7r\"\n      ],\n      \"weight\": 0.9,\n      \"cooldownSeconds\": 900,\n      \"onceOnly\": true\n    },\n    {\n      \"id\": \"echo_first_nether\",\n      \"condition\": {\n        \"type\": \"FIRST_TIME\",\n        \"event\": \"firstNetherDay\"\n      },\n      \"texts\": [\n        \"\u00a7c\u00a7o\u4f20\u9001\u95e8\u7684\u7d2b\u5149\u5728\u4f60\u8eab\u540e\u7184\u706d\uff0c\u4f60\u5df2\u65e0\u8def\u53ef\u9000\u00a7r\",\n        \"\u00a74\u00a7o\u811a\u4e0b\u662f\u6eda\u70eb\u7684\u5ca9\u6d46\u6d77\uff0c\u5934\u9876\u662f\u6c38\u4e0d\u660f\u6697\u7684\u8840\u8272\u82cd\u7a79\u00a7r\"\n      ],\n      \"weight\": 1.0,\n      \"cooldownSeconds\": 300,\n      \"onceOnly\": true\n    },\n    {\n      \"id\": \"echo_first_end\",\n      \"condition\": {\n        \"type\": \"FIRST_TIME\",\n        \"event\": \"firstEndDay\"\n      },\n      \"texts\": [\n        \"\u00a7d\u00a7o\u4f60\u8e0f\u5165\u865a\u7a7a\uff0c\u811a\u4e0b\u7684\u9ed1\u66dc\u77f3\u5e73\u53f0\u662f\u552f\u4e00\u7684\u5b58\u5728\u00a7r\",\n        \"\u00a75\u00a7o\u9f99\u541f\u5728\u8fdc\u65b9\u56de\u8361\uff0c\u7ec8\u7ed3\u7684\u5e8f\u5e55\u5df2\u7ecf\u62c9\u5f00\u00a7r\"\n      ],\n      \"weight\": 1.0,\n      \"cooldownSeconds\": 300,\n      \"onceOnly\": true\n    }\n  ]\n}";
        this.writeFile(file, json);
    }

    private void generateDefaultStatTemplates(Path file) {
        String json = "{\n  \"version\": 1,\n  \"description\": \"\u9884\u8bbe\u7edf\u8ba1\u6210\u5c31\u6a21\u677f - \u6574\u5408\u5305\u4f5c\u8005\u53ef\u8986\u76d6\u6b64\u6587\u4ef6\u81ea\u5b9a\u4e49\u7edf\u8ba1\u9608\u503c\uff0c\u6216\u65b0\u5efa\u6587\u4ef6\u6dfb\u52a0\u66f4\u591a\u6a21\u677f\",\n  \"templates\": [\n    {\n      \"id\": \"stat_blocks_placed_100\",\n      \"name\": \"\u521d\u89c1\u96cf\u5f62\",\n      \"description\": \"\u603b\u8ba1\u653e\u7f6e 100 \u4e2a\u65b9\u5757\",\n      \"icon\": \"minecraft:oak_planks\",\n      \"tab\": \"\u5efa\u9020\",\n      \"type\": \"STAT_REACH\",\n      \"statName\": \"blocksPlaced\",\n      \"threshold\": 100\n    },\n    {\n      \"id\": \"stat_blocks_placed_1000\",\n      \"name\": \"\u5efa\u7b51\u5927\u5e08\",\n      \"description\": \"\u603b\u8ba1\u653e\u7f6e 1000 \u4e2a\u65b9\u5757\",\n      \"icon\": \"minecraft:bricks\",\n      \"tab\": \"\u5efa\u9020\",\n      \"type\": \"STAT_REACH\",\n      \"statName\": \"blocksPlaced\",\n      \"threshold\": 1000,\n      \"prerequisites\": [\"stat_blocks_placed_100\"]\n    },\n    {\n      \"id\": \"stat_blocks_placed_10000\",\n      \"name\": \"\u9b3c\u65a7\u795e\u5de5\",\n      \"description\": \"\u603b\u8ba1\u653e\u7f6e 10000 \u4e2a\u65b9\u5757\",\n      \"icon\": \"minecraft:chiseled_stone_bricks\",\n      \"tab\": \"\u5efa\u9020\",\n      \"type\": \"STAT_REACH\",\n      \"statName\": \"blocksPlaced\",\n      \"threshold\": 10000,\n      \"prerequisites\": [\"stat_blocks_placed_1000\"]\n    },\n    {\n      \"id\": \"stat_items_crafted_50\",\n      \"name\": \"\u624b\u5de5\u827a\u4eba\",\n      \"description\": \"\u603b\u8ba1\u5408\u6210 50 \u4ef6\u7269\u54c1\",\n      \"icon\": \"minecraft:crafting_table\",\n      \"tab\": \"\u5de5\u827a\",\n      \"type\": \"STAT_REACH\",\n      \"statName\": \"itemsCrafted\",\n      \"threshold\": 50\n    },\n    {\n      \"id\": \"stat_items_crafted_500\",\n      \"name\": \"\u5de5\u5320\u4e4b\u5fc3\",\n      \"description\": \"\u603b\u8ba1\u5408\u6210 500 \u4ef6\u7269\u54c1\",\n      \"icon\": \"minecraft:smithing_table\",\n      \"tab\": \"\u5de5\u827a\",\n      \"type\": \"STAT_REACH\",\n      \"statName\": \"itemsCrafted\",\n      \"threshold\": 500,\n      \"prerequisites\": [\"stat_items_crafted_50\"]\n    },\n    {\n      \"id\": \"stat_animals_tamed_5\",\n      \"name\": \"\u52a8\u7269\u670b\u53cb\",\n      \"description\": \"\u603b\u8ba1\u9a6f\u670d 5 \u53ea\u52a8\u7269\",\n      \"icon\": \"minecraft:bone\",\n      \"tab\": \"\u751f\u5b58\",\n      \"type\": \"STAT_REACH\",\n      \"statName\": \"animalsTamed\",\n      \"threshold\": 5\n    },\n    {\n      \"id\": \"stat_animals_tamed_20\",\n      \"name\": \"\u52a8\u7269\u56ed\u957f\",\n      \"description\": \"\u603b\u8ba1\u9a6f\u670d 20 \u53ea\u52a8\u7269\",\n      \"icon\": \"minecraft:lead\",\n      \"tab\": \"\u751f\u5b58\",\n      \"type\": \"STAT_REACH\",\n      \"statName\": \"animalsTamed\",\n      \"threshold\": 20,\n      \"prerequisites\": [\"stat_animals_tamed_5\"]\n    },\n    {\n      \"id\": \"stat_torches_placed_64\",\n      \"name\": \"\u5e26\u6765\u5149\u660e\",\n      \"description\": \"\u603b\u8ba1\u653e\u7f6e 64 \u6839\u706b\u628a\",\n      \"icon\": \"minecraft:torch\",\n      \"tab\": \"\u5efa\u9020\",\n      \"type\": \"STAT_REACH\",\n      \"statName\": \"torchesPlaced\",\n      \"threshold\": 64\n    },\n    {\n      \"id\": \"stat_torches_placed_500\",\n      \"name\": \"\u9ed1\u6697\u9a71\u9010\u8005\",\n      \"description\": \"\u603b\u8ba1\u653e\u7f6e 500 \u6839\u706b\u628a\",\n      \"icon\": \"minecraft:lantern\",\n      \"tab\": \"\u5efa\u9020\",\n      \"type\": \"STAT_REACH\",\n      \"statName\": \"torchesPlaced\",\n      \"threshold\": 500,\n      \"prerequisites\": [\"stat_torches_placed_64\"]\n    },\n    {\n      \"id\": \"stat_crops_planted_50\",\n      \"name\": \"\u519c\u573a\u4e3b\",\n      \"description\": \"\u603b\u8ba1\u79cd\u690d 50 \u682a\u4f5c\u7269\",\n      \"icon\": \"minecraft:wheat\",\n      \"tab\": \"\u751f\u5b58\",\n      \"type\": \"STAT_REACH\",\n      \"statName\": \"cropsPlanted\",\n      \"threshold\": 50\n    },\n    {\n      \"id\": \"stat_lightning_struck\",\n      \"name\": \"\u5929\u9009\u4e4b\u4eba\",\n      \"description\": \"\u88ab\u95ea\u7535\u5288\u4e2d\",\n      \"icon\": \"minecraft:lightning_rod\",\n      \"tab\": \"\u6218\u6597\",\n      \"type\": \"STAT_REACH\",\n      \"statName\": \"lightningStrikes\",\n      \"threshold\": 1\n    },\n    {\n      \"id\": \"stat_trader_trades_10\",\n      \"name\": \"\u7cbe\u660e\u7684\u5546\u4eba\",\n      \"description\": \"\u4e0e\u6d41\u6d6a\u5546\u4eba\u4ea4\u6613 10 \u6b21\",\n      \"icon\": \"minecraft:emerald\",\n      \"tab\": \"\u751f\u5b58\",\n      \"type\": \"STAT_REACH\",\n      \"statName\": \"wanderingTraderTrades\",\n      \"threshold\": 10\n    },\n    {\n      \"id\": \"stat_name_tags_5\",\n      \"name\": \"\u547d\u540d\u8fbe\u4eba\",\n      \"description\": \"\u4f7f\u7528 5 \u4e2a\u547d\u540d\u724c\",\n      \"icon\": \"minecraft:name_tag\",\n      \"tab\": \"\u751f\u5b58\",\n      \"type\": \"STAT_REACH\",\n      \"statName\": \"nameTagsUsed\",\n      \"threshold\": 5\n    }\n  ]\n}";
        this.writeFile(file, json);
    }

    private void generateDefaultDescriptions(Path file) {
        String json = "{\n  \"version\": 1,\n  \"description\": \"\u6210\u5c31\u5b8c\u6210\u65f6\u7684\u98ce\u5473\u6587\u672c\u5e93 \u2014 \u6574\u5408\u5305\u4f5c\u8005\u53ef\u8986\u76d6\u6b64\u6587\u4ef6\u81ea\u5b9a\u4e49\u5b8c\u6210\u63d0\u793a\uff0c\u6216\u65b0\u589e\u6761\u76ee\",\n  \"entries\": [\n    {\n      \"advancement_id\": \"stat_blocks_placed_100\",\n      \"lore\": \"\u7b2c\u4e00\u5757\u7816\u77f3\u843d\u4e0b\uff0c\u4f60\u7684\u5bb6\u56ed\u5728\u8352\u91ce\u4e2d\u6162\u6162\u6210\u5f62\"\n    },\n    {\n      \"advancement_id\": \"stat_blocks_placed_1000\",\n      \"lore\": \"\u4ece\u7b80\u964b\u7684\u6ce5\u571f\u5c0f\u5c4b\u5230\u5b8f\u4f1f\u7684\u77f3\u7816\u57ce\u5821\u2014\u2014\u4f60\u662f\u8fd9\u4e2a\u4e16\u754c\u7684\u5efa\u9020\u8005\"\n    },\n    {\n      \"advancement_id\": \"stat_blocks_placed_10000\",\n      \"lore\": \"\u4e00\u4e07\u5757\u65b9\u5757\uff0c\u65e0\u6570\u4e2a\u65e5\u591c\u3002\u5c81\u6708\u5728\u4f60\u624b\u4e2d\u51dd\u56fa\u6210\u4e0d\u673d\u7684\u6bbf\u5802\"\n    },\n    {\n      \"advancement_id\": \"stat_items_crafted_50\",\n      \"lore\": \"\u5de5\u4f5c\u53f0\u7684\u6728\u5c51\u98de\u626c\uff0c\u4f60\u7684\u53cc\u624b\u5f00\u59cb\u719f\u6089\u6bcf\u4e00\u4ef6\u5de5\u5177\u7684\u6e29\u5ea6\"\n    },\n    {\n      \"advancement_id\": \"stat_items_crafted_500\",\n      \"lore\": \"\u4e94\u767e\u6b21\u7684\u6572\u6253\u4e0e\u7194\u70bc\uff0c\u5de5\u5320\u7684\u76f4\u89c9\u5df2\u878d\u5165\u4f60\u7684\u8840\u8109\"\n    },\n    {\n      \"advancement_id\": \"stat_animals_tamed_5\",\n      \"lore\": \"\u5b83\u4eec\u4fe1\u4efb\u4e86\u4f60\u3002\u5728\u65f7\u91ce\u4e4b\u4e2d\uff0c\u966a\u4f34\u662f\u6700\u6e29\u6696\u7684\u8d22\u5bcc\"\n    },\n    {\n      \"advancement_id\": \"stat_animals_tamed_20\",\n      \"lore\": \"\u4e8c\u5341\u4e2a\u5fe0\u8bda\u7684\u4f19\u4f34\uff0c\u4f60\u7684\u5c45\u6240\u6210\u4e86\u751f\u547d\u7684\u5e87\u62a4\u6240\"\n    },\n    {\n      \"advancement_id\": \"stat_torches_placed_64\",\n      \"lore\": \"\u4e00\u652f\u706b\u70ac\u70b9\u4eae\u4e00\u6b65\u8def\uff0c\u516d\u5341\u56db\u652f\u706b\u70ac\u9a71\u6563\u6574\u7247\u9ed1\u6697\"\n    },\n    {\n      \"advancement_id\": \"stat_torches_placed_500\",\n      \"lore\": \"\u4f60\u8d70\u8fc7\u7684\u5730\u65b9\uff0c\u6ca1\u6709\u9ed1\u6697\u53ef\u4ee5\u505c\u7559\u3002\u4f60\u662f\u5149\u7684\u4f7f\u8005\"\n    },\n    {\n      \"advancement_id\": \"stat_crops_planted_50\",\n      \"lore\": \"\u9ea6\u6d6a\u5728\u98ce\u4e2d\u6447\u66f3\uff0c\u4e30\u6536\u7684\u5b63\u8282\u662f\u4f60\u7684\u56de\u62a5\"\n    },\n    {\n      \"advancement_id\": \"stat_lightning_struck\",\n      \"lore\": \"\u5929\u7a7a\u7684\u6012\u706b\u51fb\u4e2d\u4e86\u4f60\uff0c\u800c\u4f60\u6d3b\u4e86\u4e0b\u6765\u3002\u547d\u8fd0\u9009\u62e9\u4f60\u627f\u53d7\u96f7\u7535\u4e4b\u529b\"\n    },\n    {\n      \"advancement_id\": \"stat_trader_trades_10\",\n      \"lore\": \"\u6234\u7740\u84dd\u5e3d\u7684\u5546\u4eba\u5728\u4e16\u754c\u5404\u5730\u6e38\u8d70\uff0c\u800c\u4f60\u4e0e\u4ed6\u4eec\u7ed3\u4e0b\u4e86\u4e0d\u89e3\u4e4b\u7f18\"\n    },\n    {\n      \"advancement_id\": \"stat_name_tags_5\",\n      \"lore\": \"\u6bcf\u4e2a\u540d\u5b57\u90fd\u662f\u4e00\u6bb5\u8bb0\u5fc6\u3002\u4f60\u7ed9\u8fd9\u4e2a\u4e16\u754c\u7559\u4e0b\u4e86\u5370\u8bb0\"\n    }\n  ]\n}";
        this.writeFile(file, json);
    }

    private void generateReadme(Path base) {
        this.generateReadmeCn(base);
        this.generateReadmeEn(base);
    }

    private void generateReadmeCn(Path base) {
        Path readme = base.resolve("README.txt");
        if (Files.exists(readme)) {
            return;
        }
        String content = "====================================================\n     Advancement Overhaul - \u53d9\u4e8b\u914d\u7f6e\u8bf4\u660e\uff08\u4e2d\u6587\uff09\n====================================================\n\n\u672c\u76ee\u5f55\u7528\u4e8e\u81ea\u5b9a\u4e49\u6a21\u7ec4\u7684\u53d9\u4e8b\u7cfb\u7edf\u6587\u672c\u3002\n\u4fee\u6539\u6587\u4ef6\u540e\u4f7f\u7528 /adv reload \u6216\u91cd\u542f\u670d\u52a1\u7aef\u751f\u6548\u3002\n\u6240\u6709\u6587\u4ef6\u5747\u4e3a UTF-8 \u7f16\u7801\u7684 JSON \u683c\u5f0f\u3002\n\n----------------------------------------------------\n\u76ee\u5f55\u7ed3\u6784\n----------------------------------------------------\n\nnarratives/\n\u251c\u2500\u2500 monologues/          \u2190 \u72ec\u767d\u6587\u672c\n\u2502   \u2514\u2500\u2500 default.json     \u2190 \u9ed8\u8ba4\u5b9a\u5236\u72ec\u767d\uff0c\u53ef\u65b0\u5efa\u66f4\u591a\u6587\u4ef6\n\u251c\u2500\u2500 echoes/              \u2190 \u6545\u5730\u56de\u58f0\n\u2502   \u2514\u2500\u2500 default.json     \u2190 \u9ed8\u8ba4\u56de\u58f0\uff0c\u53ef\u65b0\u5efa\u66f4\u591a\u6587\u4ef6\n\u251c\u2500\u2500 stat_templates.json  \u2190 \u7edf\u8ba1\u6210\u5c31\u6a21\u677f\n\u251c\u2500\u2500 advancement_descriptions.json \u2190 \u6210\u5c31\u5b8c\u6210\u98ce\u5473\u6587\u672c\n\u2514\u2500\u2500 README.txt           \u2190 \u672c\u8bf4\u660e\u6587\u4ef6\n\n----------------------------------------------------\n1. \u72ec\u767d\u6587\u672c (monologues/*.json)\n----------------------------------------------------\n\n\u683c\u5f0f\uff1a\n{\n  \"version\": 1,\n  \"settings\": {\n    \"global_cooldown_ms\": 60000,      \u2190 \u5168\u5c40\u51b7\u5374\uff08\u6beb\u79d2\uff09\n    \"category_cooldown_ms\": 300000    \u2190 \u540c\u7c7b\u522b\u51b7\u5374\uff08\u6beb\u79d2\uff09\n  },\n  \"monologues\": {\n    \"\u7c7b\u522b\u540d\": {\n      \"weight\": 1.0,                   \u2190 \u7c7b\u522b\u6743\u91cd\n      \"texts\": [\n        {\"text\": \"\u00a76\u6587\u672c\u5185\u5bb9\u00a7r\", \"weight\": 1.0},\n        ...\n      ]\n    }\n  }\n}\n\n\u7c7b\u522b\u540d\u7528\u4e8e\u89e6\u53d1\u72ec\u767d\uff0c\u76ee\u524d\u652f\u6301\u7684\u7c7b\u522b\uff1a\n  sunrise, sunset, nether, end, death,\n  diamond, enchant, distance, depth, height\n\n\u53ef\u81ea\u5b9a\u4e49\u65b0\u7c7b\u522b\uff08\u9700\u5728\u4ee3\u7801\u4e2d\u6ce8\u518c\u89e6\u53d1\u6761\u4ef6\uff09\u3002\n\n----------------------------------------------------\n2. \u6545\u5730\u56de\u58f0 (echoes/*.json)\n----------------------------------------------------\n\n\u683c\u5f0f\uff1a\n{\n  \"echoes\": [\n    {\n      \"id\": \"\u552f\u4e00ID\",\n      \"condition\": {\n        \"type\": \"\u6761\u4ef6\u7c7b\u578b\",\n        ... \u6761\u4ef6\u53c2\u6570 ...\n      },\n      \"texts\": [\"\u6587\u672c1\", \"\u6587\u672c2\", ...],\n      \"weight\": 1.0,\n      \"cooldownSeconds\": 300,\n      \"onceOnly\": false\n    }\n  ]\n}\n\n\u6761\u4ef6\u7c7b\u578b\uff1a\n- BIOME:        {\"type\": \"BIOME\", \"biome\": \"minecraft:ocean\"}\n- Y_BELOW:      {\"type\": \"Y_BELOW\", \"y\": 0}\n- Y_ABOVE:      {\"type\": \"Y_ABOVE\", \"y\": 150}\n- DIMENSION:    {\"type\": \"DIMENSION\", \"dimension\": \"minecraft:the_nether\"}\n- FIRST_TIME:   {\"type\": \"FIRST_TIME\", \"event\": \"firstNetherDay\"}\n\nFIRST_TIME \u53ef\u7528\u7684\u4e8b\u4ef6\u540d\uff1a\n  firstNetherDay, firstEndDay, firstDiamondDay,\n  firstEnchantDay, firstTameDay, firstRainSleepDay\n\n----------------------------------------------------\n3. \u7edf\u8ba1\u6210\u5c31\u6a21\u677f (stat_templates.json)\n----------------------------------------------------\n\n\u683c\u5f0f\uff1a\u89c1 default.json \u4e2d\u7684 templates \u6570\u7ec4\u3002\n\u6dfb\u52a0\u6a21\u677f\u540e\u53ef\u7528 /adv template create <id> \u751f\u6210\u6210\u5c31\u3002\n\n----------------------------------------------------\n4. \u6210\u5c31\u5b8c\u6210\u98ce\u5473\u6587\u672c (advancement_descriptions.json)\n----------------------------------------------------\n\n\u683c\u5f0f\uff1a\n{\n  \"entries\": [\n    {\"advancement_id\": \"\u6210\u5c31ID\", \"lore\": \"\u5b8c\u6210\u65f6\u663e\u793a\u7684\u53d9\u4e8b\u6587\u672c\"}\n  ]\n}\n\n----------------------------------------------------\n\u989c\u8272\u4ee3\u7801\u8bf4\u660e\n----------------------------------------------------\n\n\u00a7a \u7eff  \u00a7b \u5929\u84dd  \u00a7c \u7ea2  \u00a7d \u7c89\u7ea2  \u00a7e \u9ec4  \u00a7f \u767d\n\u00a71 \u6df1\u84dd \u00a72 \u6df1\u7eff \u00a73 \u6c34\u7eff \u00a74 \u6697\u7ea2 \u00a75 \u7d2b\u8272 \u00a76 \u91d1\u8272\n\u00a77 \u7070\u8272 \u00a78 \u6697\u7070 \u00a79 \u84dd\u8272 \u00a70 \u9ed1\u8272\n\u00a7l \u7c97\u4f53 \u00a7o \u659c\u4f53 \u00a7n \u4e0b\u5212\u7ebf \u00a7m \u5220\u9664\u7ebf \u00a7k \u95ea\u70c1\n\u00a7r \u91cd\u7f6e\u683c\u5f0f\n\n----------------------------------------------------\n\u6ce8\u610f\u4e8b\u9879\n----------------------------------------------------\n\n- \u4fee\u6539\u540e\u9700\u8981 /adv reload \u6216\u5728\u6e38\u620f\u5185\u4f7f\u7528\u91cd\u8f7d\u547d\u4ee4\u3002\n- JSON \u683c\u5f0f\u5fc5\u987b\u6b63\u786e\uff08\u63a8\u8350\u4f7f\u7528 JSON \u9a8c\u8bc1\u5de5\u5177\u68c0\u67e5\uff09\u3002\n- \u6587\u4ef6\u540d\u4e0d\u5f71\u54cd\u529f\u80fd\uff0c\u4f46\u5efa\u8bae\u4f7f\u7528\u6709\u610f\u4e49\u7684\u540d\u5b57\u3002\n- \u5220\u9664\u6587\u4ef6\u540e\u6a21\u7ec4\u4f1a\u56de\u9000\u5230\u5185\u7f6e\u9ed8\u8ba4\u503c\u3002\n- \u6587\u672c\u4e2d\u7684 \u00a7 \u662f Minecraft \u683c\u5f0f\u5316\u4ee3\u7801\u3002\n";
        this.writeFile(readme, content);
    }

    private void generateReadmeEn(Path base) {
        Path readme = base.resolve("README_EN.txt");
        if (Files.exists(readme)) {
            return;
        }
        String content = "====================================================\n   Advancement Overhaul - Narrative Config Guide\n====================================================\n\nPlace custom JSON files here to override the mod's\nnarrative text system. Changes take effect after\n/adv reload or server restart. UTF-8 encoded JSON.\n\n----------------------------------------------------\nDirectory Structure\n----------------------------------------------------\n\nnarratives/\n\u251c\u2500\u2500 monologues/          \u2190 Monologue texts\n\u2502   \u2514\u2500\u2500 default.json     \u2190 Default categories\n\u251c\u2500\u2500 echoes/              \u2190 Place echoes\n\u2502   \u2514\u2500\u2500 default.json     \u2190 Default echoes\n\u251c\u2500\u2500 stat_templates.json  \u2190 Stat-based achievement templates\n\u251c\u2500\u2500 advancement_descriptions.json \u2190 Completion lore\n\u2514\u2500\u2500 README.txt / README_EN.txt\n\n----------------------------------------------------\n1. Monologues (monologues/*.json)\n----------------------------------------------------\n\nSupported categories:\n  sunrise, sunset, nether, end, death,\n  diamond, enchant, distance, depth, height\n\nEach category has weighted texts randomly selected.\n\n----------------------------------------------------\n2. Echoes (echoes/*.json)\n----------------------------------------------------\n\nCondition types:\n  BIOME:     triggers when entering a biome\n  Y_BELOW:   triggers below a Y level\n  Y_ABOVE:   triggers above a Y level\n  DIMENSION: triggers in a dimension\n  FIRST_TIME: triggers on first-time events\n\n----------------------------------------------------\n3. Stat Templates\n----------------------------------------------------\n\nDefine stat-based achievements. Use\n/adv template create <id> to instantiate.\n\n----------------------------------------------------\n4. Completion Descriptions\n----------------------------------------------------\n\nLore text displayed when an advancement is completed.\n\n----------------------------------------------------\nColor Codes\n----------------------------------------------------\n\n\u00a7a green  \u00a7b aqua  \u00a7c red  \u00a7d pink  \u00a7e yellow  \u00a7f white\n\u00a71 dark-blue \u00a72 dark-green \u00a73 cyan \u00a74 dark-red\n\u00a75 purple \u00a76 gold \u00a77 gray \u00a78 dark-gray \u00a79 blue \u00a70 black\n\u00a7l bold \u00a7o italic \u00a7n underline \u00a7m strikethrough \u00a7k obfuscated\n\u00a7r reset\n\n----------------------------------------------------\nNotes\n----------------------------------------------------\n\n- Edit files then /adv reload to apply changes.\n- Delete files to revert to built-in defaults.\n- JSON must be valid (use a linter if unsure).\n- File names don't affect behavior.\n";
        this.writeFile(readme, content);
    }

    @FunctionalInterface
    private interface JsonConsumer {
        void accept(JsonElement element);
    }
}
