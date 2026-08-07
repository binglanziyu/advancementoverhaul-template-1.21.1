package com.dreamer.ao.data;

import com.dreamer.ao.LangKeys;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 显示名称解析器：将注册表 ID 转换为游戏内的本地化显示名。
 * <p>
 * 根据条件类型的数据源（实体/物品/方块/维度），通过 {@link BuiltInRegistries}
 * 查找对应的注册对象并返回其本地化名称。
 * 此外提供维度的友好名称转换。
 * <p>
 * 纯工具类，所有方法为静态方法，不可实例化。
 */
public final class DisplayNameResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(DisplayNameResolver.class);

    private DisplayNameResolver() {}

    public static String resolve(DataStore.ConditionType type, String targetId) {
        if (targetId == null || targetId.isEmpty())
            return Component.translatable(LangKeys.COND_ANY).getString();
        try {
            ResourceLocation rl = ResourceLocation.tryParse(targetId);
            if (rl == null) return targetId;
            if (type != null) {
                switch (type.getDataSource()) {
                    case ENTITY_TYPE -> {
                        var et = BuiltInRegistries.ENTITY_TYPE.get(rl);
                        if (et != null) return et.getDescription().getString();
                    }
                    case ITEM -> {
                        var item = BuiltInRegistries.ITEM.get(rl);
                        if (item != null) return new ItemStack(item).getHoverName().getString();
                    }
                    case BLOCK -> {
                        var block = BuiltInRegistries.BLOCK.get(rl);
                        if (block != null) {
                            try {
                                var stack = new ItemStack(block);
                                if (!stack.isEmpty()) return stack.getHoverName().getString();
                            } catch (Exception e) {
                                LOGGER.debug("Failed to get display name for block {}: {}", targetId, e.getMessage());
                            }
                        }
                    }
                    case DIMENSION -> {
                        return friendlyDimension(targetId);
                    }
                    default -> {}
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to resolve display name for type={} target={}: {}", type, targetId, e.getMessage());
        }
        return targetId;
    }

    public static String friendlyDimension(String dimId) {
        return switch (dimId) {
            case "minecraft:overworld" -> Component.translatable(LangKeys.DIM_OVERWORLD).getString();
            case "minecraft:the_nether" -> Component.translatable(LangKeys.DIM_NETHER).getString();
            case "minecraft:the_end" -> Component.translatable(LangKeys.DIM_END).getString();
            default -> {
                // 尝试读取维度自身的翻译键（mod 通常使用 "dimension.<namespace>.<path>" 格式）
                ResourceLocation rl = ResourceLocation.tryParse(dimId);
                if (rl != null) {
                    String nativeKey = "dimension." + rl.getNamespace() + "." + rl.getPath();
                    String translated = Component.translatable(nativeKey).getString();
                    if (!translated.equals(nativeKey)) {
                        yield translated;
                    }
                }
                yield dimId.replace("minecraft:", "").replace("_", " ");
            }
        };
    }
}