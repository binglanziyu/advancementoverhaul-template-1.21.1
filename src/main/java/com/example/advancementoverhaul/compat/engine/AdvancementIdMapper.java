package com.example.advancementoverhaul.compat.engine;

import com.example.advancementoverhaul.ModInfo;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义进度 ID ↔ 原版 ResourceLocation 双向映射。
 * <p>
 * 提供 O(1) 查找的自定义 ID 与原版 ResourceLocation 之间的转换，
 * 支持正向映射（customId → vanillaId）和反向映射（vanillaId → customId）。
 *
 * <h2>ID 转换规则</h2>
 * <ol>
 *   <li>替换 {@code :} → {@code _}</li>
 *   <li>移除非法字符，保留 {@code [a-z0-9_/-]}</li>
 *   <li>去除首尾下划线</li>
 *   <li>加上 {@code advancementoverhaul:custom/} 前缀</li>
 * </ol>
 */
public final class AdvancementIdMapper {

    /** 原版 ResourceLocation 路径前缀（自定义进度专用） */
    static final String PREFIX = "custom/";

    /** 自定义 ID → 原版 ResourceLocation */
    private static final Map<String, ResourceLocation> idMapping = new ConcurrentHashMap<>();

    /** 原版 ResourceLocation → 自定义 ID（O(1) 反向查找） */
    private static final Map<ResourceLocation, String> reverseIdMapping = new ConcurrentHashMap<>();

    private AdvancementIdMapper() {}

    /**
     * 自定义 ID → 原版 ResourceLocation。
     */
    public static ResourceLocation toVanillaId(String customId) {
        return idMapping.computeIfAbsent(customId, id -> {
            String safe = id.replace(':', '_')
                    .replaceAll("[^a-z0-9_/\\-]", "_")
                    .toLowerCase();
            while (safe.startsWith("_")) safe = safe.substring(1);
            while (safe.endsWith("_")) safe = safe.substring(0, safe.length() - 1);
            if (safe.isEmpty()) safe = "unknown_" + Math.abs(id.hashCode());
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(ModInfo.MOD_ID, PREFIX + safe);
            reverseIdMapping.put(rl, id);
            return rl;
        });
    }

    /** 判断 ResourceLocation 是否为本模组注入的自定义进度 */
    public static boolean isCustomAdvancement(ResourceLocation id) {
        return ModInfo.MOD_ID.equals(id.getNamespace()) && id.getPath().startsWith(PREFIX);
    }

    /**
     * 逆映射：原版 ResourceLocation → 自定义进度 ID。
     *
     * @param vanillaId 原版 ResourceLocation（如 {@code advancementoverhaul:custom/my_adv}）
     * @return 原始自定义 ID（如 {@code my_adv}），未找到则返回 null
     */
    public static String getCustomIdFromVanilla(ResourceLocation vanillaId) {
        return reverseIdMapping.get(vanillaId);
    }

    /** 清除所有 ID 映射缓存。 */
    public static void clearCache() {
        idMapping.clear();
        reverseIdMapping.clear();
    }
}
