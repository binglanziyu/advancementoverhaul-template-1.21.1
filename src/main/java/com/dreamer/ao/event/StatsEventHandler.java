package com.dreamer.ao.event;

import com.dreamer.ao.data.PlayerStats;
import com.dreamer.ao.data.PlayerStatsStore;
import com.dreamer.ao.data.ServerDataStore;
import com.dreamer.ao.narrative.event.EchoEventHandler;
import com.dreamer.ao.narrative.event.MonologueEventHandler;
import com.dreamer.ao.event.PlayerEventTrackers;
import com.dreamer.ao.logic.ConditionEvaluator;
import com.dreamer.ao.network.handler.TimelineNetworkHandler;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.NameTagItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatsEventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatsEventHandler.class);
    private static final Map<UUID, String> merchantTypeMap = new HashMap<>();
    private static final Map<UUID, ItemStack> prevResultSlot = new HashMap<>();
    private static int syncTickCounter = 0;
    private static final int SYNC_INTERVAL_TICKS = 600;
    private static final int INVENTORY_SCAN_INTERVAL = 600;

    /** 分层频率检查计数器：减少非关键检查的每tick开销 */
    private static int weatherTickCounter = 0;
    private static int distTickCounter = 0;
    private static final int WEATHER_CHECK_INTERVAL = 20;  // 天气每20tick(1秒)检查
    private static final int DIST_CHECK_INTERVAL = 100;     // 距离/高度/群系每100tick(5秒)检查

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        PlayerStatsStore.getInstance().init(server.getWorldPath(LevelResource.ROOT).resolve("advancement_overhaul"));
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        PlayerEventTrackers.resetSunrise(uuid);
        PlayerEventTrackers.resetSunset(uuid);
        PlayerEventTrackers.clearSleeping(uuid);
        merchantTypeMap.remove(uuid);
        prevResultSlot.remove(uuid);
        PlayerStatsStore.getInstance().saveIfDirty();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PlayerStatsStore.getInstance().shutdown();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        boolean doSync = ++syncTickCounter >= SYNC_INTERVAL_TICKS;
        MinecraftServer server = ServerDataStore.getInstance().getServer();
        if (server == null) {
            return;
        }
        PlayerStatsStore store = PlayerStatsStore.getInstance();
        store.tick();

        // 分层频率计数器推进
        weatherTickCounter++;
        distTickCounter++;
        boolean doWeatherCheck = weatherTickCounter % WEATHER_CHECK_INTERVAL == 0;
        boolean doDistCheck = distTickCounter % DIST_CHECK_INTERVAL == 0;

        long gameTime = server.overworld().getGameTime();
        boolean isSunrise = gameTime % 24000L >= 22800L && gameTime % 24000L <= 23200L;
        boolean isSunset = gameTime % 24000L >= 12700L && gameTime % 24000L <= 13100L;
        boolean isRaining = server.overworld().isRaining();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            PlayerStats stats = store.getOrCreate(uuid);
            ServerLevel level = player.serverLevel();
            BlockPos pos = player.blockPosition();
            if (level.canSeeSky(pos)) {
                float yaw = player.getYRot() % 360.0f;
                if (yaw < -180.0f) {
                    yaw += 360.0f;
                }
                if (yaw > 180.0f) {
                    yaw -= 360.0f;
                }
                if (isSunrise && !PlayerEventTrackers.hasSeenSunrise(uuid) && yaw >= -120.0f && yaw <= -60.0f) {
                    stats.setSunrisesViewed(stats.getSunrisesViewed() + 1);
                    store.markDirty(uuid);
                    PlayerEventTrackers.markSunrise(uuid);
                    ConditionEvaluator.checkStatReach(player, "sunrisesViewed", stats.getSunrisesViewed());
                    MonologueEventHandler.sendMonologueToPlayer(player, "sunrise");
                }
                if (isSunset && !PlayerEventTrackers.hasSeenSunset(uuid) && yaw >= 60.0f && yaw <= 120.0f) {
                    stats.setSunsetsViewed(stats.getSunsetsViewed() + 1);
                    store.markDirty(uuid);
                    PlayerEventTrackers.markSunset(uuid);
                    ConditionEvaluator.checkStatReach(player, "sunsetsViewed", stats.getSunsetsViewed());
                    MonologueEventHandler.sendMonologueToPlayer(player, "sunset");
                }
            }
            if (!isSunrise) {
                PlayerEventTrackers.resetSunrise(uuid);
            }
            if (!isSunset) {
                PlayerEventTrackers.resetSunset(uuid);
            }
            // 天气检查：每 20 tick 执行（约1秒）
            if (doWeatherCheck && isRaining && level.canSeeSky(pos)) {
                Biome biome = level.getBiome(pos).value();
                if (biome.coldEnoughToSnow(pos)) {
                    stats.setSnowTicks(stats.getSnowTicks() + WEATHER_CHECK_INTERVAL);
                    if (stats.getSnowTicks() % 20L == 0L) {
                        ConditionEvaluator.checkStatReach(player, "snowTicks", stats.getSnowTicks());
                    }
                } else if (biome.hasPrecipitation()) {
                    stats.setRainTicks(stats.getRainTicks() + WEATHER_CHECK_INTERVAL);
                    if (stats.getRainTicks() % 20L == 0L) {
                        ConditionEvaluator.checkStatReach(player, "rainTicks", stats.getRainTicks());
                    }
                }
                store.markDirty(uuid);
            }
            // 距离/高度/群系：每 100 tick 执行（约5秒）
            if (doDistCheck) {
                ResourceLocation biomeKey = level.getBiome(pos).unwrapKey().map(ResourceKey::location).orElse(null);
                if (biomeKey != null) {
                    String biomeId = biomeKey.toString();
                    stats.addBiomeTime(biomeId, (long) DIST_CHECK_INTERVAL);
                }
                if (gameTime % 24000L < DIST_CHECK_INTERVAL && gameTime % 24000L >= 0 && !stats.isBiomeTimesEmpty()) {
                    String topBiome = stats.pollTopBiome();
                    if (topBiome != null) {
                        stats.setMostFrequentBiome(topBiome);
                    }
                    store.markDirty(uuid);
                }
                BlockPos spawn = level.getSharedSpawnPos();
                double dx = player.getX() - (double) spawn.getX();
                double dz = player.getZ() - (double) spawn.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > stats.getFurthestDistance()) {
                    stats.setFurthestDistance(dist);
                    store.markDirty(uuid);
                }
                int playerY = player.blockPosition().getY();
                if (playerY < stats.getLowestY()) {
                    stats.setLowestY(playerY);
                    store.markDirty(uuid);
                }
                if (playerY > stats.getHighestY()) {
                    stats.setHighestY(playerY);
                    store.markDirty(uuid);
                }
            }
            if (syncTickCounter % INVENTORY_SCAN_INTERVAL == 0) {
                if (stats.getFirstDiamondDay() < 0) {
                    for (ItemStack stack : player.getInventory().items) {
                        if (!stack.is(Items.DIAMOND)) continue;
                        stats.setFirstDiamondDay(PlayerStats.gameDay(gameTime));
                        store.markDirty(uuid);
                        break;
                    }
                }
                if (stats.getFirstEnchantDay() < 0) {
                    for (ItemStack stack : player.getInventory().items) {
                        if (!stack.isEnchanted()) continue;
                        stats.setFirstEnchantDay(PlayerStats.gameDay(gameTime));
                        store.markDirty(uuid);
                        break;
                    }
                    if (stats.getFirstEnchantDay() < 0) {
                        for (ItemStack stack : player.getInventory().offhand) {
                            if (!stack.isEnchanted()) continue;
                            stats.setFirstEnchantDay(PlayerStats.gameDay(gameTime));
                            store.markDirty(uuid);
                            break;
                        }
                    }
                }
            }
            if (player.isSleeping() && !PlayerEventTrackers.wasSleeping(uuid)) {
                if (stats.getFirstRainSleepDay() < 0 && isRaining) {
                    stats.setFirstRainSleepDay(PlayerStats.gameDay(gameTime));
                    store.markDirty(uuid);
                }
                PlayerEventTrackers.markSleeping(uuid);
            } else if (!player.isSleeping()) {
                PlayerEventTrackers.clearSleeping(uuid);
            }
            StatsEventHandler.trackTrades(player, stats, store);
            MonologueEventHandler.checkMilestones(player, stats);
        }
        EchoEventHandler.checkAllPlayers(server.getPlayerList().getPlayers());
        if (doSync) {
            syncTickCounter = 0;
            PlayerStatsStore statsStore = PlayerStatsStore.getInstance();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!statsStore.isDirty(p.getUUID())) continue;
                TimelineNetworkHandler.pushStatsSync(p);
            }
        }
    }

    private static void trackTrades(ServerPlayer player, PlayerStats stats, PlayerStatsStore store) {
        UUID uuid = player.getUUID();
        AbstractContainerMenu abstractContainerMenu = player.containerMenu;
        if (abstractContainerMenu instanceof MerchantMenu mm) {
            ItemStack result = mm.getSlot(2).getItem();
            ItemStack prev = prevResultSlot.get(uuid);
            String type = merchantTypeMap.getOrDefault(uuid, "unknown");
            if (prev != null && !prev.isEmpty() && result.isEmpty() && "wandering_trader".equals(type)) {
                stats.setWanderingTraderTrades(stats.getWanderingTraderTrades() + 1);
                store.markDirty(uuid);
                ConditionEvaluator.checkStatReach(player, "wanderingTraderTrades", stats.getWanderingTraderTrades());
            }
            prevResultSlot.put(uuid, result.copy());
        } else {
            prevResultSlot.remove(uuid);
            merchantTypeMap.remove(uuid);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        LivingEntity livingEntity = event.getEntity();
        if (!(livingEntity instanceof ServerPlayer player)) {
            return;
        }
        UUID uuid = player.getUUID();
        PlayerStats stats = PlayerStatsStore.getInstance().getOrCreate(uuid);
        PlayerStatsStore store = PlayerStatsStore.getInstance();
        if (!stats.isFirstDeathRecorded()) {
            stats.setFirstDeathDay(PlayerStats.gameDay(player.serverLevel().getGameTime()));
            stats.setFirstDeathX(player.blockPosition().getX());
            stats.setFirstDeathY(player.blockPosition().getY());
            stats.setFirstDeathZ(player.blockPosition().getZ());
            stats.setFirstDeathRecorded(true);
        }
        stats.setLatestDeathX(player.blockPosition().getX());
        stats.setLatestDeathY(player.blockPosition().getY());
        stats.setLatestDeathZ(player.blockPosition().getZ());
        store.markDirty(uuid);
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer player2)) {
            return;
        }
        UUID uuid = player2.getUUID();
        PlayerStats stats = PlayerStatsStore.getInstance().getOrCreate(uuid);
        PlayerStatsStore store = PlayerStatsStore.getInstance();
        long gameTime = player2.serverLevel().getGameTime();
        String dim = event.getTo().location().toString();
        if (dim.equals("minecraft:the_nether") && stats.getFirstNetherDay() < 0) {
            stats.setFirstNetherDay(PlayerStats.gameDay(gameTime));
            store.markDirty(uuid);
        } else if (dim.equals("minecraft:the_end") && stats.getFirstEndDay() < 0) {
            stats.setFirstEndDay(PlayerStats.gameDay(gameTime));
            store.markDirty(uuid);
        }
    }

    @SubscribeEvent
    public static void onAnimalTame(AnimalTameEvent event) {
        Player player = event.getTamer();
        if (!(player instanceof ServerPlayer player2)) {
            return;
        }
        UUID uuid = player2.getUUID();
        PlayerStats stats = PlayerStatsStore.getInstance().getOrCreate(uuid);
        PlayerStatsStore store = PlayerStatsStore.getInstance();
        stats.setAnimalsTamed(stats.getAnimalsTamed() + 1);
        if (stats.getFirstTameDay() < 0) {
            stats.setFirstTameDay(PlayerStats.gameDay(player2.serverLevel().getGameTime()));
        }
        store.markDirty(uuid);
        ConditionEvaluator.checkStatReach(player2, "animalsTamed", stats.getAnimalsTamed());
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        UUID uuid = player.getUUID();
        PlayerStats stats = PlayerStatsStore.getInstance().getOrCreate(uuid);
        PlayerStatsStore store = PlayerStatsStore.getInstance();
        Level level = player.level();
        BlockPos pos = event.getPos();
        Block block = event.getState().getBlock();
        stats.setBlocksPlaced(stats.getBlocksPlaced() + 1);
        ConditionEvaluator.checkStatReach(player, "blocksPlaced", stats.getBlocksPlaced());
        if (!stats.isFirstBlockPlacedRecorded()) {
            stats.setFirstBlockPlacedX(pos.getX());
            stats.setFirstBlockPlacedY(pos.getY());
            stats.setFirstBlockPlacedZ(pos.getZ());
            stats.setFirstBlockPlacedRecorded(true);
        }
        if (block instanceof TorchBlock) {
            stats.setTorchesPlaced(stats.getTorchesPlaced() + 1);
            ConditionEvaluator.checkStatReach(player, "torchesPlaced", stats.getTorchesPlaced());
        }
        if (level.getFluidState(pos).is(FluidTags.WATER)) {
            stats.setBlocksPlacedInWater(stats.getBlocksPlacedInWater() + 1);
            ConditionEvaluator.checkStatReach(player, "blocksPlacedInWater", stats.getBlocksPlacedInWater());
        }
        boolean cropPlanted = false;
        if (block instanceof CropBlock || block instanceof StemBlock || block instanceof SaplingBlock
                || block instanceof NetherWartBlock || block instanceof SweetBerryBushBlock
                || block instanceof BambooSaplingBlock) {
            stats.setCropsPlanted(stats.getCropsPlanted() + 1);
            cropPlanted = true;
        }
        String blockName = BuiltInRegistries.BLOCK.getKey(block).toString();
        if (blockName.equals("minecraft:pitcher_crop") || blockName.equals("minecraft:torchflower_crop")) {
            stats.setCropsPlanted(stats.getCropsPlanted() + 1);
            cropPlanted = true;
        }
        if (cropPlanted) {
            ConditionEvaluator.checkStatReach(player, "cropsPlanted", stats.getCropsPlanted());
        }
        store.markDirty(uuid);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (!(player instanceof ServerPlayer player2)) {
            return;
        }
        UUID uuid = player2.getUUID();
        PlayerStats stats = PlayerStatsStore.getInstance().getOrCreate(uuid);
        stats.setBlocksBroken(stats.getBlocksBroken() + 1);
        PlayerStatsStore.getInstance().markDirty(uuid);
        ConditionEvaluator.checkStatReach(player2, "blocksBroken", stats.getBlocksBroken());
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer player2)) {
            return;
        }
        UUID uuid = player2.getUUID();
        PlayerStats stats = PlayerStatsStore.getInstance().getOrCreate(uuid);
        stats.setItemsCrafted(stats.getItemsCrafted() + 1);
        PlayerStatsStore.getInstance().markDirty(uuid);
        ConditionEvaluator.checkStatReach(player2, "itemsCrafted", stats.getItemsCrafted());
    }

    @SubscribeEvent
    public static void onLightningStrike(EntityStruckByLightningEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        UUID uuid = player.getUUID();
        PlayerStats stats = PlayerStatsStore.getInstance().getOrCreate(uuid);
        stats.setLightningStrikes(stats.getLightningStrikes() + 1);
        PlayerStatsStore.getInstance().markDirty(uuid);
        ConditionEvaluator.checkStatReach(player, "lightningStrikes", stats.getLightningStrikes());
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
        PlayerStats stats = PlayerStatsStore.getInstance().getOrCreate(uuid);
        stats.setFallDamageEvents(stats.getFallDamageEvents() + 1);
        PlayerStatsStore.getInstance().markDirty(uuid);
        ConditionEvaluator.checkStatReach(player, "fallDamageEvents", stats.getFallDamageEvents());
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer player2)) {
            return;
        }
        ItemStack handItem = player2.getItemInHand(event.getHand());
        if (handItem.isEmpty()) {
            return;
        }
        UUID uuid = player2.getUUID();
        PlayerStats stats = PlayerStatsStore.getInstance().getOrCreate(uuid);
        PlayerStatsStore store = PlayerStatsStore.getInstance();
        if (handItem.getItem() instanceof NameTagItem) {
            stats.setNameTagsUsed(stats.getNameTagsUsed() + 1);
            store.markDirty(uuid);
            ConditionEvaluator.checkStatReach(player2, "nameTagsUsed", stats.getNameTagsUsed());
            return;
        }
        Entity target = event.getTarget();
        if (target instanceof Animal animal && animal.isFood(handItem)) {
            stats.setAnimalsFed(stats.getAnimalsFed() + 1);
            store.markDirty(uuid);
            ConditionEvaluator.checkStatReach(player2, "animalsFed", stats.getAnimalsFed());
        }
        if (target instanceof WanderingTrader) {
            merchantTypeMap.put(uuid, "wandering_trader");
        }
    }
}
