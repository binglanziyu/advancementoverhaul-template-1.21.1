/**
 * 里程碑模型包 —— 目标迁移位置。
 *
 * <h2>当前状态</h2>
 * 里程碑数据模型类目前位于 {@code com.dreamer.ao.stats} 包中：
 * <ul>
 *   <li>{@code MilestoneDefinition} → stats/MilestoneDefinition.java</li>
 *   <li>{@code MilestoneTrigger} → stats/MilestoneTrigger.java</li>
 *   <li>{@code TimeMilestone} → stats/TimeMilestone.java</li>
 *   <li>{@code TimelineCategory} → stats/TimelineCategory.java</li>
 * </ul>
 *
 * <h2>迁移计划</h2>
 * 未来使用 IDE 重构工具将上述文件移入本包。
 * 迁移后，所有调用方使用 {@code com.dreamer.ao.milestone.model.*} 导入。
 *
 * <h2>架构位置</h2>
 * <pre>
 *   milestone/
 *   ├── model/        ← 数据模型（目标位置）
 *   ├── store/        ← 数据存储（目标位置）
 *   ├── event/        ← 事件处理
 *   ├── bridge/       ← 与成就系统的桥接接口（已实现）
 *   └── client/       ← 客户端 UI
 * </pre>
 */
package com.dreamer.ao.milestone.model;
