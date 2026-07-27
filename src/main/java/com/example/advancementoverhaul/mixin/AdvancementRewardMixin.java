package com.example.advancementoverhaul.mixin;

import com.example.advancementoverhaul.LangKeys;
import com.example.advancementoverhaul.ModInfo;
import com.example.advancementoverhaul.compat.AdvancementRegistry;
import com.example.advancementoverhaul.compat.FtbQuestsBridge;
import com.example.advancementoverhaul.data.ServerDataStore;
import com.example.advancementoverhaul.logic.ConditionEvaluator;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.util.KnownServerRegistries;
import dev.ftb.mods.ftbquests.quest.reward.AdvancementReward;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Mixin：确保 {@link AdvancementReward#fillConfigGroup} 使用的
 * NameMap 中每个 ID 在 KSR 中都有对应的条目。
 * <p>
 * 问题根因：当 KSR.client 中缺少条目时，
 * {@code fillConfigGroup} 中的 {@code NameMap.of(this.advancement, keySet.toArray())}
 * 会把当前选中值（默认 {@code minecraft:story/root}）也加入 NameMap。
 * 该 NameMap 的 displayName 回调（lambda）在 EditConfigScreen 渲染时被触发，
 * 查询 {@code ksr.advancements().get(id).name()}，若 id 不在 KSR map 中 → NPE。
 * <p>
 * 由于 NameMap lambda 持有对 KSR 底层 map 的实时引用（而非快照），
 * 注入的条目<b>不能</b>在 fillConfigGroup 结束后立即移除——lambda 会在
 * 后续的 EditConfigScreen.onInit 中再次被调用。注入条目将一直留在 KSR 中，
 * 由 {@link FtbQuestsBridge#syncClientKnownServerRegistries} 的周期性过滤负责清理。
 */
@Mixin(value = AdvancementReward.class, remap = false)
public abstract class AdvancementRewardMixin {

    @Accessor("advancement")
    public abstract ResourceLocation getCurrentAdvancement();

    @Inject(method = "fillConfigGroup", at = @At("HEAD"))
    private void aoh$ensureCurrentAdvancementInKSR(ConfigGroup config, CallbackInfo ci) {
        try {
            KnownServerRegistries ksr = KnownServerRegistries.client;
            ResourceLocation currentAdv = getCurrentAdvancement();
            if (ksr != null && currentAdv != null) {
                var map = ksr.advancements();
                // 不检查 map.isEmpty()——即使 map 为空也要注入；
                // 否则 NameMap.of(this.advancement, []) 中包含的唯一条目也无法在 KSR 中查到
                if (!map.containsKey(currentAdv)) {
                    map.put(currentAdv, FtbQuestsBridge.createClientAdvancementInfo(currentAdv));
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 在 FTB Quests 的 AdvancementReward 授予成就<b>之前</b>拦截 claim 方法。
     * <p>
     * 当玩家领取 FTB 任务奖励时，即使任务本身已完成，作为奖励的自定义成就
     * 也必须满足其自身的所有条件（如"击杀 3 个僵尸"）才能被授予。
     * 条件不满足时取消 award 调用，防止成就在条件达成前被授予。
     * <p>
     * 与 {@code ServerEventHandler.onAdvancementEarn} 不同，
     * 此 Mixin 在 award 执行<b>之前</b>拦截，避免先授予后撤销的竞态问题。
     */
    @Inject(method = "claim", at = @At("HEAD"), cancellable = true)
    private void aoh$checkConditionsBeforeClaim(ServerPlayer player, boolean notify, CallbackInfo ci) {
        try {
            ResourceLocation adv = getCurrentAdvancement();
            if (adv == null) return;
            if (!ModInfo.MOD_ID.equals(adv.getNamespace())) return;

            String customId = AdvancementRegistry.getCustomIdFromVanilla(adv);
            if (customId == null) return;

            UUID uuid = player.getUUID();
            ServerDataStore store = ServerDataStore.getInstance();

            // 已完成的无需再检查
            if (store.isCompleted(uuid, customId)) return;

            // 条件未满足 → 取消 FTB 奖励授予
            if (!ConditionEvaluator.checkAllConditionsMet(uuid, customId)) {
                ci.cancel();
                player.sendSystemMessage(Component.translatable(LangKeys.CMD_ADV_CONDITIONS_NOT_MET));
            }
            // 条件满足则放行，让 FTB 正常 award，后续由 AdvancementEarnEvent 同步到自定义系统
        } catch (Exception ignored) {
        }
    }
}
