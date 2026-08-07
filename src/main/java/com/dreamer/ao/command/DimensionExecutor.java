package com.dreamer.ao.command;

import com.dreamer.ao.LangKeys;
import com.dreamer.ao.data.DimensionLock;
import com.dreamer.ao.data.ServerDataStore;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

/**
 * /adv dimension 子命令执行器。
 * <p>
 * 管理维度的锁定/解锁及解锁条件设置：
 * <ul>
 *   <li>{@code /adv dimension lock <dim>} — 锁定维度</li>
 *   <li>{@code /adv dimension unlock <dim>} — 解锁维度</li>
 *   <li>{@code /adv dimension setcondition <dim> <advId>} — 设置解锁条件</li>
 *   <li>{@code /adv dimension removecondition <dim>} — 移除解锁条件</li>
 * </ul>
 */
final class DimensionExecutor {

    static int dimLock(CommandContext<CommandSourceStack> ctx, boolean lock) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String dim = StringArgumentType.getString(ctx, "dim");
        DimensionLock dl = ServerDataStore.getInstance().getDimensionLock(dim);
        if (dl == null) dl = new DimensionLock();
        dl.setDisabled(lock);
        ServerDataStore.getInstance().setDimensionLock(dim, dl);
        CommandHelper.syncAll(ctx);
        ctx.getSource().sendSuccess(() -> CommandHelper.translatable(lock ? LangKeys.CMD_DIM_LOCKED : LangKeys.CMD_DIM_UNLOCKED, dim), true);
        return Command.SINGLE_SUCCESS;
    }

    static int dimSetCondition(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String data = StringArgumentType.getString(ctx, "data");
        String dim = CommandHelper.splitId(data);
        String advId = CommandHelper.splitRest(data);
        if (advId.isEmpty()) {
            ctx.getSource().sendFailure(CommandHelper.translatable(LangKeys.CMD_PARSE_FAILED, "Expected: <dim> <advId>"));
            return 0;
        }
        DimensionLock dl = ServerDataStore.getInstance().getDimensionLock(dim);
        if (dl == null) dl = new DimensionLock();
        dl.setUnlockAdvancementId(advId);
        ServerDataStore.getInstance().setDimensionLock(dim, dl);
        CommandHelper.syncAll(ctx);
        ctx.getSource().sendSuccess(() -> CommandHelper.translatable(LangKeys.CMD_DIM_COND_SET, dim, advId), true);
        return Command.SINGLE_SUCCESS;
    }

    static int dimRemoveCondition(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String dim = StringArgumentType.getString(ctx, "dim");
        DimensionLock dl = ServerDataStore.getInstance().getDimensionLock(dim);
        if (dl != null) {
            dl.setUnlockAdvancementId(null);
            ServerDataStore.getInstance().setDimensionLock(dim, dl);
        }
        CommandHelper.syncAll(ctx);
        ctx.getSource().sendSuccess(() -> CommandHelper.translatable(LangKeys.CMD_DIM_COND_REMOVED, dim), true);
        return Command.SINGLE_SUCCESS;
    }
}