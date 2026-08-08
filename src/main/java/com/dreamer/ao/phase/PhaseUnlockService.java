package com.dreamer.ao.phase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.dreamer.ao.data.ServerDataStore;
import com.dreamer.ao.network.payload.PhaseSyncPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 阶段解锁与服务协调器。
 * <p>
 * 负责：
 * <ul>
 *   <li>里程碑完成 → 解锁关联阶段</li>
 *   <li>OP 强制切换 / 施加临时 / 清除临时</li>
 *   <li>重算三层叠加效果并真实生效 + 向玩家同步</li>
 * </ul>
 */
public final class PhaseUnlockService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PhaseUnlockService.class);
    private static PhaseUnlockService INSTANCE;

    private PhaseUnlockService() {
    }

    public static PhaseUnlockService get() {
        if (INSTANCE == null) {
            INSTANCE = new PhaseUnlockService();
        }
        return INSTANCE;
    }

    /** 里程碑完成：解锁关联阶段 */
    public void onMilestoneCompleted(ServerPlayer player, String milestoneId) {
        List<PhaseDefinition> toUnlock = PhaseRegistry.get().phasesUnlockedByMilestone(milestoneId);
        for (PhaseDefinition def : toUnlock) {
            if (ServerDataStore.getInstance().isPhaseUnlocked(def.getId())) {
                continue;
            }
            ServerDataStore.getInstance().unlockPhase(def.getId());
            def.getState().setUnlocked(true);
            def.getState().addHistory("解锁于里程碑: " + milestoneId);
            LOGGER.info("阶段解锁: {} (里程碑 {})", def.getId(), milestoneId);
        }
        if (!toUnlock.isEmpty()) {
            recomputeAndSync(player);
        }
    }

    /** OP 强制切换阶段（按作用域） */
    public void forcePhase(ServerPlayer player, String scope, String phaseId) {
        if (!PhaseRegistry.get().getById(phaseId).isPresent()) {
            return;
        }
        ServerDataStore sds = ServerDataStore.getInstance();
        sds.unlockPhase(phaseId);
        switch (scope) {
            case "world" -> sds.setWorldPhase(phaseId);
            case "dimension" -> {
                ResourceLocation dim = player.level().dimension().location();
                sds.setDimensionPhase(dim.toString(), phaseId);
            }
            case "player" -> sds.setPlayerPhase(player.getUUID(), phaseId);
            default -> { /* 未知作用域忽略 */ }
        }
        recomputeAndSync(player);
    }

    /** OP 施加临时阶段（带过期秒数，0=不过期） */
    public void applyTemp(ServerPlayer player, String phaseId, int seconds) {
        if (!PhaseRegistry.get().getById(phaseId).isPresent()) {
            return;
        }
        long expire = seconds <= 0 ? 0 : System.currentTimeMillis() + seconds * 1000L;
        ServerDataStore.getInstance().setTempPhase(player.getUUID(), phaseId, expire);
        recomputeAndSync(player);
    }

    /** OP 清除临时阶段 */
    public void clearTemp(ServerPlayer player) {
        ServerDataStore.getInstance().setTempPhase(player.getUUID(), null, 0);
        recomputeAndSync(player);
    }

    /** 重算当前玩家的三层叠加效果并应用 + 同步 */
    public void recomputeAndSync(ServerPlayer player) {
        ServerDataStore sds = ServerDataStore.getInstance();
        PhaseRegistry reg = PhaseRegistry.get();

        // 全局（世界）层
        PhaseEffectSet worldSet = currentWorldEffects(sds, reg);
        // 维度层
        ResourceLocation dim = player.level().dimension().location();
        PhaseEffectSet dimSet = currentDimensionEffects(sds, reg, dim.toString());
        // 玩家层（当前玩家阶段 + 临时阶段）
        PhaseEffectSet playerSet = currentPlayerEffects(sds, reg, player);

        // 维度级怪物效果（全局+维度两层合并，供 EntitySpawn 事件）
        PhaseEffectCalculator.ComputedEffects dimComputed = PhaseEffectCalculator.compute(worldSet, dimSet);
        PhaseEffectApplier.get().setDimensionEffects(dimComputed);

        // 玩家级（全局+维度+玩家三层合并）
        PhaseEffectCalculator.ComputedEffects playerComputed =
                PhaseEffectCalculator.compute(worldSet, dimSet, playerSet);
        PhaseEffectApplier.get().applyToPlayer(player, playerComputed);

        syncTo(player);
    }

    private PhaseEffectSet currentWorldEffects(ServerDataStore sds, PhaseRegistry reg) {
        String id = sds.getWorldPhase();
        if (id == null) {
            return null;
        }
        return reg.getById(id).map(PhaseDefinition::getEffects).orElse(null);
    }

    private PhaseEffectSet currentDimensionEffects(ServerDataStore sds, PhaseRegistry reg, String dim) {
        String id = sds.getDimensionPhase(dim);
        if (id == null) {
            return null;
        }
        return reg.getById(id).map(PhaseDefinition::getEffects).orElse(null);
    }

    private PhaseEffectSet currentPlayerEffects(ServerDataStore sds, PhaseRegistry reg, ServerPlayer player) {
        long now = System.currentTimeMillis();
        String id = null;
        if (sds.hasActiveTempPhase(player.getUUID(), now)) {
            id = sds.getTempPhase(player.getUUID());
        } else {
            id = sds.getPlayerPhase(player.getUUID());
        }
        if (id == null) {
            return null;
        }
        return reg.getById(id).map(PhaseDefinition::getEffects).orElse(null);
    }

    /** 向玩家同步阶段态 */
    private void syncTo(ServerPlayer player) {
        ServerDataStore sds = ServerDataStore.getInstance();
        List<String> unlocked = new ArrayList<>(sds.getUnlockedPhases());
        List<String> briefs = new ArrayList<>();
        for (PhaseDefinition def : PhaseRegistry.get().all()) {
            briefs.add(PhaseSyncPayload.defToBrief(def));
        }
        PhaseSyncPayload payload = new PhaseSyncPayload(
                sds.getWorldPhase(),
                sds.getAllDimensionPhases(),
                sds.getPlayerPhase(player.getUUID()),
                sds.getTempPhase(player.getUUID()),
                unlocked,
                briefs
        );
        PacketDistributor.sendToPlayer(player, payload);
    }
}
