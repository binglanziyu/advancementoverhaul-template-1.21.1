package com.example.advancementoverhaul.command;

import com.example.advancementoverhaul.LangKeys;
import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.ServerDataStore;
import com.example.advancementoverhaul.data.model.VanillaAdvMeta;
import com.example.advancementoverhaul.compat.AdvancementRegistry;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * /adv vanilla 子命令执行器。
 * <p>
 * 管理原版/模组进度的启用状态与画布元数据：
 * <ul>
 *   <li>{@code /adv vanilla enable/disable <id>} — 启用/禁用单个原版进度</li>
 *   <li>{@code /adv vanilla enableall/disableall} — 批量启用/禁用</li>
 *   <li>{@code /adv vanilla setpos <id> <x> <y>} — 设置画布位置</li>
 *   <li>{@code /adv vanilla settab/cleartab <id>} — 设置/清除标签页</li>
 *   <li>{@code /adv vanilla save <id> <json>} — 保存元数据 (tab + prerequisites)</li>
 * </ul>
 */
final class VanillaExecutor {

    static int vanillaEnable(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String id = StringArgumentType.getString(ctx, "id");
        ServerDataStore.getInstance().setVanillaEnabled(id, true);
        CommandHelper.syncAll(ctx);
        ctx.getSource().sendSuccess(() -> CommandHelper.translatable(LangKeys.CMD_VANILLA_ENABLED, id), true);
        return Command.SINGLE_SUCCESS;
    }

    static int vanillaDisable(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String id = StringArgumentType.getString(ctx, "id");
        ServerDataStore store = ServerDataStore.getInstance();

        // 禁用时删除所有元数据（分类、位置、前置条件），使其回到"原有成就"分类
        store.getVanillaMetaMap().remove(id);
        store.saveVanillaMeta();

        // 标记禁用
        store.setVanillaEnabled(id, false);

        // 压制已完成的进度
        for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers())
            AdvancementRegistry.suppressVanillaAdvancement(p, id);

