package com.dreamer.ao.network;

import com.dreamer.ao.Config;
import com.dreamer.ao.LangKeys;
import com.dreamer.ao.ModInfo;
import com.dreamer.ao.compat.AdvancementRegistry;
import com.dreamer.ao.data.ServerDataStore;
import com.dreamer.ao.data.PlayerStats;
import com.dreamer.ao.data.PlayerStatsStore;
import com.dreamer.ao.data.DataStore;
import com.dreamer.ao.network.payload.C2SCommandPayload;
import com.dreamer.ao.network.payload.FtbQuestCompletedPayload;
import com.dreamer.ao.network.payload.ImportFilePayload;
import com.dreamer.ao.network.payload.MonologuePayload;
import com.dreamer.ao.network.payload.ProgressSyncPayload;
import com.dreamer.ao.network.payload.StatsRequestPayload;
import com.dreamer.ao.network.payload.StatsSyncPayload;
import com.dreamer.ao.network.payload.SyncChunkPayload;
import com.dreamer.ao.network.payload.SyncPayload;
import com.dreamer.ao.network.payload.TimelineRequestPayload;
import com.dreamer.ao.network.payload.TimelineSyncPayload;
import com.dreamer.ao.network.handler.TimelineNetworkHandler;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网络层：自定义 Payload 注册 + 服务端处理逻辑。
 *
 * <h2>网络通道</h2>
 * <table>
 *   <tr><th>Payload</th><th>方向</th><th>用途</th></tr>
 *   <tr><td>{@link SyncPayload}</td><td>Server → Client</td><td>全量数据同步（由 {@link NetworkHandlerClient} 处理）</td></tr>
 *   <tr><td>{@link SyncChunkPayload}</td><td>Server → Client</td><td>分块全量同步（由 {@link NetworkHandlerClient} 处理）</td></tr>
 *   <tr><td>{@link ProgressSyncPayload}</td><td>Server → Client</td><td>增量进度更新（由 {@link NetworkHandlerClient} 处理）</td></tr>
 *   <tr><td>{@link MonologuePayload}</td><td>Server → Client</td><td>世界独白触发</td></tr>
 *   <tr><td>{@link C2SCommandPayload}</td><td>Client → Server</td><td>GUI 编辑命令</td></tr>
 * </table>
 *
 * <h2>C2S 安全措施</h2>
 * <ul>
 *   <li>命令白名单（精确匹配 + 前缀匹配）</li>
 *   <li>JSON 内容校验（大小、字段长度限制）</li>
 *   <li>UTF-8 字节长度限制</li>
 *   <li>频率限制（100ms 冷却）</li>
 *   <li>权限等级检查（可配置）</li>
 * </ul>
 */
