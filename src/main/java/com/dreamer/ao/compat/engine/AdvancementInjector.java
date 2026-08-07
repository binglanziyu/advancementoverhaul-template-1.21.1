package com.dreamer.ao.compat.engine;

import com.dreamer.ao.data.ServerDataStore;
import com.dreamer.ao.data.model.CustomAdvancement;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义进度 JSON 注入器。
 * <p>
 * 负责将自定义进度数据构建为原版 JSON 格式，
 * 注入到 Minecraft 数据加载流程的 Map 中，
 * 并提供带有缓存的 AdvancementHolder 解析。
 */
public final class AdvancementInjector {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdvancementInjector.class);

    /** 已解析的原版 AchievementHolder 缓存（避免重复 Codec 解析） */
    static final Map<ResourceLocation, AdvancementHolder> parsedHolderCache = new ConcurrentHashMap<>();

    private AdvancementInjector() {}

    /**
     * 将自定义进度 JSON 注入数据加载 Map。
     * 使用 {@code minecraft:impossible} 触发器。
     */
    public static void injectAdvancements(Map<ResourceLocation, JsonElement> data) {
        ServerDataStore store = ServerDataStore.getInstance();
        Map<String, CustomAdvancement> advancements = store.getAdvancements();
        if (advancements.isEmpty()) {
            LOGGER.info("No custom advancements found, skipping injection");
            return;
        }
        int count = 0;
        for (CustomAdvancement adv : advancements.values()) {
            ResourceLocation vanillaId = AdvancementIdMapper.toVanillaId(adv.getId());
            if (!data.containsKey(vanillaId)) {
                data.put(vanillaId, buildAdvancementJson(adv));
                count++;
            }
        }
        LOGGER.info("Total injected: {} custom advancements", count);
    }

    /**
     * 从自定义进度构建原版 JSON。
     * 使用 {@code minecraft:impossible} 触发器 + task 框架。
     */
    public static JsonObject buildAdvancementJson(CustomAdvancement adv) {
        JsonObject root = new JsonObject();

        JsonObject display = new JsonObject();
        JsonObject icon = new JsonObject();
        String iconId = (adv.getIcon() != null && !adv.getIcon().isEmpty())
                ? adv.getIcon() : "minecraft:nether_star";
        icon.addProperty("id", iconId);
        display.add("icon", icon);
        display.add("title", textObj(adv.getName()));
        String desc = adv.getDescription();
        display.add("description", textObj(desc != null && !desc.isEmpty() ? desc : adv.getId()));
        display.addProperty("background",
                "minecraft:textures/gui/advancements/backgrounds/stone.png");
        display.addProperty("frame", "task");
        display.addProperty("show_toast", false);
        display.addProperty("announce_to_chat", false);
        display.addProperty("hidden", adv.isHidden());
        root.add("display", display);

        JsonObject criteria = new JsonObject();
        JsonObject trigger = new JsonObject();
        trigger.addProperty("trigger", "minecraft:impossible");
        criteria.add("trigger", trigger);
        root.add("criteria", criteria);

        JsonArray reqOuter = new JsonArray();
        JsonArray reqInner = new JsonArray();
        reqInner.add("trigger");
        reqOuter.add(reqInner);
        root.add("requirements", reqOuter);

        return root;
    }

    static JsonObject textObj(String text) {
        JsonObject obj = new JsonObject();
        obj.addProperty("text", text != null ? text : "");
        return obj;
    }

    /**
     * 解析成就 JSON 为 AdvancementHolder，带缓存。
     * 仅缓存原版成就（自定义成就可能在编辑后变化）。
     */
    public static AdvancementHolder parseHolder(RegistryOps<JsonElement> ops,
                                                  ResourceLocation id, JsonObject json) {
        boolean isVanilla = !com.dreamer.ao.ModInfo.MOD_ID.equals(id.getNamespace());
        if (isVanilla) {
            AdvancementHolder cached = parsedHolderCache.get(id);
            if (cached != null) return cached;
        }
        try {
            DataResult<Advancement> result = Advancement.CODEC.parse(ops, json);
            Optional<Advancement> opt = result.result();
            if (opt.isPresent()) {
                AdvancementHolder holder = new AdvancementHolder(id, opt.get());
                if (isVanilla) parsedHolderCache.put(id, holder);
                return holder;
            }
            Optional<DataResult.Error<Advancement>> err = result.error();
            if (err.isPresent()) {
                LOGGER.error("Parse failed for {}: {}", id, err.get().message());
            }
            return null;
        } catch (Exception e) {
            LOGGER.error("Failed to parse advancement {}", id, e);
            return null;
        }
    }
}
