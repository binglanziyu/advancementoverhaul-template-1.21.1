package com.dreamer.ao.logic;

import com.dreamer.ao.ServerConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tick 级评估去重守卫。
 * <p>
 * 将原本散落在 {@code ConditionEvaluator} 中的静态可变状态
 * （{@code evaluatedKeys} 去重表）收敛为独立的单例状态对象，
 * 消除「静态工具类持有静态可变状态」的反模式。
 * <p>
 * <b>去重语义：</b>防止同一 tick 内 Mixin + Event 双重触发导致同一条件的重复评估。
 * 使用 ConcurrentHashMap 做 per-key 自包含的 tick 比较，消除 clear() 与 put() 之间的竞态窗口。
 */
public final class DedupGuard {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 单例：去重表为进程级共享状态，生命周期贯穿整个服务端运行期。 */
    private static final DedupGuard INSTANCE = new DedupGuard();
    public static DedupGuard getInstance() { return INSTANCE; }
    private DedupGuard() {}

    /**
     * 去重表键：玩家 + 进度 + 条件索引的不可变组合。
     * <p>
     * 相较此前的 {@code uuid + ":" + advId + ":" + condIndex} 字符串拼接，
     * record 避免了每次条件匹配都产生 StringBuilder 与 String 两个对象；
     * 同时相较将三者哈希折叠为单个 long，record 保留完整字段做 equals 比较，
     * 不存在哈希碰撞导致合法评估被静默跳过的正确性风险。
     */
    private record DedupKey(UUID uuid, String advId, int condIndex) {}

    /** Tick 级重入保护表。Value 为上次评估时的 tick 值，由 {@link #prune(long)} 周期驱逐。 */
    private final ConcurrentHashMap<DedupKey, Long> evaluatedKeys = new ConcurrentHashMap<>();

    /**
     * 检查并登记某条件在当前 tick 是否已被评估过。
     * <p>
     * 若 <b>同一 tick</b> 内同一玩家的同一成就条件已评估过，则返回 {@code true}（调用方应跳过），
     * 否则登记当前 tick 并返回 {@code false}。
     * <p>
     * 当 {@code server} 为 null（尚未绑定服务端）时，不进行去重，直接返回 {@code false} 放行。
     *
     * @param server   当前服务端实例（提供 tick 计数）
     * @param uuid     玩家 UUID
     * @param advId    成就 ID
     * @param condIndex 条件索引
     * @return true 表示应跳过本次评估（已重复）
     */
    public boolean shouldSkip(MinecraftServer server, UUID uuid, String advId, int condIndex) {
        if (server == null) return false;
        long currentTick = server.getTickCount();
        DedupKey dedupKey = new DedupKey(uuid, advId, condIndex);
        Long lastTick = evaluatedKeys.put(dedupKey, currentTick);
        if (lastTick != null && lastTick == currentTick) {
            LOGGER.debug("Skipping duplicate evaluation: {} @ tick {}", dedupKey, currentTick);
            return true;
        }
        return false;
    }

    /**
     * 驱逐超出保留窗口的去重条目，由服务端 tick 周期调用。
     * <p>
     * 去重语义只需覆盖「同一 tick」，因此任何早于
     * {@code currentTick - DEDUP_RETENTION_TICKS} 的条目都已无用。
     * 若无此清理，键空间会随「玩家 × 进度 × 条件」持续增长而无界泄漏。
     *
     * @param currentTick 当前服务端 tick
     */
    public void prune(long currentTick) {
        if (evaluatedKeys.isEmpty()) return;
        long cutoff = currentTick - ServerConstants.DEDUP_RETENTION_TICKS;
        // 服务器刚启动（tick < 保留窗口）时 cutoff 为负，此时无条目可能过期，跳过以免误删当前 tick 的守卫
        if (cutoff <= 0) return;
        // 同时剔除「记录 tick 晚于当前 tick」的条目：存档回退或 tick 计数重置会产生此类陈旧项
        evaluatedKeys.entrySet().removeIf(e -> e.getValue() < cutoff || e.getValue() > currentTick);
    }
}
