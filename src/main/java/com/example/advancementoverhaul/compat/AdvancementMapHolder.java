package com.example.advancementoverhaul.compat;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;

/**
 * 运行时进度 Map 全局持有者。
 * <p>
 * 由 {@link com.example.advancementoverhaul.mixin.AdvancementManagerMixin} 在 reload 时注入，
 * {@link AdvancementRegistry} 和模块通过此持有者访问原版 {@code ServerAdvancementManager} 内部的
 * 进度 Map，实现运行时注入/移除进度条目。
 * <p>
 * 该 map 为可变 HashMap，允许外部增删条目。
 * 通过 {@link #getRuntimeMap()} 获取只读视图，通过 {@link #setRuntimeMap} 写入。
 * 通过公共字段 {@link #runtimeMap} 直接读写。
 */
public final class AdvancementMapHolder {
    /** 运行时进度 Map（公共访问，允许 engine 子包直接操作） */
    public static volatile Map<ResourceLocation, AdvancementHolder> runtimeMap;

    private AdvancementMapHolder() {}

    /**
     * 获取运行时进度 Map 的只读视图。
     * @return 不可修改的 Map，如果尚未初始化则返回 null
     */
    @Nullable
    public static Map<ResourceLocation, AdvancementHolder> getRuntimeMap() {
        Map<ResourceLocation, AdvancementHolder> map = runtimeMap;
        return map != null ? Collections.unmodifiableMap(map) : null;
    }

    /**
     * 设置运行时进度 Map（由 AdvancementManagerMixin 调用）。
     * @param map 可变的 HashMap，允许外部增删条目
     */
    public static void setRuntimeMap(Map<ResourceLocation, AdvancementHolder> map) {
        runtimeMap = map;
    }
}