        CommandHelper.syncAll(ctx);
        ctx.getSource().sendSuccess(() -> CommandHelper.translatable(LangKeys.CMD_VANILLA_DISABLED, id), true);
        return Command.SINGLE_SUCCESS;
    }

    static int vanillaEnableAll(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        Set<String> allIds = CommandHelper.collectAllVanillaIds(ctx);
        ServerDataStore store = ServerDataStore.getInstance();
        store.enableAllVanilla(allIds);
        // 按进度树自动创建分类并分配
        store.autoAssignVanillaTabs();
        // 清除旧缓存使 SyncManager 重新构建（rootTab 会使用新的元数据）
        com.example.advancementoverhaul.network.SyncManager.markVanillaCacheDirty();
        CommandHelper.syncAll(ctx);
        ctx.getSource().sendSuccess(() -> Component.translatable(LangKeys.CMD_VANILLA_ALL_EN), true);
        return Command.SINGLE_SUCCESS;
    }

    static int vanillaDisableAll(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        Set<String> allDisabled = CommandHelper.collectAllVanillaIds(ctx);
        ServerDataStore.getInstance().setVanillaDisabledBatch(allDisabled);
        for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers())
            AdvancementRegistry.suppressAllDisabled(p);
        CommandHelper.syncAll(ctx);
        ctx.getSource().sendSuccess(() -> Component.translatable(LangKeys.CMD_VANILLA_ALL_DIS), true);
        return Command.SINGLE_SUCCESS;
    }

    static int vanillaSetPos(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String data = StringArgumentType.getString(ctx, "data");
        String[] parts = data.split(" ");
        if (parts.length < 3) {
            ctx.getSource().sendFailure(CommandHelper.translatable(LangKeys.CMD_PARSE_FAILED, "Expected: <id> <x> <y>"));
            return 0;
        }
        String id = parts[0];
        int x, y;
        try {
            x = Integer.parseInt(parts[1]);
            y = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            ctx.getSource().sendFailure(CommandHelper.translatable(LangKeys.CMD_PARSE_FAILED, "Invalid x or y"));
            return 0;
        }
        VanillaAdvMeta meta = ServerDataStore.getInstance().getVanillaMeta(id);
        if (meta == null) meta = new VanillaAdvMeta();
        meta.setX(x); meta.setY(y);
        ServerDataStore.getInstance().setVanillaMeta(id, meta);
        CommandHelper.syncAll(ctx, false); // 位置变更不触发 FTB Quests 同步
        ctx.getSource().sendSuccess(() -> CommandHelper.translatable(LangKeys.CMD_VANILLA_SET_POS, id), true);
        return Command.SINGLE_SUCCESS;
    }

    static int vanillaSetTab(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String data = StringArgumentType.getString(ctx, "data");
        String id = CommandHelper.splitId(data);
        String tab = CommandHelper.splitRest(data);
        if (tab.isEmpty()) {
            ctx.getSource().sendFailure(CommandHelper.translatable(LangKeys.CMD_PARSE_FAILED, "Expected: <id> <tab>"));
            return 0;
        }
        VanillaAdvMeta meta = ServerDataStore.getInstance().getVanillaMeta(id);
        if (meta == null) meta = new VanillaAdvMeta();
        meta.setTab(tab);
        ServerDataStore.getInstance().setVanillaMeta(id, meta);

        // ★ 分配分类时自动启用
        ServerDataStore.getInstance().setVanillaEnabled(id, true);

        CommandHelper.syncAll(ctx, false); // 分类变更不触发 FTB Quests 同步
        ctx.getSource().sendSuccess(() -> CommandHelper.translatable(LangKeys.CMD_VANILLA_SET_TAB, id, tab), true);
        return Command.SINGLE_SUCCESS;
    }

    static int vanillaClearTab(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String id = StringArgumentType.getString(ctx, "id");
        VanillaAdvMeta meta = ServerDataStore.getInstance().getVanillaMeta(id);
        if (meta != null) {
            meta.setTab(null);
            ServerDataStore.getInstance().setVanillaMeta(id, meta);
        }
        CommandHelper.syncAll(ctx, false); // 分类变更不触发 FTB Quests 同步
        ctx.getSource().sendSuccess(() -> CommandHelper.translatable(LangKeys.CMD_VANILLA_CLEAR_TAB, id), true);
        return Command.SINGLE_SUCCESS;
    }

    static int vanillaSave(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String data = StringArgumentType.getString(ctx, "data");
        String id = CommandHelper.splitId(data);
        String json = CommandHelper.splitRest(data);
        ServerDataStore store = ServerDataStore.getInstance();

        try {
            com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();

            VanillaAdvMeta meta = store.getVanillaMeta(id);
            if (meta == null) meta = new VanillaAdvMeta();

            boolean hasPrerequisites = obj.has("prerequisites");

            if (obj.has("tab")) {
                String tab = obj.get("tab").getAsString();
                if (!tab.isEmpty()) meta.setTab(tab);
            }

            if (hasPrerequisites) {
                List<String> prereqs = new ArrayList<>();
                for (var e : obj.getAsJsonArray("prerequisites")) {
                    if (e.isJsonPrimitive()) prereqs.add(e.getAsString());
                }
                meta.setPrerequisites(prereqs);
            }

            store.setVanillaMeta(id, meta);

            // ★ 保存（含分类）时自动启用
            if (meta.getTab() != null && !meta.getTab().isEmpty()) {
                store.setVanillaEnabled(id, true);
            }

            // 仅当前置条件变更时才触发 FTB Quests 同步（分类变更不触发）
            CommandHelper.syncAll(ctx, hasPrerequisites);
            final String savedTab = meta.getTab();
            ctx.getSource().sendSuccess(() -> CommandHelper.translatable(LangKeys.CMD_VANILLA_SET_TAB, id, savedTab), true);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            ctx.getSource().sendFailure(CommandHelper.translatable(LangKeys.CMD_JSON_ERROR, e.getMessage()));
            return 0;
        }
    }
}