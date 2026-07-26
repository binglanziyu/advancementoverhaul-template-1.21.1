package com.example.advancementoverhaul.mixin;

import com.example.advancementoverhaul.ModInfo;
import com.example.advancementoverhaul.compat.AdvancementMapHolder;
import com.example.advancementoverhaul.compat.AdvancementRegistry;
import com.example.advancementoverhaul.data.ServerDataStore;
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
import com.example.advancementoverhaul.event.SyncManager;

import java.util.HashMap;
import java.util.Map;

@Mixin(ServerAdvancementManager.class)
public class AdvancementManagerMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/Mixin");

    @Shadow
    private Map<ResourceLocation, AdvancementHolder> advancements;

    @Inject(method = "apply", at = @At("HEAD"))
    private void advancementoverhaul$injectAndFilter(
            Map<ResourceLocation, JsonElement> data,
            ResourceManager resourceManager,
            ProfilerFiller profiler,
            CallbackInfo ci) {
        cacheVanillaAdvancements(data);
        // [A2] 使用 clearCustomCache 替代 clearCache，保留已解析的原版Holder缓存
        // 避免 /reload 时重新 Codec 解析数千条原版成就
        AdvancementRegistry.clearCustomCache();
        AdvancementRegistry.injectAdvancements(data);
        // [M3] 移除多余分号
        SyncManager.markVanillaCacheDirty();
        LOGGER.info("[Mixin] Cached vanilla advancements, injected custom, {} entries remain in registry", data.size());
    }

    @Inject(method = "apply", at = @At("RETURN"))
    private void advancementoverhaul$makeMutable(CallbackInfo ci) {
        this.advancements = new HashMap<>(this.advancements);
        AdvancementMapHolder.runtimeMap = this.advancements;

        // ★ 新增：即使server未就绪，也根据已加载的vanillaStates做初步过滤
        // 防止FTB Quests等模组在onServerStarted之前扫描到所有成就
        filterDisabledVanillaFromMap(this.advancements);

        net.minecraft.server.MinecraftServer server =
                ServerDataStore.getInstance().getServer();
        if (server != null) {
            AdvancementRegistry.syncAllRuntime(server);
        }
        LOGGER.info("[Mixin] Runtime map captured, {} entries", this.advancements.size());
    }

    /**
     * 基于已加载的 vanillaStates 做初步过滤。
     * 不依赖 server 实例，仅使用持久化数据。
     */
    private void filterDisabledVanillaFromMap(Map<ResourceLocation, AdvancementHolder> map) {
        ServerDataStore store = ServerDataStore.getInstance();
        boolean defaultEnabled;
        try {
            defaultEnabled = com.example.advancementoverhaul.Config.VANILLA_DEFAULT_ENABLED.get();
        } catch (IllegalStateException e) {
            defaultEnabled = false;
        }

        // 默认禁用模式下，移除所有不在 enabledVanilla 中的非自定义成就
        if (!defaultEnabled) {
            map.keySet().removeIf(id -> {
                if (AdvancementRegistry.isCustomAdvancement(id)) return false;
                String idStr = id.toString();
                return !store.getEnabledVanilla().contains(idStr);
            });
        }
        // 默认启用模式下，仅移除明确禁用的
        else {
            map.keySet().removeIf(id -> {
                if (AdvancementRegistry.isCustomAdvancement(id)) return false;
                return store.getDisabledVanilla().contains(id.toString());
            });
        }
    }

    private void cacheVanillaAdvancements(Map<ResourceLocation, JsonElement> data) {
        Map<String, JsonElement> cache = new HashMap<>();
        for (var entry : data.entrySet()) {
            String id = entry.getKey().toString();
            // [M2] 使用 ModInfo.MOD_ID 常量
            if (id.startsWith(ModInfo.MOD_ID + ":")) continue;
            cache.put(id, entry.getValue());
        }
        ServerDataStore.getInstance().setVanillaAdvRawCache(cache);
        LOGGER.info("[Mixin] Cached {} vanilla advancements for SyncManager", cache.size());
    }
}