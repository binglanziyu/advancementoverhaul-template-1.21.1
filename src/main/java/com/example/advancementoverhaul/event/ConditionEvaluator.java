package com.example.advancementoverhaul.event;

import com.example.advancementoverhaul.compat.AdvancementRegistry;
import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.ServerDataStore;
import com.example.advancementoverhaul.network.ProgressSyncPayload;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import com.example.advancementoverhaul.LangKeys;
import net.minecraft.network.chat.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Evaluates advancement conditions against player actions.
 *
 * <p>Three evaluation modes:
 * <ul>
 *   <li>{@link #checkInstant} — single-trigger (KILL_ENTITY, CHANGE_DIMENSION)</li>
 *   <li>{@link #checkProgress} — count-based (BREAK_BLOCK, PLACE_BLOCK, DEAL/TAKE_DAMAGE)</li>
 *   <li>{@link #checkWithStack} — item-aware with NBT matching (CRAFT_ITEM, GET_ITEM, FISH_ITEM)</li>
 * </ul>
 *
 * <p>All modes use AND logic: an advancement with multiple conditions
 * requires every condition to be satisfied independently. Per-condition
 * progress is tracked via {@link ServerDataStore#setConditionProgress}.
 */
public final class ConditionEvaluator {

    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/ConditionEvaluator");

    private ConditionEvaluator() {}

    // ═══════════════ INSTANT ═══════════════

    /**
     * Instant: complete on single trigger, no progress counting.
     * Marks the matched condition as fully satisfied, then checks
     * whether all conditions of that advancement are met (AND logic).
     */
    public static void checkInstant(ServerPlayer player, DataStore.ConditionType type, String targetId) {
        ServerDataStore store = ServerDataStore.getInstance();
        UUID uuid = player.getUUID();

        // [E3] 根据 targetId 选择索引，避免冗余查询
        List<String> advIds;
        if (targetId != null && !targetId.isEmpty()) {
            advIds = store.getAdvIdsByCondition(type, targetId);
        } else {
            advIds = store.getAdvIdsByConditionType(type);
        }

        for (String advId : advIds) {
            if (store.isCompleted(uuid, advId)) continue;
            DataStore.CustomAdvancement adv = store.getAdvancement(advId);
            if (adv == null) continue;
            List<DataStore.AdvancementCondition> conditions = adv.getConditions();
            for (int i = 0; i < conditions.size(); i++) {
                DataStore.AdvancementCondition cond = conditions.get(i);
                if (cond.getType() != type) continue;
                if (!matchesTarget(cond.getTargetId(), targetId)) continue;
                // [E2] 标记该条件为已满足（进度 = 需求数）
                store.setConditionProgress(uuid, advId, i, cond.getCount());
                // [E2] AND 逻辑：检查是否所有条件都满足
                if (allConditionsMet(player, uuid, advId, adv)) {
                    tryComplete(player, advId);
                }
                break; // 每个事件只匹配该成就的第一个对应条件
            }
        }
    }

    // ═══════════════ PROGRESS ═══════════════

    /**
     * Progress: increment by {@code amount} per trigger.
     * Per-condition progress tracked; completion requires all conditions met (AND logic).
     */
    public static void checkProgress(ServerPlayer player, DataStore.ConditionType type,
                                     String targetId, int amount) {
        ServerDataStore store = ServerDataStore.getInstance();
        UUID uuid = player.getUUID();

        // [E3] 根据 targetId 选择索引
        List<String> advIds;
        if (targetId != null && !targetId.isEmpty()) {
            advIds = store.getAdvIdsByCondition(type, targetId);
        } else {
            advIds = store.getAdvIdsByConditionType(type);
        }

        for (String advId : advIds) {
            if (store.isCompleted(uuid, advId)) continue;
            DataStore.CustomAdvancement adv = store.getAdvancement(advId);
            if (adv == null) continue;
            List<DataStore.AdvancementCondition> conditions = adv.getConditions();
            for (int i = 0; i < conditions.size(); i++) {
                DataStore.AdvancementCondition cond = conditions.get(i);
                if (cond.getType() != type) continue;
                if (!matchesTarget(cond.getTargetId(), targetId)) continue;
                // [E2] per-condition 进度更新（截断到需求上限，防止溢出）
                int cur = store.getConditionProgress(uuid, advId, i);
                int updated = Math.min(cur + amount, cond.getCount());
                store.setConditionProgress(uuid, advId, i, updated);
                NeoForge.EVENT_BUS.post(new AdvProgressEvent(player, advId, updated, cond.getCount()));
                // [E2] AND 逻辑
                if (allConditionsMet(player, uuid, advId, adv)) {
                    tryComplete(player, advId);
                }
                break;
            }
        }
    }

    // ═══════════════ WITH STACK (NBT-aware) ═══════════════

    /**
     * Stack-aware: matches item ID + optional NBT/components.
     * Per-condition progress tracked; completion requires all conditions met (AND logic).
     */
    public static void checkWithStack(ServerPlayer player, DataStore.ConditionType type,
                                      String targetId, ItemStack stack, int amount) {
        ServerDataStore store = ServerDataStore.getInstance();
        UUID uuid = player.getUUID();
        HolderLookup.Provider lookup = player.registryAccess();

        // [E3] 根据 targetId 选择索引
        List<String> advIds;
        if (targetId != null && !targetId.isEmpty()) {
            advIds = store.getAdvIdsByCondition(type, targetId);
        } else {
            advIds = store.getAdvIdsByConditionType(type);
        }

        for (String advId : advIds) {
            if (store.isCompleted(uuid, advId)) continue;
            DataStore.CustomAdvancement adv = store.getAdvancement(advId);
            if (adv == null) continue;
            List<DataStore.AdvancementCondition> conditions = adv.getConditions();
            for (int i = 0; i < conditions.size(); i++) {
                DataStore.AdvancementCondition cond = conditions.get(i);
                if (cond.getType() != type) continue;
                if (!matchesSingleItem(cond, targetId, stack, lookup)) continue;
                // [E2] per-condition 进度更新（截断到需求上限，防止溢出）
                int cur = store.getConditionProgress(uuid, advId, i);
                int updated = Math.min(cur + amount, cond.getCount());
                store.setConditionProgress(uuid, advId, i, updated);
                NeoForge.EVENT_BUS.post(new AdvProgressEvent(player, advId, updated, cond.getCount()));
                // [E2] AND 逻辑
                if (allConditionsMet(player, uuid, advId, adv)) {
                    tryComplete(player, advId);
                }
                break;
            }
        }
    }

    // ═══════════════ COMPLETION ═══════════════

    /**
     * Force-complete: bypasses prerequisite check.
     * Used by commands (e.g. /adv complete).
     */
    public static void tryCompleteForce(ServerPlayer player, String advId) {
        doComplete(player, advId);
        // [E1] 级联由调用方触发，doComplete 内部不再递归
        releasePendingDependents(player);
    }

    /**
     * Complete with prerequisite checking.
     * If prerequisites are not met, marks as pending instead of completing.
     */
    public static void tryComplete(ServerPlayer player, String advId) {
        ServerDataStore store = ServerDataStore.getInstance();
        UUID uuid = player.getUUID();
        if (store.isCompleted(uuid, advId)) return;

        // Check prerequisites
        DataStore.CustomAdvancement adv = store.getAdvancement(advId);
        if (adv != null && !adv.getPrerequisites().isEmpty()) {
            boolean allPrereqsMet = true;
            for (String prereqId : adv.getPrerequisites()) {
                if (!store.isCompleted(uuid, prereqId)) {
                    allPrereqsMet = false;
                    break;
                }
            }
            if (!allPrereqsMet) {
                store.setPending(uuid, advId, true);
                store.savePlayerDataIfDirty();
                int progress = store.getProgress(uuid, advId);
                PacketDistributor.sendToPlayer(player, new ProgressSyncPayload(advId, false, progress, true));
                return;
            }
        }

        // All prerequisites met (or no prerequisites) → complete
        doComplete(player, advId);
        // [E1] 级联由调用方触发
        releasePendingDependents(player);
    }

    /**
     * Internal: mark as truly completed, then fire events.
     * Does NOT call releasePendingDependents — caller is responsible for cascade.
     */
    private static void doComplete(ServerPlayer player, String advId) {
        ServerDataStore store = ServerDataStore.getInstance();
        UUID uuid = player.getUUID();
        if (store.isCompleted(uuid, advId)) return;

        // 1. Mark completed, clear pending
        store.setCompleted(uuid, advId, true);
        store.setPending(uuid, advId, false);
        store.savePlayerDataIfDirty();

        // 2. Client sync
        int progress = store.getProgress(uuid, advId);
        PacketDistributor.sendToPlayer(player, new ProgressSyncPayload(advId, true, progress));

        // 3. Grant vanilla advancement
        AdvancementRegistry.grantAdvancement(player, advId);

        // 4. Fire custom event
        DataStore.CustomAdvancement adv = store.getAdvancement(advId);
        String advName = adv != null ? adv.getName() : advId;
        NeoForge.EVENT_BUS.post(new AdvCompletedEvent(player, advId, advName));
        // [E1] 不再在这里调用 releasePendingDependents，避免无限递归
    }

    /** 可配置的级联深度上限，防止超长前置链导致性能问题 */
       public static int MAX_CASCADE_DEPTH = 64;

    /**
     * Iterative cascade: check all pending advancements; complete any
     * whose prerequisites are now all met. Repeats until no new completions.
     *
     * <p>Iterative (not recursive) to avoid StackOverflow on long prerequisite chains.
     * Safe to call from tryComplete / tryCompleteForce because doComplete
     * no longer calls this method.
     */
    public static void releasePendingDependents(ServerPlayer player) {
        for (int depth = 0; depth < MAX_CASCADE_DEPTH; depth++) {
            ServerDataStore store = ServerDataStore.getInstance();
            UUID uuid = player.getUUID();

            List<String> pendingCopy = new ArrayList<>(store.getPendingAdvancements(uuid));
            boolean anyCompleted = false;

            for (String pendingId : pendingCopy) {
                if (store.isCompleted(uuid, pendingId)) continue;
                DataStore.CustomAdvancement adv = store.getAdvancement(pendingId);
                if (adv == null) continue;

                boolean allMet = true;
                List<String> prereqs = adv.getPrerequisites();
                if (!prereqs.isEmpty()) {
                    for (String prereqId : prereqs) {
                        if (!store.isCompleted(uuid, prereqId)) {
                            allMet = false;
                            break;
                        }
                    }
                }
                if (allMet) {
                    // doComplete 不再递归调用 releasePendingDependents，
                    // 所以循环会在下一轮检查新完成的 pending
                    doComplete(player, pendingId);
                    anyCompleted = true;
                }
            }

            if (!anyCompleted) break;
            if (depth == MAX_CASCADE_DEPTH - 1) {
                int remaining = store.getPendingAdvancements(uuid).size();
                LOGGER.warn("Cascade depth limit ({}) reached, {} pending advancements remain",
                        MAX_CASCADE_DEPTH, remaining);
                player.sendSystemMessage(
                        Component.translatable(LangKeys.CMD_CASCADE_DEPTH_EXCEEDED,
                                MAX_CASCADE_DEPTH, remaining));
            }
        }
    }

    // ═══════════════ AND LOGIC ═══════════════

    /**
     * Checks whether ALL conditions of an advancement have been individually satisfied.
     * Each condition's progress must meet or exceed its required count.
     *
     * @return true if every condition is satisfied, false otherwise
     */
    private static boolean allConditionsMet(ServerPlayer player, UUID uuid, String advId,
                                            DataStore.CustomAdvancement adv) {
        ServerDataStore store = ServerDataStore.getInstance();
        List<DataStore.AdvancementCondition> conditions = adv.getConditions();
        if (conditions.isEmpty()) return true;
        for (int i = 0; i < conditions.size(); i++) {
            int progress = store.getConditionProgress(uuid, advId, i);
            if (progress < conditions.get(i).getCount()) return false;
        }
        return true;
    }

    // ═══════════════ MATCHING HELPERS ═══════════════

    /**
     * Target ID matching. Empty targetId on either side = wildcard (matches anything).
     */
    private static boolean matchesTarget(String condTarget, String eventTarget) {
        if (condTarget == null || condTarget.isEmpty()) return true;
        if (eventTarget == null || eventTarget.isEmpty()) return true;
        return condTarget.equals(eventTarget);
    }

    /**
     * Item-level matching with optional NBT/Component comparison.
     */
    private static boolean matchesSingleItem(DataStore.AdvancementCondition cond, String itemId,
                                             ItemStack stack, HolderLookup.Provider registryAccess) {
        if (!itemId.equals(cond.getTargetId())) return false;
        DataStore.NbtMatchMode mode = cond.getNbtMatchMode();
        if (mode == null || mode == DataStore.NbtMatchMode.IGNORE) return true;
        String targetNbt = cond.getTargetNbt();
        if (targetNbt == null || targetNbt.isEmpty()) return true;
        return matchComponents(stack, targetNbt, mode, registryAccess);
    }

    private static boolean matchComponents(ItemStack stack, String targetNbt,
                                           DataStore.NbtMatchMode mode,
                                           HolderLookup.Provider registryAccess) {
        ItemStack target = deserializeStack(targetNbt, registryAccess);

        if (mode == DataStore.NbtMatchMode.EXACT) {
            return ItemStack.isSameItemSameComponents(stack, target);
        } else if (mode == DataStore.NbtMatchMode.CONTAINS) {
            if (target.isEmpty()) return true;
            if (!stack.is(target.getItem())) return false;
            for (var type : target.getComponents().keySet()) {
                if (!Objects.equals(target.getComponents().get(type),
                        stack.getComponents().get(type)))
                    return false;
            }
            return true;
        } else if (mode == DataStore.NbtMatchMode.NONE_EMPTY) {
            if (stack.isEmpty()) return false;
            ItemStack defaultStack = new ItemStack(stack.getItem());
            return !ItemStack.isSameItemSameComponents(stack, defaultStack);
        }
        return true;
    }

    private static ItemStack deserializeStack(String nbt, HolderLookup.Provider registryAccess) {
        if (nbt == null || nbt.isEmpty()) return ItemStack.EMPTY;
        try {
            CompoundTag tag = TagParser.parseTag(nbt);
            return ItemStack.parse(registryAccess, tag).orElse(ItemStack.EMPTY);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse condition NBT ({} chars): {}", nbt.length(), e.getMessage());
            return ItemStack.EMPTY;
        }
    }
}