package com.dreamer.ao.mixin.ftb;

import com.dreamer.ao.ModInfo;
import com.dreamer.ao.compat.ftb.FtbQuestsBridge;
import dev.ftb.mods.ftblibrary.net.SyncKnownServerRegistriesPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(SyncKnownServerRegistriesPacket.class)
public class SyncKsrMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncKsrMixin.class);

    static {
        LOGGER.info("SyncKsrMixin applied — will inject advancement registries after FTB KSR sync");
    }

    @ModifyArg(method = "handle", at = @At(value = "INVOKE", target = "Ldev/architectury/networking/NetworkManager$PacketContext;queue(Ljava/lang/Runnable;)V"), index = 0)
    private static Runnable advancementoverhaul$injectAfterKsrSync(Runnable original) {
        return () -> {
            original.run();
            if (FtbQuestsBridge.isLoaded()) {
                LOGGER.debug("KSR synced, injecting custom advancement registries");
                FtbQuestsBridge.syncClientKnownServerRegistries(null);
            }
        };
    }
}
