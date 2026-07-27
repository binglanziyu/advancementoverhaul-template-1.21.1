package com.example.advancementoverhaul.logic;

import com.example.advancementoverhaul.LangKeys;
import com.example.advancementoverhaul.compat.AdvancementRegistry;
import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.model.AdvancementCondition;
import com.example.advancementoverhaul.data.DataStore.ConditionType;
import com.example.advancementoverhaul.data.model.CustomAdvancement;
import com.example.advancementoverhaul.data.DataStore.NbtMatchMode;
import com.example.advancementoverhaul.data.ServerDataStore;
import com.example.advancementoverhaul.event.AdvCompletedEvent;
import com.example.advancementoverhaul.event.AdvProgressEvent;
import com.example.advancementoverhaul.network.ProgressSyncPayload;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 进度条件评估引擎。
 *
 * <h2>核心职责</h2>
 * 将游戏事件（击杀实体、合成物品等）与自定义进度条件进行匹配，
 * 管理逐条件进度追踪，并在所有条件满足时触发进度完成。
 *
 * <h2>评估模式</h2>
 * <ul>
 *   <li><b>Instant（即时）</b> — 单次触发即完成（KILL_ENTITY, CHANGE_DIMENSION）</li>
 *   <li><b>Progress（累积）</b> — 按量累积（BREAK_BLOCK, PLACE_BLOCK, DEAL_DAMAGE, TAKE_DAMAGE）</li>
 *   <li><b>Stack-aware（物品感知）</b> — 物品 ID + NBT/Component 匹配（CRAFT_ITEM, GET_ITEM, FISH_ITEM）</li>
 * </ul>
 *
 * <h2>AND 逻辑</h2>
 * 一个进度可以配置多个条件，所有条件必须独立满足才算完成。
 * 每个条件的进度通过 {@link ServerDataStore#setConditionProgress} 独立追踪。
 *
 * <h2>前置条件与级联</h2>
 * 当一个进度的所有条件满足时，检查其前置条件：
 * <ul>
 *   <li>前置条件满足 → 直接完成 → 级联释放依赖此进度的 pending 进度</li>
 *   <li>前置条件不满足 → 标记为 pending，等待前置完成后再释放</li>
 * </ul>
 * 级联使用迭代方式（非递归），防止超长前置链导致栈溢出。
 */
public final class ConditionEvaluator {

    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/ConditionEvaluator");

    /** 级联深度上限，防止超长前置链导致性能问题 */
    private static final int MAX_CASCADE_DEPTH = 64;

    private ConditionEvaluator() {}

    // ═══════════════ 公共评估入口 ═══════════════

    /** Instant 评估：单次触发即完成。 */
    public static void checkInstant(ServerPlayer player, ConditionType type, String targetId) {
        evaluate(player, type, targetId, null, 0, true);
    }

    /** Progress 评估：按量累积。 */
    public static void checkProgress(ServerPlayer player, ConditionType type,
                                     String targetId, int amount) {
        evaluate(player, type, targetId, null, amount, false);
    }

    /** Stack-aware 评估：物品 ID + NBT/Component 匹配。 */
    public static void checkWithStack(ServerPlayer player, ConditionType type,
                                      String targetId, ItemStack stack, int amount) {
        evaluate(player, type, targetId, stack, amount, false);
    }

    // ═══════════════ 统一评估核心 ═══════════════

    private static void evaluate(ServerPlayer player, ConditionType type, String targetId,
                                 ItemStack stack, int amount, boolean instant) {
        ServerDataStore store = ServerDataStore.getInstance();
        UUID uuid = player.getUUID();

        List<String> advIds = resolveAdvancementIds(store, type, targetId);

        for (String advId : advIds) {
            if (store.isCompleted(uuid, advId)) continue;

            CustomAdvancement adv = store.getAdvancement(advId);
            if (adv == null) continue;

            List<AdvancementCondition> conditions = adv.getConditions();
            for (int i = 0; i < conditions.size(); i++) {
                AdvancementCondition cond = conditions.get(i);
                if (cond.getType() != type) continue;

                boolean matched;
                if (stack != null) {
                    matched = matchesSingleItem(cond, targetId, stack, player.registryAccess());
                } else {
                    matched = matchesTarget(cond.getTargetId(), targetId);
                }
                if (!matched) continue;

                int current = store.getConditionProgress(uuid, advId, i);
                int newProgress;
                if (instant) {
                    newProgress = Math.min(current + 1, cond.getCount());
                } else {
                    newProgress = Math.min(current + amount, cond.getCount());
                }
                store.setConditionProgress(uuid, advId, i, newProgress);

                NeoForge.EVENT_BUS.post(new AdvProgressEvent(player, advId, newProgress, cond.getCount()));

                if (allConditionsMet(uuid, advId, adv)) {
                    tryComplete(player, advId);
                }
                break;
            }
        }
    }

    /**
     * 解析与给定条件类型和目标 ID 相关的成就 ID。
     * <p>
     * 预计算合并索引已包含通配符条件（空 targetId）在 rebuildConditionIndex 时合并到
     * 每个目标条目中，因此不再需要运行时 LinkedHashSet 去重合并。
     */
    private static List<String> resolveAdvancementIds(ServerDataStore store, ConditionType type, String targetId) {
        if (targetId != null && !targetId.isEmpty()) {
            return store.getAdvIdsByCondition(type, targetId);
        }
        return store.getAdvIdsByConditionType(type);
    }

    // ═══════════════ 完成逻辑 ═══════════════

    /** 强制完成：跳过前置条件检查。 */
    public static void tryCompleteForce(ServerPlayer player, String advId) {
        doComplete(player, advId);
        releasePendingDependents(player);
    }

    /** 带前置条件检查的完成。 */
    public static void tryComplete(ServerPlayer player, String advId) {
        ServerDataStore store = ServerDataStore.getInstance();
        UUID uuid = player.getUUID();
        if (store.isCompleted(uuid, advId)) return;

        CustomAdvancement adv = store.getAdvancement(advId);
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
                PacketDistributor.sendToPlayer(player,
                        new ProgressSyncPayload(advId, false, progress, true));
                return;
            }
        }

        doComplete(player, advId);
        releasePendingDependents(player);
    }

    private static void doComplete(ServerPlayer player, String advId) {
        ServerDataStore store = ServerDataStore.getInstance();
        UUID uuid = player.getUUID();
        if (store.isCompleted(uuid, advId)) return;

        store.setCompleted(uuid, advId, true);
        store.setPending(uuid, advId, false);
        store.savePlayerDataIfDirty();

        int progress = store.getProgress(uuid, advId);
        PacketDistributor.sendToPlayer(player,
                new ProgressSyncPayload(advId, true, progress));

        AdvancementRegistry.grantAdvancement(player, advId);

        CustomAdvancement adv = store.getAdvancement(advId);
        String advName = adv != null ? adv.getName() : advId;
        NeoForge.EVENT_BUS.post(new AdvCompletedEvent(player, advId, advName));
    }

    // ═══════════════ 级联释放 ═══════════════

    public static void releasePendingDependents(ServerPlayer player) {
        UUID uuid = player.getUUID();

        for (int depth = 0; depth < MAX_CASCADE_DEPTH; depth++) {
            ServerDataStore store = ServerDataStore.getInstance();
            List<String> pendingCopy = new ArrayList<>(store.getPendingAdvancements(uuid));
            boolean anyCompleted = false;

            for (String pendingId : pendingCopy) {
                if (store.isCompleted(uuid, pendingId)) continue;

                CustomAdvancement adv = store.getAdvancement(pendingId);
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

    // ═══════════════ AND 逻辑 ═══════════════

    /**
     * 公开的条件满足检查，供外部（如 FTB 奖励领取拦截）使用。
     * @return true 表示该进度的所有条件均已满足
     */
    public static boolean checkAllConditionsMet(UUID uuid, String advId) {
        ServerDataStore store = ServerDataStore.getInstance();
        CustomAdvancement adv = store.getAdvancement(advId);
        if (adv == null) return false;
        return allConditionsMet(uuid, advId, adv);
    }

    private static boolean allConditionsMet(UUID uuid, String advId, CustomAdvancement adv) {
        ServerDataStore store = ServerDataStore.getInstance();
        List<AdvancementCondition> conditions = adv.getConditions();
        if (conditions.isEmpty()) {
            LOGGER.warn("Advancement '{}' has no conditions and will auto-complete on first trigger", advId);
            return true;
        }
        for (int i = 0; i < conditions.size(); i++) {
            int progress = store.getConditionProgress(uuid, advId, i);
            if (progress < conditions.get(i).getCount()) return false;
        }
        return true;
    }

    // ═══════════════ 匹配辅助方法 ═══════════════

    private static boolean matchesTarget(String condTarget, String eventTarget) {
        if (condTarget == null || condTarget.isEmpty()) return true;
        if (eventTarget == null || eventTarget.isEmpty()) return true;
        return condTarget.equals(eventTarget);
    }

    private static boolean matchesSingleItem(AdvancementCondition cond, String itemId,
                                             ItemStack stack, HolderLookup.Provider registryAccess) {
        if (!itemId.equals(cond.getTargetId())) return false;

        NbtMatchMode mode = cond.getNbtMatchMode();
        if (mode == null || mode == NbtMatchMode.IGNORE) return true;

        String targetNbt = cond.getTargetNbt();
        if (targetNbt == null || targetNbt.isEmpty()) return true;

        return matchComponents(stack, targetNbt, mode, registryAccess);
    }

    private static boolean matchComponents(ItemStack stack, String targetNbt,
                                           NbtMatchMode mode,
                                           HolderLookup.Provider registryAccess) {
        ItemStack target = deserializeStack(targetNbt, registryAccess);

        if (mode == NbtMatchMode.EXACT) {
            return ItemStack.isSameItemSameComponents(stack, target);
        }
        if (mode == NbtMatchMode.CONTAINS) {
            if (target.isEmpty()) return true;
            if (!stack.is(target.getItem())) return false;
            for (var type : target.getComponents().keySet()) {
                if (!Objects.equals(target.getComponents().get(type),
                        stack.getComponents().get(type)))
                    return false;
            }
            return true;
        }
        if (mode == NbtMatchMode.NONE_EMPTY) {
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
