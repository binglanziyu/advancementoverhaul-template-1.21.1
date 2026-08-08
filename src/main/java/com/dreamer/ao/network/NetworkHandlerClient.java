package com.dreamer.ao.network;

import com.dreamer.ao.client.gui.AdvancementScreen;
import com.dreamer.ao.client.gui.CompletionChime;
import com.dreamer.ao.client.gui.CompletionPlaque;
import com.dreamer.ao.client.gui.state.ScreenState;
import com.dreamer.ao.client.gui.timeline.TimelineScreen;
import com.dreamer.ao.compat.ftb.FtbQuestsBridge;
import com.dreamer.ao.data.ClientDataStore;
import com.dreamer.ao.data.DataStore;
import com.dreamer.ao.data.DimensionLock;
import com.dreamer.ao.data.PlayerStats;
import com.dreamer.ao.data.model.CustomAdvancement;
import com.dreamer.ao.data.model.VanillaAdvMeta;
import com.dreamer.ao.network.payload.FtbQuestCompletedPayload;
import com.dreamer.ao.util.JsonParse;
import com.dreamer.ao.network.payload.ProgressSyncPayload;
import com.dreamer.ao.network.payload.StatsSyncPayload;
import com.dreamer.ao.network.payload.SyncChunkPayload;
import com.dreamer.ao.network.payload.SyncPayload;
import com.dreamer.ao.network.payload.TimelineSyncPayload;
import com.dreamer.ao.network.payload.PhaseSyncPayload;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkHandlerClient.class);
    private static final ConcurrentHashMap<Long, ChunkAssembly> CHUNK_ASSEMBLIES = new ConcurrentHashMap<>();
    private static long lastAssemblyCleanup = 0L;
    private static final long ASSEMBLY_CLEANUP_INTERVAL_MS = 10000L;

    private NetworkHandlerClient() {
    }

    static void handleSync(SyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.protocolVersion() != 1) {
                LOGGER.warn("Sync payload protocol version mismatch: received {}, expected {}. Trying to parse anyway.", payload.protocolVersion(), 1);
            }
            int failedFields = 0;
            StringBuilder failedNames = new StringBuilder();
            try {
                Gson gson = DataStore.GSON;
                ClientDataStore store = ClientDataStore.getInstance();
                JsonObject root = JsonParser.parseString(payload.data()).getAsJsonObject();
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
                    LOGGER.warn("Sync payload partial failure: {}/{} fields failed to parse: [{}]", failedFields, 12, failedNames.substring(0, failedNames.length() - 1));
                }
                store.markTabsDirty();
                FtbQuestsBridge.syncClientKnownServerRegistries(store.getAdvancements().keySet());
                Minecraft mc = Minecraft.getInstance();
                Screen currentScreen = mc.screen;
                if (currentScreen instanceof AdvancementScreen screen) {
                    screen.markFilteredDirty();
                    screen.screenState.markDirty(ScreenState.DIRTY_VANILLA_POS);
                }
            }
            catch (Exception e) {
                LOGGER.error("Failed to handle sync payload — client data may be incomplete ({}/{} fields already parsed)", 12 - failedFields, 12, e);
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
                Screen currentScreen = mc.screen;
                if (currentScreen instanceof AdvancementScreen screen) {
                    screen.addToast(name);
                }
            }
        });
    }

    static void handleStatsSync(StatsSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                PlayerStats stats = DataStore.GSON.fromJson(payload.statsJson(), PlayerStats.class);
                if (stats != null) {
                    ClientDataStore.getInstance().setPlayerStats(stats);
                    LOGGER.debug("Stats sync received ({} bytes, hasData={})", payload.statsJson().length(), stats.hasAnyData());
                }
            }
            catch (Exception e) {
                LOGGER.warn("Failed to parse stats sync payload: {}", e.getMessage());
            }
        });
    }

    static void handleTimelineSync(TimelineSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                ClientDataStore.getInstance().setTimelineData(payload.dataJson());
                LOGGER.debug("Timeline sync received ({} bytes)", payload.dataJson().length());
                Screen currentScreen = mc.screen;
                if (currentScreen instanceof TimelineScreen timelineScreen) {
                    timelineScreen.updateTimelineData(payload.dataJson());
                }
            }
            catch (Exception e) {
                LOGGER.warn("Failed to process timeline sync: {}", e.getMessage());
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

    static void handlePhaseSync(PhaseSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ClientDataStore.getInstance().setPhaseData(payload);
                LOGGER.debug("Phase sync received ({} defs)", payload.defBriefs().size());
            } catch (Exception e) {
                LOGGER.warn("Failed to process phase sync: {}", e.getMessage());
            }
        });
    }

    static void handleSyncChunk(SyncChunkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            long transferId = payload.transferId();

            // Verify chunk data integrity via SHA-256 hash
            if (!payload.verifyHash()) {
                LOGGER.warn("Chunk hash mismatch, discarding: transferId={}, index={}/{}",
                        transferId, payload.chunkIndex(), payload.totalChunks());
                return;
            }

            long now = System.currentTimeMillis();
            if (now - lastAssemblyCleanup > ASSEMBLY_CLEANUP_INTERVAL_MS) {
                lastAssemblyCleanup = now;
                CHUNK_ASSEMBLIES.entrySet().removeIf(e -> e.getValue().isExpired());
            }
            if (payload.totalChunks() == 1) {
                String fullJson = new String(payload.data(), StandardCharsets.UTF_8);
                SyncPayload full = new SyncPayload(1, fullJson);
                NetworkHandlerClient.handleSync(full, context);
                return;
            }
            ChunkAssembly assembly = CHUNK_ASSEMBLIES.computeIfAbsent(transferId, id -> new ChunkAssembly(transferId, payload.totalChunks()));
            if (!assembly.addChunk(payload.chunkIndex(), payload.data())) {
                LOGGER.debug("Duplicate or out-of-range chunk ignored: transferId={}, index={}/{}", transferId, payload.chunkIndex(), payload.totalChunks());
                return;
            }
            LOGGER.debug("Chunk received: transferId={}, {}/{}", transferId, assembly.receivedChunks, assembly.totalChunks);
            if (assembly.isComplete()) {
                CHUNK_ASSEMBLIES.remove(transferId);
                try {
                    String fullJson = assembly.assemble();
                    LOGGER.info("Chunk assembly complete: transferId={}, totalKB={}", transferId, fullJson.length() / 1024);
                    SyncPayload full = new SyncPayload(1, fullJson);
                    NetworkHandlerClient.handleSync(full, context);
                }
                catch (Exception e2) {
                    LOGGER.error("Failed to assemble chunked sync payload: transferId={}", transferId, e2);
                }
            }
        });
    }

    private static boolean parseAdvancements(Gson gson, ClientDataStore store, JsonObject root) {
        return JsonParse.parseField(gson, root, "advancements",
                new TypeToken<Map<String, CustomAdvancement>>() {}.getType(),
                store::setAdvancements);
    }

    private static boolean parseDimensionLocks(Gson gson, ClientDataStore store, JsonObject root) {
        return JsonParse.parseField(gson, root, "dimensionLocks",
                new TypeToken<Map<String, DimensionLock>>() {}.getType(),
                store::setDimensionLocks);
    }

    private static boolean parseCompletions(Gson gson, ClientDataStore store, JsonObject root) {
        return JsonParse.parseField(gson, root, "completions",
                new TypeToken<Map<String, Boolean>>() {}.getType(),
                store::setCompletedAdvancements);
    }

    private static boolean parseProgress(Gson gson, ClientDataStore store, JsonObject root) {
        return JsonParse.parseField(gson, root, "progress",
                new TypeToken<Map<String, Integer>>() {}.getType(),
                store::setAdvancementProgress);
    }

    private static boolean parseCustomTabs(Gson gson, ClientDataStore store, JsonObject root) {
        return JsonParse.parseField(gson, root, "customTabs",
                new TypeToken<List<String>>() {}.getType(),
                store::setCustomTabs);
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
            LOGGER.warn("Failed to parse 'vanillaStates': {}", e.getMessage());
            return false;
        }
    }

    private static boolean parseVanillaAdvancements(Gson gson, ClientDataStore store, JsonObject root) {
        if (!root.has("vanillaAdvancements")) {
            return true;
        }
        try {
            Type t = new TypeToken<List<Map<String, String>>>(){}.getType();
            List<Map<String, String>> vanillaList = gson.fromJson(root.get("vanillaAdvancements"), t);
            if (vanillaList != null) {
                ArrayList<ClientDataStore.VanillaAdvEntry> entries = new ArrayList<>();
                for (Map<String, String> m : vanillaList) {
                    int x = 0;
                    int y = 0;
                    try {
                        x = Integer.parseInt(m.getOrDefault("x", "0"));
                    }
                    catch (Exception ignored) {
                        LOGGER.debug("Failed to parse vanilla advancement x coordinate", ignored);
                    }
                    try {
                        y = Integer.parseInt(m.getOrDefault("y", "0"));
                    }
                    catch (Exception ignored) {
                        LOGGER.debug("Failed to parse vanilla advancement y coordinate", ignored);
                    }
                    entries.add(new ClientDataStore.VanillaAdvEntry(
                            m.getOrDefault("id", ""),
                            m.getOrDefault("name", ""),
                            m.getOrDefault("desc", ""),
                            "true".equals(m.get("hidden")),
                            m.get("nameKey"),
                            m.get("descKey"),
                            m.get("rootTab"),
                            x, y,
                            m.get("icon")));
                }
                store.setVanillaAdvancements(entries);
            }
            return true;
        }
        catch (Exception e) {
            LOGGER.warn("Failed to parse 'vanillaAdvancements': {}", e.getMessage());
            return false;
        }
    }

    private static boolean parseVanillaMeta(Gson gson, ClientDataStore store, JsonObject root) {
        return JsonParse.parseField(gson, root, "vanillaMeta",
                new TypeToken<Map<String, VanillaAdvMeta>>() {}.getType(),
                store::setVanillaMeta);
    }

    private static boolean parseVanillaParentMap(Gson gson, ClientDataStore store, JsonObject root) {
        return JsonParse.parseField(gson, root, "vanillaParentMap",
                new TypeToken<Map<String, String>>() {}.getType(),
                store::setVanillaParentMap);
    }

    private static boolean parseTabOrder(Gson gson, ClientDataStore store, JsonObject root) {
        return JsonParse.parseField(gson, root, "tabOrder",
                new TypeToken<List<String>>() {}.getType(),
                store::setTabOrder);
    }

    private static boolean parsePending(ClientDataStore store, JsonObject root) {
        if (!root.has("pending") || !root.get("pending").isJsonArray()) {
            return true;
        }
        try {
            HashSet<String> pending = new HashSet<>();
            for (JsonElement e : root.getAsJsonArray("pending")) {
                if (!e.isJsonPrimitive()) continue;
                pending.add(e.getAsString());
            }
            store.setPendingAdvancements(pending);
            return true;
        }
        catch (Exception e) {
            LOGGER.warn("Failed to parse 'pending': {}", e.getMessage());
            return false;
        }
    }

    private static boolean parsePlayerStats(Gson gson, ClientDataStore store, JsonObject root) {
        return JsonParse.parseField(gson, root, "playerStats",
                PlayerStats.class, store::setPlayerStats);
    }

    private static Set<String> parseStringSet(Gson gson, JsonElement elem) {
        return JsonParse.parseStringSet(gson, elem);
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
