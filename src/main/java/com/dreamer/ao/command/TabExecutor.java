package com.dreamer.ao.command;

import com.dreamer.ao.LangKeys;
import com.dreamer.ao.data.DataStore;
import com.dreamer.ao.data.ServerDataStore;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * /adv tab 子命令执行器。
 * <p>
 * 管理自定义标签页的增删和排序：
 * <ul>
 *   <li>{@code /adv tab add <name>} — 创建新标签页</li>
 *   <li>{@code /adv tab delete <name>} — 删除标签页（内置标签页不可删除）</li>
 *   <li>{@code /adv tab order <t1,t2,...>} — 设置标签页显示顺序</li>
 * </ul>
 */
final class TabExecutor {

    static int tabAdd(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String name = StringArgumentType.getString(ctx, "name");
        ServerDataStore.getInstance().addCustomTab(name);
        CommandHelper.syncAll(ctx, false); // 分类变更不触发 FTB Quests 同步
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
        CommandHelper.syncAll(ctx, false); // 分类变更不触发 FTB Quests 同步
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
        CommandHelper.syncAll(ctx, false); // 分类变更不触发 FTB Quests 同步
        ctx.getSource().sendSuccess(() -> Component.translatable(LangKeys.CMD_TAB_ORDERED), true);
        return Command.SINGLE_SUCCESS;
    }
}