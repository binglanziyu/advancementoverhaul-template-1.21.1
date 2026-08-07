package com.dreamer.ao.mixin;

import com.dreamer.ao.compat.ftb.FtbQuestsBridge;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.util.KnownServerRegistries;
import dev.ftb.mods.ftbquests.quest.task.AdvancementTask;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin：确保 {@link AdvancementTask#fillConfigGroup} 使用的
 * NameMap 中每个 ID 在 KSR 中都有对应的条目。
 * <p>
 * 与 {@link AdvancementRewardMixin} 相同逻辑，作用于 FTB 任务的"目标"中的成就选择。
 * <p>
 * AdvancementTask.fillConfigGroup 构建 NameMap：
 * {@code NameMap.of(this.advancement, keySet.toArray())}
 * 同样会因为当前选中值不在 KSR 中而导致 NPE 或显示错误。
 * <p>
 * 条目注入后不可在 TAIL 中立即移除（参见 AdvancementRewardMixin 文档注释）。
 * 清理工作由周期性 syncClientKnownServerRegistries 负责。
 */
@Pseudo
@Mixin(value = AdvancementTask.class, remap = false)
public abstract class AdvancementTaskMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdvancementTaskMixin.class);

    @Accessor("advancement")
    public abstract ResourceLocation getCurrentAdvancement();

    @Inject(method = "fillConfigGroup", at = @At("HEAD"))
    private void aoh$ensureCurrentAdvancementInKSR(ConfigGroup config, CallbackInfo ci) {
        try {
            KnownServerRegistries ksr = KnownServerRegistries.client;
            ResourceLocation currentAdv = getCurrentAdvancement();
            if (ksr != null && currentAdv != null) {
                var map = ksr.advancements();
                if (!map.containsKey(currentAdv)) {
                    map.put(currentAdv, FtbQuestsBridge.createClientAdvancementInfo(currentAdv));
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to ensure current advancement in KSR: {}", e.getMessage());
        }
    }
}
