package com.dreamer.ao.mixin.ftb;

import com.dreamer.ao.client.gui.AdvancementScreen;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public class FtbSoundMixin {
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
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
