package com.dreamer.ao.mixin;

import com.dreamer.ao.Config;
import com.dreamer.ao.ModInfo;
import com.dreamer.ao.compat.AdvancementMapHolder;
import com.dreamer.ao.compat.AdvancementRegistry;
import com.dreamer.ao.data.ServerDataStore;
import com.dreamer.ao.network.SyncManager;
import com.google.gson.JsonElement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Mixin 注入 {@link ServerAdvancementManager#apply} 以拦截进度加载流程。
 *
 * <h2>执行流程</h2>
 * <ol>
 *   <li><b>HEAD</b>：缓存原版 JSON → 注入自定义进度 JSON → 标记同步缓存脏</li>
 *   <li><b>RETURN</b>：将不可变 Map 包装为 HashMap → 持久化到全局持有者 →
 *       按 vanillaStates 过滤禁用进度 → 同步 runtime</li>
 * </ol>
 *
 * <h2>缓存策略</h2>
 * 仅清除自定义成就缓存（{@link AdvancementRegistry#clearCustomCache}），
 * 保留原版 Holder 缓存。原版进度来自资源包，在 reload 间通常不变，
 * 保留缓存可避免重复 Codec 解析。
 */
@Mixin(ServerAdvancementManager.class)
public class AdvancementManagerMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdvancementManagerMixin.class);

    @Shadow(remap = true)
    private Map<ResourceLocation, AdvancementHolder> advancements;

    /**
     * HEAD 注入：在进度加载前缓存原版数据并注入自定义进度。
     */
    @Inject(method = "apply", at = @At("HEAD"))
    private void advancementoverhaul$injectAndFilter(
            Map<ResourceLocation, JsonElement> data,
            ResourceManager resourceManager,
            ProfilerFiller profiler,
            CallbackInfo ci) {
        cacheVanillaAdvancements(data);
        AdvancementRegistry.clearCustomCache();
        AdvancementRegistry.injectAdvancements(data);
        SyncManager.markVanillaCacheDirty();
        LOGGER.debug("[Mixin] Cached vanilla, injected custom, {} entries in registry", data.size());
    }

    /**
     * RETURN 注入：进度加载完成后将不可变 Map 包装为可变的 HashMap。
     * 然后根据 vanillaStates 过滤禁用的原版进度。
     */
    @Inject(method = "apply", at = @At("RETURN"))
    private void advancementoverhaul$makeMutable(CallbackInfo ci) {
        if (this.advancements == null) {
            LOGGER.warn("[Mixin] advancements map is null at RETURN, skipping post-processing");
            return;
        }
        this.advancements = new HashMap<>(this.advancements);
        AdvancementMapHolder.setRuntimeMap(this.advancements);

        // 基于已加载的 vanillaStates 做初步过滤
        // 不依赖 server 实例，仅使用持久化数据
        filterDisabledVanillaFromMap(this.advancements);

        net.minecraft.server.MinecraftServer server = ServerDataStore.getInstance().getServer();
        if (server != null) {
            AdvancementRegistry.syncAllRuntime(server);
        }
        LOGGER.debug("[Mixin] Runtime map captured, {} entries", this.advancements.size());
    }

    /**
     * 基于已加载的 vanillaStates 过滤禁用的原版进度。
     * 不依赖 server 实例，在 server 启动前的 reload 阶段也可工作。
     */
    private void filterDisabledVanillaFromMap(Map<ResourceLocation, AdvancementHolder> map) {
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
     * 缓存原版进度的原始 JSON 数据。
     * 用于 SyncManager 后续合并和客户端同步。
     */
    private void cacheVanillaAdvancements(Map<ResourceLocation, JsonElement> data) {
        Map<String, JsonElement> cache = new HashMap<>();
        for (var entry : data.entrySet()) {
            String id = entry.getKey().toString();
            if (id.startsWith(ModInfo.MOD_ID + ":")) continue;
            cache.put(id, entry.getValue());
        }
        ServerDataStore.getInstance().setVanillaAdvRawCache(cache);
        LOGGER.debug("[Mixin] Cached {} vanilla advancements for SyncManager", cache.size());
    }
}
