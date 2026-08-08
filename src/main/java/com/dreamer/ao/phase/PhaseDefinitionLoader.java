package com.dreamer.ao.phase;

import com.dreamer.ao.ModInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.fml.loading.FMLPaths;

/**
 * 阶段定义加载器。
 * <p>
 * 仿 {@code TimelineDefinitionLoader}，从 {@code config/advancementoverhaul/phases/*.json} 读取阶段定义。
 * 首次运行时若目录不存在，写入一份默认示例配置（含 world/dimension/player 各一例），便于上手。
 */
public final class PhaseDefinitionLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(PhaseDefinitionLoader.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PHASE_DIR = FMLPaths.CONFIGDIR.get()
            .resolve(ModInfo.MOD_ID).resolve("phases");

    /** 阶段定义目录（供可视化编辑器写回 / 热重载使用） */
    public static Path getPhaseDir() {
        return PHASE_DIR;
    }

    private PhaseDefinitionLoader() {
    }

    /** 单个阶段定义对应的文件名（每个定义独立存储，便于增删改互不干扰） */
    public static Path definitionFile(String id) {
        return PHASE_DIR.resolve(id + ".json");
    }

    /** 保存（新建或覆盖）单个阶段定义到独立文件 */
    public static void saveDef(PhaseDefinition def) {
        try {
            if (!Files.exists(PHASE_DIR)) {
                Files.createDirectories(PHASE_DIR);
            }
            JsonArray arr = new JsonArray();
            arr.add(def.toJson());
            Files.writeString(definitionFile(def.getId()), GSON.toJson(arr), StandardCharsets.UTF_8);
            LOGGER.info("已保存阶段定义: {}", def.getId());
        } catch (IOException e) {
            LOGGER.error("保存阶段定义失败: {}", def.getId(), e);
        }
    }

    /** 删除单个阶段定义文件 */
    public static void deleteDef(String id) {
        try {
            Path file = definitionFile(id);
            if (Files.exists(file)) {
                Files.delete(file);
                LOGGER.info("已删除阶段定义: {}", id);
            }
        } catch (IOException e) {
            LOGGER.error("删除阶段定义失败: {}", id, e);
        }
    }

    /** 加载所有阶段定义 */
    public static List<PhaseDefinition> loadAll() {
        List<PhaseDefinition> result = new ArrayList<>();
        try {
            if (!Files.exists(PHASE_DIR)) {
                Files.createDirectories(PHASE_DIR);
                writeDefaultConfig();
            }
            try (var stream = Files.newDirectoryStream(PHASE_DIR, "*.json")) {
                for (Path file : stream) {
                    try {
                        String content = Files.readString(file, StandardCharsets.UTF_8);
                        JsonArray arr = GSON.fromJson(content, JsonArray.class);
                        if (arr != null) {
                            for (int i = 0; i < arr.size(); i++) {
                                result.add(PhaseDefinition.fromJson(arr.get(i).getAsJsonObject()));
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.error("阶段配置解析失败: {}", file.getFileName(), e);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("阶段配置目录读取失败", e);
        }
        return result;
    }

    /** 写入默认示例配置 */
    private static void writeDefaultConfig() throws IOException {
        JsonArray arr = new JsonArray();

        // 世界级别默认阶段
        JsonObject world = new JsonObject();
        world.addProperty("id", "world_basic");
        world.addProperty("name", "基础纪元");
        world.addProperty("tier", 0);
        world.addProperty("scope", "world");
        JsonObject we = new JsonObject();
        JsonObject wa = new JsonObject();
        wa.addProperty("max_health", 1.0);
        we.add("attributes", wa);
        world.add("effects", we);
        arr.add(world);

        // 维度级别（主世界）阶段，关联里程碑解锁
        JsonObject dim = new JsonObject();
        dim.addProperty("id", "overworld_dawn");
        dim.addProperty("name", "黎明");
        dim.addProperty("tier", 1);
        dim.addProperty("scope", "dimension");
        dim.addProperty("dimension", "minecraft:overworld");
        dim.addProperty("unlockMilestone", "m_first_spawn");
        JsonObject de = new JsonObject();
        JsonObject dm = new JsonObject();
        dm.addProperty("mob_health_mult", 0.9);
        de.add("mob_mults", dm);
        JsonObject deq = new JsonObject();
        JsonArray eq = new JsonArray();
        JsonObject equip = new JsonObject();
        equip.addProperty("chance", 0.15);
        JsonObject slots = new JsonObject();
        slots.addProperty("head", "minecraft:leather_helmet");
        slots.addProperty("mainhand", "minecraft:stone_sword");
        equip.add("slots", slots);
        eq.add(equip);
        deq.add("equipment", eq);
        de.add("mob_mults", dm);
        de.add("equipment", eq);
        dim.add("effects", de);
        arr.add(dim);

        // 玩家级别阶段，关联里程碑解锁，带状态效果
        JsonObject player = new JsonObject();
        player.addProperty("id", "hero_awaken");
        player.addProperty("name", "英雄觉醒");
        player.addProperty("tier", 2);
        player.addProperty("scope", "player");
        player.addProperty("unlockMilestone", "m_kill_dragon");
        JsonObject pe = new JsonObject();
        JsonObject pa = new JsonObject();
        pa.addProperty("attack_damage", 1.15);
        pa.addProperty("max_health", 1.1);
        pe.add("attributes", pa);
        JsonArray effArr = new JsonArray();
        JsonObject eff = new JsonObject();
        eff.addProperty("id", "minecraft:strength");
        eff.addProperty("level", 0);
        eff.addProperty("seconds", 600);
        effArr.add(eff);
        pe.add("mob_effects", effArr);
        player.add("effects", pe);
        arr.add(player);

        Path file = PHASE_DIR.resolve("default.json");
        Files.writeString(file, GSON.toJson(arr), StandardCharsets.UTF_8);
        LOGGER.info("已写入默认阶段配置: {}", file);
    }
}
