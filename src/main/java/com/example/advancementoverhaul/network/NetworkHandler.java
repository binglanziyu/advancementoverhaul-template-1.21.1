package com.example.advancementoverhaul.network;
import com.example.advancementoverhaul.Config;
import com.example.advancementoverhaul.ModInfo;
import com.example.advancementoverhaul.data.ClientDataStore;
import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.DimensionLock;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import com.example.advancementoverhaul.client.gui.AdvancementScreen;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.*;

public class NetworkHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul");

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(ModInfo.NETWORK_PROTOCOL);
        registrar.playToClient(SyncPayload.TYPE, SyncPayload.CODEC, NetworkHandler::handleSync);
        registrar.playToClient(ProgressSyncPayload.TYPE, ProgressSyncPayload.CODEC, NetworkHandler::handleProgress);
        registrar.playToServer(C2SCommandPayload.TYPE, C2SCommandPayload.CODEC, NetworkHandler::handleC2SCommand);
    }

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
            "adv vanilla setpos ", "adv vanilla settab ", "adv vanilla cleartab ", "adv vanilla save ",
            "adv seticon "
    );

    private static boolean validateUpdateJson(String cmd) {
        boolean isUpdate = cmd.startsWith("adv updatejson ");
        boolean isCreate = cmd.startsWith("adv createjson ");
        if (!isUpdate && !isCreate) return true;
        String raw = cmd.substring(15);
        if (raw.length() > 12288) { // Base64编码后比原始JSON大约大33%，所以上限放宽
            LOGGER.warn("updatejson payload too large: {} chars", raw.length());
            return false;
        }
        // 解码Base64
        String json;
        try {
            json = new String(java.util.Base64.getDecoder().decode(raw),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException notBase64) {
            json = raw; // 兼容旧格式
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
            if (obj.has("name") && obj.get("name").getAsString().length() > 256) return false;
            if (obj.has("description") && obj.get("description").getAsString().length() > 1024) return false;
            if (obj.has("icon") && obj.get("icon").getAsString().length() > 128) return false;
            if (obj.has("tab") && obj.get("tab").getAsString().length() > 64) return false;
            return true;
        } catch (Exception e) {
            LOGGER.warn("updatejson invalid JSON: {}", e.getMessage());
            return false;
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<UUID, Long> COMMAND_COOLDOWN = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 100; // 每个玩家两次命令间最短间隔 100ms

    private static void handleC2SCommand(C2SCommandPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                String cmd = payload.command();
                if (cmd == null) return;
                cmd = cmd.trim();
                if (!cmd.startsWith("adv ")) return;

                // 白名单校验
                boolean allowed = ALLOWED_EXACT.contains(cmd)
                        || ALLOWED_PREFIXES.stream().anyMatch(cmd::startsWith);
                if (!allowed) {
                    LOGGER.warn("Blocked unauthorized C2S command from {}: {}",
                            player.getName().getString(), cmd);
                    return;
                }

                // JSON内容校验（安检门）
                if (!validateUpdateJson(cmd)) {
                    LOGGER.warn("Blocked invalid updatejson from {}",
                            player.getName().getString());
                    return;
                }

                // 通用长度限制
                if (cmd.length() > 16384) {
                    LOGGER.warn("Blocked oversized C2S command ({} chars) from {}",
                            cmd.length(), player.getName().getString());
                    return;
                }

                // 频率限制
                long now = System.currentTimeMillis();
                Long last = COMMAND_COOLDOWN.get(player.getUUID());
                if (last != null && now - last < COOLDOWN_MS) {
                    LOGGER.debug("Rate limiting C2S command from {}", player.getName().getString());
                    return;
                }

               // 防止冷却 Map 无限增长
                if (COMMAND_COOLDOWN.size() > 1024) {
                    COMMAND_COOLDOWN.entrySet().removeIf(
                            e -> now - e.getValue() > 60_000);
                }

                COMMAND_COOLDOWN.put(player.getUUID(), now);

                // 权限校验：
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
            }
        });
    }

    private static void handleSync(SyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                Gson gson = DataStore.GSON;
                ClientDataStore store = ClientDataStore.getInstance();
                JsonObject root = JsonParser.parseString(payload.data()).getAsJsonObject();

                // Each field is parsed independently so a malformed field
                // does not prevent the remaining fields from loading.
                if (root.has("advancements")) {
                    try {
                        Type t = new TypeToken<Map<String, DataStore.CustomAdvancement>>(){}.getType();
                        Map<String, DataStore.CustomAdvancement> advs = gson.fromJson(root.get("advancements"), t);
                        if (advs != null) store.setAdvancements(advs);
                    } catch (Exception e) { LOGGER.warn("Failed to parse 'advancements': {}", e.getMessage()); }
                }
                if (root.has("dimensionLocks")) {
                    try {
                        Type t = new TypeToken<Map<String, DimensionLock>>(){}.getType();
                        Map<String, DimensionLock> locks = gson.fromJson(root.get("dimensionLocks"), t);
                        if (locks != null) store.setDimensionLocks(locks);
                    } catch (Exception e) { LOGGER.warn("Failed to parse 'dimensionLocks': {}", e.getMessage()); }
                }
                if (root.has("completions")) {
                    try {
                        Type t = new TypeToken<Map<String, Boolean>>(){}.getType();
                        Map<String, Boolean> comps = gson.fromJson(root.get("completions"), t);
                        if (comps != null) store.setCompletedAdvancements(comps);
                    } catch (Exception e) { LOGGER.warn("Failed to parse 'completions': {}", e.getMessage()); }
                }
                if (root.has("progress")) {
                    try {
                        Type t = new TypeToken<Map<String, Integer>>(){}.getType();
                        Map<String, Integer> progs = gson.fromJson(root.get("progress"), t);
                        if (progs != null) store.setAdvancementProgress(progs);
                    } catch (Exception e) { LOGGER.warn("Failed to parse 'progress': {}", e.getMessage()); }
                }
                if (root.has("customTabs")) {
                    try {
                        Type t = new TypeToken<List<String>>(){}.getType();
                        List<String> tabs = gson.fromJson(root.get("customTabs"), t);
                        if (tabs != null) store.setCustomTabs(tabs);
                    } catch (Exception e) { LOGGER.warn("Failed to parse 'customTabs': {}", e.getMessage()); }
                }
                if (root.has("vanillaStates")) {
                    try {
                        JsonObject vs = root.getAsJsonObject("vanillaStates");
                        if (vs != null) {
                            store.setDisabledVanilla(parseStringSet(gson, vs.get("disabled")));
                            store.setEnabledVanilla(parseStringSet(gson, vs.get("enabled")));
                        }
                    } catch (Exception e) { LOGGER.warn("Failed to parse 'vanillaStates': {}", e.getMessage()); }
                }
                if (root.has("vanillaAdvancements")) {
                    try {
                        Type t = new TypeToken<List<Map<String, String>>>(){}.getType();
                        List<Map<String, String>> vanillaList = gson.fromJson(root.get("vanillaAdvancements"), t);
                        if (vanillaList != null) {
                            List<ClientDataStore.VanillaAdvEntry> entries = new ArrayList<>();
                            for (Map<String, String> m : vanillaList) {
                                int x = 0, y = 0;
                                try { x = Integer.parseInt(m.getOrDefault("x", "0")); } catch (Exception ignored) {}
                                try { y = Integer.parseInt(m.getOrDefault("y", "0")); } catch (Exception ignored) {}
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
                    } catch (Exception e) { LOGGER.warn("Failed to parse 'vanillaAdvancements': {}", e.getMessage()); }
                }
                if (root.has("vanillaMeta")) {
                    try {
                        Type t = new TypeToken<Map<String, DataStore.VanillaAdvMeta>>(){}.getType();
                        Map<String, DataStore.VanillaAdvMeta> meta = gson.fromJson(root.get("vanillaMeta"), t);
                        if (meta != null) store.setVanillaMeta(meta);
                    } catch (Exception e) { LOGGER.warn("Failed to parse 'vanillaMeta': {}", e.getMessage()); }
                }
                if (root.has("vanillaParentMap")) {
                    try {
                        Type t = new TypeToken<Map<String, String>>(){}.getType();
                        Map<String, String> pm = gson.fromJson(root.get("vanillaParentMap"), t);
                        if (pm != null) store.setVanillaParentMap(pm);
                    } catch (Exception e) { LOGGER.warn("Failed to parse 'vanillaParentMap': {}", e.getMessage()); }
                }
                if (root.has("tabOrder")) {
                    try {
                        Type t = new TypeToken<List<String>>(){}.getType();
                        List<String> to = gson.fromJson(root.get("tabOrder"), t);
                        if (to != null) store.setTabOrder(to);
                    } catch (Exception e) { LOGGER.warn("Failed to parse 'tabOrder': {}", e.getMessage()); }
                }
                if (root.has("pending") && root.get("pending").isJsonArray()) {
                    try {
                        Set<String> pending = new HashSet<>();
                        for (JsonElement e : root.getAsJsonArray("pending"))
                            if (e.isJsonPrimitive()) pending.add(e.getAsString());
                        store.setPendingAdvancements(pending);
                    } catch (Exception e) { LOGGER.warn("Failed to parse 'pending': {}", e.getMessage()); }
                }

                store.markTabsDirty();
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof AdvancementScreen screen) {
                    screen.markFilteredDirty();
                }
            } catch (Exception e) {
                LOGGER.error("Failed to handle sync payload — client data may be incomplete", e);
            }
        });
    }

    private static Set<String> parseStringSet(Gson gson, JsonElement elem) {
        if (elem == null || elem.isJsonNull()) return Set.of();
        try {
            Set<String> result = gson.fromJson(elem, new TypeToken<Set<String>>(){}.getType());
            return result != null ? result : Set.of();
        } catch (Exception e) { return Set.of(); }
    }

    private static void handleProgress(ProgressSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientDataStore store = ClientDataStore.getInstance();

            store.updatePending(payload.advancementId(), payload.pending());
            store.updateProgress(payload.advancementId(), payload.progress());

            if (payload.completed()) {
                store.updateCompletion(payload.advancementId(), true);
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof AdvancementScreen screen) {
                    DataStore.CustomAdvancement adv = store.getAdvancement(payload.advancementId());
                    screen.addToast(adv != null ? adv.getName() : payload.advancementId());
                }
            }
            // 不再向下重置 completed 状态
            // 网络包可能乱序到达，已完成状态不应被旧包回退
        });
    }
}