/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.server.level.ServerPlayer
 *  net.neoforged.neoforge.network.PacketDistributor
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.example.advancementoverhaul.compat.ftb;

import com.example.advancementoverhaul.compat.ftb.FtbQuestsBridge;
import com.example.advancementoverhaul.compat.ftb.FtbReflectionHelper;
import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.logic.ConditionEvaluator;
import com.example.advancementoverhaul.network.payload.FtbQuestCompletedPayload;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FtbQuestListener {
    private static final Logger LOGGER = LoggerFactory.getLogger((String)"AdvancementOverhaul/QuestListener");
    private static int tickCounter = 0;
    private static final int POLL_INTERVAL = 20;
    private static volatile boolean eventListenerRegistered = false;
    private static volatile boolean questEventConfirmedMissing = false;
    private static Object cachedServerQuestFile = null;
    private static boolean sqfLookupAttempted = false;
    private static final int MAX_CACHED_PLAYERS = 256;
    private static final Map<UUID, Set<String>> questCompletionCache = new ConcurrentHashMap<UUID, Set<String>>();

    public static void onPlayerLogout(UUID uuid) {
        questCompletionCache.remove(uuid);
    }

    private static void cleanupCacheIfNeeded() {
        if (questCompletionCache.size() <= 256) {
            return;
        }
        Iterator<UUID> it = questCompletionCache.keySet().iterator();
        for (int toRemove = questCompletionCache.size() - 128; it.hasNext() && toRemove > 0; --toRemove) {
            it.next();
            it.remove();
        }
    }

    private FtbQuestListener() {
    }

    public static void onServerTick(MinecraftServer server) {
        if (!FtbQuestsBridge.isLoaded()) {
            return;
        }
        if (++tickCounter % 20 != 0) {
            return;
        }
        if (tickCounter % 6000 == 0) {
            FtbQuestListener.cleanupCacheIfNeeded();
        }
        try {
            FtbQuestListener.pollQuestCompletions(server);
        }
        catch (Exception e) {
            LOGGER.debug("Error polling FTB quest completions: {}", (Object)e.getMessage());
        }
    }

    public static void tryRegisterEventListener(MinecraftServer server) {
        if (!FtbQuestsBridge.isLoaded()) {
            return;
        }
        if (eventListenerRegistered || questEventConfirmedMissing) {
            return;
        }
        eventListenerRegistered = FtbQuestListener.registerArchitecturyEventListener();
    }

    private static boolean registerArchitecturyEventListener() {
        if (!FtbReflectionHelper.isInitialized()) {
            return false;
        }
        try {
            Consumer<Object> listener = FtbQuestListener.createQuestCompletedListener();
            FtbReflectionHelper.registerEventListener(listener);
            LOGGER.info("Registered FTB Quests QuestCompletedEvent listener (real-time)");
            return true;
        }
        catch (Exception e) {
            questEventConfirmedMissing = true;
            LOGGER.warn("QuestCompletedEvent registration failed (FTB version: {}): {} \u2014 relying on tick polling", (Object)FtbQuestsBridge.getFtbVersion(), (Object)e.getMessage());
            return false;
        }
    }

    private static Consumer<Object> createQuestCompletedListener() {
        return event -> {
            try {
                Object teamData = FtbReflectionHelper.getTeamDataFromEvent(event);
                if (teamData == null) {
                    return;
                }
                Collection<ServerPlayer> onlineMembers = FtbReflectionHelper.getOnlineMembers(teamData);
                if (onlineMembers.isEmpty()) {
                    return;
                }
                Object quest = FtbReflectionHelper.getQuestFromEvent(event);
                if (quest == null) {
                    return;
                }
                Object questId = FtbReflectionHelper.getQuestId(quest);
                String questIdStr = questId != null ? questId.toString() : "unknown";
                Component titleComp = FtbReflectionHelper.getQuestTitle(quest);
                String questDisplayName = titleComp != null ? titleComp.getString() : questIdStr;
                for (ServerPlayer player : onlineMembers) {
                    UUID uuid = player.getUUID();
                    Set completed = questCompletionCache.computeIfAbsent(uuid, k -> new HashSet());
                    if (completed.contains(questIdStr)) continue;
                    completed.add(questIdStr);
                    PacketDistributor.sendToPlayer((ServerPlayer)player, (CustomPacketPayload)new FtbQuestCompletedPayload(questDisplayName), (CustomPacketPayload[])new CustomPacketPayload[0]);
                    ConditionEvaluator.checkInstant(player, DataStore.ConditionType.FTB_QUEST_COMPLETE, questIdStr);
                    ConditionEvaluator.releasePendingDependents(player);
                    LOGGER.debug("FTB Quest completed (event): {} by player {}", (Object)questIdStr, (Object)uuid);
                }
            }
            catch (Throwable e) {
                LOGGER.debug("Error handling FTB quest completion event: {}", (Object)e.getMessage());
            }
        };
    }

    private static void pollQuestCompletions(MinecraftServer server) {
        Object sqf = FtbQuestListener.getServerQuestFile();
        if (sqf == null) {
            return;
        }
        try {
            Map<?, ?> teamDataMapRaw = FtbReflectionHelper.getTeamDataMap(sqf);
            if (teamDataMapRaw == null || teamDataMapRaw.isEmpty()) {
                return;
            }
            Map<?, ?> teamDataMap = teamDataMapRaw;
            Map<Long, Object> questObjectMap = FtbReflectionHelper.getQuestObjectMap(sqf);
            if (questObjectMap == null) {
                return;
            }
            for (Object teamData : teamDataMap.values()) {
                Map<Long, Long> completed;
                Collection<ServerPlayer> onlineMembers = FtbReflectionHelper.getOnlineMembers(teamData);
                if (onlineMembers.isEmpty() || (completed = FtbReflectionHelper.getTeamDataCompleted(teamData)) == null || completed.isEmpty()) continue;
                for (Long questId : completed.keySet()) {
                    Object questObj = questObjectMap.get(questId);
                    if (questObj == null) continue;
                    Object qid = FtbReflectionHelper.getQuestId(questObj);
                    String questIdStr = qid != null ? qid.toString() : questId.toString();
                    Component titleComp = FtbReflectionHelper.getQuestTitle(questObj);
                    String displayName = titleComp != null ? titleComp.getString() : questIdStr;
                    for (ServerPlayer player : onlineMembers) {
                        UUID uuid = player.getUUID();
                        Set cached = questCompletionCache.computeIfAbsent(uuid, k -> new HashSet());
                        if (cached.contains(questIdStr)) continue;
                        cached.add(questIdStr);
                        PacketDistributor.sendToPlayer((ServerPlayer)player, (CustomPacketPayload)new FtbQuestCompletedPayload(displayName), (CustomPacketPayload[])new CustomPacketPayload[0]);
                        ConditionEvaluator.checkInstant(player, DataStore.ConditionType.FTB_QUEST_COMPLETE, questIdStr);
                        ConditionEvaluator.releasePendingDependents(player);
                        LOGGER.debug("FTB Quest completed (poll): {} by player {}", (Object)questIdStr, (Object)uuid);
                    }
                }
            }
        }
        catch (Throwable e) {
            LOGGER.debug("Error in pollQuestCompletions: {}", (Object)e.getMessage());
        }
    }

    private static Object getServerQuestFile() {
        if (cachedServerQuestFile != null) {
            return cachedServerQuestFile;
        }
        if (sqfLookupAttempted) {
            return null;
        }
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

