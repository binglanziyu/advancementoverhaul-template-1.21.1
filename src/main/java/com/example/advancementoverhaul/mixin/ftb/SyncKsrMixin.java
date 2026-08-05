package com.example.advancementoverhaul.mixin.ftb;

import com.example.advancementoverhaul.compat.ftb.FtbQuestsBridge;
import dev.ftb.mods.ftblibrary.net.SyncKnownServerRegistriesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(SyncKnownServerRegistriesPacket.class)
public class SyncKsrMixin {
    @ModifyArg(method = "handle", at = @At(value = "INVOKE", target = "Ldev/architectury/networking/NetworkManager$PacketContext;queue(Ljava/lang/Runnable;)V"), index = 0)
    private static Runnable advancementoverhaul$injectAfterKsrSync(Runnable original) {
        return () -> {
            original.run();
            if (FtbQuestsBridge.isLoaded()) {
                FtbQuestsBridge.syncClientKnownServerRegistries(null);
            }
        };
    }
}
