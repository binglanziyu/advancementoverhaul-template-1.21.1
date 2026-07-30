/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.components.toasts.Toast
 *  net.minecraft.client.gui.components.toasts.ToastComponent
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package com.example.advancementoverhaul.mixin.ftb;

import com.example.advancementoverhaul.client.gui.AdvancementScreen;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ToastComponent.class})
public class FtbToastMixin {
    @Inject(method={"addToast"}, at={@At(value="HEAD")}, cancellable=true)
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

