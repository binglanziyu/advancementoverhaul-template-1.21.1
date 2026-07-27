package com.example.advancementoverhaul.network;

import com.example.advancementoverhaul.Config;
import com.example.advancementoverhaul.LangKeys;
import com.example.advancementoverhaul.ModInfo;
import com.example.advancementoverhaul.client.gui.AdvancementScreen;
import com.example.advancementoverhaul.compat.AdvancementRegistry;
import com.example.advancementoverhaul.data.ClientDataStore;
import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.ServerDataStore;
import com.example.advancementoverhaul.data.model.CustomAdvancement;
import com.example.advancementoverhaul.data.model.VanillaAdvMeta;
import com.example.advancementoverhaul.data.DimensionLock;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网络层：自定义 Payload 注册 + 服务端/客户端处理逻辑。
 *
 * <h2>网络通道</h2>
 * <table>
 *   <tr><th>Payload</th><th>方向</th><th>用途</th></tr>
 *   <tr><td>{@link SyncPayload}</td><td>Server → Client</td><td>全量数据同步（JSON + GZIP，小数据量）</td></tr>
 *   <tr><td>{@link SyncChunkPayload}</td><td>Server → Client</td><td>分块全量同步（大数据量拆分传输）</td></tr>
 *   <tr><td>{@link ProgressSyncPayload}</td><td>Server → Client</td><td>增量进度/完成/pending 更新</td></tr>
 *   <tr><td>{@link C2SCommandPayload}</td><td>Client → Server</td><td>GUI 编辑命令</td></tr>
 * </table>
 *
 * <h2>C2S 安全措施</h2>
 * <ul>
 *   <li>命令白名单（精确匹配 + 前缀匹配）</li>
 *   <li>JSON 内容校验（大小、字段长度限制）</li>
 *   <li>UTF-8 字节长度限制（与 CODEC 层一致）</li>
 *   <li>频率限制（100ms 冷却）</li>
 *   <li>权限等级检查（可配置）</li>
 * </ul>
 */
