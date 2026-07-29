package com.example.advancementoverhaul.network;

import com.example.advancementoverhaul.ModInfo;
import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.PlayerStats;
import com.example.advancementoverhaul.data.PlayerStatsStore;
import com.example.advancementoverhaul.data.model.VanillaAdvMeta;
import com.example.advancementoverhaul.data.ServerDataStore;
import com.example.advancementoverhaul.network.payload.SyncChunkPayload;
import com.example.advancementoverhaul.network.payload.SyncPayload;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 客户端数据同步管理器。
 *
 * <h2>核心职责</h2>
 * 在服务端事件发生时，将以下数据序列化并发送给客户端：
 * <ul>
 *   <li>所有自定义进度的定义和元数据</li>
 *   <li>玩家的完成状态、进度和 pending 标记</li>
 *   <li>原版进度的状态、位置和父子关系</li>
 *   <li>维度锁配置和标签页顺序</li>
 * </ul>
 *
 * <h2>原版进度缓存</h2>
 * 原版进度数据经过 BFS 遍历、缓存合并后构建为有序集合。
 * 缓存由一个 {@link AtomicBoolean} 脏标记保护，确保多玩家登录时的线程安全。
 * 缓存通过 {@link com.example.advancementoverhaul.mixin.AdvancementManagerMixin} 在 reload 时标记为脏。
 */
