package com.dreamer.ao.achievement.event;

import com.dreamer.ao.LangKeys;
import com.dreamer.ao.ModInfo;
import com.dreamer.ao.ServerConstants;
import com.dreamer.ao.compat.AdvancementRegistry;
import com.dreamer.ao.compat.ftb.FtbQuestsBridge;
import com.dreamer.ao.data.*;
import com.dreamer.ao.data.ConditionType;
import com.dreamer.ao.logic.ConditionEvaluator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;

/**
 * 服务端事件处理器 — 事件注册入口。
 * <p>
 * 将 Minecraft 游戏事件映射到 {@link ConditionEvaluator} 的条件类型评估。
 * 同时处理维度锁定检查、原版进度拦截和玩家登录/登出同步。
 *
 * <h2>职责委托</h2>
 * 本类统一持有全部 {@code @SubscribeEvent} 方法（由
 * {@code NeoForge.EVENT_BUS.register(ServerEventHandler.class)} 注册），
 * 方法体委托给同包的专职子处理器，以降低单类复杂度：
 * <ul>
 *   <li>{@link LoginSyncHandler} — 登录/登出同步与延迟重试</li>
 *   <li>{@link DimensionLockHandler} — 维度锁拦截、遣返与冷却管理</li>
 *   <li>{@link ConditionEventMapper} — 注册表事件与伤害事件的映射模板</li>
 * </ul>
 * 注意：{@link #onInventoryItemAdded} 是 {@code InventoryMixin} 的回调入口，
 * 其签名与所在类必须保持不变。
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

    // ═══════════════ 登录 / 登出 / 服务端 ═══════════════

    /**
     * 玩家登录：撤销禁用的原版进度 → 同步自定义进度 → 发送全量数据。
     * <p>
     * 具体逻辑委托给 {@link LoginSyncHandler}。
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        LoginSyncHandler.onPlayerLogin(player);
        // 登录后应用当前阶段效果并同步
        com.dreamer.ao.phase.PhaseUnlockService.get().recomputeAndSync(player);
    }

    /**
     * 服务端启动完成：设置 server 引用 → 同步 runtime。
     */
    @SubscribeEvent
    public static void onServerStarted(
            net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        ServerDataStore.getInstance().setServer(event.getServer());
        AdvancementRegistry.syncAllRuntime(event.getServer());
        // 加载阶段定义
        com.dreamer.ao.phase.PhaseRegistry.get().load();
        // 提前注册阶段效果应用器（确保区块加载期生成的怪物也能套用 B 类效果）
        com.dreamer.ao.phase.PhaseEffectApplier.get();
    }

    /**
     * 玩家登出：保存数据 → 回收该玩家占用的各类缓存条目。
     * <p>
     * 具体逻辑委托给 {@link LoginSyncHandler}。
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LoginSyncHandler.onPlayerLogout(event.getEntity().getUUID());
    }

    // ═══════════════ 服务端 Tick（定期保存） ═══════════════

    /** KSR 同步退避计数器（tick），失败时指数增长避免每 tick 无脑重试 */
    private static int ksrRetryCooldown = 0;

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
            // 带指数退避重试，防止 AdvancementReward.fillConfigGroup NPE
            if (!FtbQuestsBridge.isKsrSynced() && --ksrRetryCooldown <= 0) {
                FtbQuestsBridge.syncToKnownServerRegistries(server);
                // 指数退避：20 → 60 → 140 → ... → 最多 1200 tick（60秒）
                ksrRetryCooldown = Math.min(
                        Math.max(ksrRetryCooldown * 2 + ServerConstants.KSR_RETRY_BACKOFF_BASE_TICKS,
                                ServerConstants.KSR_RETRY_BACKOFF_BASE_TICKS),
                        ServerConstants.KSR_RETRY_BACKOFF_MAX_TICKS);
            }

            long tickCount = server.getTickCount();

            // 周期性维护：合并到单个取模分支，避免每 tick 多次取模判断
            if (tickCount % ServerConstants.MAINTENANCE_INTERVAL_TICKS == 0) {
                runPeriodicMaintenance(tickCount);
            }

            // 重试因数据未就绪而延迟的登录同步（hasPending 短路，绝大多数 tick 零开销）
            if (LoginSyncHandler.hasPending() && store.getDataFolder() != null) {
                LoginSyncHandler.retryPending(server);
            }
        }
    }

    /**
     * 周期性维护任务：驱逐各类缓存表中的过期条目。
     * <p>
     * 由 {@link #onServerTick} 按 {@link ServerConstants#MAINTENANCE_INTERVAL_TICKS}
     * 间隔调用，将原先分散的多个取模判断合并到单次执行，降低每 tick 峰值开销。
     */
    private static void runPeriodicMaintenance(long tickCount) {
        DimensionLockHandler.pruneCooldowns();
        ConditionEvaluator.pruneEvaluatedKeys(tickCount);
    }

    // ═══════════════ 条件事件映射 ═══════════════

    /** 击杀实体 → KILL_ENTITY（instant） */
    @SubscribeEvent
    public static void onEntityKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        ConditionEventMapper.handleRegistryEvent(event.getEntity().getType(),
                BuiltInRegistries.ENTITY_TYPE::getKey,
                player, ConditionType.KILL_ENTITY,
                (p, id) -> ConditionEvaluator.checkInstant(p, ConditionType.KILL_ENTITY, id));
    }

    /** 合成物品 → CRAFT_ITEM（stack-aware） */
    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack crafted = event.getCrafting();
        if (crafted.isEmpty()) return;
        ConditionEventMapper.handleRegistryEvent(crafted.getItem(),
                BuiltInRegistries.ITEM::getKey,
                player, ConditionType.CRAFT_ITEM,
                (p, id) -> ConditionEvaluator.checkWithStack(p, ConditionType.CRAFT_ITEM, id, crafted, crafted.getCount()));
    }

    /** 破坏方块 → BREAK_BLOCK（progress） */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        ConditionEventMapper.handleRegistryEvent(event.getState().getBlock(),
                BuiltInRegistries.BLOCK::getKey,
                player, ConditionType.BREAK_BLOCK,
                (p, id) -> ConditionEvaluator.checkProgress(p, ConditionType.BREAK_BLOCK, id, 1));
    }

    /** 放置方块 → PLACE_BLOCK（progress） */
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ConditionEventMapper.handleRegistryEvent(event.getState().getBlock(),
                BuiltInRegistries.BLOCK::getKey,
                player, ConditionType.PLACE_BLOCK,
                (p, id) -> ConditionEvaluator.checkProgress(p, ConditionType.PLACE_BLOCK, id, 1));
    }

    /**
     * 维度传送前检查 — 如果目标维度被锁定且玩家不满足解锁条件，
     * 取消传送并将玩家传送到当前维度的安全位置（优先重生点，其次传送门周围地面）。
     * <p>
     * 使用 {@link EntityTravelToDimensionEvent}（传送前触发）确保坐标来自源维度。
     * <p>
     * 具体逻辑委托给 {@link DimensionLockHandler}，其中也说明了绕过场景的兜底方案与已知限制。
     */
    @SubscribeEvent
    public static void onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        DimensionLockHandler.onEntityTravelToDimension(event);
    }

    /**
     * 维度切换 → CHANGE_DIMENSION（instant）成就条件检查 + 维度锁兜底校验。
     * <p>
     * 主锁定检查在 {@link #onEntityTravelToDimension} 中完成；此处的事后校验用于
     * 缓解第三方 mod 直接调用传送 API 绕过前置事件的情形（详见
     * {@link DimensionLockHandler#verifyAfterDimensionChange}）。
     */
    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ConditionEvaluator.checkInstant(player, ConditionType.CHANGE_DIMENSION,
                event.getTo().location().toString());
        DimensionLockHandler.verifyAfterDimensionChange(player, event.getFrom());
        // 维度切换后重算阶段效果（维度层变化）
        com.dreamer.ao.phase.PhaseUnlockService.get().recomputeAndSync(player);
    }

    /** 造成伤害 → DEAL_DAMAGE（progress，向上取整） */
    @SubscribeEvent
    public static void onDealDamage(LivingDamageEvent.Pre event) {
        ConditionEventMapper.handleDamageEvent(event, true, ConditionType.DEAL_DAMAGE);
    }

    /** 受到伤害 → TAKE_DAMAGE（progress，向上取整） */
    @SubscribeEvent
    public static void onTakeDamage(LivingDamageEvent.Pre event) {
        ConditionEventMapper.handleDamageEvent(event, false, ConditionType.TAKE_DAMAGE);
    }

    /** 钓鱼 → FISH_ITEM（stack-aware，每个钓上来的物品独立检查） */
    @SubscribeEvent
    public static void onFishItem(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        for (ItemStack stack : event.getDrops()) {
            if (stack.isEmpty()) continue;
            ConditionEventMapper.handleRegistryEvent(stack.getItem(),
                    BuiltInRegistries.ITEM::getKey,
                    player, ConditionType.FISH_ITEM,
                    (p, id) -> ConditionEvaluator.checkWithStack(p, ConditionType.FISH_ITEM, id, stack, 1));
        }
    }

    // ═══════════════ Mixin 回调 ═══════════════

    /**
     * 由 {@link com.dreamer.ao.mixin.InventoryMixin} 调用的回调。
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

}
