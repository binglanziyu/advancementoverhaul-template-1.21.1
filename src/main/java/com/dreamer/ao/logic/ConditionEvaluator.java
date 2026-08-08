package com.dreamer.ao.logic;

import com.dreamer.ao.LangKeys;
import com.dreamer.ao.ServerConstants;
import com.dreamer.ao.compat.AdvancementRegistry;
import com.dreamer.ao.data.ConditionIndex.AdvIdCondIndex;
import com.dreamer.ao.data.DataStore;
import com.dreamer.ao.data.model.AdvancementCondition;
import com.dreamer.ao.data.ConditionType;
import com.dreamer.ao.data.model.CustomAdvancement;
import com.dreamer.ao.data.NbtMatchMode;
import com.dreamer.ao.data.ServerDataStore;
import com.dreamer.ao.achievement.event.AdvCompletedEvent;
import com.dreamer.ao.achievement.event.AdvProgressEvent;
import com.dreamer.ao.network.payload.ProgressSyncPayload;
import net.minecraft.core.HolderLookup;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
/**
 * 进度条件评估引擎。
 *
 * <h2>核心职责</h2>
 * 将游戏事件（击杀实体、合成物品等）与自定义进度条件进行匹配，
 * 管理逐条件进度追踪，并在所有条件满足时触发进度完成。
 *
 * <h2>评估模式</h2>
 * <ul>
 *   <li><b>Instant（即时）</b> — 单次触发加1（KILL_ENTITY, CHANGE_DIMENSION）</li>
 *   <li><b>Progress（累积）</b> — 按量累积 current + amount（BREAK_BLOCK, PLACE_BLOCK, DEAL_DAMAGE, TAKE_DAMAGE）</li>
 *   <li><b>Stack-aware（物品感知）</b> — 物品 ID + NBT/Component 匹配（CRAFT_ITEM, GET_ITEM, FISH_ITEM）</li>
 *   <li><b>StatReach（统计达成）</b> — 以当前统计值直接比较阈值，由 {@link #checkStatReach} 调用，复用 {@link #evaluate} 核心路径</li>
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
 * <p>
 * <b>级联边界保障：</b>每次完成（{@link #doComplete} 结束后）都会调用
 * {@link #releasePendingDependents}，因此无论触发来源（本类的 evaluate/tryComplete、
 * 外部 AdvCrudExecutor、FtbQuestListener），级联释放始终生效。
 */
