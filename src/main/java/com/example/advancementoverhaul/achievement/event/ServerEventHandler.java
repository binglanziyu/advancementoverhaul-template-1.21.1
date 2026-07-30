package com.example.advancementoverhaul.achievement.event;

import com.example.advancementoverhaul.LangKeys;
import com.example.advancementoverhaul.ModInfo;
import com.example.advancementoverhaul.compat.AdvancementRegistry;
import com.example.advancementoverhaul.compat.ftb.FtbQuestsBridge;
import com.example.advancementoverhaul.data.*;
import com.example.advancementoverhaul.data.DataStore.ConditionType;
import com.example.advancementoverhaul.data.model.CustomAdvancement;
import com.example.advancementoverhaul.logic.ConditionEvaluator;
import com.example.advancementoverhaul.network.SyncManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端事件处理器。
 * <p>
 * 将 Minecraft 游戏事件映射到 {@link ConditionEvaluator} 的条件类型评估。
 * 同时处理维度锁定检查、原版进度拦截和玩家登录/登出同步。
 *
 * <h2>映射关系</h2>
 * <pre>
 * 游戏事件                          → 条件类型          → 评估模式
 * LivingDeathEvent                  → KILL_ENTITY      → checkInstant
 * ItemCraftedEvent                  → CRAFT_ITEM       → checkWithStack
 * BlockEvent.BreakEvent             → BREAK_BLOCK      → checkProgress
 * BlockEvent.EntityPlaceEvent       → PLACE_BLOCK      → checkProgress
 * EntityTravelToDimensionEvent       → 维度锁定检查（传送前取消）
 * PlayerChangedDimensionEvent       → CHANGE_DIMENSION → checkInstant
 * LivingDamageEvent (source)        → DEAL_DAMAGE      → checkProgress
 * LivingDamageEvent (target)        → TAKE_DAMAGE      → checkProgress
 * ItemFishedEvent                   → FISH_ITEM        → checkWithStack
 * InventoryMixin (add)              → GET_ITEM         → checkWithStack
 * </pre>
 */
