package com.example.advancementoverhaul.milestone.bridge;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * 里程碑系统与成就系统之间的桥接接口。
 *
 * <h2>设计目的</h2>
 * 消除里程碑系统对成就系统具体实现的直接依赖，实现两大系统的解耦。
 * <ul>
 *   <li>里程碑模块通过此接口调用成就系统，而不是直接引用 {@code ConditionEvaluator}/{@code ServerDataStore}</li>
 *   <li>成就模块提供此接口的实现并在初始化时注册</li>
 *   <li>测试时可注入 Mock 实现</li>
 * </ul>
 *
 * <h2>层级关系</h2>
 * <pre>
 *   milestone/  ←→  bridge/AchievementBridge  ←→  achievement/
 *   (调用方)         (接口层)                        (实现方)
 * </pre>
 */
public interface AchievementBridge {

    /** 默认空实现，在成就系统不可用时使用 */
    AchievementBridge NOOP = new AchievementBridge() {
        @Override
        public boolean isAdvancementCompleted(UUID uuid, String advancementId) { return false; }

        @Override
        public void checkLinkedAdvancement(ServerPlayer player, String milestoneId, long currentValue) {}

        @Override
        public void triggerAutoAdvancement(ServerPlayer player, String milestoneId,
                                           String nameKey, String descriptionKey, String iconItem) {}
    };

    /**
     * 检查指定玩家是否已完成某个成就。
     * 里程碑系统用此方法判断 requiredAdvancement 前置条件是否满足。
     */
    boolean isAdvancementCompleted(UUID uuid, String advancementId);

    /** 检查与里程碑关联的成就（linkedAdvancement）是否应被触发 */
    void checkLinkedAdvancement(ServerPlayer player, String milestoneId, long currentValue);

    /** 自动创建并完成一个由里程碑生成的成就（autoAdvancement） */
    void triggerAutoAdvancement(ServerPlayer player, String milestoneId, String nameKey,
                                String descriptionKey, String iconItem);
}
