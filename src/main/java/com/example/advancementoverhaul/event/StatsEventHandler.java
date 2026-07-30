/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.tags.FluidTags
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.animal.Animal
 *  net.minecraft.world.entity.npc.WanderingTrader
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.inventory.AbstractContainerMenu
 *  net.minecraft.world.inventory.MerchantMenu
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.Items
 *  net.minecraft.world.item.NameTagItem
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.biome.Biome
 *  net.minecraft.world.level.block.BambooSaplingBlock
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.CropBlock
 *  net.minecraft.world.level.block.NetherWartBlock
 *  net.minecraft.world.level.block.SaplingBlock
 *  net.minecraft.world.level.block.StemBlock
 *  net.minecraft.world.level.block.SweetBerryBushBlock
 *  net.minecraft.world.level.block.TorchBlock
 *  net.minecraft.world.level.storage.LevelResource
 *  net.neoforged.bus.api.SubscribeEvent
 *  net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent
 *  net.neoforged.neoforge.event.entity.living.AnimalTameEvent
 *  net.neoforged.neoforge.event.entity.living.LivingDeathEvent
 *  net.neoforged.neoforge.event.entity.living.LivingFallEvent
 *  net.neoforged.neoforge.event.entity.player.PlayerEvent$ItemCraftedEvent
 *  net.neoforged.neoforge.event.entity.player.PlayerEvent$PlayerChangedDimensionEvent
 *  net.neoforged.neoforge.event.entity.player.PlayerEvent$PlayerLoggedOutEvent
 *  net.neoforged.neoforge.event.entity.player.PlayerInteractEvent$EntityInteract
 *  net.neoforged.neoforge.event.level.BlockEvent$BreakEvent
 *  net.neoforged.neoforge.event.level.BlockEvent$EntityPlaceEvent
 *  net.neoforged.neoforge.event.server.ServerStartedEvent
 *  net.neoforged.neoforge.event.server.ServerStoppingEvent
 *  net.neoforged.neoforge.event.tick.ServerTickEvent$Post
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.example.advancementoverhaul.event;

