package com.example.advancementoverhaul.command;

import com.example.advancementoverhaul.LangKeys;
import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.ServerDataStore;
import com.example.advancementoverhaul.client.gui.layout.AutoLayout;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;


final class ImportExportExecutor {

    private static final String EXPORT_FILENAME = "advancements_export.json";

    static int importAdvancements(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        try {
            Path file = ServerDataStore.getInstance().getDataFolder().resolve(EXPORT_FILENAME);
            if (!Files.exists(file)) {
                ctx.getSource().sendFailure(Component.translatable(LangKeys.CMD_IMPORT_NOT_FOUND));
                return 0;
            }
            String raw = Files.readString(file).trim();
            if (raw.isEmpty()) {
                ctx.getSource().sendFailure(Component.translatable(LangKeys.CMD_JSON_EMPTY));
                return 0;
            }
            com.google.gson.JsonObject data = com.google.gson.JsonParser.parseString(raw).getAsJsonObject();
            ServerDataStore.getInstance().importAll(data);
            CommandHelper.syncAll(ctx);
            ctx.getSource().sendSuccess(() -> Component.translatable(LangKeys.CMD_IMPORT_DONE), true);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            ctx.getSource().sendFailure(CommandHelper.translatable(LangKeys.CMD_IMPORT_FAILED, e.getMessage()));
            return 0;
        }
    }

    static int exportAdvancements(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        try {
            com.google.gson.JsonObject exported = ServerDataStore.getInstance().exportAll();
            String json = DataStore.GSON_PRETTY.toJson(exported);
            Path file = ServerDataStore.getInstance().getDataFolder().resolve(EXPORT_FILENAME);
            Files.writeString(file, json);
            ctx.getSource().sendSuccess(() -> CommandHelper.translatable(LangKeys.CMD_EXPORT_DONE, file.toString()), true);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            ctx.getSource().sendFailure(CommandHelper.translatable(LangKeys.CMD_EXPORT_FAILED, e.getMessage()));
            return 0;
        }
    }
    static int autoLayout(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        AutoLayout.apply(ServerDataStore.getInstance().getAdvancements());
        ServerDataStore.getInstance().saveAdvancements();
        CommandHelper.syncAll(ctx);
        ctx.getSource().sendSuccess(() -> Component.translatable(LangKeys.CMD_AUTOLAYOUT_DONE), true);
        return Command.SINGLE_SUCCESS;
    }
}