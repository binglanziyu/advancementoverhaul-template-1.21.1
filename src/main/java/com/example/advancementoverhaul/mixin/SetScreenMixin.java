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

@Mixin(Minecraft.class)
public abstract class SetScreenMixin {
    @Unique
    private static boolean replacing;

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void aoh$replaceScreens(Screen screen, CallbackInfo ci) {
        if (replacing) {
            return;
        }
        if (screen instanceof AdvancementsScreen && Config.HIDE_VANILLA.get()) {
            replacing = true;
            try {
                Minecraft.getInstance().setScreen(new AdvancementScreen());
            } finally {
                replacing = false;
            }
            ci.cancel();
            return;
        }
        if (screen instanceof StatsScreen) {
            replacing = true;
            try {
                Minecraft.getInstance().setScreen(new TimelineScreen());
            } finally {
                replacing = false;
            }
            ci.cancel();
        }
    }
}
