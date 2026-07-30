/**
 * 里程碑事件包 —— 里程碑系统的事件处理。
 *
 * <h2>包中包含</h2>
 * <ul>
 *   <li>{@code MilestoneChecker} — 里程碑解锁核心逻辑</li>
 *   <li>{@code TimelineEventHandler} — 时间线事件入口</li>
 * </ul>
 *
 * <h2>依赖规则</h2>
 * 本包不直接依赖成就系统。与成就系统的所有交互通过
 * {@link com.example.advancementoverhaul.milestone.bridge.AchievementBridge} 桥接接口完成。
 */
package com.example.advancementoverhaul.milestone.event;
