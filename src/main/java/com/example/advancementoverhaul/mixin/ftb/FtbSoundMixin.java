/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.resources.sounds.SoundInstance
 *  net.minecraft.client.sounds.SoundEngine
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.example.advancementoverhaul.mixin.ftb;

import com.example.advancementoverhaul.client.gui.AdvancementScreen;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={SoundEngine.class})
public class FtbSoundMixin {
    @Inject(method={"play"}, at={@At(value="HEAD")}, cancellable=true)
    private void advancementoverhaul$filterFtbSound(SoundInstance soundInstance, CallbackInfo ci) {
        int mode = AdvancementScreen.ftbNotifMode;
        if (mode == 0) {
            return;
        }
        String namespace = soundInstance.getLocation().getNamespace();
        if ("ftbquests".equals(namespace) || "ftblibrary".equals(namespace)) {
            ci.cancel();
        }
    }
}

