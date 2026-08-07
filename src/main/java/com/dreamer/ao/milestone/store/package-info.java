/**
 * 里程碑存储包 —— 目标迁移位置。
 *
 * <h2>当前状态</h2>
 * 里程碑存储类目前位于 {@code com.dreamer.ao.stats} 包中：
 * <ul>
 *   <li>{@code TimelineStore} → stats/TimelineStore.java</li>
 *   <li>{@code TimelineDefinitionLoader} → stats/TimelineDefinitionLoader.java</li>
 *   <li>{@code StatValueStore} → stats/StatValueStore.java</li>
 * </ul>
 *
 * <h2>迁移计划</h2>
 * 先确保 stats/ 与 milestone.store 包中类的 API 完全一致，再使用 IDE 重构工具迁移。
 *
 * <h2>与成就存储的关系</h2>
 * 里程碑存储与成就存储完全独立：
 * <ul>
 *   <li>成就数据：{@code advancement_overhaul/advancements/}</li>
 *   <li>里程碑数据：{@code advancement_overhaul/timeline/}</li>
 * </ul>
 */
package com.dreamer.ao.milestone.store;
