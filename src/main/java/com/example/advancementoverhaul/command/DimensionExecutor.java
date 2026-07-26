package com.example.advancementoverhaul.command;

import com.example.advancementoverhaul.LangKeys;
import com.example.advancementoverhaul.data.DimensionLock;
import com.example.advancementoverhaul.data.ServerDataStore;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

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