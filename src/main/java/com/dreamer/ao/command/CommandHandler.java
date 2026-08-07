package com.dreamer.ao.command;

import com.dreamer.ao.Config;
import com.dreamer.ao.LangKeys;
import com.dreamer.ao.compat.AdvancementRegistry;
import com.dreamer.ao.data.ServerDataStore;
import com.dreamer.ao.network.SyncManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * /adv 命令注册与路由。
 *
 * <h2>命令树结构</h2>
 * <pre>
 * /adv
 * ├── complete     <id> [player]     — 强制完成进度（跳过前置）
 * ├── reset        <player> <id|all> — 重置玩家进度
 * ├── give         <id> [player]     — 授予进度
 * ├── revoke       <id> [player]     — 撤销进度
 * ├── check        <id>              — 查看进度状态
 * ├── delete       <id>              — 删除自定义进度
 * ├── batchdelete  <ids,...>         — 批量删除
 * ├── setname      <id> <name>       — 设置名称
 * ├── setdescription <id> <desc>     — 设置描述
 * ├── seticon      <id> <icon>       — 设置图标
 * ├── togglehidden <id>              — 切换隐藏状态
 * ├── setprereq    <id> <ids,...>    — 设置前置条件
 * ├── createjson   <json>            — JSON 创建进度
 * ├── updatejson   <json>            — JSON 更新进度
 * ├── import                        — 从文件导入
 * ├── export                        — 导出到文件
 * ├── autolayout                    — 自动布局
 * ├── reload                        — 重载数据
 * ├── dimension     lock/unlock/setcondition/removecondition
 * ├── tab           add/delete/order
 * └── vanilla       enable/disable/enableall/disableall/setpos/settab/cleartab/save
 * </pre>
 */
public class CommandHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandHandler.class);

    // ═══════════════ Tab 补全提供器 ═══════════════

    /** 补全自定义进度 ID */
    private static CompletableFuture<Suggestions> suggestAdvIds(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String id : ServerDataStore.getInstance().getAdvancements().keySet()) {
            builder.suggest(id);
        }
        return builder.buildFuture();
    }

    /** 补全自定义标签页名称 */
    private static CompletableFuture<Suggestions> suggestTabNames(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        for (String tab : ServerDataStore.getInstance().getCustomTabs()) {
            builder.suggest(tab);
        }
        return builder.buildFuture();
    }

    /** 补全维度 ID（所有已注册维度，包括原版和 mod 维度） */
    private static CompletableFuture<Suggestions> suggestDimensions(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        Set<String> allDims = new LinkedHashSet<>();
        try {
            var access = ctx.getSource().getServer().registryAccess();
            access.registryOrThrow(Registries.DIMENSION).keySet().forEach(rl -> allDims.add(rl.toString()));
        } catch (Exception e) {
            LOGGER.debug("Failed to enumerate dimension registry for suggestions: {}", e.getMessage());
        }
        // 兜底：确保三个原版维度始终出现
        allDims.add("minecraft:overworld");
        allDims.add("minecraft:the_nether");
        allDims.add("minecraft:the_end");
        for (String dim : allDims) {
            builder.suggest(dim);
        }
        return builder.buildFuture();
    }

    /** 补全原版（非自定义）进度 ID */
    private static CompletableFuture<Suggestions> suggestVanillaIds(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        try {
            for (var holder : ctx.getSource().getServer().getAdvancements().getAllAdvancements()) {
                if (!AdvancementRegistry.isCustomAdvancement(holder.id())) {
                    builder.suggest(holder.id().toString());
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to suggest vanilla advancement IDs: {}", e.getMessage());
        }
        return builder.buildFuture();
    }

    // ═══════════════ 注册入口 ═══════════════

    /** NeoForge 事件回调入口 */
    public static void registerCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    /**
     * 注册完整的 /adv 命令树。
     * <p>
     * 所有子命令委托给对应的 Executor 类：
     * <ul>
     *   <li>{@link AdvPlayerExecutor} — 玩家操作（complete/reset/give/revoke/check）</li>
     *   <li>{@link AdvCrudExecutor} — CRUD 操作（create/update/delete/set*）</li>
     *   <li>{@link DimensionExecutor} — 维度锁定</li>
     *   <li>{@link TabExecutor} — 标签页管理</li>
     *   <li>{@link VanillaExecutor} — 原版进度管理</li>
     *   <li>{@link ImportExportExecutor} — 导入导出</li>
     * </ul>
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("adv")
                .requires(source -> source.hasPermission(Config.EDIT_PERMISSION_LEVEL.get()))
                // ── 玩家操作 ──
                .then(Commands.literal("complete")
                        .then(Commands.argument("id", StringArgumentType.greedyString())
                                .suggests(CommandHandler::suggestAdvIds)
                                .executes(AdvPlayerExecutor::completeAdvancement)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(AdvPlayerExecutor::completeAdvancement))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("target", StringArgumentType.greedyString())
                                        .suggests((ctx, b) -> {
                                            b.suggest("all");
                                            return suggestAdvIds(ctx, b);
                                        })
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
                .then(Commands.literal("check")
                        .then(Commands.argument("id", StringArgumentType.greedyString())
                                .suggests(CommandHandler::suggestAdvIds)
                                .executes(AdvPlayerExecutor::checkAdvancement)))

                // ── CRUD 操作 ──
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
                .then(Commands.literal("createjson")
                        .then(Commands.argument("json", StringArgumentType.greedyString())
                                .executes(AdvCrudExecutor::createFromJson)))
                .then(Commands.literal("updatejson")
                        .then(Commands.argument("json", StringArgumentType.greedyString())
                                .executes(AdvCrudExecutor::updateFromJson)))

                // ── 工具 ──
                .then(Commands.literal("import")
                        .executes(ImportExportExecutor::importAdvancements))
                .then(Commands.literal("export")
                        .executes(ImportExportExecutor::exportAdvancements))
                .then(Commands.literal("autolayout")
                        .executes(ImportExportExecutor::autoLayout))
                .then(Commands.literal("reload")
                        .executes(CommandHandler::reloadCommand))

                // ── 维度锁定 ──
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

                // ── 标签页管理 ──
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

                // ── 原版进度管理 ──
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

    /** /adv reload 执行器 */
    private static int reloadCommand(CommandContext<CommandSourceStack> ctx) {
        ServerDataStore.getInstance().forceReload();
        AdvancementRegistry.syncAllRuntime(ctx.getSource().getServer());
        SyncManager.syncAll(ctx.getSource().getServer());

        // 重载叙事配置（独白文本、故地回声、统计模板、成就描述）
        com.dreamer.ao.data.NarrativeConfigLoader.getInstance()
                .reload(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get());
        // 重置客户端独白冷却和回声状态
        com.dreamer.ao.narrative.event.EchoEventHandler.resetAll();

        ctx.getSource().sendSuccess(
                () -> Component.translatable(LangKeys.CMD_RELOAD_DONE), false);
        return 1;
    }
}
