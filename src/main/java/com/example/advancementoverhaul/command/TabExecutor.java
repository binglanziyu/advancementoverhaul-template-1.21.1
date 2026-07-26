package com.example.advancementoverhaul.command;

import com.example.advancementoverhaul.LangKeys;
import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.ServerDataStore;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

final class TabExecutor {

    static int tabAdd(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String name = StringArgumentType.getString(ctx, "name");
        ServerDataStore.getInstance().addCustomTab(name);
        CommandHelper.syncAll(ctx);
        ctx.getSource().sendSuccess(() -> CommandHelper.translatable(LangKeys.CMD_TAB_ADDED, name), true);
        return Command.SINGLE_SUCCESS;
    }

    static int tabDelete(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String name = StringArgumentType.getString(ctx, "name");
        if (DataStore.isBuiltinTab(name)) {
            ctx.getSource().sendFailure(Component.translatable(LangKeys.CMD_TAB_BUILTIN_NODELETE));
            return 0;
        }
        ServerDataStore.getInstance().removeCustomTab(name);
        CommandHelper.syncAll(ctx);
        ctx.getSource().sendSuccess(() -> CommandHelper.translatable(LangKeys.CMD_TAB_DELETED, name), true);
        return Command.SINGLE_SUCCESS;
    }

    static int tabOrder(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String orderStr = StringArgumentType.getString(ctx, "order");
        List<String> order = new ArrayList<>(List.of(orderStr.split(",")));
        order.replaceAll(String::trim);
        order.removeIf(String::isEmpty);
        ServerDataStore.getInstance().setTabOrder(order);
        CommandHelper.syncAll(ctx);
        ctx.getSource().sendSuccess(() -> Component.translatable(LangKeys.CMD_TAB_ORDERED), true);
        return Command.SINGLE_SUCCESS;
    }
}