package com.example.advancementoverhaul.mixin;

import com.example.advancementoverhaul.client.gui.AdvancementScreen;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin：拦截 FTB Quests 的 Toast 通知。
 * <p>
 * 当 FTB 通知模式不为"默认"时，阻止 FTB Quests 的 Toast 显示。
 * 通过检查 Toast 实现类是否位于 ftbquests 包下来判断。
 */
@Mixin(ToastComponent.class)
public class FtbToastMixin {

    @Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
    private void advancementoverhaul$filterFtbToast(Toast toast, CallbackInfo ci) {
        int mode = AdvancementScreen.ftbNotifMode;
        if (mode == 0) return; // 默认模式：放行所有 Toast

        // 检查 Toast 是否为 FTB Quests 产生的
        String className = toast.getClass().getName();
        if (className.contains("ftbquests") || className.contains("ftb")) {
            ci.cancel();
        }
    }
}
