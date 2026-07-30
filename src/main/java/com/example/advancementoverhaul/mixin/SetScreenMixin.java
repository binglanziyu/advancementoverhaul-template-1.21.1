/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.gui.screens.achievement.StatsScreen
 *  net.minecraft.client.gui.screens.advancements.AdvancementsScreen
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.example.advancementoverhaul.mixin;

import com.example.advancementoverhaul.Config;
import com.example.advancementoverhaul.client.gui.AdvancementScreen;
import com.example.advancementoverhaul.client.gui.timeline.TimelineScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Minecraft.class})
public abstract class SetScreenMixin {
    @Unique
    private static boolean replacing;

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Inject(method={"setScreen"}, at={@At(value="HEAD")}, cancellable=true)
    private void aoh$replaceScreens(Screen screen, CallbackInfo ci) {
        if (replacing) {
            return;
        }
        if (screen instanceof AdvancementsScreen && Config.HIDE_VANILLA.get()) {
            replacing = true;
            try {
                Minecraft.getInstance().setScreen(new AdvancementScreen());
            }
            finally {
                replacing = false;
            }
            ci.cancel();
            return;
        }
        if (screen instanceof StatsScreen) {
            replacing = true;
            try {
                Minecraft.getInstance().setScreen(new TimelineScreen());
            }
            finally {
                replacing = false;
            }
            ci.cancel();
        }
    }
}

