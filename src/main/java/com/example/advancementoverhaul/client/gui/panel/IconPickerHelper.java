package com.example.advancementoverhaul.client.gui.panel;

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

        entries.add(new ListSelector.Entry("__sep_items__", Component.translatable(LangKeys.ICON_ITEMS).getString()));
        int count = 0;
        for (var item : BuiltInRegistries.ITEM) {
            ResourceLocation rl = BuiltInRegistries.ITEM.getKey(item);
            if (rl == null) continue;
            String displayName = new ItemStack(item).getHoverName().getString();
            entries.add(new ListSelector.Entry(rl.toString(), displayName));
            if (++count > 200) break;
        }

        entries.add(new ListSelector.Entry("__sep_entities__", Component.translatable(LangKeys.ICON_ENTITIES).getString()));
        count = 0;
        for (var et : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation rl = BuiltInRegistries.ENTITY_TYPE.getKey(et);
            if (rl == null) continue;
            String displayName = et.getDescription().getString();
            entries.add(new ListSelector.Entry("entity:" + rl.toString(), displayName));
            if (++count > 200) break;
        }

        entries.add(new ListSelector.Entry("__sep_blocks__", Component.translatable(LangKeys.ICON_BLOCKS).getString()));
        count = 0;
        for (var block : BuiltInRegistries.BLOCK) {
            ResourceLocation rl = BuiltInRegistries.BLOCK.getKey(block);
            if (rl == null) continue;
            String displayName;
            try { ItemStack stack = new ItemStack(block); displayName = stack.isEmpty() ? rl.getPath() : stack.getHoverName().getString(); }
            catch (Exception e) { displayName = rl.getPath(); }
            entries.add(new ListSelector.Entry(rl.toString(), displayName));
            if (++count > 200) break;
        }

        screen.showSelector(entries, e -> {
            if (e.id().isEmpty()) onPick.accept(null);
            else if (!e.id().startsWith("__sep_")) onPick.accept(e.id());
        });
    }
}