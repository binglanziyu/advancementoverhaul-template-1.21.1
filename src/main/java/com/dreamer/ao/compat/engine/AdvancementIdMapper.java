package com.dreamer.ao.compat.engine;

import com.dreamer.ao.ModInfo;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义进度 ID ↔ 原版 ResourceLocation 双向映射。
 * <p>
 * 提供 O(1) 查找的自定义 ID 与原版 ResourceLocation 之间的转换，
 * 支持正向映射（customId → vanillaId）和反向映射（vanillaId → customId）。
 * <p>
 * 包含碰撞检测：当两个自定义 ID 映射到同一个 vanilla ID 时，
 * 通过在末尾追加 hashCode 低16位（十六进制）的 {@code _XXXX} 后缀解决冲突。
 *
 * <h2>ID 转换规则</h2>
 * <ol>
 *   <li>将 {@code :} 替换为可逆的 {@code __ns__}（保留 namespace 语义）</li>
 *   <li>移除非法字符，保留 {@code [a-z0-9_/-]}</li>
 *   <li>去除首尾下划线</li>
 *   <li>加上 {@code advancementoverhaul:custom/} 前缀</li>
 *   <li>若发生碰撞，追加 {@code _XXXX} 后缀</li>
 * </ol>
 */
public final class AdvancementIdMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdvancementIdMapper.class);

    /** 原版 ResourceLocation 路径前缀（自定义进度专用） */
    static final String PREFIX = "custom/";

    /** 自定义 ID → 原版 ResourceLocation */
    private static final Map<String, ResourceLocation> idMapping = new ConcurrentHashMap<>();

    /** 原版 ResourceLocation → 自定义 ID（O(1) 反向查找） */
    private static final Map<ResourceLocation, String> reverseIdMapping = new ConcurrentHashMap<>();

    /** safeKey → 已注册的 customId（用于碰撞检测），仅存第一次注册的 ID */
    private static final Map<String, String> safeKeyRegistry = new ConcurrentHashMap<>();

    private AdvancementIdMapper() {}

    /**
     * 自定义 ID → 原版 ResourceLocation。
     * <p>
     * 内含碰撞检测：若不同 customId 经规范化后得到相同 safeKey，
     * 则在 safeKey 后追加 {@code _XXXX}（原始 customId 的 hashCode 低16位），
     * 并通过 LOGGER.warn 记录碰撞信息。
     */
    public static ResourceLocation toVanillaId(String customId) {
        return idMapping.computeIfAbsent(customId, id -> {
            String safe = id.replace(":", "__ns__")
                    .replaceAll("[^a-z0-9_/\\-]", "_")
                    .toLowerCase();
            while (safe.startsWith("_")) safe = safe.substring(1);
            while (safe.endsWith("_")) safe = safe.substring(0, safe.length() - 1);
            if (safe.isEmpty()) safe = "unknown_" + Math.abs(id.hashCode());

            // Collision detection: check if safeKey already used by a different customId
            String existing = safeKeyRegistry.putIfAbsent(safe, id);
            if (existing != null && !existing.equals(id)) {
                String collisionSuffix = String.format("_%04x", id.hashCode() & 0xFFFF);
                safe = safe + collisionSuffix;
                LOGGER.warn("Advancement ID collision: '{}' and '{}' both map to base key '{}', resolved as '{}'",
                        id, existing, safe.substring(0, safe.length() - collisionSuffix.length()), safe);
                safeKeyRegistry.putIfAbsent(safe, id);
            }

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

    /**
     * 将规范化后的 safeKey 反向还原为带 namespace 分隔符的原始 ID。
     * <p>
     * 仅将 {@code __ns__} 替换回 {@code :}，不保证结果完全等于原始 customId
     * （因为原始 ID 可能包含被过滤的非法字符），但能恢复 namespace 语义。
     *
     * @param safeKey 规范化后的 key（如 {@code my__ns__adv}）
     * @return 还原后的 ID（如 {@code my:adv}）
     */
    public static String fromSafeKey(String safeKey) {
        return safeKey.replace("__ns__", ":");
    }

    /** 清除所有 ID 映射缓存。 */
    public static void clearCache() {
        idMapping.clear();
        reverseIdMapping.clear();
        safeKeyRegistry.clear();
    }
}
