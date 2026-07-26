package com.example.advancementoverhaul.compat;

import com.example.advancementoverhaul.Config;
import com.example.advancementoverhaul.ModInfo;
import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.ServerDataStore;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AdvancementRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/Registry");
    private static final String PREFIX = "custom/";

    // [A1] 前向映射：自定义ID → 原版ResourceLocation
    private static final Map<String, ResourceLocation> idMapping = new ConcurrentHashMap<>();
    // [A1] 反向映射：原版ResourceLocation → 自定义ID（O(1)查找）
    private static final Map<ResourceLocation, String> reverseIdMapping = new ConcurrentHashMap<>();
    // [A2] 已解析的原版成就Holder缓存，避免重复Codec解析
    private static final Map<ResourceLocation, AdvancementHolder> parsedHolderCache = new ConcurrentHashMap<>();
    // [A3] 上次同步的成就ID快照，用于增量同步
    private static volatile Set<ResourceLocation> lastSyncedAdvIds = new HashSet<>();

    private AdvancementRegistry() {}

    public static ResourceLocation toVanillaId(String customId) {
        return idMapping.computeIfAbsent(customId, id -> {
            String safe = id.replace(':', '_')
                    .replaceAll("[^a-z0-9_/\\-]", "_")
                    .toLowerCase();
            while (safe.startsWith("_")) safe = safe.substring(1);
            while (safe.endsWith("_")) safe = safe.substring(0, safe.length() - 1);
            if (safe.isEmpty()) safe = "unknown_" + Math.abs(id.hashCode());
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(ModInfo.MOD_ID, PREFIX + safe);
            reverseIdMapping.put(rl, id); // [A1] 维护反向映射
            return rl;
        });
    }

    // [A1] O(1) 反向查找，替代原来的 O(n) 遍历
    public static String fromVanillaId(ResourceLocation vanillaId) {
        if (!ModInfo.MOD_ID.equals(vanillaId.getNamespace())) return null;
        String path = vanillaId.getPath();
        if (!path.startsWith(PREFIX)) return null;
        return reverseIdMapping.get(vanillaId);
    }

    public static boolean isCustomAdvancement(ResourceLocation id) {
        return ModInfo.MOD_ID.equals(id.getNamespace()) && id.getPath().startsWith(PREFIX);
    }

    public static void injectAdvancements(Map<ResourceLocation, JsonElement> data) {
        ServerDataStore store = ServerDataStore.getInstance();
        Map<String, DataStore.CustomAdvancement> advancements = store.getAdvancements();
        if (advancements.isEmpty()) {
            LOGGER.info("No custom advancements found, skipping injection");
            return;
        }
        int count = 0;
        for (var adv : advancements.values()) {
            ResourceLocation vanillaId = toVanillaId(adv.getId());
            if (!data.containsKey(vanillaId)) {
                data.put(vanillaId, buildAdvancementJson(adv));
                count++;
                LOGGER.debug("Injected: {} → {}", adv.getId(), vanillaId);
            }
        }
        LOGGER.info("Total injected: {} custom advancements", count);
    }

    private static JsonObject buildAdvancementJson(DataStore.CustomAdvancement adv) {
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
        display.addProperty("background", "minecraft:textures/gui/advancements/backgrounds/stone.png");
        display.addProperty("frame", "task");
        display.addProperty("show_toast", true);
        display.addProperty("announce_to_chat", true);
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

    private static JsonObject textObj(String text) {
        JsonObject obj = new JsonObject();
        obj.addProperty("text", text != null ? text : "");
        return obj;
    }

    public static void grantAdvancement(ServerPlayer player, String customId) {
        ResourceLocation vanillaId = toVanillaId(customId);
        AdvancementHolder holder = player.server.getAdvancements().get(vanillaId);
        if (holder == null) {
            LOGGER.warn("Cannot grant - not found: {} (for {})", vanillaId, customId);
            return;
        }
        boolean newlyDone = player.getAdvancements().award(holder, "trigger");
        player.getAdvancements().flushDirty(player);
        LOGGER.debug("Granted {} to {}: newlyDone={}", vanillaId, player.getName().getString(), newlyDone);
    }

    public static void revokeAdvancement(ServerPlayer player, String customId) {
        ResourceLocation vanillaId = toVanillaId(customId);
        AdvancementHolder holder = player.server.getAdvancements().get(vanillaId);
        if (holder == null) return;
        player.getAdvancements().revoke(holder, "trigger");
        player.getAdvancements().flushDirty(player);
        LOGGER.debug("Revoked {} from {}", vanillaId, player.getName().getString());
    }

    public static void syncToVanilla(ServerPlayer player) {
        ServerDataStore store = ServerDataStore.getInstance();
        var completions = store.getPlayerCompletions(player.getUUID());
        int granted = 0, revoked = 0;
        for (var entry : store.getAdvancements().entrySet()) {
            String customId = entry.getKey();
            boolean completed = Boolean.TRUE.equals(completions.get(customId));
            ResourceLocation vanillaId = toVanillaId(customId);
            AdvancementHolder holder = player.server.getAdvancements().get(vanillaId);
            if (holder == null) continue;
            if (completed) {
                player.getAdvancements().award(holder, "trigger");
                granted++;
            } else {
                var progress = player.getAdvancements().getOrStartProgress(holder);
                if (progress.isDone()) {
                    player.getAdvancements().revoke(holder, "trigger");
                    revoked++;
                }
            }
        }
        if (granted > 0 || revoked > 0) {
            player.getAdvancements().flushDirty(player);
            LOGGER.info("Synced for {}: {} granted, {} revoked", player.getName().getString(), granted, revoked);
        }
    }

    public static void suppressVanillaAdvancement(ServerPlayer player, String advId) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(advId);
            if (rl == null) return;
            AdvancementHolder holder = player.server.getAdvancements().get(rl);
            if (holder == null) return;
            var progress = player.getAdvancements().getOrStartProgress(holder);
            if (progress.isDone()) {
                for (String criterion : progress.getCompletedCriteria())
                    player.getAdvancements().revoke(holder, criterion);
                player.getAdvancements().flushDirty(player);
                LOGGER.debug("Suppressed vanilla {} for {}", advId, player.getName().getString());
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to suppress vanilla advancement {}: {}", advId, e.getMessage());
        }
    }

    public static void suppressAllDisabled(ServerPlayer player) {
        ServerDataStore store = ServerDataStore.getInstance();
        for (String id : store.getDisabledVanilla()) suppressVanillaAdvancement(player, id);
        if (!Config.VANILLA_DEFAULT_ENABLED.get()) {
            try {
                for (var holder : player.server.getAdvancements().getAllAdvancements()) {
                    String id = holder.id().toString();
                    if (isCustomAdvancement(holder.id())) continue;
                    if (!store.isVanillaEnabled(id)) suppressVanillaAdvancement(player, id);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed during suppressAllDisabled for {}", player.getName().getString(), e);
            }
        }
    }

    // ═══════════════ 缓存管理 ═══════════════

    /**
     * [A1]+[A2]+[A3] 完全清除所有缓存。
     * 适用于模组完全重载或需要彻底清理的场景。
     */
    public static void clearCache() {
        idMapping.clear();
        reverseIdMapping.clear();
        parsedHolderCache.clear();
        lastSyncedAdvIds = new HashSet<>();
    }

    /**
     * [A2] 仅清除自定义成就相关缓存，保留原版Holder缓存。
     * 用于 Mixin reload 场景：原版成就来自资源包，
     * 在两次 reload 之间通常不变，保留缓存可避免重复 Codec 解析。
     *
     * <p>注意：如果原版资源包内容发生变化（如添加了新的原版成就），
     * 应调用 {@link #clearCache()} 完全清除。
     */
    public static void clearCustomCache() {
        idMapping.clear();
        reverseIdMapping.clear();
        // 保留 parsedHolderCache —— 原版Holder在reload间通常不变
    }

    // ═══════════════ Runtime injection ═══════════════

    /**
     * [A2]+[A3] 同步所有自定义成就到运行时Map。
     * 使用Holder缓存避免重复Codec解析，使用增量同步减少网络带宽。
     */
    public static void syncAllRuntime(MinecraftServer server) {
        Map<ResourceLocation, AdvancementHolder> map = AdvancementMapHolder.runtimeMap;
        if (map == null) {
            LOGGER.warn("Runtime map unavailable, cannot sync");
            return;
        }

        ServerDataStore store = ServerDataStore.getInstance();
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);

        // 1. 移除所有现有自定义成就
        map.keySet().removeIf(AdvancementRegistry::isCustomAdvancement);

        // 2. 重新注入自定义成就
        int customCount = 0;
        for (DataStore.CustomAdvancement adv : store.getAdvancements().values()) {
            ResourceLocation vanillaId = toVanillaId(adv.getId());
            JsonObject json = buildAdvancementJson(adv);
            AdvancementHolder holder = parseHolder(ops, vanillaId, json);
            if (holder != null) {
                map.put(vanillaId, holder);
                customCount++;
            }
        }

        // 3. 处理原版：重新注入已启用的，移除已禁用的
        int vanillaReinjected = 0;
        Map<String, JsonElement> vanillaCache = store.getVanillaAdvRawCache();
        if (vanillaCache != null) {
            for (var entry : vanillaCache.entrySet()) {
                ResourceLocation rl = ResourceLocation.tryParse(entry.getKey());
                if (rl == null) continue;

                if (store.isVanillaEnabled(entry.getKey())) {
                    if (!map.containsKey(rl) && entry.getValue().isJsonObject()) {
                        AdvancementHolder holder = parseHolder(ops, rl, entry.getValue().getAsJsonObject());
                        if (holder != null) {
                            map.put(rl, holder);
                            vanillaReinjected++;
                        }
                    }
                } else {
                    map.remove(rl);
                }
            }
        }

        LOGGER.info("syncAllRuntime: {} custom + {} vanilla advancements, map total: {}",
                customCount, vanillaReinjected, map.size());

        // [A3] 计算增量差集
        Set<ResourceLocation> currentAdvIds = Set.copyOf(map.keySet());
        Set<ResourceLocation> previousAdvIds = lastSyncedAdvIds;

        Set<ResourceLocation> addedIds = new HashSet<>(currentAdvIds);
        addedIds.removeAll(previousAdvIds);
        Set<ResourceLocation> removedIds = new HashSet<>(previousAdvIds);
        removedIds.removeAll(currentAdvIds);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncToVanilla(player);
            suppressAllDisabled(player);
            // [A3] 增量同步：仅发送变更的成就
            resyncVanillaAdvancements(player, addedIds, removedIds, map);
        }

        lastSyncedAdvIds = currentAdvIds;
    }

    /**
     * [A3] 增量同步：仅向客户端发送新增和移除的成就，而非全量重置。
     * 对大型整合包（数千成就）可显著减少网络带宽。
     *
     * <p>注意：自定义成就的内容变更（如修改名称/图标）不会触发此方法，
     * 内容变更由 SyncPayload（全量数据同步）负责。
     */
    private static void resyncVanillaAdvancements(ServerPlayer player,
                                                  Set<ResourceLocation> addedIds, Set<ResourceLocation> removedIds,
                                                  Map<ResourceLocation, AdvancementHolder> map) {
        try {
            if (addedIds.isEmpty() && removedIds.isEmpty()) return;

            var toAdd = new ArrayList<AdvancementHolder>();
            for (var id : addedIds) {
                var holder = map.get(id);
                if (holder != null) toAdd.add(holder);
            }

            if (!toAdd.isEmpty() || !removedIds.isEmpty()) {
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket(
                        false, toAdd, removedIds, Map.of()));
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to resync vanilla advancements to {}: {}",
                    player.getName().getString(), e.getMessage());
        }
    }

    /**
     * [A2] 解析成就JSON为AdvancementHolder，带缓存。
     * 仅缓存原版成就Holder（自定义成就可能在编辑后变化）。
     */
    private static AdvancementHolder parseHolder(
            RegistryOps<JsonElement> ops,
            ResourceLocation id, JsonObject json) {
        // [A2] 仅对原版成就使用缓存（自定义成就的内容可能变化）
        boolean isVanilla = !ModInfo.MOD_ID.equals(id.getNamespace());
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