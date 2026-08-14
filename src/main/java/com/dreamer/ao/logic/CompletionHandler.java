package com.dreamer.ao.logic;

import com.dreamer.ao.LangKeys;
import com.dreamer.ao.ServerConstants;
import com.dreamer.ao.compat.AdvancementRegistry;
import com.dreamer.ao.data.DataStore;
import com.dreamer.ao.data.ServerDataStore;
import com.dreamer.ao.data.model.CustomAdvancement;
import com.dreamer.ao.achievement.event.AdvCompletedEvent;
import com.dreamer.ao.network.payload.ProgressSyncPayload;
import com.dreamer.ao.network.NetworkSender;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 进度完成与级联释放处理器。
 * <p>
 * 从 {@link ConditionEvaluator} 中剥离出的「完成判定 / 奖励授予 / 级联释放」职责，
 * 使评估器专注于「事件 → 条件匹配 → 进度更新」，不再混合完成逻辑。
 * <p>
 * <b>级联边界保障：</b>{@link #tryComplete} 和 {@link #tryCompleteForce} 在
 * {@link #doComplete} 之后均调用 {@link #releasePendingDependents}。
 * 此外 FtbQuestListener、AdvCrudExecutor、AdvancementAPI 等外部完成入口
 * 也通过 tryComplete/tryCompleteForce → 本方法实现级联。因此无论触发来源为何，
 * 级联释放始终生效。
 */
public final class CompletionHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 级联深度上限。详见 {@link ServerConstants#MAX_CASCADE_DEPTH}。
     */
    private static final int MAX_CASCADE_DEPTH = ServerConstants.MAX_CASCADE_DEPTH;

    private CompletionHandler() {}

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
                NetworkSender.toPlayer(player,
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
        NetworkSender.toPlayer(player,
                new ProgressSyncPayload(advId, true, progress));

        AdvancementRegistry.grantAdvancement(player, advId);

        CustomAdvancement adv = store.getAdvancement(advId);
        String advName = adv != null ? adv.getName() : advId;
        NeoForge.EVENT_BUS.post(new AdvCompletedEvent(player, advId, advName));
    }

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
}
