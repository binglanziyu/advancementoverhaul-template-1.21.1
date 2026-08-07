package com.dreamer.ao.achievement.bridge;

import com.dreamer.ao.data.DataStore;
import com.dreamer.ao.data.ServerDataStore;
import com.dreamer.ao.logic.ConditionEvaluator;
import com.dreamer.ao.milestone.bridge.AchievementBridge;
import com.google.gson.JsonObject;
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
 * {@link com.dreamer.ao.milestone.bridge.BridgeRegistry}。
 */
public class AchievementBridgeImpl implements AchievementBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger(AchievementBridgeImpl.class);

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
            // 用 JsonObject 安全构建 JSON，防止注入
            JsonObject obj = new JsonObject();
            obj.addProperty("id", advId);
            obj.addProperty("name", nameKey);
            obj.addProperty("description", descriptionKey);
            obj.addProperty("icon", iconItem);
            obj.addProperty("tab", "milestones");
            String json = obj.toString();

            // 以服务器权限执行命令，避免玩家权限不足而静默失败
            var source = player.server.createCommandSourceStack().withSuppressedOutput();
            int createResult = player.server.getCommands().getDispatcher().execute(
                    "adv createjson " + Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)),
                    source);
            if (createResult == 0) {
                LOGGER.warn("adv createjson failed for milestone {}", milestoneId);
                return;
            }
            int completeResult = player.server.getCommands().getDispatcher().execute(
                    "adv complete " + advId,
                    source);
            if (completeResult == 0) {
                LOGGER.warn("adv complete failed for milestone {}, rolling back created advancement", milestoneId);
                // 回滚已创建的成就，防止留下孤立空成就
                player.server.getCommands().getDispatcher().execute(
                        "adv remove " + advId, source);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to auto-create advancement for milestone {}: {}", milestoneId, e.getMessage());
        }
    }
}
