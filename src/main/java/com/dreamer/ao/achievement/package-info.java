/**
 * 成就系统包 —— 自定义成就/进度的完整实现。
 *
 * <h2>包结构</h2>
 * <pre>
 *   achievement/
 *   ├── bridge/       ← 桥接实现（暴露给里程碑系统）
 *   ├── model/        ← 成就数据模型（待迁移：目前位于 data/model/）
 *   ├── store/        ← 成就数据存储（待迁移：目前位于 data/）
 *   ├── logic/        ← 成就业务逻辑（待迁移：目前位于 logic/）
 *   ├── event/        ← 成就事件处理（待迁移：目前位于 event/）
 *   ├── compat/       ← 兼容层（待迁移：目前位于 compat/）
 *   ├── command/      ← 命令系统（待迁移：目前位于 command/）
 *   ├── network/      ← 成就网络同步（待迁移：目前位于 network/）
 *   └── client/       ← 成就客户端 UI（待迁移：目前位于 client/）
 * </pre>
 *
 * <h2>核心类</h2>
 * <ul>
 *   <li>{@code CustomAdvancement} — 自定义成就数据模型</li>
 *   <li>{@code AdvancementCondition} — 成就条件定义</li>
 *   <li>{@code AdvancementStore} — 成就 CRUD + 持久化</li>
 *   <li>{@code PlayerDataStore} — 玩家完成/进度状态</li>
 *   <li>{@code ConditionEvaluator} — 条件求值引擎</li>
 * </ul>
 *
 * <h2>与里程碑系统的交互</h2>
 * 成就系统通过 {@link com.dreamer.ao.milestone.bridge.AchievementBridge}
 * 接口向里程碑系统暴露以下能力：
 * <ul>
 *   <li>检查前置成就是否完成</li>
 *   <li>触发关联成就</li>
 *   <li>自动生成里程碑成就</li>
 * </ul>
 */
package com.dreamer.ao.achievement;
