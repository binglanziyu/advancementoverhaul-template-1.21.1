package com.example.advancementoverhaul.narrative.event;

import com.example.advancementoverhaul.data.PlayerStats;
import com.example.advancementoverhaul.data.PlayerStatsStore;
import com.example.advancementoverhaul.network.payload.MonologuePayload;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public class MonologueEventHandler {
    private static final Map<UUID, Double> lastDistances = new ConcurrentHashMap<UUID, Double>();
    private static final Map<UUID, Integer> lastLowestY = new ConcurrentHashMap<UUID, Integer>();
    private static final Map<UUID, Integer> lastHighestY = new ConcurrentHashMap<UUID, Integer>();

    public static void sendMonologueToPlayer(ServerPlayer player, String category) {
        MonologueEventHandler.sendMonologue(player, category);
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        LivingEntity livingEntity = event.getEntity();
        if (!(livingEntity instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player = (ServerPlayer)livingEntity;
        PlayerStats stats = PlayerStatsStore.getInstance().getOrCreate(player.getUUID());
        if (!stats.firstDeathRecorded) {
            MonologueEventHandler.sendMonologue(player, "death");
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player2 = (ServerPlayer)player;
        PlayerStats stats = PlayerStatsStore.getInstance().getOrCreate(player2.getUUID());
        String dimPath = event.getTo().location().getPath();
        if ("the_nether".equals(dimPath) && stats.firstNetherDay > 0) {
            MonologueEventHandler.sendMonologue(player2, "nether");
        } else if ("the_end".equals(dimPath) && stats.firstEndDay > 0) {
            MonologueEventHandler.sendMonologue(player2, "end");
        }
    }

    public static void checkMilestones(ServerPlayer player, PlayerStats stats) {
        int lastMilestone;
        int currentMilestone;
        UUID uuid = player.getUUID();
        double currentDist = stats.furthestDistance;
        Double lastDist = lastDistances.get(uuid);
        if (lastDist == null) {
            lastDist = 0.0;
        }
        if ((currentMilestone = (int)(currentDist / 500.0)) > (lastMilestone = (int)(lastDist / 500.0)) && currentMilestone > 0) {
            MonologueEventHandler.sendMonologue(player, "distance");
        }
        lastDistances.put(uuid, currentDist);
        if (stats.hasLowestY()) {
            int lowest = stats.lowestY;
            Integer lastLow = lastLowestY.getOrDefault(uuid, Integer.MAX_VALUE);
            int currentDepthMilestone = Math.abs(lowest) / 16;
            int lastDepthMilestone = Math.abs(lastLow) / 16;
            if (MonologueEventHandler.lowerThan(lowest, lastLow) && currentDepthMilestone > lastDepthMilestone) {
                MonologueEventHandler.sendMonologue(player, "depth");
            }
            lastLowestY.put(uuid, lowest);
        }
        if (stats.hasHighestY()) {
            int highest = stats.highestY;
            Integer lastHigh = lastHighestY.getOrDefault(uuid, Integer.MIN_VALUE);
            int currentHeightMilestone = Math.abs(highest) / 32;
            int lastHeightMilestone = Math.abs(lastHigh) / 32;
            if (highest > lastHigh && currentHeightMilestone > lastHeightMilestone) {
                MonologueEventHandler.sendMonologue(player, "height");
            }
            lastHighestY.put(uuid, highest);
        }
    }

    private static boolean lowerThan(int a, int b) {
        if (b == Integer.MAX_VALUE) {
            return true;
        }
        return a < b;
    }

    private static void sendMonologue(ServerPlayer player, String category) {
        PacketDistributor.sendToPlayer((ServerPlayer)player, (CustomPacketPayload)new MonologuePayload(category), (CustomPacketPayload[])new CustomPacketPayload[0]);
    }
}
