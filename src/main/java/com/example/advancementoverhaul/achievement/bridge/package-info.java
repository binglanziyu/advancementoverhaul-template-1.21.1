/**
 * 成就系统桥接实现 —— 向里程碑系统暴露成就能力的实现层。
 *
 * <p>本包中的 {@link com.example.advancementoverhaul.achievement.bridge.AchievementBridgeImpl}
 * 实现了 {@link com.example.advancementoverhaul.milestone.bridge.AchievementBridge} 接口，
 * 将成就系统的功能通过干净的接口暴露给里程碑系统。
 *
 * <h2>依赖方向</h2>
 * <pre>
 *   milestone/bridge/AchievementBridge  (接口定义，在里程碑包中)
 *        ↑ 实现
 *   achievement/bridge/AchievementBridgeImpl  (实现，在成就包中)
 *        ↓ 调用
 *   logic/ConditionEvaluator, data/ServerDataStore  (成就系统内部)
 * </pre>
 *
 * <p>这个设计使得：
 * <ul>
 *   <li>里程碑系统不直接依赖成就系统的任何具体类</li>
 *   <li>测试时可注入 Mock 实现</li>
 *   <li>未来如果成就系统重构，只需更新此实现</li>
 * </ul>
 */
package com.example.advancementoverhaul.achievement.bridge;
