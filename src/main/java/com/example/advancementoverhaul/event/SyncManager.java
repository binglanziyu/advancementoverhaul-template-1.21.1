package com.example.advancementoverhaul.event;

import com.example.advancementoverhaul.ModInfo;
import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.ServerDataStore;
import com.example.advancementoverhaul.network.SyncPayload;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

public final class SyncManager {

    private SyncManager() {}

    // ═══════════════ Vanilla Cache ═══════════════

    /**
     * Cached result of vanilla advancement collection (BFS-ordered).
     * Invalidated when the Mixin re-runs on advancement reload.
     * Thread-safe via volatile visibility; worst case two concurrent
     * syncPlayer calls both rebuild (idempotent, not incorrect).
     */
    private static volatile VanillaCollection cachedVanillaData = null;
    private static volatile boolean vanillaCacheDirty = true;

    /**
     * Called by AdvancementManagerMixin when advancements are reloaded.
     * Signals that the next syncPlayer call must rebuild vanilla data.
     */
    public static void markVanillaCacheDirty() {
        vanillaCacheDirty = true;
        cachedVanillaData = null;
    }

    private static VanillaCollection getOrRebuildVanillaData(ServerPlayer player) {
        if (!vanillaCacheDirty && cachedVanillaData != null) {
            return cachedVanillaData;
        }
        VanillaCollection fresh = collectVanillaAdvancements(player);
        cachedVanillaData = fresh;
        vanillaCacheDirty = false;
        return fresh;
    }

    public static void syncPlayer(ServerPlayer player) {
        ServerDataStore store = ServerDataStore.getInstance();
        UUID uuid = player.getUUID();

        var vanillaData = getOrRebuildVanillaData(player);

        SyncPayload payload = SyncPayload.fromServer(
                store.getAdvancements(),
                store.getDimensionLocks(),
                store.getPlayerCompletions(uuid),
                store.getPlayerProgressSnapshot(uuid),
                store.getCustomTabs(),
                store.getDisabledVanilla(),
                store.getEnabledVanilla(),
                vanillaData.list(),
                store.getVanillaMetaMap(),
                vanillaData.parentMap(),
                store.getTabOrder(),
                store.getPendingAdvancements(uuid)
        );
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void syncAll(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) syncPlayer(p);
    }

    private record VanillaCollection(List<Map<String, String>> list, Map<String, String> parentMap) {}

    // ═══════════════ Vanilla collection pipeline ═══════════════

    /**
     * Collects all vanilla advancements for syncing to the client.
     * Pipeline: collect from manager → merge with cache → BFS traverse → return ordered collection.
     */
    private static VanillaCollection collectVanillaAdvancements(ServerPlayer player) {
        Map<String, AdvancementHolder> fromManager = collectFromManager(player);
        Map<String, JsonElement> cache = ServerDataStore.getInstance().getVanillaAdvRawCache();
        var merged = mergeVanillaEntries(fromManager, cache);
        return buildTraversedCollection(merged.entries, merged.parentMap);
    }

    /**
     * Collects all non-modded advancements from the server's AdvancementManager.
     */
    private static Map<String, AdvancementHolder> collectFromManager(ServerPlayer player) {
        Map<String, AdvancementHolder> result = new HashMap<>();
        for (var holder : player.server.getAdvancements().getAllAdvancements()) {
            String id = holder.id().toString();
            if (id.startsWith(ModInfo.MOD_ID + ":")) continue;
            result.put(id, holder);
        }
        return result;
    }

    private record MergedEntries(
            Map<String, Map<String, String>> entries,
            Map<String, String> parentMap) {}

    /**
     * Merges AdvancementManager entries (primary) with raw cache entries (fallback).
     * Manager entries always take precedence; cache only fills in missing entries.
     */
    private static MergedEntries mergeVanillaEntries(
            Map<String, AdvancementHolder> fromManager,
            Map<String, JsonElement> cache) {

        Map<String, Map<String, String>> allEntries = new LinkedHashMap<>();
        Map<String, String> parentMap = new HashMap<>();

        // Manager entries are primary
        for (var holder : fromManager.values()) {
            String id = holder.id().toString();
            allEntries.put(id, buildEntryFromHolder(holder));
            holder.value().parent().ifPresent(p -> parentMap.put(id, p.toString()));
        }

        // Cache fills in entries missing from manager
        if (cache != null && !cache.isEmpty()) {
            for (var cached : cache.entrySet()) {
                String id = cached.getKey();
                if (!allEntries.containsKey(id)) {
                    allEntries.put(id, buildEntryFromJson(id, cached.getValue()));
                }
                String parentId = extractParentId(cached.getValue());
                if (parentId != null && !parentMap.containsKey(id)) parentMap.put(id, parentId);
            }
        }

        return new MergedEntries(allEntries, parentMap);
    }

