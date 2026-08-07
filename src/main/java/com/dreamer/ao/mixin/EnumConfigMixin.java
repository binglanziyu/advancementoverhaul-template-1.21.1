package com.dreamer.ao.mixin;

import dev.ftb.mods.ftblibrary.config.ConfigCallback;
import dev.ftb.mods.ftblibrary.config.EnumConfig;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;

/**
 * Mixin：将 FTB Library 的 EnumConfig 对成就（ResourceLocation）枚举的交互方式
 * 从"点击循环切换"强制改为"打开列表选择"。
 * <p>
 * 原始逻辑：当 NameMap 条目数 {@code > 16} 或按住 Ctrl 时才打开列表，否则每次点击
 * 循环切换到下一个值。本模组过滤后 KSR 通常只剩少量条目，导致目标/结果里的成就
 * 选择变成点击切换，而不是列表。因此当枚举值为 ResourceLocation 时直接强制弹出列表。
 * <p>
 * 使用 MethodHandle 替代反射提高调用性能，失败时自动回退到反射方式。
 */
@Pseudo
@Mixin(value = EnumConfig.class, remap = false)
public class EnumConfigMixin<E> {

    private static final Logger LOGGER = LoggerFactory.getLogger("AO/EnumConfig");

    @Shadow
    @Final
    public NameMap<E> nameMap;

    // ── 缓存的 MethodHandle（懒初始化），失败则回退到反射 ──

    private static volatile MethodHandle screenConstructor;
    private static volatile MethodHandle setSearchBox;
    private static volatile MethodHandle showBottomPanel;
    private static volatile MethodHandle showCloseButton;
    private static volatile MethodHandle openGui;
    private static volatile boolean handlesResolved;
    private static volatile boolean handlesFailed;

    private static void resolveHandles() {
        if (handlesResolved || handlesFailed) return;
        synchronized (EnumConfigMixin.class) {
            if (handlesResolved || handlesFailed) return;
            try {
                Class<?> screenClass = Class.forName(
                        "dev.ftb.mods.ftblibrary.config.EnumConfig$EnumSelectScreen");
                var lookup = MethodHandles.privateLookupIn(screenClass, MethodHandles.lookup());
                screenConstructor = lookup.findConstructor(screenClass,
                        MethodType.methodType(void.class, EnumConfig.class, Panel.class, ConfigCallback.class));
                setSearchBox = lookup.findVirtual(screenClass, "setHasSearchBox",
                        MethodType.methodType(void.class, boolean.class));
                showBottomPanel = lookup.findVirtual(screenClass, "showBottomPanel",
                        MethodType.methodType(void.class, boolean.class));
                showCloseButton = lookup.findVirtual(screenClass, "showCloseButton",
                        MethodType.methodType(void.class, boolean.class));
                openGui = lookup.findVirtual(screenClass, "openGui",
                        MethodType.methodType(void.class));
                handlesResolved = true;
                LOGGER.debug("EnumSelectScreen MethodHandles resolved successfully");
            } catch (Throwable e) {
                handlesFailed = true;
                LOGGER.debug("Failed to resolve EnumSelectScreen MethodHandles, "
                        + "will fall back to reflection: {}", e.getMessage());
            }
        }
    }

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

        resolveHandles();

        if (handlesResolved) {
            tryMethodHandle(widget, callback, ci);
        } else {
            tryReflection(widget, callback, ci);
        }
    }

    private void tryMethodHandle(Widget widget, ConfigCallback callback, CallbackInfo ci) {
        try {
            EnumConfig<?> config = (EnumConfig<?>) (Object) this;
            Object screen = screenConstructor.invoke(config, widget.getParent(), callback);
            setSearchBox.invoke(screen, true);
            showBottomPanel.invoke(screen, false);
            showCloseButton.invoke(screen, true);
            openGui.invoke(screen);
            ci.cancel();
        } catch (Throwable e) {
            LOGGER.debug("MethodHandle invocation failed, falling back to reflection: {}", e.getMessage());
            tryReflection(widget, callback, ci);
        }
    }

    /** 反射回退：原始实现，兼容 MethodHandle 解析失败或运行时失败的情况 */
    private void tryReflection(Widget widget, ConfigCallback callback, CallbackInfo ci) {
        try {
            EnumConfig<?> config = (EnumConfig<?>) (Object) this;
            Class<?> screenClass = Class.forName(
                    "dev.ftb.mods.ftblibrary.config.EnumConfig$EnumSelectScreen");
            Constructor<?> ctor = screenClass.getDeclaredConstructor(
                    EnumConfig.class, Panel.class, ConfigCallback.class);
            ctor.setAccessible(true);
            Object screen = ctor.newInstance(config, widget.getParent(), callback);
            screenClass.getMethod("setHasSearchBox", boolean.class).invoke(screen, true);
            screenClass.getMethod("showBottomPanel", boolean.class).invoke(screen, false);
            screenClass.getMethod("showCloseButton", boolean.class).invoke(screen, true);
            screenClass.getMethod("openGui").invoke(screen);
            ci.cancel();
        } catch (Exception e) {
            LOGGER.debug("EnumSelectScreen reflection also failed: {}", e.getMessage());
            // 回退到原始行为（不取消事件）
        }
    }
}
