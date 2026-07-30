/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonArray
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.entity.player.Player
 *  net.neoforged.neoforge.network.PacketDistributor
 *  net.neoforged.neoforge.network.handling.IPayloadContext
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.example.advancementoverhaul.network.handler;

import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.PlayerStats;
import com.example.advancementoverhaul.data.PlayerStatsStore;
import com.example.advancementoverhaul.network.payload.StatsRequestPayload;
import com.example.advancementoverhaul.network.payload.StatsSyncPayload;
import com.example.advancementoverhaul.network.payload.TimelineRequestPayload;
import com.example.advancementoverhaul.network.payload.TimelineSyncPayload;
import com.example.advancementoverhaul.milestone.store.TimelineStore;
import com.google.gson.JsonArray;
import java.util.UUID;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TimelineNetworkHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger((String)"AdvancementOverhaul/Network");

    private TimelineNetworkHandler() {
    }

    public static void handleTimelineRequest(TimelineRequestPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player2 = (ServerPlayer)player;
        context.enqueueWork(() -> {
            UUID uuid = player2.getUUID();
            JsonArray data = TimelineStore.getInstance().toSyncJson(uuid);
            PacketDistributor.sendToPlayer((ServerPlayer)player2, (CustomPacketPayload)new TimelineSyncPayload(data.toString()), (CustomPacketPayload[])new CustomPacketPayload[0]);
            LOGGER.debug("Timeline sync sent to {} ({} milestones)", (Object)player2.getName().getString(), (Object)data.size());
        });
    }

    public static void handleStatsRequest(StatsRequestPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player2 = (ServerPlayer)player;
        context.enqueueWork(() -> {
            UUID uuid = player2.getUUID();
            PlayerStats stats = PlayerStatsStore.getInstance().getOrCreate(uuid);
            String json = DataStore.GSON.toJson((Object)stats);
            LOGGER.debug("Stats request from {} \u2014 replying with {} bytes", (Object)player2.getName().getString(), (Object)json.length());
            PacketDistributor.sendToPlayer((ServerPlayer)player2, (CustomPacketPayload)new StatsSyncPayload(json), (CustomPacketPayload[])new CustomPacketPayload[0]);
        });
    }

    public static void pushStatsSync(ServerPlayer player) {
        PlayerStats stats = PlayerStatsStore.getInstance().getOrCreate(player.getUUID());
        String json = DataStore.GSON.toJson((Object)stats);
        LOGGER.debug("Pushing stats sync to {} ({} chars)", (Object)player.getName().getString(), (Object)json.length());
        PacketDistributor.sendToPlayer((ServerPlayer)player, (CustomPacketPayload)new StatsSyncPayload(json), (CustomPacketPayload[])new CustomPacketPayload[0]);
    }
}

