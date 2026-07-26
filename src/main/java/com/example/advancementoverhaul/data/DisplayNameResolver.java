package com.example.advancementoverhaul.data;

import com.example.advancementoverhaul.LangKeys;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class DisplayNameResolver {
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
                            } catch (Exception ignored) {}
                        }
                    }
                    case DIMENSION -> {
                        return switch (targetId) {
                            case "minecraft:overworld" -> Component.translatable(LangKeys.DIM_OVERWORLD).getString();
                            case "minecraft:the_nether" -> Component.translatable(LangKeys.DIM_NETHER).getString();
                            case "minecraft:the_end" -> Component.translatable(LangKeys.DIM_END).getString();
                            default -> targetId;
                        };
                    }
                    default -> {}
                }
            }
        } catch (Exception ignored) {}
        return targetId;
    }

    public static String friendlyDimension(String dimId) {
        return switch (dimId) {
            case "minecraft:overworld" -> Component.translatable(LangKeys.DIM_OVERWORLD).getString();
            case "minecraft:the_nether" -> Component.translatable(LangKeys.DIM_NETHER).getString();
            case "minecraft:the_end" -> Component.translatable(LangKeys.DIM_END).getString();
            default -> dimId.replace("minecraft:", "").replace("_", " ");
        };
    }
}