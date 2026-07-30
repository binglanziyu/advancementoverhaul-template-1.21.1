package com.example.advancementoverhaul.achievement.bridge;

import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.ServerDataStore;
import com.example.advancementoverhaul.logic.ConditionEvaluator;
import com.example.advancementoverhaul.milestone.bridge.AchievementBridge;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * {@link AchievementBridge} 的实现，桥接里程碑系统 → 成就系统。
 *
 * <p>在模组初始化时由 {@code AdvancementOverhaul} 注册到
 * {@link com.example.advancementoverhaul.milestone.bridge.BridgeRegistry}。
 */
public class AchievementBridgeImpl implements AchievementBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/AchievementBridge");

    @Override
    public boolean isAdvancementCompleted(UUID uuid, String advancementId) {
        if (advancementId == null || advancementId.isEmpty()) {
            return true;
        }
        return ServerDataStore.getInstance().isCompleted(uuid, advancementId);
    }

    @Override
    public void checkLinkedAdvancement(ServerPlayer player, String milestoneId, long currentValue) {
        if (currentValue > 0L) {
            ConditionEvaluator.checkStatReach(player, milestoneId, currentValue);
        } else {
            ConditionEvaluator.checkInstant(player, DataStore.ConditionType.STAT_REACH, milestoneId);
        }
    }

    @Override
    public void triggerAutoAdvancement(ServerPlayer player, String milestoneId,
                                       String nameKey, String descriptionKey, String iconItem) {
        String advId = "milestone_" + milestoneId;
        try {
            String json = String.format(
                    "{\"id\":\"%s\",\"name\":\"%s\",\"description\":\"%s\",\"icon\":\"%s\",\"tab\":\"milestones\"}",
                    advId, nameKey, descriptionKey, iconItem);
            player.server.getCommands().getDispatcher().execute(
                    "adv createjson " + Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)),
                    player.createCommandSourceStack().withSuppressedOutput());
            player.server.getCommands().getDispatcher().execute(
                    "adv complete " + advId,
                    player.createCommandSourceStack().withSuppressedOutput());
        } catch (Exception e) {
            LOGGER.warn("Failed to auto-create advancement for milestone {}: {}", milestoneId, e.getMessage());
        }
    }
}
