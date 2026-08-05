package com.example.advancementoverhaul.compat.engine;

import com.example.advancementoverhaul.compat.AdvancementMapHolder;
import com.example.advancementoverhaul.compat.ftb.FtbQuestsBridge;
import com.example.advancementoverhaul.data.ServerDataStore;
import com.example.advancementoverhaul.data.model.CustomAdvancement;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 原版进度同步服务。
 * <p>
 * 负责自定义进度与原版 AdvancementManager 之间的数据同步，
 * 包括进度的授予/撤销、全量/增量运行时同步、以及网络包推送。
 *
 * <h2>防抖机制</h2>
 * 使用代数计数法避免短时间内多次调用导致的全量重建重复执行。
 * N 次快速连续调用 = 1 次实际执行。
 */
public final class VanillaSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/Sync");

    /** 上一次同步的快照（用于计算增量差集） */
    private static volatile Set<ResourceLocation> lastSyncedAdvIds = new HashSet<>();

    /** 同步去重防抖代数计数器 */
    private static final AtomicInteger pendingSyncGeneration = new AtomicInteger(0);

    /** 已执行的最新同步代数 */
    private static volatile int lastExecutedSyncGeneration = 0;

    private VanillaSyncService() {}

    // ═══════════════ 进度的授予/撤销 ═══════════════

    /** 授予自定义进度（通过原版 AdvancementManager） */
    public static void grantAdvancement(ServerPlayer player, String customId) {
        ResourceLocation vanillaId = AdvancementIdMapper.toVanillaId(customId);
        AdvancementHolder holder = player.server.getAdvancements().get(vanillaId);
        if (holder == null) {
            LOGGER.warn("Cannot grant - not found: {} (for {})", vanillaId, customId);
            return;
        }
        player.getAdvancements().award(holder, "trigger");
        player.getAdvancements().flushDirty(player);
    }

    /** 撤销自定义进度 */
    public static void revokeAdvancement(ServerPlayer player, String customId) {
        ResourceLocation vanillaId = AdvancementIdMapper.toVanillaId(customId);
        AdvancementHolder holder = player.server.getAdvancements().get(vanillaId);
        if (holder == null) return;
        player.getAdvancements().revoke(holder, "trigger");
        player.getAdvancements().flushDirty(player);
    }

    /**
     * 将玩家的完成状态同步到原版系统。
     * 遍历所有自定义进度：已完成 → award，未完成 → revoke。
     */
    public static void syncToVanilla(ServerPlayer player) {
        ServerDataStore store = ServerDataStore.getInstance();
        var completions = store.getPlayerCompletions(player.getUUID());
        int granted = 0, revoked = 0;
        for (var entry : store.getAdvancements().entrySet()) {
            String customId = entry.getKey();
            boolean completed = Boolean.TRUE.equals(completions.get(customId));
            ResourceLocation vanillaId = AdvancementIdMapper.toVanillaId(customId);
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
        }
    }

    // ═══════════════ 增量 Runtime 更新（FTB Quests 实时兼容） ═══════════════

    /**
     * 增量更新单个自定义进度到 runtime Map。
     * 相比全量重建，仅操作单个进度，并向所有在线玩家增量推送变更。
     */
    public static void updateAdvancementInRuntime(MinecraftServer server, String customId) {
        Map<ResourceLocation, AdvancementHolder> map = AdvancementMapHolder.runtimeMap;
        if (map == null) {
            LOGGER.warn("Runtime map unavailable, cannot update advancement {}", customId);
            return;
        }

        ServerDataStore store = ServerDataStore.getInstance();
        CustomAdvancement adv = store.getAdvancement(customId);
        if (adv == null) {
            LOGGER.warn("Advancement '{}' not found in store, cannot update runtime", customId);
            return;
        }

        ResourceLocation vanillaId = AdvancementIdMapper.toVanillaId(customId);
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        JsonObject json = AdvancementInjector.buildAdvancementJson(adv);
        AdvancementHolder newHolder = AdvancementInjector.parseHolder(ops, vanillaId, json);

        if (newHolder != null) {
            map.put(vanillaId, newHolder);
            LOGGER.debug("Incrementally updated advancement in runtime: {}", customId);
            sendAdvancementUpdateToAll(server, vanillaId, newHolder);
            FtbQuestsBridge.notifyAttributeChange(server);
        }
    }

    /**
     * 从 runtime Map 中移除单个自定义进度。
     */
    public static void removeAdvancementFromRuntime(MinecraftServer server, String customId) {
        Map<ResourceLocation, AdvancementHolder> map = AdvancementMapHolder.runtimeMap;
        if (map == null) return;

        ResourceLocation vanillaId = AdvancementIdMapper.toVanillaId(customId);
        if (map.remove(vanillaId) != null) {
            LOGGER.debug("Incrementally removed advancement from runtime: {}", customId);

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                try {
                    player.connection.send(
                            new ClientboundUpdateAdvancementsPacket(
                                    false, List.of(), Set.of(vanillaId), Map.of()));
                } catch (Exception e) {
                    LOGGER.warn("Failed to send removal packet to {}: {}",
                            player.getName().getString(), e.getMessage());
                }
            }

            FtbQuestsBridge.notifyAttributeChange(server);
        }
    }

    /** 向所有在线玩家发送单个进度的更新包。 */
    private static void sendAdvancementUpdateToAll(MinecraftServer server,
                                                    ResourceLocation vanillaId,
                                                    AdvancementHolder holder) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                player.connection.send(
                        new ClientboundUpdateAdvancementsPacket(
                                false, List.of(holder), Set.of(), Map.of()));
            } catch (Exception e) {
                LOGGER.warn("Failed to send advancement update to {}: {}",
                        player.getName().getString(), e.getMessage());
            }
        }
    }

    // ═══════════════ 全量运行时同步 ═══════════════

    /** 全量同步（默认通知 FTB Quests）。 */
    public static void syncAllRuntime(MinecraftServer server) {
        syncAllRuntime(server, true);
    }

    /**
     * 全量同步 runtime Map，可选择是否通知 FTB Quests。
     * 使用代数防抖：多次快速连续调用只执行最后一次。
     */
    public static void syncAllRuntime(MinecraftServer server, boolean notifyFtb) {
        int generation = pendingSyncGeneration.incrementAndGet();
        server.execute(() -> {
            if (generation < pendingSyncGeneration.get()) {
                LOGGER.debug("Skipping superseded syncAllRuntime (gen {} < current {})",
                        generation, pendingSyncGeneration.get());
                return;
            }
            if (generation <= lastExecutedSyncGeneration) {
                LOGGER.debug("Skipping already-executed syncAllRuntime (gen {} <= last {})",
                        generation, lastExecutedSyncGeneration);
                return;
            }
            lastExecutedSyncGeneration = generation;
            doSyncAllRuntime(server, notifyFtb);
        });
    }

    /**
     * 实际执行全量同步。
     */
    private static void doSyncAllRuntime(MinecraftServer server, boolean notifyFtb) {
        Map<ResourceLocation, AdvancementHolder> map = AdvancementMapHolder.runtimeMap;
        if (map == null) {
            LOGGER.warn("Runtime map unavailable, cannot sync");
            return;
        }

        ServerDataStore store = ServerDataStore.getInstance();
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);

        // 1. 移除所有现有自定义成就
        map.keySet().removeIf(AdvancementIdMapper::isCustomAdvancement);

        // 2. 重新注入自定义成就
        int customCount = 0;
        for (CustomAdvancement adv : store.getAdvancements().values()) {
            ResourceLocation vanillaId = AdvancementIdMapper.toVanillaId(adv.getId());
            JsonObject json = AdvancementInjector.buildAdvancementJson(adv);
            AdvancementHolder holder = AdvancementInjector.parseHolder(ops, vanillaId, json);
            if (holder != null) {
                map.put(vanillaId, holder);
                customCount++;
            } else {
                LOGGER.warn("Failed to parse custom advancement '{}' for runtime injection", adv.getId());
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
                        AdvancementHolder holder = AdvancementInjector.parseHolder(ops, rl,
                                entry.getValue().getAsJsonObject());
                        if (holder != null) {
                            map.put(rl, holder);
                            vanillaReinjected++;
                        } else {
                            LOGGER.warn("Failed to parse vanilla advancement '{}' for runtime injection", entry.getKey());
                        }
                    }
                } else {
                    map.remove(rl);
                }
            }
        }

        LOGGER.info("syncAllRuntime: {} custom + {} vanilla, map total: {}",
                customCount, vanillaReinjected, map.size());

        // 4. 计算增量差集
        Set<ResourceLocation> currentAdvIds = Set.copyOf(map.keySet());
        Set<ResourceLocation> previousAdvIds = lastSyncedAdvIds;

        Set<ResourceLocation> addedIds = new HashSet<>(currentAdvIds);
        addedIds.removeAll(previousAdvIds);
        Set<ResourceLocation> removedIds = new HashSet<>(previousAdvIds);
        removedIds.removeAll(currentAdvIds);

        // 5. 对每个在线玩家：撤销禁用 → 同步 → 增量推送
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            VanillaSuppressor.suppressAllDisabled(player);
            syncToVanilla(player);
            resyncVanillaAdvancements(player, addedIds, removedIds, map);
        }

        lastSyncedAdvIds = currentAdvIds;

        // 通知 FTB Quests
        if (notifyFtb) {
            FtbQuestsBridge.notifyAttributeChange(server);
        }
        FtbQuestsBridge.syncToKnownServerRegistries(server);
    }

    /**
     * 增量同步：仅向客户端发送新增和移除的成就。
     */
    private static void resyncVanillaAdvancements(ServerPlayer player,
                                                  Set<ResourceLocation> addedIds,
                                                  Set<ResourceLocation> removedIds,
                                                  Map<ResourceLocation, AdvancementHolder> map) {
        try {
            if (addedIds.isEmpty() && removedIds.isEmpty()) return;

            var toAdd = new ArrayList<AdvancementHolder>();
            for (ResourceLocation id : addedIds) {
                AdvancementHolder holder = map.get(id);
                if (holder != null) toAdd.add(holder);
            }

            if (!toAdd.isEmpty() || !removedIds.isEmpty()) {
                player.connection.send(
                        new ClientboundUpdateAdvancementsPacket(
                                false, toAdd, removedIds, Map.of()));
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to resync vanilla advancements to {}: {}",
                    player.getName().getString(), e.getMessage());
        }
    }
}
