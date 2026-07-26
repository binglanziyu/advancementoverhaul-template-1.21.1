package com.example.advancementoverhaul.event;
import com.example.advancementoverhaul.LangKeys;
import com.example.advancementoverhaul.ModInfo;
import com.example.advancementoverhaul.compat.AdvancementRegistry;
import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.DimensionLock;
import com.example.advancementoverhaul.data.ServerDataStore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Server-side event handler.
 * Maps game events → condition types → {@link ConditionEvaluator}.
 * Sync operations delegated to {@link SyncManager}.
 */
public class ServerEventHandler {

    private static final ConcurrentHashMap<UUID, Long> lastDimLockTime = new ConcurrentHashMap<>();
    private static final long DIM_LOCK_COOLDOWN_MS = 1000;

    // ═══════════════ LOGIN / LOGOUT / SERVER ═══════════════

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        suppressVanillaAdvancements(player);
        AdvancementRegistry.syncToVanilla(player);
        SyncManager.syncPlayer(player);
    }

    @SubscribeEvent
    public static void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        ServerDataStore.getInstance().setServer(event.getServer());
        AdvancementRegistry.syncAllRuntime(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        ServerDataStore.getInstance().savePlayerDataIfDirty();
        lastDimLockTime.remove(uuid);
    }

    // ═══════════════ SERVER TICK (周期性保存) ═══════════════

    /**
     * [A4] 使用服务端 Tick 事件替代 PlayerTick。
     * 每个游戏 tick 只调用一次，而非每个玩家一次。
     * 避免 N 个玩家时产生 N 倍冗余调用。
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerDataStore.getInstance().tick();
    }

    // ═══════════════ KILL_ENTITY (instant) ═══════════════

    @SubscribeEvent
    public static void onEntityKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
        ConditionEvaluator.checkInstant(player, DataStore.ConditionType.KILL_ENTITY, typeId.toString());
    }

    // ═══════════════ CRAFT_ITEM (stack-aware) ═══════════════

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack crafted = event.getCrafting();
        if (crafted.isEmpty()) return;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(crafted.getItem());
        ConditionEvaluator.checkWithStack(
                player, DataStore.ConditionType.CRAFT_ITEM,
                itemId.toString(), crafted, crafted.getCount());
    }

    // ═══════════════ BREAK_BLOCK (progress) ═══════════════

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock());
        ConditionEvaluator.checkProgress(
                player, DataStore.ConditionType.BREAK_BLOCK, blockId.toString(), 1);
    }

    // ═══════════════ PLACE_BLOCK (progress) ═══════════════

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock());
        ConditionEvaluator.checkProgress(
                player, DataStore.ConditionType.PLACE_BLOCK, blockId.toString(), 1);
    }

    // ═══════════════ CHANGE_DIMENSION (instant + lock) ═══════════════

    /**
     * [A5] 维度锁定检查移至条件触发之前。
     * 避免被锁定的玩家在被送回原维度前，错误触发 CHANGE_DIMENSION 成就。
     */
    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ResourceKey<Level> toKey = event.getTo();
        ResourceKey<Level> fromKey = event.getFrom();
        String dimIdStr = toKey.location().toString();

        // [A5] 先检查维度锁定，再触发成就条件
        ServerDataStore store = ServerDataStore.getInstance();
        DimensionLock lock = store.getDimensionLock(dimIdStr);
        if (lock != null && lock.isDisabled()) {
            String reqAdv = lock.getUnlockAdvancementId();
            boolean allowed = reqAdv != null && !reqAdv.isEmpty()
                    && store.isCompleted(player.getUUID(), reqAdv);
            if (!allowed) {
                Long last = lastDimLockTime.get(player.getUUID());
                long now = System.currentTimeMillis();
                if (last != null && now - last < DIM_LOCK_COOLDOWN_MS) return;
                lastDimLockTime.put(player.getUUID(), now);

                ServerLevel prev = player.server.getLevel(fromKey);
                if (prev != null)
                    player.teleportTo(prev, player.getX(), player.getY(), player.getZ(),
                            player.getYRot(), player.getXRot());
                player.sendSystemMessage(Component.translatable(LangKeys.DIM_LOCKED_MSG));
                if (reqAdv != null && !reqAdv.isEmpty()) {
                    DataStore.CustomAdvancement req = store.getAdvancement(reqAdv);
                    String name = req != null ? req.getName() : reqAdv;
                    player.sendSystemMessage(Component.translatable(LangKeys.NEED_ADV_MSG, name));
                }
                return; // [A5] 被锁定时不触发成就检查
            }
        }

        // 维度变化被允许，触发成就条件
        ConditionEvaluator.checkInstant(
                player, DataStore.ConditionType.CHANGE_DIMENSION, dimIdStr);
    }

    // ═══════════════ DEAL_DAMAGE (progress) ═══════════════

    @SubscribeEvent
    public static void onDealDamage(LivingDamageEvent.Pre event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        float raw = event.getOriginalDamage();
        if (raw <= 0) return;
        int amount = Math.max(1, Math.round(raw));
        ConditionEvaluator.checkProgress(
                player, DataStore.ConditionType.DEAL_DAMAGE, "", amount);
    }
    // ═══════════════ TAKE_DAMAGE (progress) ═══════════════

    @SubscribeEvent
    public static void onTakeDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        float raw = event.getOriginalDamage();
        if (raw <= 0) return;
        int amount = Math.max(1, Math.round(raw));
        ConditionEvaluator.checkProgress(
                player, DataStore.ConditionType.TAKE_DAMAGE, "", amount);
    }
    // ═══════════════ FISH_ITEM (stack-aware) ═══════════════

    @SubscribeEvent
    public static void onFishItem(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        for (ItemStack stack : event.getDrops()) {
            if (stack.isEmpty()) continue;
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            ConditionEvaluator.checkWithStack(
                    player, DataStore.ConditionType.FISH_ITEM,
                    itemId.toString(), stack, 1);
        }
    }

    // ═══════════════ GET_ITEM (Mixin callback, stack-aware) ═══════════════

    public static void onInventoryItemAdded(ServerPlayer player, String itemId,
                                            ItemStack addedStack, int count) {
        ConditionEvaluator.checkWithStack(
                player, DataStore.ConditionType.GET_ITEM,
                itemId, addedStack, count);
    }

    // ═══════════════ VANILLA ADVANCEMENT INTERCEPTION ═══════════════

    @SubscribeEvent
    public static void onAdvancementEarn(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var holder = event.getAdvancement();
        String id = holder.id().toString();

        if (id.startsWith(ModInfo.MOD_ID + ":")) return;

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

    @SuppressWarnings("deprecation")
    private static void suppressVanillaAdvancements(ServerPlayer player) {
        AdvancementRegistry.suppressAllDisabled(player);
    }
}