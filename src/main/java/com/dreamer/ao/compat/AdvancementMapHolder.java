package com.dreamer.ao.compat;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行时进度 Map 全局持有者。
 * <p>
 * 由 {@link com.dreamer.ao.mixin.AdvancementManagerMixin} 在 reload 时注入，
 * {@link AdvancementRegistry} 和模块通过此持有者访问原版 {@code ServerAdvancementManager} 内部的
 * 进度 Map，实现运行时注入/移除进度条目。
 * <p>
 * 使用 {@link ConcurrentHashMap} 确保线程安全，消除 TOCTOU 竞态窗口。
 */
public final class AdvancementMapHolder {
    /** 运行时进度 Map（线程安全） */
    private static volatile ConcurrentHashMap<ResourceLocation, AdvancementHolder> runtimeMap;

    private AdvancementMapHolder() {}

    /**
     * 获取运行时进度 Map 的只读视图。
     * @return 不可修改的 Map，如果尚未初始化则返回 null
     */
    @Nullable
    public static Map<ResourceLocation, AdvancementHolder> getRuntimeMap() {
        ConcurrentHashMap<ResourceLocation, AdvancementHolder> map = runtimeMap;
        return map != null ? Collections.unmodifiableMap(map) : null;
    }

    /**
     * 返回可变 Map 用于加入/移除（直接返回 ConcurrentHashMap，线程安全）。
     * @return 可修改的 Map，如果尚未初始化则返回 null
     */
    @Nullable
    public static ConcurrentHashMap<ResourceLocation, AdvancementHolder> getMutableMap() {
        return runtimeMap;
    }

    /**
     * 设置运行时进度 Map（由 AdvancementManagerMixin 调用）。
     * 自动转换为 ConcurrentHashMap 以确保线程安全。
     * @param map 可变的 Map
     */
    public static void setRuntimeMap(Map<ResourceLocation, AdvancementHolder> map) {
        if (map instanceof ConcurrentHashMap<ResourceLocation, AdvancementHolder> chm) {
            runtimeMap = chm;
        } else {
            runtimeMap = new ConcurrentHashMap<>(map);
        }
    }
}