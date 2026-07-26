package com.example.advancementoverhaul.command;

import com.example.advancementoverhaul.data.ServerDataStore;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.example.advancementoverhaul.compat.AdvancementRegistry;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.example.advancementoverhaul.LangKeys;
import com.example.advancementoverhaul.event.SyncManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class CommandHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/Commands");

    // ═══════════════ SUGGESTIONS ═══════════════

    private static CompletableFuture<Suggestions> suggestAdvIds(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String id : ServerDataStore.getInstance().getAdvancements().keySet()) builder.suggest(id);
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestTabNames(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String tab : ServerDataStore.getInstance().getCustomTabs()) builder.suggest(tab);
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestDimensions(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        builder.suggest("minecraft:overworld");
        builder.suggest("minecraft:the_nether");
        builder.suggest("minecraft:the_end");
        for (String dim : ServerDataStore.getInstance().getDimensionLocks().keySet()) builder.suggest(dim);
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestVanillaIds(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        try {
            for (var holder : ctx.getSource().getServer().getAdvancements().getAllAdvancements()) {
                if (!com.example.advancementoverhaul.compat.AdvancementRegistry.isCustomAdvancement(holder.id())) {
                    builder.suggest(holder.id().toString());
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to suggest vanilla advancement IDs: {}", e.getMessage());
        }
        return builder.buildFuture();
    }
    // ═══════════════ REGISTRATION ═══════════════

    public static void registerCommands(RegisterCommandsEvent event) { register(event.getDispatcher()); }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("adv")
                .then(Commands.literal("complete")
                        .then(Commands.argument("id", StringArgumentType.greedyString())
                                .suggests(CommandHandler::suggestAdvIds)
                                .executes(AdvPlayerExecutor::completeAdvancement)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(AdvPlayerExecutor::completeAdvancement))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("target", StringArgumentType.greedyString())
                                        .suggests((ctx, b) -> { b.suggest("all"); return suggestAdvIds(ctx, b); })
                                        .executes(AdvPlayerExecutor::resetAdvancement))))
                .then(Commands.literal("give")
                        .then(Commands.argument("id", StringArgumentType.greedyString())
                                .suggests(CommandHandler::suggestAdvIds)
                                .executes(ctx -> AdvPlayerExecutor.giveRevoke(ctx, true))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> AdvPlayerExecutor.giveRevoke(ctx, true)))))
                .then(Commands.literal("revoke")
                        .then(Commands.argument("id", StringArgumentType.greedyString())
                                .suggests(CommandHandler::suggestAdvIds)
                                .executes(ctx -> AdvPlayerExecutor.giveRevoke(ctx, false))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> AdvPlayerExecutor.giveRevoke(ctx, false)))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("id", StringArgumentType.greedyString())
                                .suggests(CommandHandler::suggestAdvIds)
                                .executes(AdvCrudExecutor::deleteAdvancement)))
                .then(Commands.literal("batchdelete")
                        .then(Commands.argument("ids", StringArgumentType.greedyString())
                                .executes(AdvCrudExecutor::batchDelete)))
                .then(Commands.literal("setname")
                        .then(Commands.argument("data", StringArgumentType.greedyString())
                                .suggests(CommandHandler::suggestAdvIds)
                                .executes(AdvCrudExecutor::setName)))
                .then(Commands.literal("setdescription")
                        .then(Commands.argument("data", StringArgumentType.greedyString())
                                .suggests(CommandHandler::suggestAdvIds)
                                .executes(AdvCrudExecutor::setDescription)))
                .then(Commands.literal("seticon")
                        .then(Commands.argument("data", StringArgumentType.greedyString())
                                .suggests(CommandHandler::suggestAdvIds)
                                .executes(AdvCrudExecutor::setIcon)))
                .then(Commands.literal("togglehidden")
                        .then(Commands.argument("id", StringArgumentType.greedyString())
                                .suggests(CommandHandler::suggestAdvIds)
                                .executes(AdvCrudExecutor::toggleHidden)))
                .then(Commands.literal("setprereq")
                        .then(Commands.argument("data", StringArgumentType.greedyString())
                                .suggests(CommandHandler::suggestAdvIds)
                                .executes(AdvCrudExecutor::setPrereq)))
                .then(Commands.literal("check")
                        .then(Commands.argument("id", StringArgumentType.greedyString())
                                .suggests(CommandHandler::suggestAdvIds)
                                .executes(AdvPlayerExecutor::checkAdvancement)))
                .then(Commands.literal("createjson")
                        .then(Commands.argument("json", StringArgumentType.greedyString())
                                .executes(AdvCrudExecutor::createFromJson)))
                .then(Commands.literal("updatejson")
                        .then(Commands.argument("json", StringArgumentType.greedyString())
                                .executes(AdvCrudExecutor::updateFromJson)))
                .then(Commands.literal("import")
                        .executes(ImportExportExecutor::importAdvancements))
                .then(Commands.literal("export")
                        .executes(ImportExportExecutor::exportAdvancements))
                .then(Commands.literal("autolayout")
                        .executes(ImportExportExecutor::autoLayout))
                .then(Commands.literal("reload")
                        .executes(CommandHandler::reloadCommand))
                .then(Commands.literal("dimension")
                        .then(Commands.literal("lock")
                                .then(Commands.argument("dim", StringArgumentType.greedyString())
                                        .suggests(CommandHandler::suggestDimensions)
                                        .executes(ctx -> DimensionExecutor.dimLock(ctx, true))))
                        .then(Commands.literal("unlock")
                                .then(Commands.argument("dim", StringArgumentType.greedyString())
                                        .suggests(CommandHandler::suggestDimensions)
                                        .executes(ctx -> DimensionExecutor.dimLock(ctx, false))))
                        .then(Commands.literal("setcondition")
                                .then(Commands.argument("data", StringArgumentType.greedyString())
                                        .suggests(CommandHandler::suggestDimensions)
                                        .executes(DimensionExecutor::dimSetCondition)))
                        .then(Commands.literal("removecondition")
                                .then(Commands.argument("dim", StringArgumentType.greedyString())
                                        .suggests(CommandHandler::suggestDimensions)
                                        .executes(DimensionExecutor::dimRemoveCondition))))
                .then(Commands.literal("tab")
                        .then(Commands.literal("add")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(TabExecutor::tabAdd)))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .suggests(CommandHandler::suggestTabNames)
                                        .executes(TabExecutor::tabDelete)))
                        .then(Commands.literal("order")
                                .then(Commands.argument("order", StringArgumentType.greedyString())
                                        .executes(TabExecutor::tabOrder))))
                .then(Commands.literal("vanilla")
                        .then(Commands.literal("enable")
                                .then(Commands.argument("id", StringArgumentType.greedyString())
                                        .suggests(CommandHandler::suggestVanillaIds)
                                        .executes(VanillaExecutor::vanillaEnable)))
                        .then(Commands.literal("disable")
                                .then(Commands.argument("id", StringArgumentType.greedyString())
                                        .suggests(CommandHandler::suggestVanillaIds)
                                        .executes(VanillaExecutor::vanillaDisable)))
                        .then(Commands.literal("enableall")
                                .executes(VanillaExecutor::vanillaEnableAll))
                        .then(Commands.literal("disableall")
                                .executes(VanillaExecutor::vanillaDisableAll))
                        .then(Commands.literal("setpos")
                                .then(Commands.argument("data", StringArgumentType.greedyString())
                                        .suggests(CommandHandler::suggestVanillaIds)
                                        .executes(VanillaExecutor::vanillaSetPos)))
                        .then(Commands.literal("settab")
                                .then(Commands.argument("data", StringArgumentType.greedyString())
                                        .suggests(CommandHandler::suggestVanillaIds)
                                        .executes(VanillaExecutor::vanillaSetTab)))
                        .then(Commands.literal("cleartab")
                                .then(Commands.argument("id", StringArgumentType.greedyString())
                                        .suggests(CommandHandler::suggestVanillaIds)
                                        .executes(VanillaExecutor::vanillaClearTab)))
                        .then(Commands.literal("save")
                                .then(Commands.argument("data", StringArgumentType.greedyString())
                                        .suggests(CommandHandler::suggestVanillaIds)
                                        .executes(VanillaExecutor::vanillaSave))))
        );
    }

    private static int reloadCommand(CommandContext<CommandSourceStack> ctx) {
        ServerDataStore.getInstance().forceReload();
        AdvancementRegistry.syncAllRuntime(ctx.getSource().getServer());
        SyncManager.syncAll(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() ->
                Component.translatable(LangKeys.CMD_RELOAD_DONE), false);
        return 1;
    }
}