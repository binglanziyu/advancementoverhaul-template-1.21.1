package com.dreamer.ao.client.gui.render;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 图标缓存（PERF-4）。
 * <p>
 * 将「默认图标」与「按字符串键缓存的 ItemStack」从 {@code CardRenderer} 中剥离，
 * 使其可独立测试与复用。{@code CardRenderer} 在解析卡片图标时委托本类，
 * 对外保持 {@code resolveIcon} / {@code clearIconCache} 的方法签名不变。
 */
public final class CardIconCache {

    private ItemStack cachedDefaultIcon;
    private final Map<String, ItemStack> iconCache = new HashMap<>();

    private ItemStack getCachedDefaultIcon() {
        if (cachedDefaultIcon == null) {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse("minecraft:nether_star"));
            cachedDefaultIcon = item != null ? new ItemStack(item) : ItemStack.EMPTY;
        }
        return cachedDefaultIcon;
    }

    /**
     * 将图标字符串解析为可渲染的 {@link ItemStack}。
     * <p>
     * 空串或 {@code entity:} 前缀返回空栈（由调用方回退为文字首字母）；
     * 其余按物品注册表解析并缓存，避免每帧重复查表。
     */
    public ItemStack resolveIcon(String iconStr) {
        if (iconStr == null || iconStr.isEmpty()) return getCachedDefaultIcon();
        if (iconStr.startsWith("entity:")) return ItemStack.EMPTY;
        return iconCache.computeIfAbsent(iconStr, key -> {
            ResourceLocation rl = ResourceLocation.tryParse(key);
            if (rl == null) return ItemStack.EMPTY;
            var item = BuiltInRegistries.ITEM.get(rl);
            return item != null ? new ItemStack(item) : ItemStack.EMPTY;
        });
    }

    /** 清空缓存（如资源包重载或屏幕重新初始化时调用）。 */
    public void clear() { iconCache.clear(); }
}
