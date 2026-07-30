package com.example.advancementoverhaul.milestone.event;

import com.example.advancementoverhaul.data.PlayerStats;
import com.example.advancementoverhaul.data.ServerDataStore;
import com.example.advancementoverhaul.milestone.model.MilestoneTrigger;
import com.example.advancementoverhaul.milestone.store.StatValueStore;
import com.example.advancementoverhaul.milestone.store.TimelineDefinitionLoader;
import com.example.advancementoverhaul.milestone.store.TimelineStore;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimelineEventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/TimelineEvent");
    private static final Set<UUID> sunriseChecked = new HashSet<>();
    private static final Set<UUID> sunsetChecked = new HashSet<>();
    private static final Map<UUID, BlockPos> rainTravelStartPos = new HashMap<>();
    private static final Map<UUID, Boolean> rainTravelNotified = new HashMap<>();
    private static final Set<UUID> wasSleeping = new HashSet<>();
    private static int syncTickCounter;
    private static final int SYNC_INTERVAL_TICKS = 600;

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        TimelineStore.getInstance().init(server.getWorldPath(LevelResource.ROOT).resolve("advancement_overhaul"));
        TimelineDefinitionLoader.getInstance().init(FMLPaths.CONFIGDIR.get());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer player2)) {
            return;
        }
        UUID uuid = player2.getUUID();
        TimelineStore.getInstance().loadPlayer(uuid);
        long gameTime = player2.serverLevel().getGameTime();
        int gameDay = PlayerStats.gameDay(gameTime);
        MilestoneChecker.checkAndUnlock(player2, uuid, gameDay, gameTime, def -> def.getTrigger() == MilestoneTrigger.WORLD_JOIN, null);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        sunriseChecked.remove(uuid);
        sunsetChecked.remove(uuid);
        rainTravelStartPos.remove(uuid);
        rainTravelNotified.remove(uuid);
        TimelineStore.getInstance().savePlayer(uuid);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TimelineStore.getInstance().saveAll();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        boolean doSync = ++syncTickCounter >= SYNC_INTERVAL_TICKS;
        MinecraftServer server = ServerDataStore.getInstance().getServer();
        if (server == null) {
            return;
        }
        long gameTime = server.overworld().getGameTime();
        int gameDay = PlayerStats.gameDay(gameTime);
        boolean isSunrise = gameTime % 24000L >= 22800L && gameTime % 24000L <= 23200L;
        boolean isSunset = gameTime % 24000L >= 12700L && gameTime % 24000L <= 13100L;
        boolean isRaining = server.overworld().isRaining();
        TimelineStore timelineStore = TimelineStore.getInstance();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            ServerLevel level = player.serverLevel();
            BlockPos pos = player.blockPosition();
            StatValueStore stats = timelineStore.getOrCreateStats(uuid);

            if (isSunrise && level.canSeeSky(pos) && !sunriseChecked.contains(uuid)) {
                float yaw = normalizeYaw(player.getYRot());
                if (yaw >= -120.0f && yaw <= -60.0f) {
                    sunriseChecked.add(uuid);
                    long newVal = stats.incrementCounter("sunrises_viewed", 1L);
                    MilestoneChecker.checkAndUnlock(player, uuid, gameDay, gameTime, def -> def.getTrigger() == MilestoneTrigger.SUNRISE_VIEWED, null);
                    MilestoneChecker.checkCounter(player, uuid, gameDay, gameTime, "sunrises_viewed", newVal);
                }
            }
            if (!isSunrise) {
                sunriseChecked.remove(uuid);
            }

            if (isSunset && level.canSeeSky(pos) && !sunsetChecked.contains(uuid)) {
                float yaw = normalizeYaw(player.getYRot());
                if (yaw >= 60.0f && yaw <= 120.0f) {
                    sunsetChecked.add(uuid);
                    long newVal = stats.incrementCounter("sunsets_viewed", 1L);
                    MilestoneChecker.checkAndUnlock(player, uuid, gameDay, gameTime, def -> def.getTrigger() == MilestoneTrigger.SUNSET_VIEWED, null);
                    MilestoneChecker.checkCounter(player, uuid, gameDay, gameTime, "sunsets_viewed", newVal);
                }
            }
            if (!isSunset) {
                sunsetChecked.remove(uuid);
            }

            BlockPos spawn = level.getSharedSpawnPos();
            double dist = Math.sqrt(Math.pow(player.getX() - (double) spawn.getX(), 2.0) + Math.pow(player.getZ() - (double) spawn.getZ(), 2.0));
            long oldDist = stats.getCounter("furthest_distance");
            if (dist > (double) oldDist) {
                stats.setCounter("furthest_distance", (long) dist);
                MilestoneChecker.checkCounter(player, uuid, gameDay, gameTime, "furthest_distance", (long) dist);
            }

            checkRainTravel(player, uuid, gameDay, gameTime, isRaining, pos);

            if (isRaining && level.canSeeSky(pos)) {
                if (level.getBiome(pos).value().coldEnoughToSnow(pos)) {
                    long val = stats.incrementCounter("snow_ticks", 1L);
                    MilestoneChecker.checkCounter(player, uuid, gameDay, gameTime, "snow_ticks", val);
                } else if (level.getBiome(pos).value().hasPrecipitation()) {
                    long val = stats.incrementCounter("rain_ticks", 1L);
                    MilestoneChecker.checkCounter(player, uuid, gameDay, gameTime, "rain_ticks", val);
                }
            }

            if (syncTickCounter % 100 == 0) {
                scanInventoryMilestones(player, uuid, gameDay, gameTime);
            }
        }
        if (doSync) {
            syncTickCounter = 0;
            MilestoneChecker.syncTimelineToAll(server.getPlayerList().getPlayers());
        }
    }

    private static void checkRainTravel(ServerPlayer player, UUID uuid, int gameDay, long gameTime, boolean isRaining, BlockPos pos) {
        if (isRaining) {
            rainTravelStartPos.putIfAbsent(uuid, pos);
            BlockPos startPos = rainTravelStartPos.get(uuid);
            double rainDist = Math.sqrt(Math.pow(pos.getX() - startPos.getX(), 2.0) + Math.pow(pos.getZ() - startPos.getZ(), 2.0));
            long time = gameTime % 24000L;
            boolean isNight = time >= 13000L && time <= 23000L;
            if (isNight && rainDist >= 500.0 && !rainTravelNotified.containsKey(uuid)) {
                rainTravelNotified.put(uuid, true);
                MilestoneChecker.checkAndUnlock(player, uuid, gameDay, gameTime, def -> def.getTrigger() == MilestoneTrigger.RAIN_NIGHT_TRAVEL, null);
            }
        } else {
            rainTravelStartPos.remove(uuid);
            rainTravelNotified.remove(uuid);
        }
    }

    private static void scanInventoryMilestones(ServerPlayer player, UUID uuid, int gameDay, long gameTick) {
        HashSet<String> heldItems = new HashSet<>();
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) {
                heldItems.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (!stack.isEmpty()) {
                heldItems.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            }
        }
        MilestoneChecker.scanInventory(player, uuid, gameDay, gameTick, heldItems);
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        LivingEntity livingEntity = event.getEntity();
        if (!(livingEntity instanceof ServerPlayer player)) {
            return;
        }
        UUID uuid = player.getUUID();
        long gameTime = player.serverLevel().getGameTime();
        int gameDay = PlayerStats.gameDay(gameTime);
        MilestoneChecker.checkAndUnlock(player, uuid, gameDay, gameTime, def -> def.getTrigger() == MilestoneTrigger.FIRST_DEATH, null);
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer player2)) {
            return;
        }
        UUID uuid = player2.getUUID();
        long gameTime = player2.serverLevel().getGameTime();
        int gameDay = PlayerStats.gameDay(gameTime);
        String dim = event.getTo().location().toString();
        MilestoneChecker.checkAndUnlock(player2, uuid, gameDay, gameTime, def -> def.getTrigger() == MilestoneTrigger.FIRST_DIMENSION && (def.getTriggerParam() == null || def.getTriggerParam().equals(dim)), dim);
    }

    @SubscribeEvent
    public static void onAnimalTame(AnimalTameEvent event) {
        Player player = event.getTamer();
        if (!(player instanceof ServerPlayer player2)) {
            return;
        }
        UUID uuid = player2.getUUID();
        long gameTime = player2.serverLevel().getGameTime();
        int gameDay = PlayerStats.gameDay(gameTime);
        StatValueStore stats = TimelineStore.getInstance().getOrCreateStats(uuid);
        long val = stats.incrementCounter("animals_tamed", 1L);
        MilestoneChecker.checkAndUnlock(player2, uuid, gameDay, gameTime, def -> def.getTrigger() == MilestoneTrigger.FIRST_TAME, null);
        MilestoneChecker.checkCounter(player2, uuid, gameDay, gameTime, "animals_tamed", val);
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        UUID uuid = player.getUUID();
        long gameTime = player.serverLevel().getGameTime();
        int gameDay = PlayerStats.gameDay(gameTime);
        StatValueStore stats = TimelineStore.getInstance().getOrCreateStats(uuid);
        long val = stats.incrementCounter("blocks_placed", 1L);
        MilestoneChecker.checkAndUnlock(player, uuid, gameDay, gameTime, def -> def.getTrigger() == MilestoneTrigger.FIRST_BLOCK_PLACE, null);
        MilestoneChecker.checkCounter(player, uuid, gameDay, gameTime, "blocks_placed", val);
        if (event.getState().getBlock() instanceof TorchBlock) {
            long tVal = stats.incrementCounter("torches_placed", 1L);
            MilestoneChecker.checkCounter(player, uuid, gameDay, gameTime, "torches_placed", tVal);
        }
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer player2)) {
            return;
        }
        UUID uuid = player2.getUUID();
        long gameTime = player2.serverLevel().getGameTime();
        int gameDay = PlayerStats.gameDay(gameTime);
        StatValueStore stats = TimelineStore.getInstance().getOrCreateStats(uuid);
        long val = stats.incrementCounter("items_crafted", 1L);
        MilestoneChecker.checkCounter(player2, uuid, gameDay, gameTime, "items_crafted", val);
    }

    @SubscribeEvent
    public static void onLightningStrike(EntityStruckByLightningEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        UUID uuid = player.getUUID();
        long gameTime = player.serverLevel().getGameTime();
        int gameDay = PlayerStats.gameDay(gameTime);
        StatValueStore stats = TimelineStore.getInstance().getOrCreateStats(uuid);
        long val = stats.incrementCounter("lightning_strikes", 1L);
        MilestoneChecker.checkAndUnlock(player, uuid, gameDay, gameTime, def -> def.getTrigger() == MilestoneTrigger.FIRST_LIGHTNING, null);
        MilestoneChecker.checkCounter(player, uuid, gameDay, gameTime, "lightning_strikes", val);
    }

    @SubscribeEvent
    public static void onFallDamage(LivingFallEvent event) {
        LivingEntity livingEntity = event.getEntity();
        if (!(livingEntity instanceof ServerPlayer player)) {
            return;
        }
        if (event.getDistance() <= 3.0f) {
            return;
        }
        UUID uuid = player.getUUID();
        long gameTime = player.serverLevel().getGameTime();
        int gameDay = PlayerStats.gameDay(gameTime);
        StatValueStore stats = TimelineStore.getInstance().getOrCreateStats(uuid);
        long val = stats.incrementCounter("fall_damage_events", 1L);
        MilestoneChecker.checkCounter(player, uuid, gameDay, gameTime, "fall_damage_events", val);
    }

    @SubscribeEvent
    public static void onServerTickPre(ServerTickEvent.Pre event) {
        MinecraftServer server = ServerDataStore.getInstance().getServer();
        if (server == null) {
            return;
        }
        long gameTime = server.overworld().getGameTime();
        int gameDay = PlayerStats.gameDay(gameTime);
        boolean isRaining = server.overworld().isRaining();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            if (player.isSleeping() && !wasSleeping.contains(uuid)) {
                if (isRaining) {
                    MilestoneChecker.checkAndUnlock(player, uuid, gameDay, gameTime, def -> def.getTrigger() == MilestoneTrigger.FIRST_RAIN_SLEEP, null);
                }
                wasSleeping.add(uuid);
            } else if (!player.isSleeping()) {
                wasSleeping.remove(uuid);
            }
        }
    }

    public static void notifyStatChanged(ServerPlayer player, String statKey, long newValue) {
        UUID uuid = player.getUUID();
        long gameTime = player.serverLevel().getGameTime();
        int gameDay = PlayerStats.gameDay(gameTime);
        StatValueStore stats = TimelineStore.getInstance().getOrCreateStats(uuid);
        stats.setCounter(statKey, newValue);
        MilestoneChecker.checkCounter(player, uuid, gameDay, gameTime, statKey, newValue);
    }

    private static float normalizeYaw(float yaw) {
        yaw %= 360.0f;
        if (yaw < -180.0f) {
            yaw += 360.0f;
        }
        if (yaw > 180.0f) {
            yaw -= 360.0f;
        }
        return yaw;
    }
}