import com.example.advancementoverhaul.data.PlayerStats;
import com.example.advancementoverhaul.data.PlayerStatsStore;
import com.example.advancementoverhaul.data.ServerDataStore;
import com.example.advancementoverhaul.narrative.event.EchoEventHandler;
import com.example.advancementoverhaul.narrative.event.MonologueEventHandler;
import com.example.advancementoverhaul.logic.ConditionEvaluator;
import com.example.advancementoverhaul.network.handler.TimelineNetworkHandler;
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
    private static final Logger LOGGER = LoggerFactory.getLogger((String)"AdvancementOverhaul/StatsEvent");
    private static final Set<UUID> sunriseChecked = new HashSet<UUID>();
    private static final Set<UUID> sunsetChecked = new HashSet<UUID>();
    private static final Set<UUID> wasSleeping = new HashSet<UUID>();
    private static final Map<UUID, String> merchantTypeMap = new HashMap<UUID, String>();
    private static final Map<UUID, ItemStack> prevResultSlot = new HashMap<UUID, ItemStack>();
    private static int syncTickCounter = 0;
    private static final int SYNC_INTERVAL_TICKS = 600;
    private static final int INVENTORY_SCAN_INTERVAL = 100;

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        PlayerStatsStore.getInstance().init(server.getWorldPath(LevelResource.ROOT).resolve("advancement_overhaul"));
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        sunriseChecked.remove(uuid);
        sunsetChecked.remove(uuid);
        wasSleeping.remove(uuid);
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
        boolean doSync = ++syncTickCounter >= 600;
        MinecraftServer server = ServerDataStore.getInstance().getServer();
        if (server == null) {
            return;
        }
        PlayerStatsStore store = PlayerStatsStore.getInstance();
        store.tick();
        long gameTime = server.overworld().getGameTime();
        boolean isSunrise = gameTime % 24000L >= 22800L && gameTime % 24000L <= 23200L;
        boolean isSunset = gameTime % 24000L >= 12700L && gameTime % 24000L <= 13100L;
        boolean isRaining = server.overworld().isRaining();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int playerY;
            double dz;
            ResourceLocation biomeKey;
            BlockPos pos;
            UUID uuid = player.getUUID();
            PlayerStats stats = store.getOrCreate(uuid);
            ServerLevel level = player.serverLevel();
            if (level.canSeeSky(pos = player.blockPosition())) {
                float yaw = player.getYRot() % 360.0f;
                if (yaw < -180.0f) {
                    yaw += 360.0f;
                }
                if (yaw > 180.0f) {
                    yaw -= 360.0f;
                }
                if (isSunrise && !sunriseChecked.contains(uuid) && yaw >= -120.0f && yaw <= -60.0f) {
                    ++stats.sunrisesViewed;
                    store.markDirty(uuid);
                    sunriseChecked.add(uuid);
                    ConditionEvaluator.checkStatReach(player, "sunrisesViewed", stats.sunrisesViewed);
                    MonologueEventHandler.sendMonologueToPlayer(player, "sunrise");
                }
                if (isSunset && !sunsetChecked.contains(uuid) && yaw >= 60.0f && yaw <= 120.0f) {
                    ++stats.sunsetsViewed;
                    store.markDirty(uuid);
                    sunsetChecked.add(uuid);
                    ConditionEvaluator.checkStatReach(player, "sunsetsViewed", stats.sunsetsViewed);
                    MonologueEventHandler.sendMonologueToPlayer(player, "sunset");
                }
            }
            if (!isSunrise) {
                sunriseChecked.remove(uuid);
            }
            if (!isSunset) {
                sunsetChecked.remove(uuid);
            }
            if (isRaining && level.canSeeSky(pos)) {
                if (((Biome)level.getBiome(pos).value()).coldEnoughToSnow(pos)) {
                    ++stats.snowTicks;
                    if (stats.snowTicks % 20L == 0L) {
                        ConditionEvaluator.checkStatReach(player, "snowTicks", stats.snowTicks);
                    }
                } else if (((Biome)level.getBiome(pos).value()).hasPrecipitation()) {
                    ++stats.rainTicks;
                    if (stats.rainTicks % 20L == 0L) {
                        ConditionEvaluator.checkStatReach(player, "rainTicks", stats.rainTicks);
                    }
                }
                store.markDirty(uuid);
            }
            if ((biomeKey = (ResourceLocation)level.getBiome(pos).unwrapKey().map(ResourceKey::location).orElse(null)) != null) {
                String biomeId = biomeKey.toString();
                stats.biomeTimes.merge(biomeId, 1L, Long::sum);
            }
            if (gameTime % 24000L == 23999L && !stats.biomeTimes.isEmpty()) {
                String topBiome = null;
                long topTime = 0L;
                for (Map.Entry<String, Long> e : stats.biomeTimes.entrySet()) {
                    if (e.getValue() <= topTime) continue;
                    topTime = e.getValue();
                    topBiome = e.getKey();
                }
                if (topBiome != null) {
                    stats.mostFrequentBiome = topBiome;
                }
                stats.biomeTimes.clear();
                store.markDirty(uuid);
            }
            BlockPos spawn = level.getSharedSpawnPos();
            double dx = player.getX() - (double)spawn.getX();
            double dist = Math.sqrt(dx * dx + (dz = player.getZ() - (double)spawn.getZ()) * dz);
            if (dist > stats.furthestDistance) {
                stats.furthestDistance = dist;
                store.markDirty(uuid);
            }
            if ((playerY = player.blockPosition().getY()) < stats.lowestY) {
                stats.lowestY = playerY;
                store.markDirty(uuid);
            }
            if (playerY > stats.highestY) {
                stats.highestY = playerY;
                store.markDirty(uuid);
            }
            if (syncTickCounter % 100 == 0) {
                if (stats.firstDiamondDay < 0) {
                    for (ItemStack stack : player.getInventory().items) {
                        if (!stack.is(Items.DIAMOND)) continue;
                        stats.firstDiamondDay = PlayerStats.gameDay(gameTime);
                        store.markDirty(uuid);
                        break;
                    }
                }
                if (stats.firstEnchantDay < 0) {
                    for (ItemStack stack : player.getInventory().items) {
                        if (!stack.isEnchanted()) continue;
                        stats.firstEnchantDay = PlayerStats.gameDay(gameTime);
                        store.markDirty(uuid);
                        break;
                    }
                    if (stats.firstEnchantDay < 0) {
                        for (ItemStack stack : player.getInventory().offhand) {
                            if (!stack.isEnchanted()) continue;
                            stats.firstEnchantDay = PlayerStats.gameDay(gameTime);
                            store.markDirty(uuid);
                            break;
                        }
                    }
                }
            }
            if (player.isSleeping() && !wasSleeping.contains(uuid)) {
                if (stats.firstRainSleepDay < 0 && isRaining) {
                    stats.firstRainSleepDay = PlayerStats.gameDay(gameTime);
                    store.markDirty(uuid);
                }
                wasSleeping.add(uuid);
            } else if (!player.isSleeping()) {
                wasSleeping.remove(uuid);
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
        if (abstractContainerMenu instanceof MerchantMenu) {
            String type;
            MerchantMenu mm = (MerchantMenu)abstractContainerMenu;
            ItemStack result = mm.getSlot(2).getItem();
            ItemStack prev = prevResultSlot.get(uuid);
            if (prev != null && !prev.isEmpty() && result.isEmpty() && "wandering_trader".equals(type = merchantTypeMap.getOrDefault(uuid, "unknown"))) {
                ++stats.wanderingTraderTrades;
                store.markDirty(uuid);
                ConditionEvaluator.checkStatReach(player, "wanderingTraderTrades", stats.wanderingTraderTrades);
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
        if (!(livingEntity instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player = (ServerPlayer)livingEntity;
        UUID uuid = player.getUUID();
        PlayerStats stats = PlayerStatsStore.getInstance().getOrCreate(uuid);
        PlayerStatsStore store = PlayerStatsStore.getInstance();
        if (!stats.firstDeathRecorded) {
            stats.firstDeathDay = PlayerStats.gameDay(player.serverLevel().getGameTime());
            stats.firstDeathX = player.blockPosition().getX();
            stats.firstDeathY = player.blockPosition().getY();
            stats.firstDeathZ = player.blockPosition().getZ();
            stats.firstDeathRecorded = true;
        }
        stats.latestDeathX = player.blockPosition().getX();
        stats.latestDeathY = player.blockPosition().getY();
        stats.latestDeathZ = player.blockPosition().getZ();
        store.markDirty(uuid);
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player2 = (ServerPlayer)player;
        UUID uuid = player2.getUUID();
        PlayerStats stats = PlayerStatsStore.getInstance().getOrCreate(uuid);
        PlayerStatsStore store = PlayerStatsStore.getInstance();
        long gameTime = player2.serverLevel().getGameTime();
        String dim = event.getTo().location().toString();
        if (dim.equals("minecraft:the_nether") && stats.firstNetherDay < 0) {
            stats.firstNetherDay = PlayerStats.gameDay(gameTime);
            store.markDirty(uuid);
        } else if (dim.equals("minecraft:the_end") && stats.firstEndDay < 0) {
            stats.firstEndDay = PlayerStats.gameDay(gameTime);
            store.markDirty(uuid);
        }
    }

    @SubscribeEvent
    public static void onAnimalTame(AnimalTameEvent event) {
        Player player = event.getTamer();
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player2 = (ServerPlayer)player;
        UUID uuid = player2.getUUID();
        PlayerStats stats = PlayerStatsStore.getInstance().getOrCreate(uuid);
        PlayerStatsStore store = PlayerStatsStore.getInstance();
        ++stats.animalsTamed;
        if (stats.firstTameDay < 0) {
            stats.firstTameDay = PlayerStats.gameDay(player2.serverLevel().getGameTime());
        }
        store.markDirty(uuid);
        ConditionEvaluator.checkStatReach(player2, "animalsTamed", stats.animalsTamed);
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        String blockName;
        Entity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player = (ServerPlayer)entity;
        UUID uuid = player.getUUID();
        PlayerStats stats = PlayerStatsStore.getInstance().getOrCreate(uuid);
        PlayerStatsStore store = PlayerStatsStore.getInstance();
        Level level = player.level();
        BlockPos pos = event.getPos();
        Block block = event.getState().getBlock();
        ++stats.blocksPlaced;
        ConditionEvaluator.checkStatReach(player, "blocksPlaced", stats.blocksPlaced);
        if (!stats.firstBlockPlacedRecorded) {
            stats.firstBlockPlacedX = pos.getX();
            stats.firstBlockPlacedY = pos.getY();
            stats.firstBlockPlacedZ = pos.getZ();
            stats.firstBlockPlacedRecorded = true;
        }
        if (block instanceof TorchBlock) {
            ++stats.torchesPlaced;
            ConditionEvaluator.checkStatReach(player, "torchesPlaced", stats.torchesPlaced);
        }
        if (level.getFluidState(pos).is(FluidTags.WATER)) {
            ++stats.blocksPlacedInWater;
            ConditionEvaluator.checkStatReach(player, "blocksPlacedInWater", stats.blocksPlacedInWater);
        }
        boolean cropPlanted = false;
        if (block instanceof CropBlock || block instanceof StemBlock || block instanceof SaplingBlock || block instanceof NetherWartBlock || block instanceof SweetBerryBushBlock || block instanceof BambooSaplingBlock) {
            ++stats.cropsPlanted;
            cropPlanted = true;
        }
        if ((blockName = BuiltInRegistries.BLOCK.getKey(block).toString()).equals("minecraft:pitcher_crop") || blockName.equals("minecraft:torchflower_crop")) {
            ++stats.cropsPlanted;
            cropPlanted = true;
        }
        if (cropPlanted) {
            ConditionEvaluator.checkStatReach(player, "cropsPlanted", stats.cropsPlanted);
        }
        store.markDirty(uuid);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player2 = (ServerPlayer)player;
        UUID uuid = player2.getUUID();
        PlayerStats stats = PlayerStatsStore.getInstance().getOrCreate(uuid);
        ++stats.blocksBroken;
        PlayerStatsStore.getInstance().markDirty(uuid);
        ConditionEvaluator.checkStatReach(player2, "blocksBroken", stats.blocksBroken);
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player2 = (ServerPlayer)player;
        UUID uuid = player2.getUUID();
        PlayerStats stats = PlayerStatsStore.getInstance().getOrCreate(uuid);
        ++stats.itemsCrafted;
        PlayerStatsStore.getInstance().markDirty(uuid);
        ConditionEvaluator.checkStatReach(player2, "itemsCrafted", stats.itemsCrafted);
    }

    @SubscribeEvent
    public static void onLightningStrike(EntityStruckByLightningEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player = (ServerPlayer)entity;
        UUID uuid = player.getUUID();
        PlayerStats stats = PlayerStatsStore.getInstance().getOrCreate(uuid);
        ++stats.lightningStrikes;
        PlayerStatsStore.getInstance().markDirty(uuid);
        ConditionEvaluator.checkStatReach(player, "lightningStrikes", stats.lightningStrikes);
    }

    @SubscribeEvent
    public static void onFallDamage(LivingFallEvent event) {
        LivingEntity livingEntity = event.getEntity();
        if (!(livingEntity instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player = (ServerPlayer)livingEntity;
        if (event.getDistance() <= 3.0f) {
            return;
        }
        UUID uuid = player.getUUID();
        PlayerStats stats = PlayerStatsStore.getInstance().getOrCreate(uuid);
        ++stats.fallDamageEvents;
        PlayerStatsStore.getInstance().markDirty(uuid);
        ConditionEvaluator.checkStatReach(player, "fallDamageEvents", stats.fallDamageEvents);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Animal animal;
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        ServerPlayer player2 = (ServerPlayer)player;
        ItemStack handItem = player2.getItemInHand(event.getHand());
        if (handItem.isEmpty()) {
            return;
        }
        UUID uuid = player2.getUUID();
        PlayerStats stats = PlayerStatsStore.getInstance().getOrCreate(uuid);
        PlayerStatsStore store = PlayerStatsStore.getInstance();
        if (handItem.getItem() instanceof NameTagItem) {
            ++stats.nameTagsUsed;
            store.markDirty(uuid);
            ConditionEvaluator.checkStatReach(player2, "nameTagsUsed", stats.nameTagsUsed);
            return;
        }
        Entity target = event.getTarget();
        if (target instanceof Animal && (animal = (Animal)target).isFood(handItem)) {
            ++stats.animalsFed;
            store.markDirty(uuid);
            ConditionEvaluator.checkStatReach(player2, "animalsFed", stats.animalsFed);
        }
        if (target instanceof WanderingTrader) {
            merchantTypeMap.put(uuid, "wandering_trader");
        }
    }
}

