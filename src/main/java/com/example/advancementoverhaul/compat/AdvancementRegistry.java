package com.example.advancementoverhaul.compat;

import com.example.advancementoverhaul.Config;
import com.example.advancementoverhaul.ModInfo;
import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.DataStore.*;
import com.example.advancementoverhaul.data.model.CustomAdvancement;
import com.example.advancementoverhaul.compat.ftb.FtbQuestsBridge;
import com.example.advancementoverhaul.data.ServerDataStore;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义进度 ←→ 原版进度系统的核心适配器。
 * <p>
 * <b>定位说明：</b>虽然文件位于 {@code compat/} 包下，但此类实际承担的是模组核心引擎职责。
 * 它是自定义进度系统与 Minecraft 原版进度系统之间的唯一桥梁，负责：
 * <ul>
 *   <li>将自定义进度 JSON 注入原版 AdvancementManager 的数据加载流程</li>
 *   <li>管理自定义 ID ↔ 原版 ResourceLocation 的双向映射</li>
 *   <li>同步自定义进度的完成/撤销到原版系统（用于 FTB Quests 等兼容）</li>
 *   <li>运行时增量更新进度到 Advancement Map（FTB Quests 实时兼容）</li>
 *   <li>撤销禁用的原版进度</li>
 * </ul>
 *
 * <h2>ID 映射</h2>
 * 自定义进度 ID（如 "my_custom_adv"）通过 {@link #toVanillaId} 转换为
 * 原版 ResourceLocation（如 "advancementoverhaul:custom/my_custom_adv"）。
 * 双向映射（{@link #idMapping} + {@link #reverseIdMapping}）保证 O(1) 查找。
 *
 * <h2>缓存策略</h2>
 * {@link #parsedHolderCache} 缓存原版进度的解析结果，
 * {@link #clearCustomCache()} 仅清除自定义成就（保留原版缓存）。
 */
public final class AdvancementRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/Registry");

    /** 原版 ResourceLocation 路径前缀（自定义进度专用） */
    private static final String PREFIX = "custom/";

    /** 自定义 ID → 原版 ResourceLocation */
    private static final Map<String, ResourceLocation> idMapping = new ConcurrentHashMap<>();

    /** 原版 ResourceLocation → 自定义 ID（O(1) 反向查找） */
    private static final Map<ResourceLocation, String> reverseIdMapping = new ConcurrentHashMap<>();

    /** 已解析的原版 AchievementHolder 缓存（避免重复 Codec 解析） */
    private static final Map<ResourceLocation, AdvancementHolder> parsedHolderCache =
            new ConcurrentHashMap<>();

    /** 上一次同步的快照（用于计算增量差集） */
    private static volatile Set<ResourceLocation> lastSyncedAdvIds = new HashSet<>();

    /**
     * 同步去重防抖：使用代数计数法避免短时间内多次调用导致的全量重建重复执行。
     * <p>
     * 每次 syncAllRuntime 递增代数并提交到 server.execute()。
     * 当实际执行时，若当前代数已被后续调用超越，则跳过本次执行。
     * 由此实现：N 次快速连续调用 = 1 次实际执行。
     */
    private static final java.util.concurrent.atomic.AtomicInteger pendingSyncGeneration =
            new java.util.concurrent.atomic.AtomicInteger(0);

    /** 已执行的最新同步代数 */
    private static volatile int lastExecutedSyncGeneration = 0;

    private AdvancementRegistry() {}

    // ═══════════════ FTB Quests 兼容层（委托给 FtbQuestsBridge） ═══════════════

    // ═══════════════ ID 映射 ═══════════════

    /**
     * 自定义 ID → 原版 ResourceLocation。
     * <p>
     * 转换规则：
     * <ol>
     *   <li>替换 {@code :} → {@code _}</li>
     *   <li>移除非法字符，保留 {@code [a-z0-9_/-]}</li>
     *   <li>去除首尾下划线</li>
     *   <li>加上 {@code advancementoverhaul:custom/} 前缀</li>
     * </ol>
     */
    public static ResourceLocation toVanillaId(String customId) {
        return idMapping.computeIfAbsent(customId, id -> {
            String safe = id.replace(':', '_')
                    .replaceAll("[^a-z0-9_/\\-]", "_")
                    .toLowerCase();
            while (safe.startsWith("_")) safe = safe.substring(1);
            while (safe.endsWith("_")) safe = safe.substring(0, safe.length() - 1);
            if (safe.isEmpty()) safe = "unknown_" + Math.abs(id.hashCode());
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(ModInfo.MOD_ID, PREFIX + safe);
            reverseIdMapping.put(rl, id);
            return rl;
        });
    }

    /** 判断 ResourceLocation 是否为本模组注入的自定义进度 */
    public static boolean isCustomAdvancement(ResourceLocation id) {
        return ModInfo.MOD_ID.equals(id.getNamespace()) && id.getPath().startsWith(PREFIX);
    }

    /**
     * 逆映射：原版 ResourceLocation → 自定义进度 ID。
     * <p>
     * 用于外部系统（如 FTB Quests AdvancementReward）通过原版 award()
     * 授予自定义进度后，将完成状态同步回自定义系统的场景。
     *
     * @param vanillaId 原版 ResourceLocation（如 {@code advancementoverhaul:custom/my_adv}）
     * @return 原始自定义 ID（如 {@code my_adv}），未找到则返回 null
     */
    public static String getCustomIdFromVanilla(ResourceLocation vanillaId) {
        return reverseIdMapping.get(vanillaId);
    }

    // ═══════════════ 进度注入 ═══════════════

    /**
     * 将自定义进度 JSON 注入数据加载 Map。
     * <p>
     * 使用 {@code minecraft:impossible} 触发器（所有自定义进度通过条件系统判断完成，
     * 而非原版触发器机制）。
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
            ResourceLocation vanillaId = toVanillaId(adv.getId());
            if (!data.containsKey(vanillaId)) {
                data.put(vanillaId, buildAdvancementJson(adv));
                count++;
            }
        }
        LOGGER.info("Total injected: {} custom advancements", count);
    }

    /**
     * 从自定义进度构建原版 JSON。
     * 使用 {@code minecraft:impossible} 触发器 + task 框架 + 默认石头背景。
     */
    private static JsonObject buildAdvancementJson(CustomAdvancement adv) {
        JsonObject root = new JsonObject();

        // display
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
        // 禁用原版 Toast 和聊天公告——改用自定义牌匾 UI 和紫水晶音效
        display.addProperty("show_toast", false);
        display.addProperty("announce_to_chat", false);
        display.addProperty("hidden", adv.isHidden());
        root.add("display", display);

        // criteria（impossible 触发器）
        JsonObject criteria = new JsonObject();
        JsonObject trigger = new JsonObject();
        trigger.addProperty("trigger", "minecraft:impossible");
        criteria.add("trigger", trigger);
        root.add("criteria", criteria);

        // requirements
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

    // ═══════════════ 原版进度操作 ═══════════════

    /** 授予自定义进度（通过原版 AdvancementManager） */
    public static void grantAdvancement(ServerPlayer player, String customId) {
        ResourceLocation vanillaId = toVanillaId(customId);
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
        ResourceLocation vanillaId = toVanillaId(customId);
        AdvancementHolder holder = player.server.getAdvancements().get(vanillaId);
        if (holder == null) return;
        player.getAdvancements().revoke(holder, "trigger");
        player.getAdvancements().flushDirty(player);
    }

    /**
     * 将玩家的完成状态同步到原版系统。
     * <p>
     * 遍历所有自定义进度：已完成 → award，未完成 → revoke。
     */
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
        }
    }

    /**
     * 撤销单个禁用的原版进度。
     */
    public static void suppressVanillaAdvancement(ServerPlayer player, String advId) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(advId);
            if (rl == null) return;
            AdvancementHolder holder = player.server.getAdvancements().get(rl);
            if (holder == null) return;
            var progress = player.getAdvancements().getOrStartProgress(holder);
            if (progress.isDone()) {
                for (String criterion : progress.getCompletedCriteria()) {
                    player.getAdvancements().revoke(holder, criterion);
                }
                player.getAdvancements().flushDirty(player);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to suppress vanilla advancement {}: {}", advId, e.getMessage());
        }
    }

    /**
     * 撤销玩家所有禁用的原版进度。
     * <p>
     * 先撤销明确禁用的，再在默认禁用模式下撤销所有未启用的。
     */
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
                LOGGER.warn("Failed during suppressAllDisabled for {}",
                        player.getName().getString(), e);
            }
        }
    }

    // ═══════════════ 缓存管理 ═══════════════

    /**
     * 仅清除自定义成就相关缓存，保留原版 Holder 缓存。
     * 用于 Mixin reload 场景（原版成就通常不变）。
     */
    public static void clearCustomCache() {
        idMapping.clear();
        reverseIdMapping.clear();
    }

    // ═══════════════ 增量 Runtime 更新（FTB Quests 实时兼容） ═══════════════

    /**
     * 增量更新单个自定义进度到 runtime Map。
     * <p>
     * 相比 {@link #syncAllRuntime} 的全量重建，此方法仅操作单个进度：
     * <ol>
     *   <li>重新构建原版 JSON</li>
     *   <li>解析为新的 AdvancementHolder</li>
     *   <li>替换 runtime Map 中的对应条目</li>
     *   <li>向所有在线玩家增量推送变更</li>
     *   <li>通知 FTB Quests 刷新缓存</li>
     * </ol>
     * <p>
     * FTB Quests 缓存了 AdvancementHolder 引用，增量更新（替换原 key 而非删除再添加）
     * 可避免 FTB Quests 的旧引用失效。
     *
     * @param server   服务端实例
     * @param customId 自定义进度 ID
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

        ResourceLocation vanillaId = toVanillaId(customId);
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        JsonObject json = buildAdvancementJson(adv);
        AdvancementHolder newHolder = parseHolder(ops, vanillaId, json);

        if (newHolder != null) {
            // 原地替换：保持 ResourceLocation key 不变，仅换 AdvancementHolder 值
            // FTB Quests 通过 ResourceLocation 查询，key 不变则引用不失效
            map.put(vanillaId, newHolder);
            LOGGER.debug("Incrementally updated advancement in runtime: {}", customId);

            // 向所有在线玩家推送更新
            sendAdvancementUpdateToAll(server, vanillaId, newHolder);

            // 通知 FTB Quests（属性变更）
            FtbQuestsBridge.notifyAttributeChange(server);
        }
    }

    /**
     * 从 runtime Map 中移除单个自定义进度。
     * <p>
     * 调用此方法而非 {@link #syncAllRuntime} 可避免重新 Codec 解析所有进度。
     *
     * @param server   服务端实例
     * @param customId 自定义进度 ID
     */
    public static void removeAdvancementFromRuntime(MinecraftServer server, String customId) {
        Map<ResourceLocation, AdvancementHolder> map = AdvancementMapHolder.runtimeMap;
        if (map == null) return;

        ResourceLocation vanillaId = toVanillaId(customId);
        if (map.remove(vanillaId) != null) {
            LOGGER.debug("Incrementally removed advancement from runtime: {}", customId);

            // 向所有在线玩家推送移除
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                try {
                    player.connection.send(
                            new net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket(
                                    false, List.of(), Set.of(vanillaId), Map.of()));
                } catch (Exception e) {
                    LOGGER.warn("Failed to send removal packet to {}: {}",
                            player.getName().getString(), e.getMessage());
                }
            }

            // 通知 FTB Quests（删除 = 属性变更）
            FtbQuestsBridge.notifyAttributeChange(server);
        }
    }

    /**
     * 向所有在线玩家发送单个进度的更新包。
     * 使用 ClientboundUpdateAdvancementsPacket 的 add=true 模式。
     */
    private static void sendAdvancementUpdateToAll(MinecraftServer server,
                                                    ResourceLocation vanillaId,
                                                    AdvancementHolder holder) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                player.connection.send(
                        new net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket(
                                false, List.of(holder), Set.of(), Map.of()));
            } catch (Exception e) {
                LOGGER.warn("Failed to send advancement update to {}: {}",
                        player.getName().getString(), e.getMessage());
            }
        }
    }

    // ═══════════════ 运行时注入 ═══════════════

    /**
     * 同步所有成就到运行时 Map。
     * <p>
     * 步骤：
     * <ol>
     *   <li>移除所有现有自定义成就</li>
     *   <li>重新注入自定义 + 已启用的原版成就</li>
     *   <li>对每个在线玩家：撤销禁用 → 同步完成状态 → 增量发送变更</li>
     * </ol>
     */
    /**
     * 全量同步 runtime Map（触发 FTB Quests 属性同步）。
     * <p>
     * 等同于 {@code syncAllRuntime(server, true)}。
     */
    public static void syncAllRuntime(MinecraftServer server) {
        syncAllRuntime(server, true);
    }

    /**
     * 全量同步 runtime Map，可选择是否通知 FTB Quests。
     *
     * <p>使用代数防抖：多次快速连续调用只执行最后一次，避免冗余全量重建。
     *
     * @param server    服务端实例
     * @param notifyFtb 是否触发 FTB Quests KnownServerRegistries 同步。
     *                  对位置/分类变更应传 {@code false}
     */
    public static void syncAllRuntime(MinecraftServer server, boolean notifyFtb) {
        int generation = pendingSyncGeneration.incrementAndGet();
        server.execute(() -> {
            // 若已有更新的同步请求，跳过本次
            if (generation < pendingSyncGeneration.get()) {
                LOGGER.debug("Skipping superseded syncAllRuntime (gen {} < current {})",
                        generation, pendingSyncGeneration.get());
                return;
            }
            // 防止同一代重复执行
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
     * 实际执行全量同步（由 {@link #syncAllRuntime(MinecraftServer, boolean)} 的防抖机制调用）。
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
        map.keySet().removeIf(AdvancementRegistry::isCustomAdvancement);

        // 2. 重新注入自定义成就
        int customCount = 0;
        for (CustomAdvancement adv : store.getAdvancements().values()) {
            ResourceLocation vanillaId = toVanillaId(adv.getId());
            JsonObject json = buildAdvancementJson(adv);
            AdvancementHolder holder = parseHolder(ops, vanillaId, json);
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
                        AdvancementHolder holder = parseHolder(ops, rl,
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
            suppressAllDisabled(player);
            syncToVanilla(player);
            resyncVanillaAdvancements(player, addedIds, removedIds, map);
        }

        lastSyncedAdvIds = currentAdvIds;

        // 通知 FTB Quests（仅属性变更时）
        if (notifyFtb) {
            FtbQuestsBridge.notifyAttributeChange(server);
        }
        // 同步自定义进度到 FTB KnownServerRegistries（修复 NPE 崩溃 + 启用任务完成监听）
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
                        new net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket(
                                false, toAdd, removedIds, Map.of()));
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to resync vanilla advancements to {}: {}",
                    player.getName().getString(), e.getMessage());
        }
    }

    /**
     * 解析成就 JSON 为 AdvancementHolder，带缓存。
     * 仅缓存原版成就（自定义成就可能在编辑后变化）。
     */
    private static AdvancementHolder parseHolder(RegistryOps<JsonElement> ops,
                                                  ResourceLocation id, JsonObject json) {
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
