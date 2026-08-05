package com.example.advancementoverhaul.compat.engine;

import com.example.advancementoverhaul.Config;
import com.example.advancementoverhaul.data.ServerDataStore;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 原版进度禁用管理器。
 * <p>
 * 负责撤销玩家已禁用原版进度的完成状态，
 * 支持单个禁用和批量禁用两种模式。
 */
public final class VanillaSuppressor {

    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/Suppressor");

    private VanillaSuppressor() {}

    /**
     * 撤销单个禁用的原版进度。
     */
    public static void suppressVanillaAdvancement(ServerPlayer player, String advId) {
        try {
            ResourceLocation rl = ResourceLocation.tryParse(advId);
            if (rl == null) return;
            AdvancementHolder holder = player.server.getAdvancements().get(rl);
            if (holder == null) return;
            var progress = player.getAdvancements().getOrStartProgress(holder);
            if (progress.isDone()) {
                for (String criterion : progress.getCompletedCriteria()) {
                    player.getAdvancements().revoke(holder, criterion);
                }
                player.getAdvancements().flushDirty(player);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to suppress vanilla advancement {}: {}", advId, e.getMessage());
        }
    }

    /**
     * 撤销玩家所有禁用的原版进度。
     * <p>
     * 先撤销明确禁用的，再在默认禁用模式下撤销所有未启用的。
     */
    public static void suppressAllDisabled(ServerPlayer player) {
        ServerDataStore store = ServerDataStore.getInstance();
        for (String id : store.getDisabledVanilla()) suppressVanillaAdvancement(player, id);
        if (!Config.VANILLA_DEFAULT_ENABLED.get()) {
            try {
                for (var holder : player.server.getAdvancements().getAllAdvancements()) {
                    String id = holder.id().toString();
                    if (AdvancementIdMapper.isCustomAdvancement(holder.id())) continue;
                    if (!store.isVanillaEnabled(id)) suppressVanillaAdvancement(player, id);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed during suppressAllDisabled for {}",
                        player.getName().getString(), e);
            }
        }
    }
}
