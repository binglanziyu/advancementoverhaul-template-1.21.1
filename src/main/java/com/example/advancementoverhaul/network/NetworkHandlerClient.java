/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonParser
 *  com.google.gson.reflect.TypeToken
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.screens.Screen
 *  net.neoforged.neoforge.network.handling.IPayloadContext
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.example.advancementoverhaul.network;

import com.example.advancementoverhaul.client.gui.AdvancementScreen;
import com.example.advancementoverhaul.client.gui.CompletionChime;
import com.example.advancementoverhaul.client.gui.CompletionPlaque;
import com.example.advancementoverhaul.client.gui.timeline.TimelineScreen;
import com.example.advancementoverhaul.compat.ftb.FtbQuestsBridge;
import com.example.advancementoverhaul.data.ClientDataStore;
import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.DimensionLock;
import com.example.advancementoverhaul.data.PlayerStats;
import com.example.advancementoverhaul.data.model.CustomAdvancement;
import com.example.advancementoverhaul.data.model.VanillaAdvMeta;
import com.example.advancementoverhaul.network.payload.FtbQuestCompletedPayload;
import com.example.advancementoverhaul.network.payload.ProgressSyncPayload;
import com.example.advancementoverhaul.network.payload.StatsSyncPayload;
import com.example.advancementoverhaul.network.payload.SyncChunkPayload;
import com.example.advancementoverhaul.network.payload.SyncPayload;
import com.example.advancementoverhaul.network.payload.TimelineSyncPayload;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NetworkHandlerClient {
    private static final Logger LOGGER = LoggerFactory.getLogger((String)"AdvancementOverhaul/Client");
    private static final ConcurrentHashMap<Long, ChunkAssembly> CHUNK_ASSEMBLIES = new ConcurrentHashMap();
    private static long lastAssemblyCleanup = 0L;
    private static final long ASSEMBLY_CLEANUP_INTERVAL_MS = 10000L;

    private NetworkHandlerClient() {
    }

    static void handleSync(SyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.protocolVersion() != 1) {
                LOGGER.warn("Sync payload protocol version mismatch: received {}, expected {}. Trying to parse anyway.", (Object)payload.protocolVersion(), (Object)1);
            }
            int failedFields = 0;
            StringBuilder failedNames = new StringBuilder();
            try {
                Gson gson = DataStore.GSON;
                ClientDataStore store = ClientDataStore.getInstance();
                JsonObject root = JsonParser.parseString((String)payload.data()).getAsJsonObject();
                if (!NetworkHandlerClient.parseAdvancements(gson, store, root)) {
                    ++failedFields;
                    failedNames.append("advancements,");
                }
                if (!NetworkHandlerClient.parseDimensionLocks(gson, store, root)) {
                    ++failedFields;
                    failedNames.append("dimensionLocks,");
                }
                if (!NetworkHandlerClient.parseCompletions(gson, store, root)) {
                    ++failedFields;
                    failedNames.append("completions,");
                }
                if (!NetworkHandlerClient.parseProgress(gson, store, root)) {
                    ++failedFields;
                    failedNames.append("progress,");
                }
                if (!NetworkHandlerClient.parseCustomTabs(gson, store, root)) {
                    ++failedFields;
                    failedNames.append("customTabs,");
                }
                if (!NetworkHandlerClient.parseVanillaStates(gson, store, root)) {
                    ++failedFields;
                    failedNames.append("vanillaStates,");
                }
                if (!NetworkHandlerClient.parseVanillaAdvancements(gson, store, root)) {
                    ++failedFields;
                    failedNames.append("vanillaAdvancements,");
                }
                if (!NetworkHandlerClient.parseVanillaMeta(gson, store, root)) {
                    ++failedFields;
                    failedNames.append("vanillaMeta,");
                }
                if (!NetworkHandlerClient.parseVanillaParentMap(gson, store, root)) {
                    ++failedFields;
                    failedNames.append("vanillaParentMap,");
                }
                if (!NetworkHandlerClient.parseTabOrder(gson, store, root)) {
                    ++failedFields;
                    failedNames.append("tabOrder,");
                }
                if (!NetworkHandlerClient.parsePending(store, root)) {
                    ++failedFields;
                    failedNames.append("pending,");
                }
                if (!NetworkHandlerClient.parsePlayerStats(gson, store, root)) {
                    ++failedFields;
                    failedNames.append("playerStats,");
                }
                if (failedFields > 0) {
                    LOGGER.warn("Sync payload partial failure: {}/{} fields failed to parse: [{}]", new Object[]{failedFields, 12, failedNames.substring(0, failedNames.length() - 1)});
                }
                store.markTabsDirty();
                FtbQuestsBridge.syncClientKnownServerRegistries(store.getAdvancements().keySet());
                Minecraft mc = Minecraft.getInstance();
                Screen patt0$temp = mc.screen;
                if (patt0$temp instanceof AdvancementScreen) {
                    AdvancementScreen screen = (AdvancementScreen)patt0$temp;
                    screen.markFilteredDirty();
                    screen.vanillaPositionsDirty = true;
                }
            }
            catch (Exception e) {
                LOGGER.error("Failed to handle sync payload \u2014 client data may be incomplete ({}/{} fields already parsed)", new Object[]{12 - failedFields, 12, e});
            }
        });
    }

    static void handleProgress(ProgressSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientDataStore store = ClientDataStore.getInstance();
            store.updatePending(payload.advancementId(), payload.pending());
            store.updateProgress(payload.advancementId(), payload.progress());
            if (payload.completed()) {
                store.updateCompletion(payload.advancementId(), true);
                Minecraft mc = Minecraft.getInstance();
                CustomAdvancement adv = store.getAdvancement(payload.advancementId());
                String name = adv != null ? adv.getName() : payload.advancementId();
                String lore = adv != null ? adv.getLore() : null;
                CompletionChime.play(mc);
                CompletionPlaque.show(name, lore);
                Screen patt0$temp = mc.screen;
                if (patt0$temp instanceof AdvancementScreen) {
                    AdvancementScreen screen = (AdvancementScreen)patt0$temp;
                    screen.addToast(name);
                }
            }
        });
    }

    static void handleStatsSync(StatsSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                PlayerStats stats = (PlayerStats)DataStore.GSON.fromJson(payload.statsJson(), PlayerStats.class);
                if (stats != null) {
                    ClientDataStore.getInstance().setPlayerStats(stats);
                    LOGGER.debug("Stats sync received ({} bytes, hasData={})", (Object)payload.statsJson().length(), (Object)stats.hasAnyData());
                }
            }
            catch (Exception e) {
                LOGGER.warn("Failed to parse stats sync payload: {}", (Object)e.getMessage());
            }
        });
    }

    static void handleTimelineSync(TimelineSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                ClientDataStore.getInstance().setTimelineData(payload.dataJson());
                LOGGER.debug("Timeline sync received ({} bytes)", (Object)payload.dataJson().length());
                Screen patt0$temp = mc.screen;
                if (patt0$temp instanceof TimelineScreen) {
                    TimelineScreen timelineScreen = (TimelineScreen)patt0$temp;
                    timelineScreen.updateTimelineData(payload.dataJson());
                }
            }
            catch (Exception e) {
                LOGGER.warn("Failed to process timeline sync: {}", (Object)e.getMessage());
            }
        });
    }

    static void handleFtbQuestCompleted(FtbQuestCompletedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (AdvancementScreen.ftbNotifMode != 2) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            CompletionChime.play(mc);
            CompletionPlaque.show(payload.questName());
        });
    }

    static void handleSyncChunk(SyncChunkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            long transferId = payload.transferId();
            long now = System.currentTimeMillis();
            if (now - lastAssemblyCleanup > 10000L) {
                lastAssemblyCleanup = now;
                CHUNK_ASSEMBLIES.entrySet().removeIf(e -> ((ChunkAssembly)e.getValue()).isExpired());
            }
            if (payload.totalChunks() == 1) {
                String fullJson = new String(payload.data(), StandardCharsets.UTF_8);
                SyncPayload full = new SyncPayload(1, fullJson);
                NetworkHandlerClient.handleSync(full, context);
                return;
            }
            ChunkAssembly assembly = CHUNK_ASSEMBLIES.computeIfAbsent(transferId, id -> new ChunkAssembly(transferId, payload.totalChunks()));
            if (!assembly.addChunk(payload.chunkIndex(), payload.data())) {
                LOGGER.debug("Duplicate or out-of-range chunk ignored: transferId={}, index={}/{}", new Object[]{transferId, payload.chunkIndex(), payload.totalChunks()});
                return;
            }
            LOGGER.debug("Chunk received: transferId={}, {}/{}", new Object[]{transferId, assembly.receivedChunks, assembly.totalChunks});
            if (assembly.isComplete()) {
                CHUNK_ASSEMBLIES.remove(transferId);
                try {
                    String fullJson = assembly.assemble();
                    LOGGER.info("Chunk assembly complete: transferId={}, totalKB={}", (Object)transferId, (Object)(fullJson.length() / 1024));
                    SyncPayload full = new SyncPayload(1, fullJson);
                    NetworkHandlerClient.handleSync(full, context);
                }
                catch (Exception e2) {
                    LOGGER.error("Failed to assemble chunked sync payload: transferId={}", (Object)transferId, (Object)e2);
                }
            }
        });
    }

    private static boolean parseAdvancements(Gson gson, ClientDataStore store, JsonObject root) {
        if (!root.has("advancements")) {
            return true;
        }
        try {
            Type t = new TypeToken<Map<String, CustomAdvancement>>(){}.getType();
            Map advs = (Map)gson.fromJson(root.get("advancements"), t);
            if (advs != null) {
                store.setAdvancements(advs);
            }
            return true;
        }
        catch (Exception e) {
            LOGGER.warn("Failed to parse 'advancements': {}", (Object)e.getMessage());
            return false;
        }
    }

    private static boolean parseDimensionLocks(Gson gson, ClientDataStore store, JsonObject root) {
        if (!root.has("dimensionLocks")) {
            return true;
        }
        try {
            Type t = new TypeToken<Map<String, DimensionLock>>(){}.getType();
            Map locks = (Map)gson.fromJson(root.get("dimensionLocks"), t);
            if (locks != null) {
                store.setDimensionLocks(locks);
            }
            return true;
        }
        catch (Exception e) {
            LOGGER.warn("Failed to parse 'dimensionLocks': {}", (Object)e.getMessage());
            return false;
        }
    }

    private static boolean parseCompletions(Gson gson, ClientDataStore store, JsonObject root) {
        if (!root.has("completions")) {
            return true;
        }
        try {
            Type t = new TypeToken<Map<String, Boolean>>(){}.getType();
            Map comps = (Map)gson.fromJson(root.get("completions"), t);
            if (comps != null) {
                store.setCompletedAdvancements(comps);
            }
            return true;
        }
        catch (Exception e) {
            LOGGER.warn("Failed to parse 'completions': {}", (Object)e.getMessage());
            return false;
        }
    }

    private static boolean parseProgress(Gson gson, ClientDataStore store, JsonObject root) {
        if (!root.has("progress")) {
            return true;
        }
        try {
            Type t = new TypeToken<Map<String, Integer>>(){}.getType();
            Map progs = (Map)gson.fromJson(root.get("progress"), t);
            if (progs != null) {
                store.setAdvancementProgress(progs);
            }
            return true;
        }
        catch (Exception e) {
            LOGGER.warn("Failed to parse 'progress': {}", (Object)e.getMessage());
            return false;
        }
    }

    private static boolean parseCustomTabs(Gson gson, ClientDataStore store, JsonObject root) {
        if (!root.has("customTabs")) {
            return true;
        }
        try {
            Type t = new TypeToken<List<String>>(){}.getType();
            List tabs = (List)gson.fromJson(root.get("customTabs"), t);
            if (tabs != null) {
                store.setCustomTabs(tabs);
            }
            return true;
        }
        catch (Exception e) {
            LOGGER.warn("Failed to parse 'customTabs': {}", (Object)e.getMessage());
            return false;
        }
    }

    private static boolean parseVanillaStates(Gson gson, ClientDataStore store, JsonObject root) {
        if (!root.has("vanillaStates")) {
            return true;
        }
        try {
            JsonObject vs = root.getAsJsonObject("vanillaStates");
            store.setDisabledVanilla(NetworkHandlerClient.parseStringSet(gson, vs.get("disabled")));
            store.setEnabledVanilla(NetworkHandlerClient.parseStringSet(gson, vs.get("enabled")));
            return true;
        }
        catch (Exception e) {
            LOGGER.warn("Failed to parse 'vanillaStates': {}", (Object)e.getMessage());
            return false;
        }
    }

    private static boolean parseVanillaAdvancements(Gson gson, ClientDataStore store, JsonObject root) {
        if (!root.has("vanillaAdvancements")) {
            return true;
        }
        try {
            Type t = new TypeToken<List<Map<String, String>>>(){}.getType();
                @SuppressWarnings("unchecked")
                List<Map<String, String>> vanillaList = (List<Map<String, String>>) gson.fromJson(root.get("vanillaAdvancements"), t);
                if (vanillaList != null) {
                    ArrayList<ClientDataStore.VanillaAdvEntry> entries = new ArrayList<ClientDataStore.VanillaAdvEntry>();
                    for (Map<String, String> m : vanillaList) {
                        int x = 0;
                        int y = 0;
                        try {
                            x = Integer.parseInt(m.getOrDefault("x", "0"));
                        }
                        catch (Exception exception) {
                            // empty catch block
                        }
                        try {
                            y = Integer.parseInt(m.getOrDefault("y", "0"));
                        }
                        catch (Exception exception) {
                        // empty catch block
                    }
                    entries.add(new ClientDataStore.VanillaAdvEntry(m.getOrDefault("id", ""), m.getOrDefault("name", ""), m.getOrDefault("desc", ""), "true".equals(m.get("hidden")), (String)m.get("nameKey"), (String)m.get("descKey"), (String)m.get("rootTab"), x, y, (String)m.get("icon")));
                }
                store.setVanillaAdvancements(entries);
            }
            return true;
        }
        catch (Exception e) {
            LOGGER.warn("Failed to parse 'vanillaAdvancements': {}", (Object)e.getMessage());
            return false;
        }
    }

    private static boolean parseVanillaMeta(Gson gson, ClientDataStore store, JsonObject root) {
        if (!root.has("vanillaMeta")) {
            return true;
        }
        try {
            Type t = new TypeToken<Map<String, VanillaAdvMeta>>(){}.getType();
            Map meta = (Map)gson.fromJson(root.get("vanillaMeta"), t);
            if (meta != null) {
                store.setVanillaMeta(meta);
            }
            return true;
        }
        catch (Exception e) {
            LOGGER.warn("Failed to parse 'vanillaMeta': {}", (Object)e.getMessage());
            return false;
        }
    }

    private static boolean parseVanillaParentMap(Gson gson, ClientDataStore store, JsonObject root) {
        if (!root.has("vanillaParentMap")) {
            return true;
        }
        try {
            Type t = new TypeToken<Map<String, String>>(){}.getType();
            Map pm = (Map)gson.fromJson(root.get("vanillaParentMap"), t);
            if (pm != null) {
                store.setVanillaParentMap(pm);
            }
            return true;
        }
        catch (Exception e) {
            LOGGER.warn("Failed to parse 'vanillaParentMap': {}", (Object)e.getMessage());
            return false;
        }
    }

    private static boolean parseTabOrder(Gson gson, ClientDataStore store, JsonObject root) {
        if (!root.has("tabOrder")) {
            return true;
        }
        try {
            Type t = new TypeToken<List<String>>(){}.getType();
            List to = (List)gson.fromJson(root.get("tabOrder"), t);
            if (to != null) {
                store.setTabOrder(to);
            }
            return true;
        }
        catch (Exception e) {
            LOGGER.warn("Failed to parse 'tabOrder': {}", (Object)e.getMessage());
            return false;
        }
    }

    private static boolean parsePending(ClientDataStore store, JsonObject root) {
        if (!root.has("pending") || !root.get("pending").isJsonArray()) {
            return true;
        }
        try {
            HashSet<String> pending = new HashSet<String>();
            for (JsonElement e : root.getAsJsonArray("pending")) {
                if (!e.isJsonPrimitive()) continue;
                pending.add(e.getAsString());
            }
            store.setPendingAdvancements(pending);
            return true;
        }
        catch (Exception e) {
            LOGGER.warn("Failed to parse 'pending': {}", (Object)e.getMessage());
            return false;
        }
    }

    private static boolean parsePlayerStats(Gson gson, ClientDataStore store, JsonObject root) {
        if (!root.has("playerStats")) {
            return true;
        }
        try {
            PlayerStats stats = (PlayerStats)gson.fromJson(root.get("playerStats"), PlayerStats.class);
            if (stats != null) {
                store.setPlayerStats(stats);
            }
            return true;
        }
        catch (Exception e) {
            LOGGER.warn("Failed to parse 'playerStats': {}", (Object)e.getMessage());
            return false;
        }
    }

    private static Set<String> parseStringSet(Gson gson, JsonElement elem) {
        if (elem == null || elem.isJsonNull()) {
            return Set.of();
        }
        try {
            Set<String> result = (Set<String>)gson.fromJson(elem, new TypeToken<Set<String>>(){}.getType());
            return result != null ? result : Set.of();
        }
        catch (Exception e) {
            return Set.of();
        }
    }

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
            if (index < 0 || index >= this.totalChunks || this.chunks[index] != null) {
                return false;
            }
            this.chunks[index] = data;
            ++this.receivedChunks;
            return true;
        }

        boolean isComplete() {
            return this.receivedChunks == this.totalChunks;
        }

        String assemble() {
            int totalLen = 0;
            for (byte[] c : this.chunks) {
                totalLen += c.length;
            }
            byte[] full = new byte[totalLen];
            int pos = 0;
            for (byte[] c : this.chunks) {
                System.arraycopy(c, 0, full, pos, c.length);
                pos += c.length;
            }
            return new String(full, StandardCharsets.UTF_8);
        }

        boolean isExpired() {
            return System.currentTimeMillis() - this.createdAt > 30000L;
        }
    }
}

