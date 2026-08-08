package com.dreamer.ao.achievement.event;

import com.dreamer.ao.LangKeys;
import com.dreamer.ao.ServerConstants;
import com.dreamer.ao.data.DimensionLock;
import com.dreamer.ao.data.ServerDataStore;
import com.dreamer.ao.data.model.CustomAdvancement;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 维度锁定检查与遣返处理。
 * <p>
 * 由 {@link ServerEventHandler} 的 {@code @SubscribeEvent} 方法委托调用，
 * 本类不直接订阅事件。
 *
 * <h2>两道防线</h2>
 * <ol>
 *   <li><b>传送前拦截</b>（{@link #onEntityTravelToDimension}）—— 基于
 *       {@link EntityTravelToDimensionEvent}，在传送发生前取消，坐标来自源维度，
 *       是原版流程下的主要防线。</li>
 *   <li><b>切换后校验</b>（{@link #verifyAfterDimensionChange}）—— 基于
 *       {@code PlayerChangedDimensionEvent}，在玩家已落地后复查，
 *       缓解第三方 mod 直接调用传送 API 绕过前置事件的情形。</li>
 * </ol>
 *
 * <h2>已知限制（问题 #18）</h2>
 * 第二道防线属于<b>事后补救</b>而非拦截：玩家会短暂出现在锁定维度中（通常不足一 tick 的可感知时间），
 * 随后被遣返。这意味着极端情况下仍可能观察到锁定维度的一帧画面，或触发落地瞬间的方块交互。
 * 完全杜绝需要接管所有传送入口（含各 mod 的自定义传送器），耦合与维护成本过高，
 * 当前双防线在正常游戏流程与常见 mod 传送场景下已足够可靠。
 */
final class DimensionLockHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DimensionLockHandler.class);

    /** 维度锁定冷却时间表（防止同一 tick 内多次弹出消息） */
    private static final ConcurrentHashMap<UUID, Long> lastDimLockTime = new ConcurrentHashMap<>();

    private DimensionLockHandler() {}

    /** 玩家登出时回收其冷却记录。 */
    static void onPlayerLogout(UUID uuid) {
        lastDimLockTime.remove(uuid);
    }

    /** 周期性驱逐过期的冷却条目（兜底，正常回收由登出完成）。 */
    static void pruneCooldowns() {
        long cutoff = System.currentTimeMillis() - ServerConstants.DIM_LOCK_CLEANUP_TIMEOUT_MS;
        lastDimLockTime.values().removeIf(t -> t < cutoff);
    }

    /**
     * 维度传送前检查 — 如果目标维度被锁定且玩家不满足解锁条件，
     * 取消传送并将玩家传送到当前维度的安全位置（优先重生点，其次原地附近地面）。
     * <p>
     * 使用 {@link EntityTravelToDimensionEvent}（传送前触发）确保坐标来自源维度。
     */
    static void onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ResourceKey<Level> targetDim = event.getDimension();
        ServerDataStore store = ServerDataStore.getInstance();
        String reqAdv = lockedRequirement(store, player, targetDim.location().toString());
        if (reqAdv == null) return;

        if (!tryAcquireMessageCooldown(player.getUUID())) return;

        // 取消传送 — 玩家仍在源维度，坐标正确
        event.setCanceled(true);
        relocateToSafePosition(player);
        notifyBlocked(player, store, reqAdv);
    }

    /**
     * 维度切换后的兜底校验 — 缓解绕过 {@link EntityTravelToDimensionEvent} 的传送。
     * <p>
     * 当第三方 mod 直接调用传送 API 时，前置事件不会触发，玩家会直接落地在锁定维度。
     * 此处在落地后复查：若当前维度处于锁定状态且条件未满足，将玩家遣返回源维度的安全位置。
     *
     * @param player 已完成维度切换的玩家
     * @param from   源维度
     */
    static void verifyAfterDimensionChange(ServerPlayer player, ResourceKey<Level> from) {
        ServerDataStore store = ServerDataStore.getInstance();
        ServerLevel current = player.serverLevel();
        String reqAdv = lockedRequirement(store, player, current.dimension().location().toString());
        if (reqAdv == null) return;

        ServerLevel origin = player.server.getLevel(from);
        if (origin == null) {
            LOGGER.warn("Cannot repatriate {} from locked dimension {}: source dimension {} unavailable",
                    player.getName().getString(), current.dimension().location(), from.location());
            return;
        }

        LOGGER.info("Repatriating {} from locked dimension {} (bypassed travel event)",
                player.getName().getString(), current.dimension().location());

        int groundY = findSafeGround(origin, (int) player.getX(), (int) player.getZ(), (int) player.getY());
        double ty = groundY != Integer.MIN_VALUE ? groundY : player.getY();
        player.teleportTo(origin, player.getX(), ty, player.getZ(), player.getYRot(), player.getXRot());

        if (tryAcquireMessageCooldown(player.getUUID())) {
            notifyBlocked(player, store, reqAdv);
        }
    }

    // ── 内部辅助 ──

    /**
     * 判断指定维度对该玩家是否处于锁定状态。
     *
     * @return 解锁所需的进度 ID（可能为空字符串表示无条件锁定）；未锁定或已满足条件时返回 {@code null}
     */
    private static String lockedRequirement(ServerDataStore store, ServerPlayer player, String dimId) {
        DimensionLock lock = store.getDimensionLock(dimId);
        if (lock == null || !lock.isLocked()) return null;

        String reqAdv = lock.getUnlockAdvancementId();
        boolean allowed = reqAdv != null && !reqAdv.isEmpty()
                && store.isCompleted(player.getUUID(), reqAdv);
        if (allowed) return null;

        return reqAdv != null ? reqAdv : "";
    }

    /**
     * 尝试获取消息冷却许可，防止同一 tick 内重复弹出提示。
     *
     * @return true 表示允许本次提示
     */
    private static boolean tryAcquireMessageCooldown(UUID playerUuid) {
        long now = System.currentTimeMillis();
        Long last = lastDimLockTime.get(playerUuid);
        if (last != null && now - last < ServerConstants.DIM_LOCK_COOLDOWN_MS) return false;

        // 超过软上限时在写入路径立即驱逐，不等待周期清理（高并发下防止条目累积）
        if (lastDimLockTime.size() > ServerConstants.DIM_LOCK_MAX_SIZE) {
            long evictBefore = now - ServerConstants.DIM_LOCK_CLEANUP_TIMEOUT_MS;
            lastDimLockTime.values().removeIf(t -> t < evictBefore);
        }
        lastDimLockTime.put(playerUuid, now);
        return true;
    }

    /** 将玩家移动到当前维度的安全位置（优先有效重生点，其次原地附近地面）。 */
    private static void relocateToSafePosition(ServerPlayer player) {
        ServerLevel currentLevel = player.serverLevel();
        double tx = player.getX();
        double ty = player.getY();
        double tz = player.getZ();

        BlockPos respawnPos = player.getRespawnPosition();
        if (respawnPos != null && player.getRespawnDimension() == currentLevel.dimension()
                && isValidRespawnAnchor(currentLevel, respawnPos)) {
            tx = respawnPos.getX() + 0.5;
            ty = respawnPos.getY() + 1.0;
            tz = respawnPos.getZ() + 0.5;
        } else {
            int groundY = findSafeGround(currentLevel, (int) tx, (int) tz, (int) ty);
            if (groundY != Integer.MIN_VALUE) ty = groundY;
        }

        player.teleportTo(currentLevel, tx, ty, tz, player.getYRot(), player.getXRot());
    }

    /** 重生点是否为有效的床或已充能的重生锚。 */
    private static boolean isValidRespawnAnchor(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BedBlock) return true;
        return state.getBlock() instanceof RespawnAnchorBlock
                && state.getValue(RespawnAnchorBlock.CHARGE) > 0;
    }

    /** 通知玩家维度被锁定及所需进度。 */
    private static void notifyBlocked(ServerPlayer player, ServerDataStore store, String reqAdv) {
        player.sendSystemMessage(Component.translatable(LangKeys.DIM_LOCKED_MSG));
        if (reqAdv != null && !reqAdv.isEmpty()) {
            CustomAdvancement req = store.getAdvancement(reqAdv);
            String name = req != null ? req.getName() : reqAdv;
            player.sendSystemMessage(Component.translatable(LangKeys.NEED_ADV_MSG, name));
        }
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
        for (int dy = 0; dy < ServerConstants.SAFE_GROUND_SCAN_RANGE; dy++) {
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
