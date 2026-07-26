package com.example.advancementoverhaul.client;

import com.example.advancementoverhaul.Config;
import com.example.advancementoverhaul.client.gui.AdvancementScreen;
import com.example.advancementoverhaul.client.gui.TranslatedStrings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

public class ClientEvents {

    private static String lastLanguage = "";

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.register(ClientEvents.class);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        // ★ 语言切换检测：每tick比对语言代码，变化时清除翻译缓存
        if (mc.getLanguageManager() != null) {
            String lang = mc.getLanguageManager().getSelected();
            if (!lang.equals(lastLanguage)) {
                lastLanguage = lang;
                TranslatedStrings.invalidate();
            }
        }

        if (mc.screen instanceof AdvancementsScreen && Config.HIDE_VANILLA.get()) {
            mc.setScreen(new AdvancementScreen());
        }
    }
}