package com.example.advancementoverhaul.command;

import com.example.advancementoverhaul.LangKeys;
import com.example.advancementoverhaul.compat.AdvancementRegistry;
import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.model.CustomAdvancement;
import com.example.advancementoverhaul.data.ServerDataStore;
import com.example.advancementoverhaul.event.AdvResetEvent;
import com.example.advancementoverhaul.logic.ConditionEvaluator;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.UUID;

/**
 * /adv 命令 — 玩家操作执行器。
 *
 * <h2>包含的子命令</h2>
 * <ul>
 *   <li>complete — 强制完成进度（跳过前置条件检查）</li>
 *   <li>reset — 重置单个或全部进度</li>
 *   <li>give / revoke — 授予/撤销进度</li>
 *   <li>check — 查看进度状态和完成率</li>
 * </ul>
 */
final class AdvPlayerExecutor {

    // ═══════════════ COMPLETE ═══════════════

    /**
     * 强制完成进度。
     * 命令格式：{@code /adv complete <id> [player]}
     */
    static int completeAdvancement(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String id = StringArgumentType.getString(ctx, "id");
        CustomAdvancement adv = ServerDataStore.getInstance().getAdvancement(id);
        if (adv == null) {
            ctx.getSource().sendFailure(
                    CommandHelper.translatable(LangKeys.CMD_ADV_NOT_FOUND, id));
            return 0;
        }

        ServerPlayer player;
        try {
            player = EntityArgument.getPlayer(ctx, "player");
        } catch (Exception e) {
            if (ctx.getSource().getEntity() instanceof ServerPlayer sp) player = sp;
            else {
                ctx.getSource().sendFailure(
                        Component.translatable(LangKeys.CMD_PLAYER_ONLY));
                return 0;
            }
        }

        ConditionEvaluator.tryCompleteForce(player, id);
        CommandHelper.syncTargetPlayer(player);

        final String playerName = player.getName().getString();
        ctx.getSource().sendSuccess(
                () -> CommandHelper.translatable(LangKeys.CMD_ADV_COMPLETED,
                        adv.getName() + " (" + playerName + ")"), true);
        return Command.SINGLE_SUCCESS;
    }

    // ═══════════════ RESET ═══════════════

    /**
     * 重置玩家进度。
     * 命令格式：{@code /adv reset <player> <id|all>}
     */
    static int resetAdvancement(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;

        ServerPlayer target;
        try {
            target = EntityArgument.getPlayer(ctx, "player");
        } catch (Exception e) {
            ctx.getSource().sendFailure(
                    Component.translatable(LangKeys.CMD_PLAYER_NOT_FOUND));
            return 0;
        }

        String targetStr = StringArgumentType.getString(ctx, "target");
        ServerDataStore store = ServerDataStore.getInstance();
        UUID uuid = target.getUUID();

        // 全部重置
        if ("all".equalsIgnoreCase(targetStr)) {
            int count = 0;
            for (String advId : new ArrayList<>(store.getPlayerCompletions(uuid).keySet())) {
                store.setCompleted(uuid, advId, false);
                store.resetConditionProgress(uuid, advId);
                store.setPending(uuid, advId, false);
                AdvancementRegistry.revokeAdvancement(target, advId);
                NeoForge.EVENT_BUS.post(new AdvResetEvent(target, advId));
                count++;
            }
            // 同时清除所有 pending
            store.getPendingAdvancements(uuid).clear();
            CommandHelper.syncTargetPlayer(target);

            final int fc = count;
            ctx.getSource().sendSuccess(
                    () -> CommandHelper.translatable(LangKeys.CMD_ADV_RESET_ALL,
                            target.getName().getString(), fc), true);
            return Command.SINGLE_SUCCESS;
        }

        // 单个进度重置
        CustomAdvancement adv = store.getAdvancement(targetStr);
        if (adv == null) {
            ctx.getSource().sendFailure(
                    CommandHelper.translatable(LangKeys.CMD_ADV_NOT_FOUND, targetStr));
            return 0;
        }
        store.setCompleted(uuid, targetStr, false);
        store.resetConditionProgress(uuid, targetStr);
        store.setPending(uuid, targetStr, false);
        AdvancementRegistry.revokeAdvancement(target, targetStr);
        NeoForge.EVENT_BUS.post(new AdvResetEvent(target, targetStr));
        CommandHelper.syncTargetPlayer(target);

        ctx.getSource().sendSuccess(
                () -> CommandHelper.translatable(LangKeys.CMD_ADV_RESET_ONE,
                        target.getName().getString(), adv.getName()), true);
        return Command.SINGLE_SUCCESS;
    }

