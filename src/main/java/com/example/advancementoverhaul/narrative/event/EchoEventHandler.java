package com.example.advancementoverhaul.narrative.event;

import com.example.advancementoverhaul.data.NarrativeConfigLoader;
import com.example.advancementoverhaul.data.PlayerStats;
import com.example.advancementoverhaul.data.PlayerStatsStore;
import com.example.advancementoverhaul.data.model.EchoEntry;
import com.example.advancementoverhaul.network.payload.MonologuePayload;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.network.PacketDistributor;

public class EchoEventHandler {
    private static final Map<String, Map<UUID, Long>> cooldowns = new HashMap<String, Map<UUID, Long>>();
    private static final Map<String, Set<UUID>> onceOnlyTriggered = new HashMap<String, Set<UUID>>();
    private static int echoTickCounter;
    private static final int ECHO_CHECK_INTERVAL = 100;
    private static final Random RNG;

    public static void checkAllPlayers(List<ServerPlayer> players) {
        if (++echoTickCounter % 100 != 0) {
            return;
        }
        Map<String, EchoEntry> echoes = NarrativeConfigLoader.getInstance().getEchoes();
        if (echoes.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (ServerPlayer player : players) {
            UUID uuid = player.getUUID();
            BlockPos pos = player.blockPosition();
            for (EchoEntry echo : echoes.values()) {
                String text;
                if (!EchoEventHandler.checkCondition(player, uuid, pos, echo, now) || !EchoEventHandler.checkCooldown(uuid, echo, now) || (text = EchoEventHandler.selectText(echo)) == null) continue;
                PacketDistributor.sendToPlayer((ServerPlayer)player, (CustomPacketPayload)new MonologuePayload("echo:" + echo.getId()), (CustomPacketPayload[])new CustomPacketPayload[0]);
                cooldowns.computeIfAbsent(echo.getId(), k -> new HashMap()).put(uuid, now);
                if (!echo.isOnceOnly()) continue;
                onceOnlyTriggered.computeIfAbsent(echo.getId(), k -> new HashSet()).add(uuid);
            }
        }
    }

    private static boolean checkCondition(ServerPlayer player, UUID uuid, BlockPos pos, EchoEntry echo, long now) {
        EchoEntry.EchoCondition cond = echo.getCondition();
        if (cond == null || cond.getType() == null) {
            return false;
        }
        return switch (cond.getType().toUpperCase()) {
            case "BIOME" -> EchoEventHandler.checkBiome(player, pos, cond);
            case "Y_BELOW" -> {
                if (pos.getY() < cond.getY()) {
                    yield true;
                }
                yield false;
            }
            case "Y_ABOVE" -> {
                if (pos.getY() > cond.getY()) {
                    yield true;
                }
                yield false;
            }
            case "DIMENSION" -> EchoEventHandler.checkDimension(player, cond);
            case "FIRST_TIME" -> EchoEventHandler.checkFirstTime(player, cond);
            default -> false;
        };
    }

    private static boolean checkBiome(ServerPlayer player, BlockPos pos, EchoEntry.EchoCondition cond) {
        if (cond.getBiome() == null) {
            return false;
        }
        ResourceLocation targetBiome = ResourceLocation.tryParse((String)cond.getBiome());
        if (targetBiome == null) {
            return false;
        }
        Holder<Biome> biomeHolder = player.serverLevel().getBiome(pos);
        ResourceLocation currentBiome = biomeHolder.unwrapKey().map(ResourceKey::location).orElse(null);
        return targetBiome.equals((Object)currentBiome);
    }

    private static boolean checkDimension(ServerPlayer player, EchoEntry.EchoCondition cond) {
        if (cond.getDimension() == null) {
            return false;
        }
        ResourceLocation targetDim = ResourceLocation.tryParse((String)cond.getDimension());
        if (targetDim == null) {
            return false;
        }
        ResourceLocation currentDim = player.serverLevel().dimension().location();
        return targetDim.equals((Object)currentDim);
    }

    private static boolean checkFirstTime(ServerPlayer player, EchoEntry.EchoCondition cond) {
        if (cond.getEvent() == null) {
            return false;
        }
        PlayerStats stats = PlayerStatsStore.getInstance().getOrCreate(player.getUUID());
        return switch (cond.getEvent()) {
            case "firstNetherDay" -> {
                if (stats.firstNetherDay > 0) {
                    yield true;
                }
                yield false;
            }
            case "firstEndDay" -> {
                if (stats.firstEndDay > 0) {
                    yield true;
                }
                yield false;
            }
            case "firstDiamondDay" -> {
                if (stats.firstDiamondDay > 0) {
                    yield true;
                }
                yield false;
            }
            case "firstEnchantDay" -> {
                if (stats.firstEnchantDay > 0) {
                    yield true;
                }
                yield false;
            }
            case "firstTameDay" -> {
                if (stats.firstTameDay > 0) {
                    yield true;
                }
                yield false;
            }
            case "firstRainSleepDay" -> {
                if (stats.firstRainSleepDay > 0) {
                    yield true;
                }
                yield false;
            }
            default -> false;
        };
    }

    private static boolean checkCooldown(UUID uuid, EchoEntry echo, long now) {
        Long lastTrigger;
        Set<UUID> triggered;
        if (echo.isOnceOnly() && (triggered = onceOnlyTriggered.get(echo.getId())) != null && triggered.contains(uuid)) {
            return false;
        }
        Map<UUID, Long> echoCooldowns = cooldowns.get(echo.getId());
        if (echoCooldowns != null && (lastTrigger = echoCooldowns.get(uuid)) != null) {
            long cooldownMs = (long)echo.getCooldownSeconds() * 1000L;
            if (now - lastTrigger < cooldownMs) {
                return false;
            }
        }
        return true;
    }

    private static String selectText(EchoEntry echo) {
        String[] texts = echo.getTexts();
        if (texts == null || texts.length == 0) {
            return null;
        }
        if (texts.length == 1) {
            return texts[0];
        }
        return texts[RNG.nextInt(texts.length)];
    }

    public static void resetAll() {
        cooldowns.clear();
        onceOnlyTriggered.clear();
    }

    static {
        RNG = new Random();
    }
}
