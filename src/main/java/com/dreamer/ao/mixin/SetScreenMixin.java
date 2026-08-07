package com.dreamer.ao.mixin;

import com.dreamer.ao.Config;
import com.dreamer.ao.client.gui.AdvancementScreen;
import com.dreamer.ao.client.gui.timeline.TimelineScreen;
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
    /** ThreadLocal 标志，仅阻止当前线程递归调用 setScreen，不影响其他线程/其他 mod。 */
    @Unique
    private static final ThreadLocal<Boolean> REPLACING = ThreadLocal.withInitial(() -> false);

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void aoh$replaceScreens(Screen screen, CallbackInfo ci) {
        if (Boolean.TRUE.equals(REPLACING.get())) {
            return;
        }
        if (screen instanceof AdvancementsScreen && Config.HIDE_VANILLA.get()) {
            REPLACING.set(true);
            try {
                ci.cancel(); // 先取消原 setScreen，再设置替换画面
                Minecraft.getInstance().setScreen(new AdvancementScreen());
            } finally {
                REPLACING.set(false);
            }
            return;
        }
        if (screen instanceof StatsScreen && Config.REPLACE_STATS_SCREEN.get()) {
            REPLACING.set(true);
            try {
                ci.cancel(); // 先取消原 setScreen，再设置替换画面
                Minecraft.getInstance().setScreen(new TimelineScreen());
            } finally {
                REPLACING.set(false);
            }
        }
    }
}
