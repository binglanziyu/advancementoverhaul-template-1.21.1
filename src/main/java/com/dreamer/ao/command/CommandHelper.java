package com.dreamer.ao.command;

import com.dreamer.ao.Config;
import com.dreamer.ao.LangKeys;
import com.dreamer.ao.compat.AdvancementRegistry;
import com.dreamer.ao.data.ServerDataStore;
import com.dreamer.ao.network.SyncManager;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * 命令系统的通用辅助方法。
 * <p>
 * 提供权限检查、参数解析、数据同步等所有命令执行器共享的功能。
 */
public final class CommandHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandHelper.class);

    private CommandHelper() {}

    /**
     * 创建可翻译的 Component。
     * 委托给 Minecraft 内置的 {@code Component.translatable(key, args)}，
     * 原生支持 {@code %s}/{@code %d} 占位符。
     */
    public static Component translatable(String key, Object... args) {
        return Component.translatable(key, args);
    }

    /**
     * 从 "id value" 格式的字符串中提取第一个 token（ID 部分）。
     * 返回空字符串当 data 为 null。
     */
    public static String splitId(String data) {
        if (data == null || data.isEmpty()) return "";
        int idx = data.indexOf(' ');
        return idx < 0 ? data : data.substring(0, idx);
    }

    /**
     * 从 "id value" 格式的字符串中提取第一个空格之后的剩余部分（值部分）。
     * 返回空字符串当 data 为 null 或没有空格时。
     */
    public static String splitRest(String data) {
        if (data == null || data.isEmpty()) return "";
        int idx = data.indexOf(' ');
        return idx < 0 ? "" : data.substring(idx + 1);
    }

    /**
     * 检查命令执行者是否有编辑权限。
     * 权限不足时发送失败消息。
     */
    public static boolean checkPerm(CommandContext<CommandSourceStack> ctx) {
        if (!ctx.getSource().hasPermission(Config.EDIT_PERMISSION_LEVEL.get())) {
            ctx.getSource().sendFailure(Component.translatable(LangKeys.CMD_PERM_DENIED));
            return false;
        }
        return true;
    }

    /** Greedy string 输入最大长度限制，防止滥用。 */
    private static final int MAX_GREEDY_INPUT_LENGTH = 1024;

    /**
     * 校验 greedy string 参数输入是否合法（非空、长度限制）。
     *
     * @return true 表示输入合法，false 表示已被拒绝
     */
    public static boolean validateGreedyInput(CommandContext<CommandSourceStack> ctx, String fieldName, String value) {
        if (value == null || value.isEmpty()) {
            ctx.getSource().sendFailure(
                    Component.translatable(LangKeys.CMD_INPUT_EMPTY, fieldName));
            return false;
        }
        if (value.length() > MAX_GREEDY_INPUT_LENGTH) {
            ctx.getSource().sendFailure(
                    Component.translatable(LangKeys.CMD_INPUT_TOO_LONG, fieldName, MAX_GREEDY_INPUT_LENGTH));
            return false;
        }
        return true;
    }

    /**
     * 向所有在线玩家同步数据（含 FTB Quests 属性同步）。
     * <p>
     * 等同于 {@code syncAll(ctx, true)}。
     */
    public static void syncAll(CommandContext<CommandSourceStack> ctx) {
        syncAll(ctx, true);
    }

    /**
     * 向所有在线玩家同步数据，可选择是否通知 FTB Quests。
     * <p>
     * 依次调用：runtime 同步 → 网络全量同步 → FTB Quests 通知（可选）。
     * <p>
     * 注意：对于单个进度的增删改，{@link ServerDataStore#addAdvancement}
     * 和 {@link ServerDataStore#removeAdvancement} 已经通过回调自动触发
     * 增量 runtime 更新，此方法仅在需要全量同步时（如 reload/import）使用。
     *
     * @param ctx       命令上下文
     * @param notifyFtb 是否触发 FTB Quests KnownServerRegistries 同步。
     *                  位置/分类变更应传 {@code false}
     */
    public static void syncAll(CommandContext<CommandSourceStack> ctx, boolean notifyFtb) {
        MinecraftServer server = ctx.getSource().getServer();
        AdvancementRegistry.syncAllRuntime(server, notifyFtb);
        SyncManager.syncAll(server);
    }

    /**
     * 向单个玩家同步数据。
     * 保存玩家数据后发送全量同步包。
     */
    public static void syncTargetPlayer(ServerPlayer target) {
        ServerDataStore.getInstance().savePlayerData();
        SyncManager.syncPlayer(target);
    }

    /**
     * 收集服务端已知的所有原版（非自定义）进度 ID。
     * 用于命令 Tab 补全和前置条件验证。
     */
    public static Set<String> collectAllVanillaIds(CommandContext<CommandSourceStack> ctx) {
        Set<String> ids = new HashSet<>();
        try {
            for (var holder : ctx.getSource().getServer().getAdvancements().getAllAdvancements()) {
                String id = holder.id().toString();
                if (!AdvancementRegistry.isCustomAdvancement(holder.id())) {
                    ids.add(id);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Advancement manager not ready when collecting vanilla IDs: {}", e.getMessage());
        }
        return ids;
    }
}
