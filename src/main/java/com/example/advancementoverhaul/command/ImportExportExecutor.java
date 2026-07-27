package com.example.advancementoverhaul.command;

import com.example.advancementoverhaul.LangKeys;
import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.ServerDataStore;
import com.example.advancementoverhaul.data.model.VanillaAdvMeta;
import com.example.advancementoverhaul.client.gui.layout.AutoLayout;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * /adv 导入/导出/自动布局子命令执行器。
 * <p>
 * 支持：
 * <ul>
 *   <li>{@code /adv import} — 从 import/ 文件夹扫描 .json 文件导入数据</li>
 *   <li>{@code /adv export} — 导出所有配置到 export/ 文件夹</li>
 *   <li>{@code /adv autolayout} — 对画布上的进度自动排版布局</li>
 * </ul>
 * <p>
 * 导出和导入使用独立的子文件夹，避免导出的文件被直接导入。
 */
final class ImportExportExecutor {

    private static final String EXPORT_DIR = "export";
    private static final String IMPORT_DIR = "import";
    private static final DateTimeFormatter EXPORT_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    static int importAdvancements(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        try {
            Path importDir = ServerDataStore.getInstance().getDataFolder().resolve(IMPORT_DIR);
            if (!Files.isDirectory(importDir)) {
                ctx.getSource().sendFailure(Component.translatable(LangKeys.CMD_IMPORT_NO_FILES));
                return 0;
            }

            // 扫描 import/ 下所有 .json 文件
            List<Path> jsonFiles;
            try (Stream<Path> s = Files.list(importDir)) {
                jsonFiles = s.filter(p -> p.toString().toLowerCase().endsWith(".json"))
                        .sorted().toList();
            }

            if (jsonFiles.isEmpty()) {
                ctx.getSource().sendFailure(Component.translatable(LangKeys.CMD_IMPORT_NO_FILES));
                return 0;
            }
            if (jsonFiles.size() > 1) {
                ctx.getSource().sendFailure(Component.translatable(LangKeys.CMD_IMPORT_MULTIPLE, jsonFiles.size()));
                return 0;
            }

            // 只有一个文件，直接导入
            Path file = jsonFiles.get(0);
            String raw = Files.readString(file).trim();
            if (raw.isEmpty()) {
                ctx.getSource().sendFailure(Component.translatable(LangKeys.CMD_JSON_EMPTY));
                return 0;
            }
            com.google.gson.JsonObject data = com.google.gson.JsonParser.parseString(raw).getAsJsonObject();
            ServerDataStore.getInstance().importAll(data);
            CommandHelper.syncAll(ctx);
            ctx.getSource().sendSuccess(() -> Component.translatable(LangKeys.CMD_IMPORT_DONE, file.getFileName().toString()), true);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            ctx.getSource().sendFailure(CommandHelper.translatable(LangKeys.CMD_IMPORT_FAILED, e.getMessage()));
            return 0;
        }
    }

    static int exportAdvancements(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        try {
            Path exportDir = ServerDataStore.getInstance().getDataFolder().resolve(EXPORT_DIR);
            Files.createDirectories(exportDir);
            String ts = LocalDateTime.now().format(EXPORT_TS);
            Path file = exportDir.resolve("advancements_export_" + ts + ".json");

            com.google.gson.JsonObject exported = ServerDataStore.getInstance().exportAll();
            String json = DataStore.GSON_PRETTY.toJson(exported);
            Files.writeString(file, json);

            // 向所有管理员广播导出成功消息
            ctx.getSource().sendSuccess(
                    () -> CommandHelper.translatable(LangKeys.CMD_EXPORT_DONE, file.toAbsolutePath().toString()),
                    true);

            // 额外向执行命令的玩家发送系统消息，以确保玩家看到明确的路径通知
            if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                player.sendSystemMessage(
                        CommandHelper.translatable(LangKeys.CMD_EXPORT_DONE, file.toAbsolutePath().toString()));
            }

            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            ctx.getSource().sendFailure(CommandHelper.translatable(LangKeys.CMD_EXPORT_FAILED, e.getMessage()));
            return 0;
        }
    }
    static int autoLayout(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        ServerDataStore store = ServerDataStore.getInstance();

        // 收集已启用的原版进度元数据
        Map<String, VanillaAdvMeta> vanillaForLayout = new HashMap<>();
        for (var e : store.getVanillaMetaMap().entrySet()) {
            if (store.isVanillaEnabled(e.getKey())) {
                vanillaForLayout.put(e.getKey(), e.getValue());
            }
        }

        AutoLayout.apply(store.getAdvancements(), vanillaForLayout, store.getVanillaParentMap());
        store.saveAdvancements();
        store.saveVanillaMeta();
        CommandHelper.syncAll(ctx);
        ctx.getSource().sendSuccess(() -> Component.translatable(LangKeys.CMD_AUTOLAYOUT_DONE), true);
        return Command.SINGLE_SUCCESS;
    }
}