public class ServerEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerEventHandler.class);

    /** 维度锁定冷却时间表（防止同一 tick 内多次弹出消息） */
    private static final ConcurrentHashMap<UUID, Long> lastDimLockTime = new ConcurrentHashMap<>();

    private static final long DIM_LOCK_COOLDOWN_MS = 1000;

    // ═══════════════ 登录 / 登出 / 服务端 ═══════════════

    /**
     * 玩家登录：撤销禁用的原版进度 → 同步自定义进度 → 发送全量数据。
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        suppressVanillaAdvancements(player);
        AdvancementRegistry.syncToVanilla(player);
        SyncManager.syncPlayer(player);
        // 玩家登录时 FTB Library 的 SyncKnownServerRegistriesPacket 会替换
        // 整个 KSR.client，需要重新注入自定义进度以防止 NPE 崩溃
        FtbQuestsBridge.syncToKnownServerRegistries(player.server);
    }

    /**
     * 服务端启动完成：设置 server 引用 → 同步 runtime。
     */
    @SubscribeEvent
    public static void onServerStarted(
            net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        ServerDataStore.getInstance().setServer(event.getServer());
        AdvancementRegistry.syncAllRuntime(event.getServer());
    }

    /**
     * 玩家登出：保存数据 → 清理维度锁冷却和标记。
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        ServerDataStore.getInstance().savePlayerDataIfDirty();
        lastDimLockTime.remove(uuid);
        // 清理 FTB Quests 任务完成缓存，防止内存泄漏
        FtbQuestsBridge.onPlayerLogout(uuid);
    }

    // ═══════════════ 服务端 Tick（定期保存） ═══════════════

    /**
     * 服务端 Tick 事件（替代 PlayerTick）。
     * 每 game tick 调用一次而非每玩家一次，避免 N 倍冗余。
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerDataStore store = ServerDataStore.getInstance();
        store.tick();
        // FTB Quests 任务完成轮询/事件监听
        var server = store.getServer();
        if (server != null) {
            FtbQuestsBridge.onServerTick(server);
            // 如果 KSR 之前未同步成功（ServerStartedEvent 时 KSR 尚未初始化），
            // 每 tick 重试一次直到成功，防止 AdvancementReward.fillConfigGroup NPE
            if (!FtbQuestsBridge.isKsrSynced()) {
                FtbQuestsBridge.syncToKnownServerRegistries(server);
            }
        }
    }

    // ═══════════════ 条件事件映射 ═══════════════

    /** 击杀实体 → KILL_ENTITY（instant） */
    @SubscribeEvent
    public static void onEntityKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        if (typeId == null) {
            LOGGER.debug("Skipping KILL_ENTITY check: unregistered entity type {}", event.getEntity().getType());
            return;
        }
        ConditionEvaluator.checkInstant(player, ConditionType.KILL_ENTITY, typeId.toString());
    }

    /** 合成物品 → CRAFT_ITEM（stack-aware） */
    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack crafted = event.getCrafting();
        if (crafted.isEmpty()) return;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(crafted.getItem());
        if (itemId == null) {
            LOGGER.debug("Skipping CRAFT_ITEM check: unregistered item {}", crafted.getItem());
            return;
        }
        ConditionEvaluator.checkWithStack(player, ConditionType.CRAFT_ITEM,
                itemId.toString(), crafted, crafted.getCount());
    }

    /** 破坏方块 → BREAK_BLOCK（progress） */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock());
        if (blockId == null) {
            LOGGER.debug("Skipping BREAK_BLOCK check: unregistered block {}", event.getState().getBlock());
            return;
        }
        ConditionEvaluator.checkProgress(player, ConditionType.BREAK_BLOCK, blockId.toString(), 1);
    }

    /** 放置方块 → PLACE_BLOCK（progress） */
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock());
        if (blockId == null) {
            LOGGER.debug("Skipping PLACE_BLOCK check: unregistered block {}", event.getState().getBlock());
            return;
        }
        ConditionEvaluator.checkProgress(player, ConditionType.PLACE_BLOCK, blockId.toString(), 1);
    }

    /**
     * 维度传送前检查 — 如果目标维度被锁定且玩家不满足解锁条件，
     * 取消传送并将玩家传送到当前维度的安全位置（优先重生点，其次传送门周围地面）。
     * <p>
     * 使用 {@link EntityTravelToDimensionEvent}（传送前触发）确保坐标来自源维度。
     */
    @SubscribeEvent
    public static void onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ResourceKey<Level> targetDim = event.getDimension();
        String dimIdStr = targetDim.location().toString();

        ServerDataStore store = ServerDataStore.getInstance();
        DimensionLock lock = store.getDimensionLock(dimIdStr);
        if (lock == null || !lock.isDisabled()) return;

        String reqAdv = lock.getUnlockAdvancementId();
        boolean allowed = reqAdv != null && !reqAdv.isEmpty()
                && store.isCompleted(player.getUUID(), reqAdv);
        if (allowed) return;

        // 冷却检查
        UUID playerUuid = player.getUUID();
        Long last = lastDimLockTime.get(playerUuid);
        long now = System.currentTimeMillis();
        if (last != null && now - last < DIM_LOCK_COOLDOWN_MS) return;
        lastDimLockTime.put(playerUuid, now);

        // 取消传送 — 玩家仍在源维度，坐标正确
        event.setCanceled(true);

        ServerLevel currentLevel = player.serverLevel();
        double tx = player.getX();
        double ty = player.getY();
        double tz = player.getZ();
        float tyRot = player.getYRot();
        float txRot = player.getXRot();

        // 1. 优先尝试重生点（床/重生锚）
        BlockPos respawnPos = player.getRespawnPosition();
        if (respawnPos != null && player.getRespawnDimension() == currentLevel.dimension()) {
            BlockState blockState = currentLevel.getBlockState(respawnPos);
            boolean validBed = blockState.getBlock() instanceof BedBlock;
            boolean validAnchor = blockState.getBlock() instanceof RespawnAnchorBlock
                    && blockState.getValue(RespawnAnchorBlock.CHARGE) > 0;
            if (validBed || validAnchor) {
                tx = respawnPos.getX() + 0.5;
                ty = respawnPos.getY() + 1.0;
                tz = respawnPos.getZ() + 0.5;
            } else {
                int groundY = findSafeGround(currentLevel, (int) tx, (int) tz, (int) ty);
                if (groundY != Integer.MIN_VALUE) ty = groundY;
            }
        } else {
            int groundY = findSafeGround(currentLevel, (int) tx, (int) tz, (int) ty);
            if (groundY != Integer.MIN_VALUE) ty = groundY;
        }

        // 传送到当前维度的安全位置
        player.teleportTo(currentLevel, tx, ty, tz, tyRot, txRot);

        // 通知玩家
        player.sendSystemMessage(Component.translatable(LangKeys.DIM_LOCKED_MSG));
        if (reqAdv != null && !reqAdv.isEmpty()) {
            CustomAdvancement req = store.getAdvancement(reqAdv);
            String name = req != null ? req.getName() : reqAdv;
            player.sendSystemMessage(Component.translatable(LangKeys.NEED_ADV_MSG, name));
        }
    }

    /**
     * 维度切换 → CHANGE_DIMENSION（instant）成就条件检查。
     * 仅处理维度切换的成就追踪，锁定检查已移至 {@link #onEntityTravelToDimension}。
     */
    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ConditionEvaluator.checkInstant(player, ConditionType.CHANGE_DIMENSION,
                event.getTo().location().toString());
    }

    /** 造成伤害 → DEAL_DAMAGE（progress，向上取整） */
    @SubscribeEvent
    public static void onDealDamage(LivingDamageEvent.Pre event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        float raw = event.getOriginalDamage();
        if (raw <= 0) return;
        int amount = Math.max(1, Math.round(raw));
        ConditionEvaluator.checkProgress(player, ConditionType.DEAL_DAMAGE, "", amount);
    }

    /** 受到伤害 → TAKE_DAMAGE（progress，向上取整） */
    @SubscribeEvent
    public static void onTakeDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        float raw = event.getOriginalDamage();
        if (raw <= 0) return;
        int amount = Math.max(1, Math.round(raw));
        ConditionEvaluator.checkProgress(player, ConditionType.TAKE_DAMAGE, "", amount);
    }

    /** 钓鱼 → FISH_ITEM（stack-aware，每个钓上来的物品独立检查） */
    @SubscribeEvent
    public static void onFishItem(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        for (ItemStack stack : event.getDrops()) {
            if (stack.isEmpty()) continue;
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId == null) {
                LOGGER.debug("Skipping FISH_ITEM check: unregistered item {}", stack.getItem());
                continue;
            }
            ConditionEvaluator.checkWithStack(player, ConditionType.FISH_ITEM,
                    itemId.toString(), stack, 1);
        }
    }

    // ═══════════════ Mixin 回调 ═══════════════

    /**
     * 由 {@link com.example.advancementoverhaul.mixin.InventoryMixin} 调用的回调。
     * 物品加入背包时触发 GET_ITEM 条件检查。
     */
    public static void onInventoryItemAdded(ServerPlayer player, String itemId,
                                            ItemStack addedStack, int count) {
        ConditionEvaluator.checkWithStack(player, ConditionType.GET_ITEM,
                itemId, addedStack, count);
    }

    // ═══════════════ 原版成就拦截 ═══════════════

    /**
     * 拦截原版成就获得事件。
     * <p>
     * 承担两类职责：
     * <ol>
     *   <li><b>外部来源完成的自定义成就同步</b> — FTB Quests 的
     *       AdvancementReward 通过原版 award() 授予我们的自定义成就时，
     *       AdvancementEarnEvent 会被触发但我们的 ServerDataStore 尚未感知。
     *       此处将完成状态同步回自定义系统，防止后续 syncToVanilla() 撤销成就。</li>
     *   <li><b>禁用原版成就撤销</b> — 如果原版/模组进度被禁用，撤销成就并刷新客户端。</li>
     * </ol>
     */
    @SubscribeEvent
    public static void onAdvancementEarn(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var holder = event.getAdvancement();
        String id = holder.id().toString();

        // ── 自定义成就：同步外部来源（如 FTB AdvancementReward）的完成 ──
        if (id.startsWith(ModInfo.MOD_ID + ":")) {
            // 逆映射：ResourceLocation → 自定义 ID
            String customId = AdvancementRegistry.getCustomIdFromVanilla(holder.id());
            if (customId != null) {
                ServerDataStore store = ServerDataStore.getInstance();
                UUID uuid = player.getUUID();
                if (!store.isCompleted(uuid, customId)) {
                    // 检查所有条件是否满足：如果条件已满足，正常完成；
                    // 如果条件未满足（如 FTB 奖励在任务未完成时就被领取），撤销授予并通知玩家
                    if (ConditionEvaluator.checkAllConditionsMet(uuid, customId)) {
                        ConditionEvaluator.tryComplete(player, customId);
                    } else {
                        player.getAdvancements().revoke(holder, "trigger");
                        player.getAdvancements().flushDirty(player);
                        player.sendSystemMessage(Component.translatable(LangKeys.CMD_ADV_CONDITIONS_NOT_MET));
                    }
                }
            }
            return;
        }

        // ── 原版/模组成就：撤销已禁用的 ──
        if (!ServerDataStore.getInstance().isVanillaEnabled(id)) {
            var progress = player.getAdvancements().getOrStartProgress(holder);
            if (progress.isDone()) {
                for (String criterion : progress.getCompletedCriteria()) {
                    player.getAdvancements().revoke(holder, criterion);
                }
                player.getAdvancements().flushDirty(player);
            }
        }
    }

    /**
     * 玩家登录时撤销所有禁用的原版进度。
     * 委托给 {@link AdvancementRegistry#suppressAllDisabled}。
     */
    private static void suppressVanillaAdvancements(ServerPlayer player) {
        AdvancementRegistry.suppressAllDisabled(player);
    }

    /**
     * 在 (x, z) 处从 startY 向上/下逐层搜索安全站立位置。
     * 安全位置定义为：下方为实心方块，上方 2 格均为空气。
     *
     * @return 玩家脚部 Y 坐标，或 {@link Integer#MIN_VALUE} 表示未找到
     */
    private static int findSafeGround(ServerLevel level, int x, int z, int startY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 2;
        startY = Math.max(minY, Math.min(maxY, startY));
        for (int dy = 0; dy < 64; dy++) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                int y = startY + dy * sign;
                if (y < minY || y >= maxY) continue;
                pos.set(x, y, z);
                BlockState ground = level.getBlockState(pos);
                if (!ground.isAir() && Block.isShapeFullBlock(ground.getCollisionShape(level, pos))
                        && level.getBlockState(pos.set(x, y + 1, z)).isAir()
                        && level.getBlockState(pos.set(x, y + 2, z)).isAir()) {
                    return y + 1;
                }
            }
        }
        return Integer.MIN_VALUE;
    }
}