public class NetworkHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkHandler.class);

    // ═══════════════ 命令白名单 ═══════════════

    private static final Set<String> ALLOWED_EXACT = Set.of(
            "adv import", "adv export", "adv autolayout",
            "adv vanilla enableall", "adv vanilla disableall",
            "adv reload"
    );

    private static final Set<String> ALLOWED_PREFIXES = Set.of(
            "adv complete ", "adv reset ", "adv give ", "adv revoke ",
            "adv delete ", "adv batchdelete ", "adv setname ", "adv setdescription ",
            "adv togglehidden ", "adv setprereq ", "adv check ",
            "adv createjson ", "adv updatejson ",
            "adv dimension lock ", "adv dimension unlock ",
            "adv dimension setcondition ", "adv dimension removecondition ",
            "adv tab add ", "adv tab delete ", "adv tab order ",
            "adv vanilla enable ", "adv vanilla disable ",
            "adv vanilla setpos ", "adv vanilla settab ", "adv vanilla cleartab ",
            "adv vanilla save ", "adv seticon "
    );

    // ═══════════════ 频率限制 ═══════════════

    private static final ConcurrentHashMap<UUID, Long> COMMAND_COOLDOWN = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 100;
    private static final int COOLDOWN_MAX_SIZE = 1024;
    private static final long COOLDOWN_EXPIRE_MS = 60_000;

    private static final ConcurrentHashMap<UUID, Long> IMPORT_COOLDOWN = new ConcurrentHashMap<>();
    private static final long IMPORT_COOLDOWN_MS = 2000;

    private static final int CMD_MAX_UTF8_BYTES = 16384;
    private static final int IMPORT_MAX_CHARS = 1_048_576;

    /** JSON 嵌套深度上限，防止深度嵌套导致栈溢出 / DoS */
    private static final int JSON_MAX_DEPTH = 32;

    // ═══════════════ 注册 ═══════════════

    /**
     * 注册自定义 Payload 通道。
     * <p>
     * S2C 的 TYPE/CODEC 必须在双方都注册，才能完成 NeoForge 网络通道协商。
     * 处理方法通过中间方法代理到 {@link NetworkHandlerClient}（仅在客户端被调用时才解析）。
     * 这样服务端不会触发 {@code Minecraft} 等客户端类的加载。
     */
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ModInfo.NETWORK_PROTOCOL);

        // S2C：TYPE/CODEC 双方注册（通道协商需要），handler 仅在客户端被调用
        registrar.playToClient(SyncPayload.TYPE, SyncPayload.CODEC,
                NetworkHandler::handleSyncDelegate);
        registrar.playToClient(SyncChunkPayload.TYPE, SyncChunkPayload.CODEC,
                NetworkHandler::handleSyncChunkDelegate);
        registrar.playToClient(ProgressSyncPayload.TYPE, ProgressSyncPayload.CODEC,
                NetworkHandler::handleProgressDelegate);
        registrar.playToClient(FtbQuestCompletedPayload.TYPE, FtbQuestCompletedPayload.CODEC,
                NetworkHandler::handleFtbQuestCompletedDelegate);
        registrar.playToClient(MonologuePayload.TYPE, MonologuePayload.STREAM_CODEC,
                MonologuePayload::handle);

        // C2S：服务端侧处理器
        registrar.playToServer(C2SCommandPayload.TYPE, C2SCommandPayload.CODEC,
                NetworkHandler::handleC2SCommand);
        registrar.playToServer(ImportFilePayload.TYPE, ImportFilePayload.CODEC,
                NetworkHandler::handleImportFile);
        registrar.playToServer(StatsRequestPayload.TYPE, StatsRequestPayload.CODEC,
                NetworkHandler::handleStatsRequest);

        // S2C：PlayerStats 增量同步
        registrar.playToClient(StatsSyncPayload.TYPE, StatsSyncPayload.CODEC,
                NetworkHandler::handleStatsSyncDelegate);

        // Timeline
        registrar.playToClient(TimelineSyncPayload.TYPE, TimelineSyncPayload.CODEC,
                NetworkHandler::handleTimelineSyncDelegate);
        registrar.playToServer(TimelineRequestPayload.TYPE, TimelineRequestPayload.CODEC,
                TimelineNetworkHandler::handleTimelineRequest);
    }

    // ═══════════════ S2C 中间处理方法（仅在客户端被调用） ═══════════════

    private static void handleSyncDelegate(SyncPayload payload, IPayloadContext context) {
        NetworkHandlerClient.handleSync(payload, context);
    }

    private static void handleSyncChunkDelegate(SyncChunkPayload payload, IPayloadContext context) {
        NetworkHandlerClient.handleSyncChunk(payload, context);
    }

    private static void handleProgressDelegate(ProgressSyncPayload payload, IPayloadContext context) {
        NetworkHandlerClient.handleProgress(payload, context);
    }

    private static void handleFtbQuestCompletedDelegate(FtbQuestCompletedPayload payload, IPayloadContext context) {
        NetworkHandlerClient.handleFtbQuestCompleted(payload, context);
    }

    private static void handleStatsSyncDelegate(StatsSyncPayload payload, IPayloadContext context) {
        NetworkHandlerClient.handleStatsSync(payload, context);
    }

    private static void handleTimelineSyncDelegate(TimelineSyncPayload payload, IPayloadContext context) {
        NetworkHandlerClient.handleTimelineSync(payload, context);
    }

    // ═══════════════ C2S 命令处理 ═══════════════

    private static void handleC2SCommand(C2SCommandPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            String cmd = payload.command();
            if (cmd == null) return;
            cmd = cmd.replaceAll("\\s+", " ").trim();
            if (!cmd.startsWith("adv ")) return;

            boolean allowed = ALLOWED_EXACT.contains(cmd)
                    || ALLOWED_PREFIXES.stream().anyMatch(cmd::startsWith);
            if (!allowed) {
                LOGGER.warn("Blocked unauthorized C2S command from {}: {}",
                        player.getName().getString(), cmd);
                return;
            }

            if (!validateUpdateJson(cmd)) {
                LOGGER.warn("Blocked invalid updatejson from {}", player.getName().getString());
                return;
            }

            int utf8Len = cmd.getBytes(StandardCharsets.UTF_8).length;
            if (utf8Len > CMD_MAX_UTF8_BYTES) {
                LOGGER.warn("Blocked oversized C2S command ({} UTF-8 bytes) from {}",
                        utf8Len, player.getName().getString());
                return;
            }

            long now = System.currentTimeMillis();
            Long last = COMMAND_COOLDOWN.get(player.getUUID());
            if (last != null && now - last < COOLDOWN_MS) {
                LOGGER.debug("Rate limiting C2S command from {}", player.getName().getString());
                return;
            }

            if (COMMAND_COOLDOWN.size() > COOLDOWN_MAX_SIZE) {
                COMMAND_COOLDOWN.entrySet().removeIf(e -> now - e.getValue() > COOLDOWN_EXPIRE_MS);
            }
            COMMAND_COOLDOWN.put(player.getUUID(), now);

            try {
                int requiredPerm = Config.EDIT_PERMISSION_LEVEL.get();
                net.minecraft.commands.CommandSourceStack src = player.createCommandSourceStack();
                if (!src.hasPermission(requiredPerm)) {
                    LOGGER.warn("Player {} lacks required permission ({}) for: {}",
                            player.getName().getString(), requiredPerm, cmd);
                    return;
                }
                player.server.getCommands().getDispatcher().execute(cmd, src);
            } catch (Exception e) {
                LOGGER.warn("C2S command failed: {} - {}", cmd, e.getMessage());
            }
        });
    }

    private static boolean validateUpdateJson(String cmd) {
        boolean isUpdate = cmd.startsWith("adv updatejson ");
        boolean isCreate = cmd.startsWith("adv createjson ");
        if (!isUpdate && !isCreate) return true;

        String raw = cmd.substring(15);
        if (raw.length() > 12288) {
            LOGGER.warn("updatejson payload too large: {} chars", raw.length());
            return false;
        }

        String json;
        try {
            json = new String(java.util.Base64.getDecoder().decode(raw), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException notBase64) {
            json = raw;
        }

        if (json.length() > 8192) {
            LOGGER.warn("updatejson decoded JSON too large: {} chars", json.length());
            return false;
        }

        try {
            // 先用流式检查深度，防止深度嵌套导致栈溢出 / DoS
            if (!checkJsonDepth(json, JSON_MAX_DEPTH)) {
                LOGGER.warn("updatejson JSON nesting too deep (max {})", JSON_MAX_DEPTH);
                return false;
            }
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (!obj.has("id") && !obj.has("name")) {
                LOGGER.warn("updatejson missing both id and name");
                return false;
            }
            if (obj.has("name")) {
                String name = obj.get("name").getAsString();
                if (name.length() > 256 || containsControlChars(name)) return false;
            }
            if (obj.has("description")) {
                String desc = obj.get("description").getAsString();
                if (desc.length() > 1024 || containsControlChars(desc)) return false;
            }
            if (obj.has("icon")) {
                String icon = obj.get("icon").getAsString();
                if (icon.length() > 128 || containsControlChars(icon)) return false;
            }
            if (obj.has("tab")) {
                String tab = obj.get("tab").getAsString();
                if (tab.length() > 64 || containsControlChars(tab)) return false;
            }
            if (obj.has("id")) {
                String id = obj.get("id").getAsString();
                if (containsControlChars(id)) return false;
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn("updatejson invalid JSON: {}", e.getMessage());
            return false;
        }
    }

    private static boolean containsControlChars(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c < 0x20 && c != 0x09 && c != 0x0A && c != 0x0D) || c == 0x7F) return true;
        }
        return false;
    }

    /**
     * 流式检查 JSON 字符串的嵌套深度，防止 {@link JsonParser#parseString} 栈溢出 / DoS。
     * 字符级遍历，不分配中间对象。
     * @param json JSON 字符串
     * @param maxDepth 允许的最大嵌套深度
     * @return true 表示深度在限制内
     */
    private static boolean checkJsonDepth(String json, int maxDepth) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;
            if (c == '{' || c == '[') {
                depth++;
                if (depth > maxDepth) return false;
            } else if (c == '}' || c == ']') {
                depth--;
            }
        }
        return true;
    }

    // ═══════════════ PlayerStats 请求处理（客户端请求最新数据） ═══════════════

    /**
     * 客户端打开 NarrativeStatsScreen 时请求最新的 PlayerStats。
     * 服务端直接回发当前玩家的 PlayerStats JSON。
     * <p>
     * 注意：不能使用 {@code context.reply()}，因为它必须在原始数据包处理上下文中调用；
     * 在 {@code enqueueWork()} 内部调用时会静默失败（上下文已释放）。
     * 改用 {@code PacketDistributor.sendToPlayer()} 显式发送。
     */
    private static void handleStatsRequest(StatsRequestPayload payload, IPayloadContext context) {
        // 必须在 enqueueWork 之外捕获 player 引用（enqueueWork 内部 context 可能已释放）
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            UUID uuid = player.getUUID();
            PlayerStats stats = PlayerStatsStore.getInstance().getOrCreate(uuid);
            String json = DataStore.GSON.toJson(stats);
            LOGGER.debug("Stats request from {} — replying with {} bytes",
                    player.getName().getString(), json.length());
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new StatsSyncPayload(json));
        });
    }

    /** 服务端主动推送：向指定玩家发送最新 PlayerStats */
    public static void pushStatsSync(ServerPlayer player) {
        PlayerStats stats = PlayerStatsStore.getInstance().getOrCreate(player.getUUID());
        String json = DataStore.GSON.toJson(stats);
        int len = json.length();
        LOGGER.debug("Pushing stats sync to {} ({} chars)",
                player.getName().getString(), len);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new StatsSyncPayload(json));
    }

    // ═══════════════ 文件导入处理 ═══════════════

    private static void handleImportFile(ImportFilePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            String content = payload.content();
            if (content == null || content.trim().isEmpty()) {
                player.sendSystemMessage(Component.translatable(LangKeys.CMD_JSON_EMPTY));
                return;
            }

            int requiredPerm = Config.EDIT_PERMISSION_LEVEL.get();
            if (!player.hasPermissions(requiredPerm)) {
                player.sendSystemMessage(Component.translatable(LangKeys.CMD_PERM_DENIED));
                return;
            }

            long now = System.currentTimeMillis();
            Long lastImport = IMPORT_COOLDOWN.get(player.getUUID());
            if (lastImport != null && now - lastImport < IMPORT_COOLDOWN_MS) {
                LOGGER.debug("Rate limiting import from {}", player.getName().getString());
                player.sendSystemMessage(Component.translatable(LangKeys.CMD_RATE_LIMITED));
                return;
            }
            if (IMPORT_COOLDOWN.size() > COOLDOWN_MAX_SIZE) {
                IMPORT_COOLDOWN.entrySet().removeIf(e -> now - e.getValue() > COOLDOWN_EXPIRE_MS);
            }
            IMPORT_COOLDOWN.put(player.getUUID(), now);

            if (content.length() > IMPORT_MAX_CHARS) {
                player.sendSystemMessage(Component.translatable(LangKeys.CMD_IMPORT_TOO_LARGE));
                return;
            }

            try {
                String raw = content.trim();
                JsonObject data = JsonParser.parseString(raw).getAsJsonObject();
                // 导入前备份当前数据，失败时回滚
                JsonObject backup = ServerDataStore.getInstance().exportAll();
                ServerDataStore.getInstance().importAll(data);
                try {
                    AdvancementRegistry.syncAllRuntime(player.server, true);
                    SyncManager.syncAll(player.server);
                } catch (Exception syncEx) {
                    // syncAll/syncAllRuntime 失败时回滚到导入前状态
                    LOGGER.error("Sync after import failed, rolling back: {}", syncEx.getMessage());
                    ServerDataStore.getInstance().restoreFromBackup(backup);
                    throw syncEx; // rethrow to outer catch for user notification
                }
                player.sendSystemMessage(Component.translatable(LangKeys.CMD_IMPORT_DONE));
                LOGGER.info("Import successful by player {}", player.getName().getString());
            } catch (Exception e) {
                LOGGER.error("Import failed for player {}: {}", player.getName().getString(), e.getMessage());
                player.sendSystemMessage(Component.translatable(LangKeys.CMD_IMPORT_FAILED, e.getMessage()));
            }
        });
    }
}
