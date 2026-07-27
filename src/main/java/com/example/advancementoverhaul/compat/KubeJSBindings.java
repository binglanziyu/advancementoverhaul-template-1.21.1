package com.example.advancementoverhaul.compat;

import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.List;

/**
 * KubeJS 脚本绑定类。
 * <p>
 * 通过 {@code bindings.add("AdvancementOverhaul", KubeJSBindings.class)} 注册后，
 * 脚本中可直接使用：
 * <pre>{@code
 * // 查询
 * AdvancementOverhaul.getAllIds()
 * AdvancementOverhaul.getName("my_adv")
 * AdvancementOverhaul.isCompleted(event.player, "my_adv")
 * AdvancementOverhaul.getProgress(event.player, "my_adv")
 *
 * // 操作
 * AdvancementOverhaul.complete(event.player, "my_adv")
 * AdvancementOverhaul.reset(event.player, "my_adv")
 *
 * // 创建
 * AdvancementOverhaul.builder("my_adv")
 *     .name("我的成就")
 *     .description("描述")
 *     .tab("默认")
 *     .condition("kill_entity", "minecraft:zombie", 10)
 *     .register()
 * }</pre>
 * <p>
 * <b>生命周期警告：</b>所有事件对象（如 {@code AdvCompletedEvent}、{@code AdvProgressEvent}）
 * 仅在事件回调期间有效。请勿将事件引用存入全局变量或长时间持有，否则可能导致
 * {@link ServerPlayer} 引用无法被 GC，引发内存泄漏。
 */
@SuppressWarnings("unused") // 所有方法均通过 KubeJS 反射调用
public final class KubeJSBindings {

    private KubeJSBindings() {}

    /** 获取所有自定义成就 ID 列表 */
    public static List<String> getAllIds() {
        return AdvancementAPI.getAllIds();
    }

    /**
     * 获取成就显示名称。
     * @param advId 成就 ID
     * @return 显示名称，如果成就不存在则返回 null
     */
    @Nullable
    public static String getName(String advId) {
        return AdvancementAPI.getName(advId);
    }

    /** 查询玩家是否已完成指定成就 */
    public static boolean isCompleted(ServerPlayer player, String advId) {
        if (player == null) return false;
        return AdvancementAPI.isCompleted(player.getUUID(), advId);
    }

    /** 获取玩家在指定成就上的进度百分比（0-100） */
    public static int getProgress(ServerPlayer player, String advId) {
        if (player == null) return 0;
        return AdvancementAPI.getProgress(player.getUUID(), advId);
    }

    /** 强制完成成就（跳过前置条件检查，触发事件和原版 grant） */
    public static void complete(ServerPlayer player, String advId) {
        if (player == null) return;
        AdvancementAPI.complete(player, advId);
    }

    /** 重置成就（撤销完成状态和进度，不级联） */
    public static void reset(ServerPlayer player, String advId) {
        if (player == null) return;
        AdvancementAPI.reset(player, advId);
    }

    /** 创建成就构造器，支持链式调用 */
    public static AdvancementAPI.AdvancementBuilder builder(String id) {
        return AdvancementAPI.builder(id);
    }
}
