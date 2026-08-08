package com.dreamer.ao.achievement.bridge;

import com.dreamer.ao.achievement.AdvancementCrudService;
import com.dreamer.ao.data.ConditionType;
import com.dreamer.ao.data.ServerDataStore;
import com.dreamer.ao.data.model.CustomAdvancement;
import com.dreamer.ao.logic.ConditionEvaluator;
import com.dreamer.ao.milestone.bridge.AchievementBridge;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

/**
 * {@link AchievementBridge} 的实现，桥接里程碑系统 → 成就系统。
 *
 * <p>在模组初始化时由 {@code AdvancementOverhaul} 注册到
 * {@link com.dreamer.ao.milestone.bridge.BridgeRegistry}。
 *
 * <p>里程碑 → 成就的创建与完成直接调用领域服务
 * （{@link AdvancementCrudService} 与 {@link ServerDataStore}），
 * 不再绕行 Brigadier 命令分发器，从而保留类型安全与异常信息，
 * 并能以服务级调用正确回滚，避免孤立空成就。
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
            ConditionEvaluator.checkInstant(player, ConditionType.STAT_REACH, milestoneId);
        }
    }

    @Override
    public void triggerAutoAdvancement(ServerPlayer player, String milestoneId,
                                       String nameKey, String descriptionKey, String iconItem) {
        String advId = "milestone_" + milestoneId;
        var sds = ServerDataStore.getInstance();
        try {
            // 直接构造领域对象，避免 JSON 序列化往返与命令字符串注入
            CustomAdvancement adv = new CustomAdvancement();
            adv.setId(advId);
            Map<String, Object> data = Map.of(
                    "id", advId,
                    "name", nameKey,
                    "description", descriptionKey,
                    "icon", iconItem == null ? "" : iconItem,
                    "tab", "milestones");
            AdvancementCrudService.applyJsonToAdvancement(adv, data);

            // 1) 创建（内部已触发运行时增量更新并落盘）
            sds.addAdvancement(adv);
            // 2) 完成（内部已向玩家推送同步、授予进度并触发事件）
            ConditionEvaluator.tryCompleteForce(player, advId);
        } catch (Exception e) {
            // 异常时回滚：删除已创建的成就，防止留下孤立空成就
            LOGGER.warn("Failed to auto-create advancement for milestone {}, rolling back: {}",
                    milestoneId, e.getMessage());
            try {
                sds.removeAdvancement(advId);
            } catch (Exception rollbackErr) {
                LOGGER.error("Rollback failed for milestone advancement {}", advId, rollbackErr);
            }
        }
    }
}
