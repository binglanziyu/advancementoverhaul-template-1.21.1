package com.example.advancementoverhaul.milestone.bridge;

/**
 * 里程碑系统与外部系统的桥接注册中心。
 *
 * <p>使用方式：
 * <pre>{@code
 * // 初始化时（由 AdvancementOverhaul 主类）
 * BridgeRegistry.setAchievementBridge(new AchievementBridgeImpl());
 *
 * // 使用时（由 MilestoneChecker）
 * BridgeRegistry.getAchievementBridge().checkLinkedAdvancement(...);
 * }</pre>
 */
public final class BridgeRegistry {

    private static volatile AchievementBridge achievementBridge = AchievementBridge.NOOP;

    private BridgeRegistry() {}

    public static AchievementBridge getAchievementBridge() {
        return achievementBridge;
    }

    public static void setAchievementBridge(AchievementBridge bridge) {
        achievementBridge = bridge != null ? bridge : AchievementBridge.NOOP;
    }
}
