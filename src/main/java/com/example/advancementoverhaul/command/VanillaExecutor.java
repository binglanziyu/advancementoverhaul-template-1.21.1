package com.example.advancementoverhaul.command;

import com.example.advancementoverhaul.LangKeys;
import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.ServerDataStore;
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

        // ★ 禁用：清除所有元数据（分类、位置、前置条件），回归原样
        store.getVanillaMetaMap().remove(id);
        store.saveVanillaMeta();  // ← 需要确认此方法是否公开，见下方说明

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
        ServerDataStore.getInstance().enableAllVanilla(allIds);
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
        DataStore.VanillaAdvMeta meta = ServerDataStore.getInstance().getVanillaMeta(id);
        if (meta == null) meta = new DataStore.VanillaAdvMeta();
        meta.setX(x); meta.setY(y);
        ServerDataStore.getInstance().setVanillaMeta(id, meta);
        CommandHelper.syncAll(ctx);
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
        DataStore.VanillaAdvMeta meta = ServerDataStore.getInstance().getVanillaMeta(id);
        if (meta == null) meta = new DataStore.VanillaAdvMeta();
        meta.setTab(tab);
        ServerDataStore.getInstance().setVanillaMeta(id, meta);

        // ★ 分配分类时自动启用
        ServerDataStore.getInstance().setVanillaEnabled(id, true);

        CommandHelper.syncAll(ctx);
        ctx.getSource().sendSuccess(() -> CommandHelper.translatable(LangKeys.CMD_VANILLA_SET_TAB, id, tab), true);
        return Command.SINGLE_SUCCESS;
    }

    static int vanillaClearTab(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String id = StringArgumentType.getString(ctx, "id");
        DataStore.VanillaAdvMeta meta = ServerDataStore.getInstance().getVanillaMeta(id);
        if (meta != null) {
            meta.setTab(null);
            ServerDataStore.getInstance().setVanillaMeta(id, meta);
        }
        CommandHelper.syncAll(ctx);
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

            DataStore.VanillaAdvMeta meta = store.getVanillaMeta(id);
            if (meta == null) meta = new DataStore.VanillaAdvMeta();

            if (obj.has("tab")) {
                String tab = obj.get("tab").getAsString();
                if (!tab.isEmpty()) meta.setTab(tab);
            }

            if (obj.has("prerequisites")) {
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

            CommandHelper.syncAll(ctx);
            final String savedTab = meta.getTab();
            ctx.getSource().sendSuccess(() -> CommandHelper.translatable(LangKeys.CMD_VANILLA_SET_TAB, id, savedTab), true);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            ctx.getSource().sendFailure(CommandHelper.translatable(LangKeys.CMD_JSON_ERROR, e.getMessage()));
            return 0;
        }
    }
}