    /**
     * BFS traversal over the parent→child tree to assign depth and ordering.
     * Orphan nodes (not reachable from any root) are appended at the end.
     */
    private static VanillaCollection buildTraversedCollection(
            Map<String, Map<String, String>> allEntries,
            Map<String, String> parentMap) {

        List<Map<String, String>> result = new ArrayList<>();
        Map<String, String> parentResult = new HashMap<>();
        String builtinTab = DataStore.TAB_VANILLA;

        // Find roots: no parent, or parent not in the entry set
        List<String> roots = new ArrayList<>();
        for (String id : allEntries.keySet()) {
            String parentId = parentMap.get(id);
            if (parentId == null || !allEntries.containsKey(parentId)) roots.add(id);
        }
        Collections.sort(roots);

        // Build children adjacency list
        Map<String, List<String>> childrenOf = new HashMap<>();
        for (String childId : allEntries.keySet()) {
            String pid = parentMap.get(childId);
            if (pid != null && allEntries.containsKey(pid)) {
                childrenOf.computeIfAbsent(pid, k -> new ArrayList<>()).add(childId);
            }
        }
        // Sort children lists for stable BFS ordering
        for (List<String> children : childrenOf.values()) {
            Collections.sort(children);
        }

        // BFS from each root
        Set<String> visited = new HashSet<>();
        for (String rootId : roots) {
            Queue<Map.Entry<String, Integer>> queue = new ArrayDeque<>();
            queue.add(Map.entry(rootId, 0));
            visited.add(rootId);

            while (!queue.isEmpty()) {
                var current = queue.poll();
                String id = current.getKey();
                int depth = current.getValue();

                Map<String, String> entry = allEntries.get(id);
                if (entry != null) {
                    entry.put("rootTab", builtinTab);
                    entry.put("depth", String.valueOf(depth));
                    result.add(entry);
                }

                for (String child : childrenOf.getOrDefault(id, Collections.emptyList())) {
                    if (visited.add(child)) {
                        queue.add(Map.entry(child, depth + 1));
                        parentResult.put(child, id);
                    }
                }
            }
        }

        // Append orphan nodes (not reachable from any root)
        for (var entry : allEntries.entrySet()) {
            if (!visited.contains(entry.getKey())) {
                entry.getValue().put("depth", "1");
                entry.getValue().put("rootTab", builtinTab);
                result.add(entry.getValue());
            }
        }

        return new VanillaCollection(result, parentResult);
    }

    // ═══════════════ Entry builders ═══════════════

    private static Map<String, String> buildEntryFromHolder(AdvancementHolder holder) {
        Map<String, String> entry = new HashMap<>();
        entry.put("id", holder.id().toString());

        var disp = holder.value().display();
        Component titleComp = disp.map(d -> d.getTitle()).orElse(null);
        Component descComp = disp.map(d -> d.getDescription()).orElse(null);

        entry.put("name", titleComp != null ? titleComp.getString() : holder.id().getPath());
        entry.put("desc", descComp != null ? descComp.getString() : "");
        entry.put("hidden", "false");

        String nameKey = extractTranslationKey(titleComp);
        String descKey = extractTranslationKey(descComp);
        if (nameKey != null) entry.put("nameKey", nameKey);
        if (descKey != null) entry.put("descKey", descKey);

        disp.ifPresent(d -> {
            ItemStack iconStack = d.getIcon();
            if (!iconStack.isEmpty()) {
                ResourceLocation iconRl = BuiltInRegistries.ITEM.getKey(iconStack.getItem());
                if (iconRl != null) entry.put("icon", iconRl.toString());
            }
        });

        return entry;
    }

    private static Map<String, String> buildEntryFromJson(String id, JsonElement elem) {
        Map<String, String> entry = new HashMap<>();
        entry.put("id", id);
        if (elem == null || !elem.isJsonObject()) return entry;
        JsonObject obj = elem.getAsJsonObject();
        if (obj.has("display") && obj.get("display").isJsonObject()) {
            JsonObject display = obj.getAsJsonObject("display");
            extractTextComponent(display, "title", entry, "nameKey", "name");
            extractTextComponent(display, "description", entry, "descKey", "desc");
            if (display.has("icon") && display.get("icon").isJsonObject()) {
                JsonObject iconObj = display.getAsJsonObject("icon");
                if (iconObj.has("id")) entry.put("icon", iconObj.get("id").getAsString());
            }
        }
        if (!entry.containsKey("name")) entry.put("name", id);
        if (!entry.containsKey("desc")) entry.put("desc", "");
        entry.put("hidden", "false");
        return entry;
    }

    // ═══════════════ JSON / Component helpers ═══════════════

    private static void extractTextComponent(JsonObject parent, String field,
                                             Map<String, String> entry, String keyField, String textField) {
        if (!parent.has(field)) return;
        JsonElement elem = parent.get(field);
        if (elem.isJsonObject()) {
            JsonObject obj = elem.getAsJsonObject();
            if (obj.has("translate")) { String key = obj.get("translate").getAsString(); entry.put(keyField, key); entry.put(textField, key); }
            else if (obj.has("text")) { entry.put(textField, obj.get("text").getAsString()); }
        } else if (elem.isJsonPrimitive()) { entry.put(textField, elem.getAsString()); }
    }

    private static String extractParentId(JsonElement elem) {
        if (elem == null || !elem.isJsonObject()) return null;
        JsonObject obj = elem.getAsJsonObject();
        if (obj.has("parent") && obj.get("parent").isJsonPrimitive()) return obj.get("parent").getAsString();
        return null;
    }

    private static String extractTranslationKey(Component comp) {
        if (comp == null) return null;
        if (comp.getContents() instanceof TranslatableContents tc) return tc.getKey();
        return null;
    }
}