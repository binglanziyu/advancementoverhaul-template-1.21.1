package com.example.advancementoverhaul.mixin;

import dev.ftb.mods.ftblibrary.config.ConfigCallback;
import dev.ftb.mods.ftblibrary.config.EnumConfig;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;

/**
 * Mixin：将 FTB Library 的 EnumConfig 对成就（ResourceLocation）枚举的交互方式
 * 从"点击循环切换"强制改为"打开列表选择"。
 * <p>
 * 原始逻辑：当 NameMap 条目数 {@code > 16} 或按住 Ctrl 时才打开列表，否则每次点击
 * 循环切换到下一个值。本模组过滤后 KSR 通常只剩少量条目，导致目标/结果里的成就
 * 选择变成点击切换，而不是列表。因此当枚举值为 ResourceLocation 时直接强制弹出列表。
 */
@Mixin(value = EnumConfig.class, remap = false)
public class EnumConfigMixin<E> {

    @Shadow
    @Final
    public NameMap<E> nameMap;

    @Inject(method = "onClicked", at = @At("HEAD"), cancellable = true)
    private void aoh$forceListForResourceLocations(Widget widget, MouseButton button,
                                                   ConfigCallback callback, CallbackInfo ci) {
        if (nameMap == null || nameMap.size() == 0) {
            return;
        }
        E first = nameMap.get(0);
        if (!(first instanceof ResourceLocation)) {
            return;
        }

        try {
            EnumConfig<?> config = (EnumConfig<?>) (Object) this;
            Class<?> screenClass = Class.forName("dev.ftb.mods.ftblibrary.config.EnumConfig$EnumSelectScreen");
            Constructor<?> ctor = screenClass.getDeclaredConstructor(EnumConfig.class, Panel.class, ConfigCallback.class);
            ctor.setAccessible(true);
            Object screen = ctor.newInstance(config, widget.getParent(), callback);
            screenClass.getMethod("setHasSearchBox", boolean.class).invoke(screen, true);
            screenClass.getMethod("showBottomPanel", boolean.class).invoke(screen, false);
            screenClass.getMethod("showCloseButton", boolean.class).invoke(screen, true);
            screenClass.getMethod("openGui").invoke(screen);
            ci.cancel();
        } catch (Exception e) {
            // 失败时回退到原始行为（不取消）
        }
    }
}
