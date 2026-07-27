package com.example.advancementoverhaul.client.gui.panel;

/**
 * 图标选择器辅助工具：提供按类别（物品/实体/方块）浏览注册表并选择图标。
 * <p>
 * 在编辑面板中渲染图标选择器 UI，支持滚动浏览和点击选择。
 * 选中后通过 C2S 命令将图标 ID 发送到服务端。
 */
import com.example.advancementoverhaul.LangKeys;
import com.example.advancementoverhaul.client.gui.AdvancementScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.Consumer;

public final class IconPickerHelper {
    private IconPickerHelper() {}

    public static void open(AdvancementScreen screen, Consumer<String> onPick) {
        List<ListSelector.Entry> entries = new ArrayList<>();
        entries.add(new ListSelector.Entry("", "(" + Component.translatable(LangKeys.NONE).getString() + ")"));

        // 物品：加载全部（列表自带搜索过滤，无需截断）
        entries.add(new ListSelector.Entry("__sep_items__", Component.translatable(LangKeys.ICON_ITEMS).getString()));
        for (var item : BuiltInRegistries.ITEM) {
            ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
            if (rl == null) continue;
            String displayName = new ItemStack(item).getHoverName().getString();
            entries.add(new ListSelector.Entry(rl.toString(), displayName));
        }

        // 实体类型：加载全部
        entries.add(new ListSelector.Entry("__sep_entities__", Component.translatable(LangKeys.ICON_ENTITIES).getString()));
        for (var et : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation rl = BuiltInRegistries.ENTITY_TYPE.getKey(et);
            if (rl == null) continue;
            String displayName = et.getDescription().getString();
            entries.add(new ListSelector.Entry("entity:" + rl.toString(), displayName));
        }

        // 方块：加载全部
        entries.add(new ListSelector.Entry("__sep_blocks__", Component.translatable(LangKeys.ICON_BLOCKS).getString()));
        for (var block : BuiltInRegistries.BLOCK) {
            ResourceLocation rl = BuiltInRegistries.BLOCK.getKey(block);
            if (rl == null) continue;
            String displayName;
            try { ItemStack stack = new ItemStack(block); displayName = stack.isEmpty() ? rl.getPath() : stack.getHoverName().getString(); }
            catch (Exception e) { displayName = rl.getPath(); }
            entries.add(new ListSelector.Entry(rl.toString(), displayName));
        }

        screen.showSelector(entries, e -> {
            if (e.id().isEmpty()) onPick.accept(null);
            else if (!e.id().startsWith("__sep_")) onPick.accept(e.id());
        });
    }
}