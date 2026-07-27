package com.example.advancementoverhaul.mixin;

import com.example.advancementoverhaul.compat.FtbQuestsBridge;
import dev.ftb.mods.ftblibrary.net.SyncKnownServerRegistriesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Mixin：拦截 FTB Library 的 KnownServerRegistries 客户端同步。
 * <p>
 * {@link SyncKnownServerRegistriesPacket#handle} 会在客户端侧直接替换
 * {@code KnownServerRegistries.client}，覆盖掉我们注入的自定义进度条目。
 * <p>
 * 此 Mixin 包装 handle 中提交给主线程的 Runnable，在原 Runnable 设置
 * KSR.client 之后立即调用 {@link FtbQuestsBridge#syncClientKnownServerRegistries}，
 * 回填所有原版进度树中存在但 KSR 中缺失的条目。
 * <p>
 * 配合 {@link com.example.advancementoverhaul.client.ClientEvents} 中的周期性 tick 检查，
 * 双重保障确保 KSR.client 始终完整，防止 FTB Quests 的
 * {@code AdvancementReward.fillConfigGroup} 因 KSR 缺失条目而 NPE 崩溃。
 */
@Mixin(SyncKnownServerRegistriesPacket.class)
public class SyncKsrMixin {

    @ModifyArg(
        method = "handle",
        at = @At(
            value = "INVOKE",
            target = "Ldev/architectury/networking/NetworkManager$PacketContext;queue(Ljava/lang/Runnable;)V"
        ),
        index = 0
    )
    private static Runnable advancementoverhaul$injectAfterKsrSync(Runnable original) {
        return () -> {
            original.run();
            // KSR.client 已被 SyncKnownServerRegistriesPacket 替换
            // 立即重新注入自定义进度 + 回填树中缺失条目，防止 AdvancementReward.fillConfigGroup NPE
            if (FtbQuestsBridge.isLoaded()) {
                FtbQuestsBridge.syncClientKnownServerRegistries(null);
            }
        };
    }
}
