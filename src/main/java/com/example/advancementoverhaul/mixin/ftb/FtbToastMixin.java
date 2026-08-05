package com.example.advancementoverhaul.mixin.ftb;

import com.example.advancementoverhaul.client.gui.AdvancementScreen;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToastComponent.class)
public class FtbToastMixin {
    @Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
    private void advancementoverhaul$filterFtbToast(Toast toast, CallbackInfo ci) {
        int mode = AdvancementScreen.ftbNotifMode;
        if (mode == 0) {
            return;
        }
        String className = toast.getClass().getName();
        if (className.contains("ftbquests") || className.contains("ftb")) {
            ci.cancel();
        }
    }
}
