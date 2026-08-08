package com.dreamer.ao.mixin;

import com.dreamer.ao.ModInfo;
import com.dreamer.ao.compat.AdvancementMapHolder;
import com.dreamer.ao.compat.AdvancementRegistry;
import com.dreamer.ao.compat.VanillaAdvancementFilter;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mixin 注入 {@link ServerAdvancementManager#apply} 以拦截进度加载流程。
 *
 * <h2>执行流程</h2>
 * <ol>
 *   <li><b>HEAD</b>：缓存原版 JSON → 注入自定义进度 JSON → 标记同步缓存脏</li>
 *   <li><b>RETURN</b>：将不可变 Map 包装为 ConcurrentHashMap → 持久化到全局持有者 →
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
     * RETURN 注入：进度加载完成后将不可变 Map 包装为可变的并发 Map。
     * <p>
     * 初始启动时不在此处过滤禁用进度——延迟到 {@code ServerStartedEvent}，
     * 确保所有模组的 RETURN 注入器都能看到完整进度 Map。<br>
     * 仅重载（/reload）时立即过滤，防止禁用进度被同步给已连接玩家。
     *
     * <h2>为何使用 ConcurrentHashMap</h2>
     * 该 Map 在运行时会被 {@code AdvancementRegistry.updateAdvancementInRuntime} /
     * {@code removeAdvancementFromRuntime} 增删（由 {@code ServerDataStore} 的
     * 变更回调触发），同时被原版进度系统读取。使用 {@code HashMap} 时，
     * 写入触发的 rehash 可能与并发读取交错，导致读取到损坏的桶结构。
     * {@code ConcurrentHashMap} 在保持 {@code Map} 接口不变的前提下消除该风险，
     * 对所有调用方零影响。
     */
    @Inject(method = "apply", at = @At("RETURN"))
    private void advancementoverhaul$makeMutable(CallbackInfo ci) {
        if (this.advancements == null) {
            LOGGER.warn("[Mixin] advancements map is null at RETURN, skipping post-processing");
            return;
        }
        // ConcurrentHashMap 不接受 null 键/值，逐条过滤后再放入以防其它模组注入了 null 条目
        Map<ResourceLocation, AdvancementHolder> mutable = new ConcurrentHashMap<>(this.advancements.size());
        for (var entry : this.advancements.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                LOGGER.warn("[Mixin] Skipping null advancement entry: key={}", entry.getKey());
                continue;
            }
            mutable.put(entry.getKey(), entry.getValue());
        }
        this.advancements = mutable;
        AdvancementMapHolder.setRuntimeMap(this.advancements);

        net.minecraft.server.MinecraftServer server = ServerDataStore.getInstance().getServer();
        boolean isReload = server != null;

        if (isReload) {
            // 重载时立即过滤，防止禁用进度短暂暴露给已连接玩家
            VanillaAdvancementFilter.filterDisabledVanillaFromMap(this.advancements);
        }
        // 初始启动时延迟到 ServerStartedEvent 过滤（server 为 null）

        if (server != null) {
            AdvancementRegistry.syncAllRuntime(server);
        }
        LOGGER.debug("[Mixin] Runtime map captured, {} entries", this.advancements.size());
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