public final class ConditionEvaluator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConditionEvaluator.class);

    /**
     * 级联深度上限。详见 {@link ServerConstants#MAX_CASCADE_DEPTH}。
     */
    private static final int MAX_CASCADE_DEPTH = ServerConstants.MAX_CASCADE_DEPTH;

    /**
     * 去重表键：玩家 + 进度 + 条件索引的不可变组合。
     * <p>
     * 相较此前的 {@code uuid + ":" + advId + ":" + condIndex} 字符串拼接，
     * record 避免了每次条件匹配都产生 StringBuilder 与 String 两个对象；
     * 同时相较将三者哈希折叠为单个 long，record 保留完整字段做 equals 比较，
     * 不存在哈希碰撞导致合法评估被静默跳过的正确性风险。
     */
    private record DedupKey(UUID uuid, String advId, int condIndex) {}

    /** Tick 级重入保护：防止同一 tick 内 Mixin + Event 双重触发导致重复评估。
     *  使用 ConcurrentHashMap 做 per-key 自包含 tick 比较，消除 clear() 与 put() 之间的竞态窗口。
     *  Value 为上次评估时的 tick 值，由 {@link #pruneEvaluatedKeys(long)} 周期驱逐。 */
    private static final ConcurrentHashMap<DedupKey, Long> evaluatedKeys = new ConcurrentHashMap<>();

    private ConditionEvaluator() {}

    /**
     * 驱逐超出保留窗口的去重条目，由服务端 tick 周期调用。
     * <p>
     * 去重语义只需覆盖「同一 tick」，因此任何早于
     * {@code currentTick - DEDUP_RETENTION_TICKS} 的条目都已无用。
     * 若无此清理，键空间会随「玩家 × 进度 × 条件」持续增长而无界泄漏。
     *
     * @param currentTick 当前服务端 tick
     */
    public static void pruneEvaluatedKeys(long currentTick) {
        if (evaluatedKeys.isEmpty()) return;
        long cutoff = currentTick - ServerConstants.DEDUP_RETENTION_TICKS;
        // 服务器刚启动（tick < 保留窗口）时 cutoff 为负，此时无条目可能过期，跳过以免误删当前 tick 的守卫
        if (cutoff <= 0) return;
        // 同时剔除「记录 tick 晚于当前 tick」的条目：存档回退或 tick 计数重置会产生此类陈旧项
        evaluatedKeys.entrySet().removeIf(e -> e.getValue() < cutoff || e.getValue() > currentTick);
    }

    // ═══════════════ 公共评估入口 ═══════════════

    /** Instant 评估：单次触发加 1。amount=1 明确语义，与 instant 的"加1"行为一致。 */
    public static void checkInstant(ServerPlayer player, ConditionType type, String targetId) {
        evaluate(player, type, targetId, null, 1,
                (current, count, amount) -> Math.min(current + 1, count));
    }

    /** Progress 评估：按量累积。 */
    public static void checkProgress(ServerPlayer player, ConditionType type,
                                     String targetId, int amount) {
        evaluate(player, type, targetId, null, amount,
                (current, count, a) -> Math.min(current + a, count));
    }

    /** Stack-aware 评估：物品 ID + NBT/Component 匹配。 */
    public static void checkWithStack(ServerPlayer player, ConditionType type,
                                      String targetId, ItemStack stack, int amount) {
        evaluate(player, type, targetId, stack, amount,
                (current, count, a) -> Math.min(current + a, count));
    }

    /**
     * STAT_REACH 评估：以当前统计值直接与条件阈值比较。
     * <p>
     * 统计值本身就是累积结果，因此直接设置为统计值而非累加。
     * 当统计值 ≥ 条件要求的 count 时触发完成。
     * 复用 {@link #evaluate} 核心路径，消除与 progress 模式之间的代码重复。
     * <p>
     * 由 {@code StatsEventHandler} 在每次更新统计值后调用。
     * <p>
     * <b>int 上限说明：</b>{@code count} 字段和进度系统全程使用 {@code int} 存储，
     * 当统计值超过 {@link Integer#MAX_VALUE}（约 21 亿）时会饱和。
     * 对于 Minecraft 统计值的量级而言，此上限在实践中有充分余量。
     *
     * @param player   目标玩家
     * @param statId   PlayerStats 字段名（如 "sunrisesViewed"）
     * @param newValue 该统计的当前值
     */
    public static void checkStatReach(ServerPlayer player, String statId, long newValue) {
        evaluate(player, ConditionType.STAT_REACH, statId, null,
                (int) Math.min(newValue, Integer.MAX_VALUE),
                (current, count, amount) -> Math.min(amount, count));
    }

    // ═══════════════ 进度更新策略 ═══════════════

    /**
     * 进度更新策略函数式接口。
     * <p>
     * 不同评估模式对进度有不同更新逻辑：
     * <ul>
     *   <li>Instant：加1（不依赖 amount）</li>
     *   <li>Progress/Stack：current + amount</li>
     *   <li>StatReach：直接设置为统计值</li>
     * </ul>
     */
    @FunctionalInterface
    private interface ProgressUpdater {
        /**
         * @param currentProgress 当前条件进度
         * @param conditionCount  条件目标值
         * @param eventAmount     本次事件增量
         * @return 新的进度值
         */
        int compute(int currentProgress, int conditionCount, int eventAmount);
    }

    // ═══════════════ 统一评估核心 ═══════════════

    /**
     * 统一评估核心，支持条件级别索引直接跳转。
     * <p>
     * 优先使用 {@link AdvIdCondIndex} 条件级别索引（在 rebuildConditionIndex 时预计算）
     * 直接跳转到匹配条件，避免对每个成就的所有条件做内层全量遍历。
     * 当索引尚未构建完成时回退到旧的全量遍历路径。
     *
     * @param player   目标玩家
     * @param type     条件类型
     * @param targetId 事件目标 ID（如实体/物品/方块注册名）
     * @param stack    物品堆（stack-aware 模式传入，否则 null）
     * @param amount   本次事件量
     * @param updater  进度更新策略
     */
    private static void evaluate(ServerPlayer player, ConditionType type, String targetId,
                                 ItemStack stack, int amount, ProgressUpdater updater) {
        ServerDataStore store = ServerDataStore.getInstance();
        UUID uuid = player.getUUID();

        // 优先使用条件级别索引（精确跳转到匹配条件，省去内层全量遍历）
        if (targetId != null && !targetId.isEmpty()) {
            List<AdvIdCondIndex> indexed = store.getAdvCondIndexesByCondition(type, targetId);
            if (!indexed.isEmpty()) {
                evaluateWithIndex(player, store, uuid, type, targetId, stack, amount, updater, indexed);
                return;
            }
        }

        // 回退到全量遍历（targetId 为空、索引中无此条目等场景）
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
                processMatchedCondition(player, store, uuid, advId, adv, cond, i, amount, updater);
            }
        }
    }

    /**
     * 使用条件级别索引的快速评估路径。
     * <p>
     * 每个索引条目直接指向匹配的条件位置，无需内层循环遍历所有条件。
     * 同一成就可能有多条索引条目（如多个 kill 条件针对不同实体类型），
     * 每个都会独立评估。
     */
    private static void evaluateWithIndex(ServerPlayer player, ServerDataStore store, UUID uuid,
            ConditionType type, String targetId, ItemStack stack, int amount,
            ProgressUpdater updater, List<AdvIdCondIndex> indexed) {
        for (AdvIdCondIndex entry : indexed) {
            String advId = entry.advId();
            if (store.isCompleted(uuid, advId)) continue;
            CustomAdvancement adv = store.getAdvancement(advId);
            if (adv == null) continue;
            List<AdvancementCondition> conditions = adv.getConditions();
            int ci = entry.condIndex();
            if (ci >= conditions.size()) continue;
            AdvancementCondition cond = conditions.get(ci);
            if (cond.getType() != type) continue;
            boolean matched;
            if (stack != null) {
                matched = matchesSingleItem(cond, targetId, stack, player.registryAccess());
            } else {
                matched = matchesTarget(cond.getTargetId(), targetId);
            }
            if (!matched) continue;
            processMatchedCondition(player, store, uuid, advId, adv, cond, ci, amount, updater);
        }
    }

    /** 处理已匹配的单个条件：更新进度、发送事件、检查完成。 */
    private static void processMatchedCondition(ServerPlayer player, ServerDataStore store, UUID uuid,
            String advId, CustomAdvancement adv, AdvancementCondition cond, int condIndex,
            int amount, ProgressUpdater updater) {
        // Tick 级重入保护：同一 tick 内同一玩家的同一成就条件不重复评估
        var server = store.getServer();
        if (server != null) {
            long currentTick = server.getTickCount();
            DedupKey dedupKey = new DedupKey(uuid, advId, condIndex);
            Long lastTick = evaluatedKeys.put(dedupKey, currentTick);
            if (lastTick != null && lastTick == currentTick) {
                LOGGER.debug("Skipping duplicate evaluation: {} @ tick {}", dedupKey, currentTick);
                return;
            }
        }

        int current = store.getConditionProgress(uuid, advId, condIndex);
        int newProgress = updater.compute(current, cond.getCount(), amount);
        store.setConditionProgress(uuid, advId, condIndex, newProgress);

        NeoForge.EVENT_BUS.post(new AdvProgressEvent(player, advId, newProgress, cond.getCount()));

        if (allConditionsMet(uuid, advId, adv)) {
            tryComplete(player, advId);
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

    /**
     * 释放所有前置条件已满足的 pending 进度。使用迭代方式，深度上限 64。
     * <p>
     * <b>级联边界保障：</b>tryComplete 和 tryCompleteForce 在 doComplete 之后均调用本方法。
     * 此外 ExpCompletionListener、AdvCrudExecutor、FtbQuestListener 等外部完成入口
     * 也通过 tryComplete → 本方法实现级联。因此无论触发来源为何，
     * 级联释放始终生效（包括用户提出的 D 完成 → B 释放场景）。
     */
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

    /** 已警告过空条件列表的成就 ID（每个 ID 仅警告一次，避免日志刷屏） */
    private static final Set<String> warnedEmptyAdvs = ConcurrentHashMap.newKeySet();

    private static boolean allConditionsMet(UUID uuid, String advId, CustomAdvancement adv) {
        ServerDataStore store = ServerDataStore.getInstance();
        List<AdvancementCondition> conditions = adv.getConditions();
        if (conditions.isEmpty()) {
            if (warnedEmptyAdvs.add(advId)) {
                LOGGER.warn("Advancement '{}' has no conditions and will auto-complete on first trigger", advId);
            }
            return true;
        }
        for (int i = 0; i < conditions.size(); i++) {
            int progress = store.getConditionProgress(uuid, advId, i);
            if (progress < conditions.get(i).getCount()) return false;
        }
        return true;
    }

    // ═══════════════ 匹配辅助方法 ═══════════════

    /**
     * 匹配条件的 targetId 与事件的 targetId。
     * <p>
     * <b>空值语义：</b>condTarget 为空表示通配（匹配一切），eventTarget 为 null/空
     * 则表示事件无有效目标，此时返回 {@code false} 拒绝匹配。
     * 这与条件索引的分发逻辑保持一致——索引路径下不存在 null-target 条目，
     * 因此这里的 {@code return false} 仅影响全量遍历回退路径中的极端边缘情况。
     */
    private static boolean matchesTarget(String condTarget, String eventTarget) {
        if (condTarget == null || condTarget.isEmpty()) return true;
        if (eventTarget == null || eventTarget.isEmpty()) return false;
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

    /**
     * 将 NBT 字符串反序列化为 ItemStack。
     * <p>
     * <b>异常范围：</b>仅捕获 {@link CommandSyntaxException}（NBT 格式错误），
     * 而非泛化的 {@code Exception}。{@link TagParser#parseTag} 明确声明抛出此异常，
     * 其他未预期的运行时异常（如 OOM）应向上传播而非静默吞没。
     * 返回空堆叠后调用方 {@link #matchComponents} 将其视为不匹配。
     */
    private static ItemStack deserializeStack(String nbt, HolderLookup.Provider registryAccess) {
        if (nbt == null || nbt.isEmpty()) return ItemStack.EMPTY;
        try {
            CompoundTag tag = TagParser.parseTag(nbt);
            return ItemStack.parse(registryAccess, tag).orElse(ItemStack.EMPTY);
        } catch (CommandSyntaxException e) {
            LOGGER.warn("Failed to parse condition NBT ({} chars): {}", nbt.length(), e.getMessage());
            return ItemStack.EMPTY;
        }
    }
}