    // ═══════════════ GIVE / REVOKE ═══════════════

    /**
     * 授予或撤销进度。
     * 命令格式：{@code /adv give|revoke <id> [player]}
     *
     * @param give true=授予（force complete），false=撤销
     */
    static int giveRevoke(CommandContext<CommandSourceStack> ctx, boolean give) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String id = StringArgumentType.getString(ctx, "id");
        CustomAdvancement adv = ServerDataStore.getInstance().getAdvancement(id);
        if (adv == null) {
            ctx.getSource().sendFailure(
                    CommandHelper.translatable(LangKeys.CMD_ADV_NOT_FOUND, id));
            return 0;
        }

        ServerPlayer player;
        try {
            player = EntityArgument.getPlayer(ctx, "player");
        } catch (Exception e) {
            if (ctx.getSource().getEntity() instanceof ServerPlayer sp) player = sp;
            else {
                ctx.getSource().sendFailure(
                        Component.translatable(LangKeys.CMD_PLAYER_ONLY));
                return 0;
            }
        }

        if (give) {
            ConditionEvaluator.tryCompleteForce(player, id);
        } else {
            ServerDataStore store = ServerDataStore.getInstance();
            UUID uuid = player.getUUID();
            store.setPending(uuid, id, false);
            store.setCompleted(uuid, id, false);
            AdvancementRegistry.revokeAdvancement(player, id);
            NeoForge.EVENT_BUS.post(new AdvResetEvent(player, id));
        }

        CommandHelper.syncTargetPlayer(player);
        ctx.getSource().sendSuccess(
                () -> CommandHelper.translatable(
                        give ? LangKeys.CMD_ADV_GIVEN : LangKeys.CMD_ADV_REVOKED,
                        adv.getName()), true);
        return Command.SINGLE_SUCCESS;
    }

    // ═══════════════ CHECK ═══════════════

    /**
     * 查看进度状态。
     * 显示：完成/待定/进度百分比。
     */
    static int checkAdvancement(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "id");
        CustomAdvancement adv = ServerDataStore.getInstance().getAdvancement(id);
        if (adv == null) {
            ctx.getSource().sendFailure(
                    CommandHelper.translatable(LangKeys.CMD_ADV_NOT_FOUND, id));
            return 0;
        }

        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            ServerDataStore store = ServerDataStore.getInstance();
            UUID uuid = player.getUUID();
            boolean done = store.isCompleted(uuid, id);
            boolean pending = store.isPending(uuid, id);
            int progress = store.getProgress(uuid, id);

            ctx.getSource().sendSuccess(() -> {
                if (done) return CommandHelper.translatable(LangKeys.CMD_ADV_CHECK,
                        adv.getName(), true);
                if (pending) return Component.literal(
                        adv.getName() + " — pending (" + progress + "%)");
                return CommandHelper.translatable(LangKeys.CMD_ADV_CHECK,
                        adv.getName(), false);
            }, false);
            return Command.SINGLE_SUCCESS;
        }

        ctx.getSource().sendFailure(Component.translatable(LangKeys.CMD_PLAYER_ONLY));
        return 0;
    }
}