public class NetworkHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul");

    // ═══════════════ 命令白名单 ═══════════════

    /** 同步字段总数（handleSync 中解析的字段数，新增字段时需同步更新） */
    private static final int TOTAL_FIELDS = 11;

    /** 精确匹配的命令（无参数） */
    private static final Set<String> ALLOWED_EXACT = Set.of(
            "adv import", "adv export", "adv autolayout",
            "adv vanilla enableall", "adv vanilla disableall",
            "adv reload"
    );

    /** 前缀匹配的命令（带参数） */
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

    // ═══════════════ 注册 ═══════════════

    /**
     * 注册自定义 Payload 通道。
     * <p>
     * 必须在 {@code FMLCommonSetupEvent} 之前通过 ModBus 注册。
     *
     * @param event 注册事件
     */
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ModInfo.NETWORK_PROTOCOL);
        registrar.playToClient(SyncPayload.TYPE, SyncPayload.CODEC, NetworkHandler::handleSync);
        registrar.playToClient(SyncChunkPayload.TYPE, SyncChunkPayload.CODEC,
                NetworkHandler::handleSyncChunk);
        registrar.playToClient(ProgressSyncPayload.TYPE, ProgressSyncPayload.CODEC,
                NetworkHandler::handleProgress);
        registrar.playToClient(FtbQuestCompletedPayload.TYPE, FtbQuestCompletedPayload.CODEC,
                NetworkHandler::handleFtbQuestCompleted);
        registrar.playToServer(C2SCommandPayload.TYPE, C2SCommandPayload.CODEC,
                NetworkHandler::handleC2SCommand);
        registrar.playToServer(ImportFilePayload.TYPE, ImportFilePayload.CODEC,
                NetworkHandler::handleImportFile);
    }

    // ═══════════════ C2S 命令处理 ═══════════════

    /** 频率限制冷却表（UUID → 上次命令时间） */
    private static final ConcurrentHashMap<UUID, Long> COMMAND_COOLDOWN = new ConcurrentHashMap<>();

    /** 频率限制最短间隔（毫秒） */
    private static final long COOLDOWN_MS = 100;

    /** 冷却表最大容量，超过时清理过期条目 */
    private static final int COOLDOWN_MAX_SIZE = 1024;

    /** 冷却条目过期时间 */
    private static final long COOLDOWN_EXPIRE_MS = 60_000;

    /** 文件导入频率限制冷却表 */
    private static final ConcurrentHashMap<UUID, Long> IMPORT_COOLDOWN = new ConcurrentHashMap<>();

    /** 文件导入最短间隔（毫秒，比命令间隔更长以防护 DoS） */
    private static final long IMPORT_COOLDOWN_MS = 2000;

    /** 命令 UTF-8 字节数上限（与 CODEC 层一致） */
    private static final int CMD_MAX_UTF8_BYTES = 16384;

    /** 导入内容最大字符数 */
    private static final int IMPORT_MAX_CHARS = 1_048_576;

    // ═══════════════ 分块同步重组状态 ═══════════════

    /** 分块同步重组缓冲区：transferId → ChunkAssembly */
    private static final ConcurrentHashMap<Long, ChunkAssembly> CHUNK_ASSEMBLIES = new ConcurrentHashMap<>();

    /** 上次清理过期传输的时间 */
    private static long lastAssemblyCleanup = 0;

    /** 清理间隔（毫秒） */
    private static final long ASSEMBLY_CLEANUP_INTERVAL_MS = 10_000;

    /** 分块传输重组中 */
    private static class ChunkAssembly {
        final long transferId;
        final int totalChunks;
        final byte[][] chunks;
        final long createdAt;
        int receivedChunks;

        ChunkAssembly(long transferId, int totalChunks) {
            this.transferId = transferId;
            this.totalChunks = totalChunks;
            this.chunks = new byte[totalChunks][];
            this.createdAt = System.currentTimeMillis();
            this.receivedChunks = 0;
        }

        boolean addChunk(int index, byte[] data) {
            if (index < 0 || index >= totalChunks || chunks[index] != null) return false;
            chunks[index] = data;
            receivedChunks++;
            return true;
        }

        boolean isComplete() { return receivedChunks == totalChunks; }

        String assemble() {
            int totalLen = 0;
            for (byte[] c : chunks) totalLen += c.length;
            byte[] full = new byte[totalLen];
            int pos = 0;
            for (byte[] c : chunks) {
                System.arraycopy(c, 0, full, pos, c.length);
                pos += c.length;
            }
            return new String(full, StandardCharsets.UTF_8);
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > SyncChunkPayload.ASSEMBLY_TIMEOUT_MS;
        }
    }

    /**
     * 处理客户端发送的命令。
     * <p>
     * 安全检查链：规范化 → 白名单 → JSON 校验 → 字节长度 → 频率限制 → 权限校验 → 执行。
     * 命令在检查前会被规范化（合并连续空格），避免因空格差异绕过白名单。
     */
    private static void handleC2SCommand(C2SCommandPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            String cmd = payload.command();
            if (cmd == null) return;
            // 规范化：合并所有连续空白字符为单个空格，并 trim
            cmd = cmd.replaceAll("\\s+", " ").trim();
            if (!cmd.startsWith("adv ")) return;

            // 1. 白名单校验（在规范化后的命令上执行）
            boolean allowed = ALLOWED_EXACT.contains(cmd)
                    || ALLOWED_PREFIXES.stream().anyMatch(cmd::startsWith);
            if (!allowed) {
                LOGGER.warn("Blocked unauthorized C2S command from {}: {}",
                        player.getName().getString(), cmd);
                return;
            }

            // 2. JSON 内容校验
            if (!validateUpdateJson(cmd)) {
                LOGGER.warn("Blocked invalid updatejson from {}",
                        player.getName().getString());
                return;
            }

            // 3. UTF-8 字节长度限制（与 CODEC 层的 ByteBufCodecs.stringUtf8 一致）
            int utf8Len = cmd.getBytes(StandardCharsets.UTF_8).length;
            if (utf8Len > CMD_MAX_UTF8_BYTES) {
                LOGGER.warn("Blocked oversized C2S command ({} UTF-8 bytes) from {}",
                        utf8Len, player.getName().getString());
                return;
            }

            // 4. 频率限制
            long now = System.currentTimeMillis();
            Long last = COMMAND_COOLDOWN.get(player.getUUID());
            if (last != null && now - last < COOLDOWN_MS) {
                LOGGER.debug("Rate limiting C2S command from {}", player.getName().getString());
                return;
            }

            // 防止冷却 Map 无限增长（超过阈值时清理 60 秒前的条目）
            if (COMMAND_COOLDOWN.size() > COOLDOWN_MAX_SIZE) {
                COMMAND_COOLDOWN.entrySet()
                        .removeIf(e -> now - e.getValue() > COOLDOWN_EXPIRE_MS);
            }
            COMMAND_COOLDOWN.put(player.getUUID(), now);

            // 5. 权限校验 + 执行
            try {
                int requiredPerm = Config.EDIT_PERMISSION_LEVEL.get();
                net.minecraft.commands.CommandSourceStack src =
                        player.createCommandSourceStack();
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

    /**
     * 校验 updatejson/createjson 命令的 JSON 载荷。
     * <ul>
     *   <li>Base64 解码（兼容原始 JSON）</li>
     *   <li>大小限制（编码 ≤ 12KB，解码 ≤ 8KB）</li>
     *   <li>字段长度限制（name ≤ 256, description ≤ 1024, icon ≤ 128, tab ≤ 64）</li>
     *   <li>内容过滤：拒绝包含控制字符的字段（防止注入）</li>
     * </ul>
     */
    private static boolean validateUpdateJson(String cmd) {
        boolean isUpdate = cmd.startsWith("adv updatejson ");
        boolean isCreate = cmd.startsWith("adv createjson ");
        if (!isUpdate && !isCreate) return true; // 非 JSON 命令，不校验

        String raw = cmd.substring(15);
        if (raw.length() > 12288) {
            LOGGER.warn("updatejson payload too large: {} chars", raw.length());
            return false;
        }

        // Base64 解码（兼容旧格式）
        String json;
        try {
            json = new String(java.util.Base64.getDecoder().decode(raw), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException notBase64) {
            json = raw; // 不是 Base64 → 视为原始 JSON
        }

        if (json.length() > 8192) {
            LOGGER.warn("updatejson decoded JSON too large: {} chars", json.length());
            return false;
        }

        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (!obj.has("id") && !obj.has("name")) {
                LOGGER.warn("updatejson missing both id and name");
                return false;
            }
            // 校验每个文本字段：长度 + 控制字符
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

    /** 检查字符串是否包含控制字符（0x00-0x08, 0x0B-0x0C, 0x0E-0x1F） */
    private static boolean containsControlChars(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c < 0x20 && c != 0x09 && c != 0x0A && c != 0x0D) || c == 0x7F) {
                return true;
            }
        }
        return false;
    }

    // ═══════════════ 文件导入处理 ═══════════════

    /**
     * 处理客户端通过文件选择器发送的导入数据。
     * <p>
     * 校验权限 → 频率限制 → 大小限制 → 解析 JSON → 导入数据 → 全量同步 → 反馈结果。
     * 仅当客户端通过 GUI 文件对话框选择文件时触发此流程。
     */
    private static void handleImportFile(ImportFilePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            String content = payload.content();
            if (content == null || content.trim().isEmpty()) {
                player.sendSystemMessage(
                        Component.translatable(LangKeys.CMD_JSON_EMPTY));
                return;
            }

            // 权限检查
            int requiredPerm = Config.EDIT_PERMISSION_LEVEL.get();
            if (!player.hasPermissions(requiredPerm)) {
                player.sendSystemMessage(
                        Component.translatable(LangKeys.CMD_PERM_DENIED));
                return;
            }

            // 频率限制（独立于命令冷却，更长的间隔以防 DoS）
            long now = System.currentTimeMillis();
            Long lastImport = IMPORT_COOLDOWN.get(player.getUUID());
            if (lastImport != null && now - lastImport < IMPORT_COOLDOWN_MS) {
                LOGGER.debug("Rate limiting import from {}", player.getName().getString());
                player.sendSystemMessage(
                        Component.translatable(LangKeys.CMD_RATE_LIMITED));
                return;
            }
            // 清理过期导入冷却条目
            if (IMPORT_COOLDOWN.size() > COOLDOWN_MAX_SIZE) {
                IMPORT_COOLDOWN.entrySet()
                        .removeIf(e -> now - e.getValue() > COOLDOWN_EXPIRE_MS);
            }
            IMPORT_COOLDOWN.put(player.getUUID(), now);

            // 内容大小限制
            if (content.length() > IMPORT_MAX_CHARS) {
                player.sendSystemMessage(
                        Component.translatable(LangKeys.CMD_IMPORT_TOO_LARGE));
                return;
            }

            try {
                String raw = content.trim();
                JsonObject data = JsonParser.parseString(raw).getAsJsonObject();
                ServerDataStore.getInstance().importAll(data);
                AdvancementRegistry.syncAllRuntime(player.server, true);
                SyncManager.syncAll(player.server);
                player.sendSystemMessage(
                        Component.translatable(LangKeys.CMD_IMPORT_DONE));
                LOGGER.info("Import successful by player {}", player.getName().getString());
            } catch (Exception e) {
                LOGGER.error("Import failed for player {}: {}",
                        player.getName().getString(), e.getMessage());
                player.sendSystemMessage(
                        Component.translatable(LangKeys.CMD_IMPORT_FAILED, e.getMessage()));
            }
        });
    }

    // ═══════════════ 全量同步处理 ═══════════════

    /**
     * 处理服务端全量同步数据包。
     * <p>
     * 每个字段独立解析，一个字段解析失败不影响其他字段的加载。
     * 如果协议版本不匹配，记录警告但仍尝试解析（向后兼容旧服务端）。
     * 解析完成后刷新标签页索引和 GUI 界面。
     */
    private static void handleSync(SyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // 协议版本检查
            if (payload.protocolVersion() != SyncPayload.PROTOCOL_VERSION) {
                LOGGER.warn("Sync payload protocol version mismatch: received {}, expected {}. " +
                        "Trying to parse anyway.",
                        payload.protocolVersion(), SyncPayload.PROTOCOL_VERSION);
            }

            int failedFields = 0;
            StringBuilder failedNames = new StringBuilder();
            try {
                Gson gson = DataStore.GSON;
                ClientDataStore store = ClientDataStore.getInstance();
                JsonObject root = JsonParser.parseString(payload.data()).getAsJsonObject();

                // 每个字段独立解析，单个字段失败不影响其他字段
                // 注意：新增解析字段时需同步更新 TOTAL_FIELDS 常量
                if (!parseAdvancements(gson, store, root)) { failedFields++; failedNames.append("advancements,"); }
                if (!parseDimensionLocks(gson, store, root)) { failedFields++; failedNames.append("dimensionLocks,"); }
                if (!parseCompletions(gson, store, root)) { failedFields++; failedNames.append("completions,"); }
                if (!parseProgress(gson, store, root)) { failedFields++; failedNames.append("progress,"); }
                if (!parseCustomTabs(gson, store, root)) { failedFields++; failedNames.append("customTabs,"); }
                if (!parseVanillaStates(gson, store, root)) { failedFields++; failedNames.append("vanillaStates,"); }
                if (!parseVanillaAdvancements(gson, store, root)) { failedFields++; failedNames.append("vanillaAdvancements,"); }
                if (!parseVanillaMeta(gson, store, root)) { failedFields++; failedNames.append("vanillaMeta,"); }
                if (!parseVanillaParentMap(gson, store, root)) { failedFields++; failedNames.append("vanillaParentMap,"); }
                if (!parseTabOrder(gson, store, root)) { failedFields++; failedNames.append("tabOrder,"); }
                if (!parsePending(store, root)) { failedFields++; failedNames.append("pending,"); }

                if (failedFields > 0) {
                    LOGGER.warn("Sync payload partial failure: {}/{} fields failed to parse: [{}]",
                            failedFields, TOTAL_FIELDS,
                            failedNames.substring(0, failedNames.length() - 1));
                }

                store.markTabsDirty();

                // 全量同步后立即尝试注入 FTB Quests KSR 客户端侧
                // 避免用户在轮询间隔（100 tick）内打开 FTB Quests 配置界面时 NPE
                com.example.advancementoverhaul.compat.FtbQuestsBridge.syncClientKnownServerRegistries(
                        store.getAdvancements().keySet());

                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof AdvancementScreen screen) {
                    screen.markFilteredDirty();
                    screen.vanillaPositionsDirty = true;
                }
            } catch (Exception e) {
                LOGGER.error("Failed to handle sync payload — client data may be incomplete " +
                        "({}/{} fields already parsed)", TOTAL_FIELDS - failedFields, TOTAL_FIELDS, e);
            }
        });
    }

    private static boolean parseAdvancements(Gson gson, ClientDataStore store, JsonObject root) {
        if (!root.has("advancements")) return true;
        try {
            Type t = new TypeToken<Map<String, CustomAdvancement>>() {}.getType();
            Map<String, CustomAdvancement> advs = gson.fromJson(root.get("advancements"), t);
            if (advs != null) store.setAdvancements(advs);
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse 'advancements': {}", e.getMessage());
            return false;
        }
    }

    private static boolean parseDimensionLocks(Gson gson, ClientDataStore store, JsonObject root) {
        if (!root.has("dimensionLocks")) return true;
        try {
            Type t = new TypeToken<Map<String, DimensionLock>>() {}.getType();
            Map<String, DimensionLock> locks = gson.fromJson(root.get("dimensionLocks"), t);
            if (locks != null) store.setDimensionLocks(locks);
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse 'dimensionLocks': {}", e.getMessage());
            return false;
        }
    }

    private static boolean parseCompletions(Gson gson, ClientDataStore store, JsonObject root) {
        if (!root.has("completions")) return true;
        try {
            Type t = new TypeToken<Map<String, Boolean>>() {}.getType();
            Map<String, Boolean> comps = gson.fromJson(root.get("completions"), t);
            if (comps != null) store.setCompletedAdvancements(comps);
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse 'completions': {}", e.getMessage());
            return false;
        }
    }

    private static boolean parseProgress(Gson gson, ClientDataStore store, JsonObject root) {
        if (!root.has("progress")) return true;
        try {
            Type t = new TypeToken<Map<String, Integer>>() {}.getType();
            Map<String, Integer> progs = gson.fromJson(root.get("progress"), t);
            if (progs != null) store.setAdvancementProgress(progs);
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse 'progress': {}", e.getMessage());
            return false;
        }
    }

    private static boolean parseCustomTabs(Gson gson, ClientDataStore store, JsonObject root) {
        if (!root.has("customTabs")) return true;
        try {
            Type t = new TypeToken<List<String>>() {}.getType();
            List<String> tabs = gson.fromJson(root.get("customTabs"), t);
            if (tabs != null) store.setCustomTabs(tabs);
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse 'customTabs': {}", e.getMessage());
            return false;
        }
    }

    private static boolean parseVanillaStates(Gson gson, ClientDataStore store, JsonObject root) {
        if (!root.has("vanillaStates")) return true;
        try {
            JsonObject vs = root.getAsJsonObject("vanillaStates");
            store.setDisabledVanilla(parseStringSet(gson, vs.get("disabled")));
            store.setEnabledVanilla(parseStringSet(gson, vs.get("enabled")));
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse 'vanillaStates': {}", e.getMessage());
            return false;
        }
    }

    private static boolean parseVanillaAdvancements(Gson gson, ClientDataStore store,
                                                  JsonObject root) {
        if (!root.has("vanillaAdvancements")) return true;
        try {
            Type t = new TypeToken<List<Map<String, String>>>() {}.getType();
            List<Map<String, String>> vanillaList =
                    gson.fromJson(root.get("vanillaAdvancements"), t);
            if (vanillaList != null) {
                List<ClientDataStore.VanillaAdvEntry> entries = new ArrayList<>();
                for (Map<String, String> m : vanillaList) {
                    int x = 0, y = 0;
                    try { x = Integer.parseInt(m.getOrDefault("x", "0")); }
                    catch (Exception e) { LOGGER.debug("Failed to parse vanilla adv x coord for {}: {}", m.get("id"), e.getMessage()); }
                    try { y = Integer.parseInt(m.getOrDefault("y", "0")); }
                    catch (Exception e) { LOGGER.debug("Failed to parse vanilla adv y coord for {}: {}", m.get("id"), e.getMessage()); }
                    entries.add(new ClientDataStore.VanillaAdvEntry(
                            m.getOrDefault("id", ""),
                            m.getOrDefault("name", ""),
                            m.getOrDefault("desc", ""),
                            "true".equals(m.get("hidden")),
                            m.get("nameKey"),
                            m.get("descKey"),
                            m.get("rootTab"),
                            x, y,
                            m.get("icon")
                    ));
                }
                store.setVanillaAdvancements(entries);
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse 'vanillaAdvancements': {}", e.getMessage());
            return false;
        }
    }

    private static boolean parseVanillaMeta(Gson gson, ClientDataStore store, JsonObject root) {
        if (!root.has("vanillaMeta")) return true;
        try {
            Type t = new TypeToken<Map<String, VanillaAdvMeta>>() {}.getType();
            Map<String, VanillaAdvMeta> meta =
                    gson.fromJson(root.get("vanillaMeta"), t);
            if (meta != null) store.setVanillaMeta(meta);
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse 'vanillaMeta': {}", e.getMessage());
            return false;
        }
    }

    private static boolean parseVanillaParentMap(Gson gson, ClientDataStore store, JsonObject root) {
        if (!root.has("vanillaParentMap")) return true;
        try {
            Type t = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> pm = gson.fromJson(root.get("vanillaParentMap"), t);
            if (pm != null) store.setVanillaParentMap(pm);
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse 'vanillaParentMap': {}", e.getMessage());
            return false;
        }
    }

    private static boolean parseTabOrder(Gson gson, ClientDataStore store, JsonObject root) {
        if (!root.has("tabOrder")) return true;
        try {
            Type t = new TypeToken<List<String>>() {}.getType();
            List<String> to = gson.fromJson(root.get("tabOrder"), t);
            if (to != null) store.setTabOrder(to);
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse 'tabOrder': {}", e.getMessage());
            return false;
        }
    }

    private static boolean parsePending(ClientDataStore store, JsonObject root) {
        if (!root.has("pending") || !root.get("pending").isJsonArray()) return true;
        try {
            Set<String> pending = new HashSet<>();
            for (JsonElement e : root.getAsJsonArray("pending")) {
                if (e.isJsonPrimitive()) pending.add(e.getAsString());
            }
            store.setPendingAdvancements(pending);
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse 'pending': {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从 JSON 元素解析字符串集合。
     */
    private static Set<String> parseStringSet(Gson gson, JsonElement elem) {
        if (elem == null || elem.isJsonNull()) return Set.of();
        try {
            Set<String> result = gson.fromJson(elem,
                    new TypeToken<Set<String>>() {}.getType());
            return result != null ? result : Set.of();
        } catch (Exception e) {
            return Set.of();
        }
    }

    // ═══════════════ 增量进度处理 ═══════════════

    /**
     * 处理增量进度同步包。
     * <p>
     * 更新客户端的 pending、progress 和 completed 状态。
     * 当 completed=true 且 AdvancementScreen 打开时，弹出 Toast 通知。
     * <p>
     * 注意：不向下重置 completed 状态，因为网络包可能乱序到达，
     * 已完成状态不应被旧包回退。
     */
    private static void handleProgress(ProgressSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientDataStore store = ClientDataStore.getInstance();

            store.updatePending(payload.advancementId(), payload.pending());
            store.updateProgress(payload.advancementId(), payload.progress());

            if (payload.completed()) {
                store.updateCompletion(payload.advancementId(), true);
                Minecraft mc = Minecraft.getInstance();

                // 获取成就名称
                CustomAdvancement adv = store.getAdvancement(payload.advancementId());
                String name = adv != null ? adv.getName() : payload.advancementId();

                // 1. 播放紫水晶旋律
                com.example.advancementoverhaul.client.gui.CompletionChime.play(mc);

                // 2. 显示牌匾 UI（无论何种屏幕都会显示）
                com.example.advancementoverhaul.client.gui.CompletionPlaque.show(name);

                // 3. 保留原有 Toast（仅 AdvancementScreen 内可见）
                if (mc.screen instanceof AdvancementScreen screen) {
                    screen.addToast(name);
                }
            }
        });
    }

    // ═══════════════ FTB 任务完成通知处理 ═══════════════

    /**
     * 处理 FTB Quests 任务完成的客户端通知。
     * <p>
     * 仅在 REPLACE 模式（ftbNotifMode=2）下展示牌匾和音效。
     * DEFAULT 模式下 FTB 自带通知会正常显示，DISABLE 模式下 Mixin 已拦截。
     */
    private static void handleFtbQuestCompleted(FtbQuestCompletedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (AdvancementScreen.ftbNotifMode != 2) return; // 仅 REPLACE 模式

            Minecraft mc = Minecraft.getInstance();
            com.example.advancementoverhaul.client.gui.CompletionChime.play(mc);
            com.example.advancementoverhaul.client.gui.CompletionPlaque.show(payload.questName());
        });
    }

    // ═══════════════ 分块同步处理 ═══════════════

    /**
     * 处理分块同步数据包。
     * <p>
     * 将接收到的块缓存到 ChunkAssembly，收齐后组装完整 JSON，
     * 构造临时 SyncPayload 并复用 handleSync 逻辑。
     * <p>
     * 定期清理超时的未完成传输，防止内存泄漏。
     */
    private static void handleSyncChunk(SyncChunkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            long transferId = payload.transferId();

            // 定期清理过期传输
            long now = System.currentTimeMillis();
            if (now - lastAssemblyCleanup > ASSEMBLY_CLEANUP_INTERVAL_MS) {
                lastAssemblyCleanup = now;
                CHUNK_ASSEMBLIES.entrySet().removeIf(e -> e.getValue().isExpired());
            }

            if (payload.totalChunks() == 1) {
                // 单块传输：直接解析，无需组装
                String fullJson = new String(payload.data(), StandardCharsets.UTF_8);
                SyncPayload full = new SyncPayload(SyncPayload.PROTOCOL_VERSION, fullJson);
                handleSync(full, context);
                return;
            }

            // 多块传输：纳入组装缓冲区
            ChunkAssembly assembly = CHUNK_ASSEMBLIES.computeIfAbsent(transferId,
                    id -> new ChunkAssembly(transferId, payload.totalChunks()));

            if (!assembly.addChunk(payload.chunkIndex(), payload.data())) {
                // 重复块或越界块，可能因网络重传导致，静默忽略
                LOGGER.debug("Duplicate or out-of-range chunk ignored: transferId={}, index={}/{}",
                        transferId, payload.chunkIndex(), payload.totalChunks());
                return;
            }

            LOGGER.debug("Chunk received: transferId={}, {}/{}",
                    transferId, assembly.receivedChunks, assembly.totalChunks);

            if (assembly.isComplete()) {
                CHUNK_ASSEMBLIES.remove(transferId);
                try {
                    String fullJson = assembly.assemble();
                    LOGGER.info("Chunk assembly complete: transferId={}, totalKB={}",
                            transferId, fullJson.length() / 1024);
                    SyncPayload full = new SyncPayload(SyncPayload.PROTOCOL_VERSION, fullJson);
                    handleSync(full, context);
                } catch (Exception e) {
                    LOGGER.error("Failed to assemble chunked sync payload: transferId={}", transferId, e);
                }
            }
        });
    }

}
