package com.example.advancementoverhaul.mixin;

import com.example.advancementoverhaul.client.gui.AdvancementScreen;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin：拦截 FTB Quests 的任务完成音效。
 * <p>
 * 当 FTB 通知模式不为"默认"时，阻止来自 FTB Quests 命名空间的音效播放。
 */
@Mixin(SoundEngine.class)
public class FtbSoundMixin {

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void advancementoverhaul$filterFtbSound(SoundInstance soundInstance, CallbackInfo ci) {
        int mode = AdvancementScreen.ftbNotifMode;
        if (mode == 0) return; // 默认模式：放行所有音效

        // 检查音效是否来自 FTB Quests
        String namespace = soundInstance.getLocation().getNamespace();
        if ("ftbquests".equals(namespace) || "ftblibrary".equals(namespace)) {
            ci.cancel();
        }
    }
}