public final class SyncManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/SyncManager");

    /** 超过此字节数（UTF-8）则启用分块传输 */
    private static final int CHUNK_THRESHOLD = SyncChunkPayload.CHUNK_SIZE;

    private SyncManager() {}

    /** 原版进度缓存数据（volatile 保证可见性） */
    private static volatile VanillaCollection cachedVanillaData = null;

    /** 脏标记，使用 AtomicBoolean 保证 check-then-rebuild 的原子性 */
    private static final AtomicBoolean vanillaCacheDirty = new AtomicBoolean(true);

    // ═══════════════ 缓存生命周期 ═══════════════

    /**
     * 由 {@link com.example.advancementoverhaul.mixin.AdvancementManagerMixin}
     * 在进度数据 reload 时调用，标记缓存为脏。
     */
    public static void markVanillaCacheDirty() {
        vanillaCacheDirty.set(true);
        cachedVanillaData = null;
    }

    /**
     * 获取或重建原版进度缓存。
     */
    private static synchronized VanillaCollection getOrRebuildVanillaData(ServerPlayer player) {
        if (!vanillaCacheDirty.get() && cachedVanillaData != null) {
            return cachedVanillaData;
        }

        vanillaCacheDirty.set(false);
        VanillaCollection fresh = collectVanillaAdvancements(player);
        cachedVanillaData = fresh;
        return fresh;
    }

    // ═══════════════ 同步入口 ═══════════════

    /**
     * 向单个玩家发送全量同步数据包。
     * 在玩家登录、reload 和数据变更时调用。
     * <p>
     * 若序列化后的 JSON 超过 {@link #CHUNK_THRESHOLD} 字节，自动拆分为多个
     * {@link SyncChunkPayload} 分块发送，客户端自动重组。
     */
    public static void syncPlayer(ServerPlayer player) {
        ServerDataStore store = ServerDataStore.getInstance();
        UUID uuid = player.getUUID();

        VanillaCollection vanillaData = getOrRebuildVanillaData(player);
        PlayerStats playerStats = PlayerStatsStore.getInstance().getOrCreate(uuid);

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
                store.getPendingAdvancements(uuid),
                playerStats
        );

        String json = payload.data();
        if (json == null || json.isEmpty()) return;

        int byteSize = json.getBytes(StandardCharsets.UTF_8).length;
        if (byteSize > CHUNK_THRESHOLD) {
            // 分块传输
            long transferId = ThreadLocalRandom.current().nextLong();
            SyncChunkPayload[] chunks = SyncChunkPayload.split(transferId, json);
            LOGGER.info("Sending chunked sync to {}: {} chunks, total {} KB",
                    player.getName().getString(), chunks.length, byteSize / 1024);
            for (SyncChunkPayload chunk : chunks) {
                PacketDistributor.sendToPlayer(player, chunk);
            }
        } else {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /**
     * 向所有在线玩家广播全量同步。
     */
    public static void syncAll(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            syncPlayer(p);
        }
    }

    // ═══════════════ 内部数据结构 ═══════════════

    private record VanillaCollection(
            List<Map<String, String>> list,
            Map<String, String> parentMap
    ) {}

    private record MergedEntries(
            Map<String, Map<String, String>> entries,
            Map<String, String> parentMap
    ) {}

    // ═══════════════ 原版进度收集管线 ═══════════════

    private static VanillaCollection collectVanillaAdvancements(ServerPlayer player) {
        Map<String, AdvancementHolder> fromManager = collectFromManager(player);
        Map<String, JsonElement> cache = ServerDataStore.getInstance().getVanillaAdvRawCache();
        Map<String, VanillaAdvMeta> vanillaMeta = ServerDataStore.getInstance().getVanillaMetaMap();
        MergedEntries merged = mergeVanillaEntries(fromManager, cache);
        return buildTraversedCollection(merged.entries, merged.parentMap, vanillaMeta);
    }

    private static Map<String, AdvancementHolder> collectFromManager(ServerPlayer player) {
        Map<String, AdvancementHolder> result = new HashMap<>();
        for (AdvancementHolder holder : player.server.getAdvancements().getAllAdvancements()) {
            String id = holder.id().toString();
            if (id.startsWith(ModInfo.MOD_ID + ":")) continue;
            result.put(id, holder);
        }
        return result;
    }

    private static MergedEntries mergeVanillaEntries(
            Map<String, AdvancementHolder> fromManager,
            Map<String, JsonElement> cache) {

        Map<String, Map<String, String>> allEntries = new LinkedHashMap<>();
        Map<String, String> parentMap = new HashMap<>();

        for (AdvancementHolder holder : fromManager.values()) {
            String id = holder.id().toString();
            allEntries.put(id, buildEntryFromHolder(holder));
            holder.value().parent().ifPresent(p -> parentMap.put(id, p.toString()));
        }

        if (cache != null && !cache.isEmpty()) {
            for (Map.Entry<String, JsonElement> cached : cache.entrySet()) {
                String id = cached.getKey();
                if (!allEntries.containsKey(id)) {
                    allEntries.put(id, buildEntryFromJson(id, cached.getValue()));
                }
                String parentId = extractParentId(cached.getValue());
                if (parentId != null && !parentMap.containsKey(id)) {
                    parentMap.put(id, parentId);
                }
            }
        }

        return new MergedEntries(allEntries, parentMap);
    }

    private static VanillaCollection buildTraversedCollection(
            Map<String, Map<String, String>> allEntries,
            Map<String, String> parentMap,
            Map<String, VanillaAdvMeta> vanillaMeta) {

        List<Map<String, String>> result = new ArrayList<>();
        Map<String, String> parentResult = new HashMap<>();
        String fallbackTab = DataStore.TAB_VANILLA;

        /** 从元数据中获取 ID 所属的标签页，无元数据时使用默认值 */
        java.util.function.Function<String, String> getTab = id -> {
            VanillaAdvMeta meta = vanillaMeta != null ? vanillaMeta.get(id) : null;
            return (meta != null && meta.getTab() != null && !meta.getTab().isEmpty())
                    ? meta.getTab() : fallbackTab;
        };

        List<String> roots = new ArrayList<>();
        for (String id : allEntries.keySet()) {
            String parentId = parentMap.get(id);
            if (parentId == null || !allEntries.containsKey(parentId)) {
                roots.add(id);
            }
        }
        Collections.sort(roots);

        Map<String, List<String>> childrenOf = new HashMap<>();
        for (String childId : allEntries.keySet()) {
            String pid = parentMap.get(childId);
            if (pid != null && allEntries.containsKey(pid)) {
                childrenOf.computeIfAbsent(pid, k -> new ArrayList<>()).add(childId);
            }
        }
        childrenOf.values().forEach(Collections::sort);

        Set<String> visited = new HashSet<>();
        for (String rootId : roots) {
            Queue<Map.Entry<String, Integer>> queue = new ArrayDeque<>();
            queue.add(Map.entry(rootId, 0));
            visited.add(rootId);

            while (!queue.isEmpty()) {
                Map.Entry<String, Integer> current = queue.poll();
                String id = current.getKey();
                int depth = current.getValue();

                Map<String, String> entry = allEntries.get(id);
                if (entry != null) {
                    entry.put("rootTab", getTab.apply(id));
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

        for (Map.Entry<String, Map<String, String>> entry : allEntries.entrySet()) {
            if (!visited.contains(entry.getKey())) {
                entry.getValue().put("depth", "1");
                entry.getValue().put("rootTab", getTab.apply(entry.getKey()));
                result.add(entry.getValue());
            }
        }

        return new VanillaCollection(result, parentResult);
    }

    // ═══════════════ 条目构建器 ═══════════════

    private static Map<String, String> buildEntryFromHolder(AdvancementHolder holder) {
        Map<String, String> entry = new HashMap<>();
        entry.put("id", holder.id().toString());

        var disp = holder.value().display();
        Component titleComp = disp.map(DisplayInfo::getTitle).orElse(null);
        Component descComp = disp.map(DisplayInfo::getDescription).orElse(null);

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

    private static void extractTextComponent(JsonObject parent, String field,
                                             Map<String, String> entry,
                                             String keyField, String textField) {
        if (!parent.has(field)) return;
        JsonElement elem = parent.get(field);
        if (elem.isJsonObject()) {
            JsonObject obj = elem.getAsJsonObject();
            if (obj.has("translate")) {
                String key = obj.get("translate").getAsString();
                entry.put(keyField, key);
                entry.put(textField, key);
            } else if (obj.has("text")) {
                entry.put(textField, obj.get("text").getAsString());
            }
        } else if (elem.isJsonPrimitive()) {
            entry.put(textField, elem.getAsString());
        }
    }

    private static String extractParentId(JsonElement elem) {
        if (elem == null || !elem.isJsonObject()) return null;
        JsonObject obj = elem.getAsJsonObject();
        if (obj.has("parent") && obj.get("parent").isJsonPrimitive()) {
            return obj.get("parent").getAsString();
        }
        return null;
    }

    private static String extractTranslationKey(Component comp) {
        if (comp == null) return null;
        if (comp.getContents() instanceof TranslatableContents tc) return tc.getKey();
        return null;
    }
}
