package com.dreamer.ao.compat;

import com.dreamer.ao.compat.engine.AdvancementIdMapper;
import com.dreamer.ao.compat.engine.AdvancementInjector;
import com.dreamer.ao.compat.engine.VanillaSuppressor;
import com.dreamer.ao.compat.engine.VanillaSyncService;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * 自定义进度 ←→ 原版进度系统的核心适配器（门面类）。
 * <p>
 * 虽然文件位于 {@code compat/} 包下，但此类是模组核心引擎的入口。
 * 所有公共方法保持静态 API 不变，内部委托给以下专用子引擎：
 * <ul>
 *   <li>{@link AdvancementIdMapper} — ID 映射（双向转换、缓存管理）</li>
 *   <li>{@link AdvancementInjector} — JSON 注入（构建原版 JSON、解析 AdvancementHolder）</li>
 *   <li>{@link VanillaSyncService} — 同步服务（全量/增量运行时同步、授予/撤销）</li>
 *   <li>{@link VanillaSuppressor} — 禁用管理（撤销禁用的原版进度）</li>
 * </ul>
 *
 * 新代码建议直接使用子引擎类以获得更清晰的责任边界。
 * 本类保留以确保现有调用方无需修改。
 */
public final class AdvancementRegistry {

    private AdvancementRegistry() {}

    // ═══════════════ ID 映射（委托给 AdvancementIdMapper） ═══════════════

    public static ResourceLocation toVanillaId(String customId) {
        return AdvancementIdMapper.toVanillaId(customId);
    }

    public static boolean isCustomAdvancement(ResourceLocation id) {
        return AdvancementIdMapper.isCustomAdvancement(id);
    }

    public static String getCustomIdFromVanilla(ResourceLocation vanillaId) {
        return AdvancementIdMapper.getCustomIdFromVanilla(vanillaId);
    }

    public static void clearCustomCache() {
        AdvancementIdMapper.clearCache();
    }

    // ═══════════════ 进度注入（委托给 AdvancementInjector） ═══════════════

    public static void injectAdvancements(Map<ResourceLocation, JsonElement> data) {
        AdvancementInjector.injectAdvancements(data);
    }

    // ═══════════════ 原版进度操作（委托给 VanillaSyncService） ═══════════════

    public static void grantAdvancement(ServerPlayer player, String customId) {
        VanillaSyncService.grantAdvancement(player, customId);
    }

    public static void revokeAdvancement(ServerPlayer player, String customId) {
        VanillaSyncService.revokeAdvancement(player, customId);
    }

    public static void syncToVanilla(ServerPlayer player) {
        VanillaSyncService.syncToVanilla(player);
    }

    // ═══════════════ 原版进度禁用（委托给 VanillaSuppressor） ═══════════════

    public static void suppressVanillaAdvancement(ServerPlayer player, String advId) {
        VanillaSuppressor.suppressVanillaAdvancement(player, advId);
    }

    public static void suppressAllDisabled(ServerPlayer player) {
        VanillaSuppressor.suppressAllDisabled(player);
    }

    // ═══════════════ 运行时同步（委托给 VanillaSyncService） ═══════════════

    public static void updateAdvancementInRuntime(MinecraftServer server, String customId) {
        VanillaSyncService.updateAdvancementInRuntime(server, customId);
    }

    public static void removeAdvancementFromRuntime(MinecraftServer server, String customId) {
        VanillaSyncService.removeAdvancementFromRuntime(server, customId);
    }

    public static void syncAllRuntime(MinecraftServer server) {
        VanillaSyncService.syncAllRuntime(server);
    }

    public static void syncAllRuntime(MinecraftServer server, boolean notifyFtb) {
        VanillaSyncService.syncAllRuntime(server, notifyFtb);
    }
}
