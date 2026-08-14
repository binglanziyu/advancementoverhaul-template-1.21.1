package com.dreamer.ao.logic;

import com.dreamer.ao.LangKeys;
import com.dreamer.ao.ServerConstants;
import com.dreamer.ao.compat.AdvancementRegistry;
import com.dreamer.ao.data.ConditionIndex.AdvIdCondIndex;
import com.dreamer.ao.data.DataStore;
import com.dreamer.ao.data.ServerDataStore;
import com.dreamer.ao.data.model.AdvancementCondition;
import com.dreamer.ao.data.ConditionType;
import com.dreamer.ao.data.model.CustomAdvancement;
import com.dreamer.ao.data.NbtMatchMode;
import com.dreamer.ao.achievement.event.AdvProgressEvent;
import com.dreamer.ao.network.payload.ProgressSyncPayload;
import net.minecraft.core.HolderLookup;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import com.dreamer.ao.network.NetworkSender;
import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
/**
 * 进度条件评估引擎。
 * <p>
 * <b>核心职责（单一）：</b>将游戏事件（击杀实体、合成物品等）与自定义进度条件进行匹配，
 * 管理逐条件进度追踪，并在所有条件满足时委托 {@link CompletionHandler} 触发完成。
 * 本类不再持有「完成 / 级联释放」逻辑，亦不再持有「Tick 级去重表」
 * （已下沉至 {@link DedupGuard}），从而消除静态可变状态反模式。
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
 * <h2>完成与级联</h2>
 * 当某进度所有条件满足时，本类调用 {@link CompletionHandler#tryComplete}；
 * 完成判定、奖励授予与级联释放均由 {@link CompletionHandler} 负责。
 */
public final class ConditionEvaluator {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Tick 级去重守卫（独立单例状态对象）。 */
    private static final DedupGuard DEDUP_GUARD = DedupGuard.getInstance();

    /** 已警告过空条件列表的成就 ID（每个 ID 仅警告一次，避免日志刷屏）。属评估查询的状态。 */
    private static final Set<String> warnedEmptyAdvs = ConcurrentHashMap.newKeySet();

    private ConditionEvaluator() {}

    /**
     * 驱逐超出保留窗口的去重条目，由服务端 tick 周期调用。
     * <p>
     * 委托给 {@link DedupGuard#prune}，保留本静态门面以兼容既有调用方
     * （{@code ServerEventHandler}），避免无关调用点改动。
     *
     * @param currentTick 当前服务端 tick
     */
    public static void pruneEvaluatedKeys(long currentTick) {
        DEDUP_GUARD.prune(currentTick);
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
     * 条件匹配策略函数式接口。
     * <p>
     * 将「事件目标 / 物品堆」与条件本身的匹配判定收敛为单一策略点，
     * 消除 {@code evaluate} 与 {@code evaluateWithIndex} 中重复的
     * {@code if (stack != null) ... else ...} 分支。
     */
    @FunctionalInterface
    private interface ConditionMatcher {
        boolean match(AdvancementCondition cond, String eventTargetId, ItemStack stack,
                      HolderLookup.Provider registryAccess);
    }

    /**
     * 标准匹配策略：stack 非空时走物品感知匹配，否则走普通目标相等匹配。
     * 两处评估路径（索引 / 全量）统一复用此策略，避免分支逻辑重复。
     */
    private static final ConditionMatcher STANDARD_MATCHER = (cond, eventTargetId, stack, registryAccess) ->
            stack != null
                    ? matchesSingleItem(cond, eventTargetId, stack, registryAccess)
                    : matchesTarget(cond.getTargetId(), eventTargetId);

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
                if (!STANDARD_MATCHER.match(cond, targetId, stack, player.registryAccess())) continue;
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
            if (!STANDARD_MATCHER.match(cond, targetId, stack, player.registryAccess())) continue;
            processMatchedCondition(player, store, uuid, advId, adv, cond, ci, amount, updater);
        }
    }

    /** 处理已匹配的单个条件：更新进度、发送事件、检查完成。 */
    private static void processMatchedCondition(ServerPlayer player, ServerDataStore store, UUID uuid,
            String advId, CustomAdvancement adv, AdvancementCondition cond, int condIndex,
            int amount, ProgressUpdater updater) {
        // Tick 级重入保护：同一 tick 内同一玩家的同一成就条件不重复评估（委托 DedupGuard）
        if (DEDUP_GUARD.shouldSkip(store.getServer(), uuid, advId, condIndex)) {
            return;
        }

        int current = store.getConditionProgress(uuid, advId, condIndex);
        int newProgress = updater.compute(current, cond.getCount(), amount);
        store.setConditionProgress(uuid, advId, condIndex, newProgress);

        NeoForge.EVENT_BUS.post(new AdvProgressEvent(player, advId, newProgress, cond.getCount()));

        if (allConditionsMet(uuid, advId, adv)) {
            CompletionHandler.tryComplete(player, advId);
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
