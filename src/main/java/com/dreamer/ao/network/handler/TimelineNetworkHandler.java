package com.dreamer.ao.network.handler;

import com.dreamer.ao.data.DataStore;
import com.dreamer.ao.data.PlayerStats;
import com.dreamer.ao.data.PlayerStatsStore;
import com.dreamer.ao.network.payload.StatsRequestPayload;
import com.dreamer.ao.network.payload.StatsSyncPayload;
import com.dreamer.ao.network.payload.TimelineRequestPayload;
import com.dreamer.ao.network.payload.TimelineSyncPayload;
import com.dreamer.ao.network.NetworkSender;
import com.dreamer.ao.milestone.store.TimelineStore;
import com.google.gson.JsonArray;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TimelineNetworkHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineNetworkHandler.class);

    private TimelineNetworkHandler() {
    }

    public static void handleTimelineRequest(TimelineRequestPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        context.enqueueWork(() -> {
            UUID uuid = serverPlayer.getUUID();
            JsonArray data = TimelineStore.getInstance().toSyncJson(uuid);
            NetworkSender.toPlayer(serverPlayer, new TimelineSyncPayload(data.toString()));
            LOGGER.debug("Timeline sync sent to {} ({} milestones)", serverPlayer.getName().getString(), data.size());
        });
    }

    public static void handleStatsRequest(StatsRequestPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        context.enqueueWork(() -> {
            UUID uuid = serverPlayer.getUUID();
            PlayerStats stats = PlayerStatsStore.getInstance().getOrCreate(uuid);
            String json = DataStore.GSON.toJson(stats);
            LOGGER.debug("Stats request from {} \u2014 replying with {} bytes", serverPlayer.getName().getString(), json.length());
            NetworkSender.toPlayer(serverPlayer, new StatsSyncPayload(json));
        });
    }

    public static void pushStatsSync(ServerPlayer player) {
        PlayerStats stats = PlayerStatsStore.getInstance().getOrCreate(player.getUUID());
        String json = DataStore.GSON.toJson(stats);
        LOGGER.debug("Pushing stats sync to {} ({} chars)", player.getName().getString(), json.length());
        NetworkSender.toPlayer(player, new StatsSyncPayload(json));
    }
}
