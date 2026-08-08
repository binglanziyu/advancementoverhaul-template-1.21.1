package com.dreamer.ao.compat;

import com.dreamer.ao.Config;
import com.dreamer.ao.data.ServerDataStore;
import com.dreamer.ao.network.SyncManager;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 原版进度过滤工具类。
 * <p>
 * 独立于 Mixin 类以避免 Mixin 对非 private 静态方法注入的检查冲突。
 * 根据 {@link Config#VANILLA_DEFAULT_ENABLED} 和玩家手动开关的启用/禁用列表，
 * 从运行时进度 Map 中移除被禁用的原版进度条目。
 *
 * @see AdvancementManagerMixin
 */
public final class VanillaAdvancementFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(VanillaAdvancementFilter.class);

    private VanillaAdvancementFilter() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /**
     * 基于已加载的 vanillaStates 过滤禁用的原版进度。
     * <p>
     * 不依赖 server 实例，在 server 启动前的 reload 阶段也可工作。
     * 遍历当前运行时 Map，：
     * <ul>
     *   <li><b>默认禁用模式</b>（{@code VANILLA_DEFAULT_ENABLED = false}）：
     *       移除所有不在 enabledVanilla 列表中的非自定义进度。</li>
     *   <li><b>默认启用模式</b>（{@code VANILLA_DEFAULT_ENABLED = true}）：
     *       仅移除在 disabledVanilla 列表中的非自定义进度。</li>
     * </ul>
     * 自定义进度（以模组 ID 为命名空间前缀）不受影响。
     *
     * @param map 运行时进度 Map，将被原地修改
     */
    public static void filterDisabledVanillaFromMap(Map<ResourceLocation, AdvancementHolder> map) {
        ServerDataStore store = ServerDataStore.getInstance();
        boolean defaultEnabled;
        try {
            defaultEnabled = Config.VANILLA_DEFAULT_ENABLED.get();
        } catch (IllegalStateException e) {
            defaultEnabled = false;
        }

        if (!defaultEnabled) {
            // 默认禁用模式：移除所有不在 enabledVanilla 中的非自定义进度
            map.keySet().removeIf(id -> {
                if (AdvancementRegistry.isCustomAdvancement(id)) return false;
                return !store.getEnabledVanilla().contains(id.toString());
            });
        } else {
            // 默认启用模式：仅移除明确禁用的
            map.keySet().removeIf(id -> {
                if (AdvancementRegistry.isCustomAdvancement(id)) return false;
                return store.getDisabledVanilla().contains(id.toString());
            });
        }
    }

    /**
     * 初始启动时延迟过滤禁用的原版进度。
     * <p>
     * 由 {@code ServerStartedEvent} 监听器调用，确保所有模组的 RETURN 注入器
     * 都有机会完整读取进度 Map 后再进行过滤。过滤完成后触发全局同步。
     *
     * @param server 当前 MinecraftServer 实例
     */
    public static void filterDisabledVanillaDelayed(MinecraftServer server) {
        var map = AdvancementMapHolder.getMutableMap();
        if (map == null) {
            LOGGER.warn("[Mixin] Runtime map is null at ServerStartedEvent, cannot apply deferred filter");
            return;
        }
        filterDisabledVanillaFromMap(map);
        AdvancementRegistry.syncAllRuntime(server);
        SyncManager.syncAll(server);
        LOGGER.info("[Mixin] Deferred vanilla advancement filter applied, {} entries remaining", map.size());
    }
}
