package com.example.advancementoverhaul.compat;

import com.example.advancementoverhaul.data.DataStore.ConditionType;
import com.example.advancementoverhaul.data.ServerDataStore;
import com.example.advancementoverhaul.logic.ConditionEvaluator;
import com.example.advancementoverhaul.network.FtbQuestCompletedPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FTB Quests 任务完成监听模块。
 * <p>
 * 从 {@link FtbQuestsBridge} 拆分而来，专门负责：
 * <ul>
 *   <li>通过 Architectury 事件或 Tick 轮询检测 FTB 任务完成</li>
 *   <li>触发自定义进度条件评估与级联释放</li>
 *   <li>管理 questCompletionCache（含 LRU 清理）</li>
 * </ul>
 */
public final class FtbQuestListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/QuestListener");

    /** Tick 计数器，每 20 tick 轮询一次 */
    private static int tickCounter = 0;
    private static final int POLL_INTERVAL = 20;

    /** 是否已注册事件监听 */
    private static volatile boolean eventListenerRegistered = false;

    /** QuestCompletedEvent 类是否已确认不存在 */
    private static volatile boolean questEventConfirmedMissing = false;

    /** 缓存 ServerQuestFile 引用 */
    private static Object cachedServerQuestFile = null;
    private static boolean sqfLookupAttempted = false;

    // ═══════════════ questCompletionCache (with LRU-based cleanup) ═══════════════

    /** 最大缓存玩家数，超过时清理离线玩家条目 */
    private static final int MAX_CACHED_PLAYERS = 256;

    /** 用于追踪玩家的 FTB 任务完成状态（玩家 UUID → 已完成的任务 ID 集合） */
    private static final Map<UUID, Set<String>> questCompletionCache = new ConcurrentHashMap<>();

    /**
     * 移除离线玩家的缓存条目。
     * 在 {@link #onPlayerLogout} 和缓存超限时调用。
     */
    public static void onPlayerLogout(UUID uuid) {
        questCompletionCache.remove(uuid);
    }

    /** 清理超出上限和过时的缓存条目。在 onServerTick 中定期调用。 */
    private static void cleanupCacheIfNeeded() {
        if (questCompletionCache.size() <= MAX_CACHED_PLAYERS) return;
        // 保留最近的 MAX_CACHED_PLAYERS/2 个条目
        int toRemove = questCompletionCache.size() - MAX_CACHED_PLAYERS / 2;
        Iterator<UUID> it = questCompletionCache.keySet().iterator();
        while (it.hasNext() && toRemove > 0) {
            it.next();
            it.remove();
            toRemove--;
        }
    }

    private FtbQuestListener() {}

    // ═══════════════ 服务端 Tick ═══════════════

    /**
     * 服务端 Tick 回调。
     * 定期通过 ServerQuestFile 轮询任务完成状态。
     */
    public static void onServerTick(MinecraftServer server) {
        if (!FtbQuestsBridge.isLoaded()) return;

        tickCounter++;
        if (tickCounter % POLL_INTERVAL != 0) return;

        // 定期清理缓存
        if (tickCounter % (POLL_INTERVAL * 300) == 0) { // ~5分钟
            cleanupCacheIfNeeded();
        }

        try {
            pollQuestCompletions(server);
        } catch (Exception e) {
            LOGGER.debug("Error polling FTB quest completions: {}", e.getMessage());
        }
    }

    /**
     * 注册 Architectury 事件监听（实时通道）。
     * 由 {@link FtbKsrSyncer#syncToKnownServerRegistries} 在首次同步后调用。
     */
    public static void tryRegisterEventListener(MinecraftServer server) {
        if (!FtbQuestsBridge.isLoaded()) return;
        if (eventListenerRegistered || questEventConfirmedMissing) return;
        eventListenerRegistered = registerArchitecturyEventListener();
    }

    // ═══════════════ 内部实现 ═══════════════

    private static boolean registerArchitecturyEventListener() {
        if (!FtbReflectionHelper.isInitialized()) return false;

        try {
            Object listener = createQuestCompletedListener();
            FtbReflectionHelper.registerEventListener(listener);
            LOGGER.info("Registered FTB Quests QuestCompletedEvent listener (real-time)");
            return true;
        } catch (Exception e) {
            questEventConfirmedMissing = true;
            LOGGER.warn("QuestCompletedEvent registration failed (FTB version: {}): {} — relying on tick polling",
                    FtbQuestsBridge.getFtbVersion(), e.getMessage());
        }
        return false;
    }

    private static Object createQuestCompletedListener() {
        return (java.util.function.Consumer<Object>) event -> {
            try {
                Object teamData = FtbReflectionHelper.getTeamDataFromEvent(event);
                if (teamData == null) return;

                var onlineMembers = FtbReflectionHelper.getOnlineMembers(teamData);
                if (onlineMembers.isEmpty()) return;

                Object quest = FtbReflectionHelper.getQuestFromEvent(event);
                if (quest == null) return;

                Object questId = FtbReflectionHelper.getQuestId(quest);
                String questIdStr = questId != null ? questId.toString() : "unknown";

                Component titleComp = FtbReflectionHelper.getQuestTitle(quest);
                String questDisplayName = titleComp != null ? titleComp.getString() : questIdStr;

                for (ServerPlayer player : onlineMembers) {
                    UUID uuid = player.getUUID();

                    Set<String> completed = questCompletionCache.computeIfAbsent(
                            uuid, k -> new HashSet<>());
                    if (completed.contains(questIdStr)) continue;
                    completed.add(questIdStr);

                    PacketDistributor.sendToPlayer(player,
                            new FtbQuestCompletedPayload(questDisplayName));

                    ConditionEvaluator.checkInstant(
                            player, ConditionType.FTB_QUEST_COMPLETE, questIdStr);
                    ConditionEvaluator.releasePendingDependents(player);

                    LOGGER.debug("FTB Quest completed (event): {} by player {}", questIdStr, uuid);
                }
            } catch (Throwable e) {
                LOGGER.debug("Error handling FTB quest completion event: {}", e.getMessage());
            }
        };
    }

    private static void pollQuestCompletions(MinecraftServer server) {
        Object sqf = getServerQuestFile();
        if (sqf == null) return;

        try {
            Map<?, ?> teamDataMapRaw = FtbReflectionHelper.getTeamDataMap(sqf);
            if (teamDataMapRaw == null || teamDataMapRaw.isEmpty()) return;

            @SuppressWarnings("unchecked")
            Map<UUID, Object> teamDataMap = (Map<UUID, Object>) teamDataMapRaw;

            Map<Long, Object> questObjectMap = FtbReflectionHelper.getQuestObjectMap(sqf);
            if (questObjectMap == null) return;

            for (Object teamData : teamDataMap.values()) {
                var onlineMembers = FtbReflectionHelper.getOnlineMembers(teamData);
                if (onlineMembers.isEmpty()) continue;

                Map<Long, Long> completed = FtbReflectionHelper.getTeamDataCompleted(teamData);
                if (completed == null || completed.isEmpty()) continue;

                for (Long questId : completed.keySet()) {
                    Object questObj = questObjectMap.get(questId);
                    if (questObj == null) continue;

                    String questIdStr;
                    Object qid = FtbReflectionHelper.getQuestId(questObj);
                    questIdStr = qid != null ? qid.toString() : questId.toString();

                    Component titleComp = FtbReflectionHelper.getQuestTitle(questObj);
                    String displayName = titleComp != null ? titleComp.getString() : questIdStr;

                    for (ServerPlayer player : onlineMembers) {
                        UUID uuid = player.getUUID();
                        Set<String> cached = questCompletionCache.computeIfAbsent(
                                uuid, k -> new HashSet<>());
                        if (cached.contains(questIdStr)) continue;
                        cached.add(questIdStr);

                        PacketDistributor.sendToPlayer(player,
                                new FtbQuestCompletedPayload(displayName));

                        ConditionEvaluator.checkInstant(
                                player, ConditionType.FTB_QUEST_COMPLETE, questIdStr);
                        ConditionEvaluator.releasePendingDependents(player);

                        LOGGER.debug("FTB Quest completed (poll): {} by player {}", questIdStr, uuid);
                    }
                }
            }
        } catch (Throwable e) {
            LOGGER.debug("Error in pollQuestCompletions: {}", e.getMessage());
        }
    }

    private static Object getServerQuestFile() {
        if (cachedServerQuestFile != null) return cachedServerQuestFile;
        if (sqfLookupAttempted) return null;

        if (FtbReflectionHelper.isInitialized()) {
            cachedServerQuestFile = FtbReflectionHelper.getServerQuestFileInstance();
        }
        sqfLookupAttempted = true;

        if (cachedServerQuestFile == null) {
            LOGGER.debug("Cannot get ServerQuestFile instance");
        }
        return cachedServerQuestFile;
    }
}
