package com.example.advancementoverhaul.data;

import com.example.advancementoverhaul.Config;
import com.example.advancementoverhaul.ModInfo;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServerDataStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul");

    private static final String ADV_FILE          = "advancements.json";
    private static final String PLAYER_FILE       = "player_data.json";
    private static final String VANILLA_FILE      = "vanilla_states.json";
    private static final String VANILLA_RAW_FILE  = "vanilla_raw_cache.json";
    private static final String VANILLA_META_FILE = "vanilla_meta.json";
    private static final String TAB_ORDER_FILE    = "tab_order.json";

    private static final ServerDataStore INSTANCE = new ServerDataStore();

    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AdvancementOverhaul-Save");
        t.setDaemon(true);
        return t;
    });

    private MinecraftServer server;
    private volatile Path dataFolder;
    private volatile Path playerDataFolder;
    private long lastAutoSave = 0;
    // [S3] 改为 AtomicBoolean，消除 check-then-act 竞态
    private final AtomicBoolean playerDataDirty = new AtomicBoolean(false);
    private volatile boolean conditionIndexDirty = true;
    private volatile Map<DataStore.ConditionType, List<String>> condTypeIndex = new HashMap<>();
    private volatile Map<String, List<String>> condTargetIndex = new HashMap<>();
    // ═══ 数据存储 ═══
    // [S2] 成就写锁，保护 removeAdvancement 中的读-改-写操作
    private final Object advWriteLock = new Object();

    private final Map<String, DataStore.CustomAdvancement> advancements = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Boolean>> playerCompletions = new ConcurrentHashMap<>();
    // [E2] 进度结构：UUID → advId → (conditionIndex → progress)
    private final Map<UUID, Map<String, Map<Integer, Integer>>> playerProgress = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerPending = new ConcurrentHashMap<>();
    private final List<String> customTabs = Collections.synchronizedList(new ArrayList<>());
    private final List<String> tabOrder = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, DimensionLock> dimensionLocks = new ConcurrentHashMap<>();
    private final Set<String> disabledVanilla = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> enabledVanilla = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, DataStore.VanillaAdvMeta> vanillaMeta = new ConcurrentHashMap<>();
    private final Map<String, String> vanillaParentMap = new ConcurrentHashMap<>();
    private volatile Map<String, JsonElement> vanillaAdvRawCache = null;

    private ServerDataStore() {}

    public static ServerDataStore getInstance() { return INSTANCE; }

    /**
     * Submits a file write to the async save executor.
     * Falls back to synchronous write if the executor has been shut down.
     */
    private void asyncWrite(Path path, String content) {
        if (saveExecutor.isShutdown()) {
            try { Files.writeString(path, content); }
            catch (IOException e) { LOGGER.error("Failed to save {} synchronously", path.getFileName(), e); }
            return;
        }
        saveExecutor.submit(() -> {
            try { Files.writeString(path, content); }
            catch (IOException e) { LOGGER.error("Failed to save {}", path.getFileName(), e); }
        });
    }

    // ═══════════════ 初始化 ═══════════════
    /**
     * Flushes pending async writes and shuts down the save executor.
     * Called during server stopping.
     */
    public void shutdown() {
        savePlayerDataIfDirty();
        saveExecutor.shutdown();
        try {
            if (!saveExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                saveExecutor.shutdownNow();
                LOGGER.warn("Save executor did not terminate in 5s, forcing shutdown");
            }
        } catch (InterruptedException e) {
            saveExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void init(Path configDir) {
        if (this.dataFolder != null) return;
        Path folder = configDir.resolve("advancement_overhaul");
        try { Files.createDirectories(folder); }
        catch (IOException e) {
            LOGGER.error("Failed to create config data folder", e);
            return;
        }
        this.dataFolder = folder;
        loadAdvancements();
        loadVanillaStates();
        loadVanillaMeta();
        loadTabOrder();
    }

    public void setServer(MinecraftServer server) {
        if (this.dataFolder == null) {
            LOGGER.error("ServerDataStore.setServer() called before init() — data folder not set");
            return;
        }
        this.server = server;
        if (this.playerDataFolder == null) {
            this.playerDataFolder = server.getWorldPath(LevelResource.ROOT).resolve("advancement_overhaul");
            try { Files.createDirectories(playerDataFolder); }
            catch (IOException e) { LOGGER.error("Failed to create player data folder", e); }
            loadPlayerData();
        }
        if (vanillaAdvRawCache == null) cacheVanillaAdvancements();
    }

    public MinecraftServer getServer() { return server; }
    public Path getDataFolder() { return dataFolder; }

    public void tick() {
        long intervalMs = Config.PLAYER_DATA_SAVE_INTERVAL.get() * 50L;
        if (System.currentTimeMillis() - lastAutoSave > intervalMs) {
            savePlayerDataIfDirty();
            lastAutoSave = System.currentTimeMillis();
        }
    }

    // ═══════════════ 批量加载/保存 ═══════════════

    public void saveAll() {
        saveAdvancements();
        savePlayerData();
        saveVanillaStates();
        saveVanillaMeta();
        saveTabOrder();
    }

    public void forceReload() {
        loadAdvancements();
        loadVanillaStates();
        loadVanillaMeta();
        loadTabOrder();
        if (playerDataFolder != null) loadPlayerData();
        if (server != null && vanillaAdvRawCache == null) cacheVanillaAdvancements();
    }

    // ═══════════════ 成就数据 ═══════════════

    public Map<String, DataStore.CustomAdvancement> getAdvancements() { return advancements; }
    public DataStore.CustomAdvancement getAdvancement(String id) { return advancements.get(id); }

    // [S2] addAdvancement 也加写锁，与 removeAdvancement 保持一致
    public void addAdvancement(DataStore.CustomAdvancement adv) {
        synchronized (advWriteLock) {
            advancements.put(adv.getId(), adv);
            conditionIndexDirty = true;
        }
        saveAdvancements();
    }

    // [S2] 加写锁，保护前提条件列表的读-改-写
    public void removeAdvancement(String id) {
        synchronized (advWriteLock) {
            advancements.remove(id);
            conditionIndexDirty = true;
            for (var pd : playerCompletions.values()) pd.remove(id);
            for (var pd : playerProgress.values()) pd.remove(id);
            for (var pd : playerPending.values()) pd.remove(id);
            for (var adv : advancements.values()) {
                List<String> p = new ArrayList<>(adv.getPrerequisites());
                if (p.remove(id)) {
                    adv.setPrerequisites(p);
                }
            }
        }
        saveAdvancements();
    }
    /**
     * Same cleanup as removeAdvancement but does NOT call saveAdvancements().
     * Used by batch operations to avoid repeated file I/O.
     */
    public void removeAdvancementNoSave(String id) {
        synchronized (advWriteLock) {
            advancements.remove(id);
            conditionIndexDirty = true;
            for (var pd : playerCompletions.values()) pd.remove(id);
            for (var pd : playerProgress.values()) pd.remove(id);
            for (var pd : playerPending.values()) pd.remove(id);
            for (var adv : advancements.values()) {
                List<String> p = new ArrayList<>(adv.getPrerequisites());
                if (p.remove(id)) {
                    adv.setPrerequisites(p);
                }
            }
        }
    }


    public void saveAdvancements() {
        if (dataFolder == null) return;
        String json;
        synchronized (advWriteLock) {
            json = DataStore.GSON_PRETTY.toJson(advancements);
        }
        asyncWrite(dataFolder.resolve(ADV_FILE), json);
    }

    // [S4] copy-on-write：不再 clear()，避免中间出现空 Map
    private void loadAdvancements() {
        Path f = dataFolder == null ? null : dataFolder.resolve(ADV_FILE);
        if (f == null || !Files.exists(f)) return;
        try {
            Map<String, DataStore.CustomAdvancement> loaded = DataStore.mapFromJson(Files.readString(f));
            for (DataStore.CustomAdvancement adv : loaded.values()) {
                String tab = adv.getTab();
                if (tab != null && tab.startsWith("vanilla:")) adv.setTab(DataStore.TAB_VANILLA);
            }
            // 先 putAll 新数据，再移除已不存在的 key，避免中间出现空 Map
            advancements.putAll(loaded);
            advancements.keySet().removeIf(k -> !loaded.containsKey(k));
        } catch (Exception e) { LOGGER.error("Failed to load advancements", e); }
    }

    // ═══════════════ 玩家数据 ═══════════════

    public Map<String, Boolean> getPlayerCompletions(UUID uuid) {
        return playerCompletions.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
    }

    // [E2] 返回 per-condition 进度结构
    public Map<String, Map<Integer, Integer>> getPlayerProgress(UUID uuid) {
        return playerProgress.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
    }

    // [E2] 获取某条件的进度
    public int getConditionProgress(UUID uuid, String advId, int condIndex) {
        Map<Integer, Integer> condMap = getPlayerProgress(uuid).get(advId);
        return condMap != null ? condMap.getOrDefault(condIndex, 0) : 0;
    }
    public void resetConditionProgress(UUID uuid, String advId) {
        getPlayerProgress(uuid).remove(advId);
        playerDataDirty.set(true);
    }

    public Map<String, Integer> getPlayerProgressSnapshot(UUID uuid) {
        Map<String, Integer> snapshot = new HashMap<>();
        for (var entry : getPlayerProgress(uuid).entrySet()) {
            String advId = entry.getKey();
            DataStore.CustomAdvancement adv = getAdvancement(advId);
            if (adv == null || adv.getConditions().isEmpty()) continue;
            List<DataStore.AdvancementCondition> conditions = adv.getConditions();
            int total = 0, maxTotal = 0;
            for (int i = 0; i < conditions.size(); i++) {
                int count = conditions.get(i).getCount();
                maxTotal += count;
                total += Math.min(entry.getValue().getOrDefault(i, 0), count);
            }
            if (maxTotal > 0) snapshot.put(advId, (int)(100L * total / maxTotal));
        }
        return snapshot;
    }

    // [E2] 设置某条件的进度
    public void setConditionProgress(UUID uuid, String advId, int condIndex, int value) {
        getPlayerProgress(uuid)
                .computeIfAbsent(advId, k -> new ConcurrentHashMap<>())
                .put(condIndex, value);

        playerDataDirty.set(true);
    }

    // [E2] 获取某成就的总进度百分比（所有条件的加权平均）
    public int getProgress(UUID uuid, String advId) {
        DataStore.CustomAdvancement adv = getAdvancement(advId);
        if (adv == null || adv.getConditions().isEmpty()) return 0;
        Map<Integer, Integer> condMap = getPlayerProgress(uuid).get(advId);
        if (condMap == null) return 0;
        int total = 0, maxTotal = 0;
        List<DataStore.AdvancementCondition> conditions = adv.getConditions();
        for (int i = 0; i < conditions.size(); i++) {
            int count = conditions.get(i).getCount();
            maxTotal += count;
            total += Math.min(condMap.getOrDefault(i, 0), count);
        }
        return maxTotal > 0 ? (int)(100L * total / maxTotal) : 0;
    }

    public boolean isCompleted(UUID uuid, String advId) {
        return Boolean.TRUE.equals(getPlayerCompletions(uuid).get(advId));
    }

    public void setCompleted(UUID uuid, String advId, boolean v) {
        getPlayerCompletions(uuid).put(advId, v);
        if (v) {
            getPlayerProgress(uuid).remove(advId);
            getPendingAdvancements(uuid).remove(advId);
        }
        // [S3] 使用 AtomicBoolean
        playerDataDirty.set(true);
    }

    public Set<String> getPendingAdvancements(UUID uuid) {
        return playerPending.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
    }

    public boolean isPending(UUID uuid, String advId) {
        return getPendingAdvancements(uuid).contains(advId);
    }

    public void setPending(UUID uuid, String advId, boolean pending) {
        if (pending) {
            getPendingAdvancements(uuid).add(advId);
        } else {
            getPendingAdvancements(uuid).remove(advId);
        }
        playerDataDirty.set(true);
    }

    public void markPlayerDataDirty() { playerDataDirty.set(true); }

    // [S3] 使用 compareAndSet 消除竞态
    public void savePlayerDataIfDirty() {
        if (playerDataDirty.compareAndSet(true, false)) {
            savePlayerData();
        }
    }

    // [E2] 序列化嵌套的 per-condition 进度结构
    public void savePlayerData() {
        Path dir = playerDataFolder != null ? playerDataFolder : dataFolder;
        if (dir == null) return;
        try {
            JsonObject root = new JsonObject();

            // completions
            JsonObject comps = new JsonObject();
            for (var e : playerCompletions.entrySet()) {
                JsonObject pd = new JsonObject();
                for (var c : e.getValue().entrySet()) pd.addProperty(c.getKey(), c.getValue());
                comps.add(e.getKey().toString(), pd);
            }
            root.add("completions", comps);

            // progress — 嵌套结构：advId → { "0": 5, "1": 3 }
            JsonObject progs = new JsonObject();
            for (var e : playerProgress.entrySet()) {
                JsonObject pd = new JsonObject();
                for (var p : e.getValue().entrySet()) {
                    JsonObject condProg = new JsonObject();
                    for (var cp : p.getValue().entrySet()) {
                        condProg.addProperty(String.valueOf(cp.getKey()), cp.getValue());
                    }
                    pd.add(p.getKey(), condProg);
                }
                progs.add(e.getKey().toString(), pd);
            }
            root.add("progress", progs);

            // pending
            JsonObject pends = new JsonObject();
            for (var e : playerPending.entrySet()) {
                JsonArray arr = new JsonArray();
                for (String id : e.getValue()) arr.add(id);
                pends.add(e.getKey().toString(), arr);
            }
            root.add("pending", pends);

            String json = DataStore.GSON_PRETTY.toJson(root);
            asyncWrite(dir.resolve(PLAYER_FILE), json);
        } catch (Exception e) { LOGGER.error("Failed to serialize player data", e); }
    }

    // [E2] 反序列化嵌套结构 + 旧格式向后兼容
    private void loadPlayerData() {
        Path dir = playerDataFolder != null ? playerDataFolder : dataFolder;
        Path f = dir == null ? null : dir.resolve(PLAYER_FILE);
        if (f == null || !Files.exists(f)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            playerCompletions.clear();
            playerProgress.clear();
            playerPending.clear();

            if (root.has("completions") && root.get("completions").isJsonObject()) {
                for (var pe : root.getAsJsonObject("completions").entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(pe.getKey());
                        Map<String, Boolean> m = new ConcurrentHashMap<>();
                        if (pe.getValue().isJsonObject())
                            for (var ce : pe.getValue().getAsJsonObject().entrySet())
                                m.put(ce.getKey(), ce.getValue().getAsBoolean());
                        playerCompletions.put(uuid, m);
                    } catch (Exception ignored) {}
                }
            }

            if (root.has("progress") && root.get("progress").isJsonObject()) {
                for (var pe : root.getAsJsonObject("progress").entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(pe.getKey());
                        Map<String, Map<Integer, Integer>> m = new ConcurrentHashMap<>();
                        if (pe.getValue().isJsonObject()) {
                            for (var ce : pe.getValue().getAsJsonObject().entrySet()) {
                                Map<Integer, Integer> condMap = new ConcurrentHashMap<>();
                                if (ce.getValue().isJsonObject()) {
                                    // 新格式：{"0": 5, "1": 3}
                                    for (var cp : ce.getValue().getAsJsonObject().entrySet()) {
                                        try {
                                            condMap.put(Integer.parseInt(cp.getKey()), cp.getValue().getAsInt());
                                        } catch (Exception ignored) {}
                                    }
                                } else if (ce.getValue().isJsonPrimitive()) {
                                    // 旧格式兼容：单个整数 → 条件0的进度
                                    try {
                                        condMap.put(0, ce.getValue().getAsInt());
                                    } catch (Exception ignored) {}
                                }
                                m.put(ce.getKey(), condMap);
                            }
                        }
                        playerProgress.put(uuid, m);
                    } catch (Exception ignored) {}
                }
            }

            if (root.has("pending") && root.get("pending").isJsonObject()) {
                for (var pe : root.getAsJsonObject("pending").entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(pe.getKey());
                        Set<String> s = ConcurrentHashMap.newKeySet();
                        if (pe.getValue().isJsonArray())
                            for (JsonElement elem : pe.getValue().getAsJsonArray())
                                s.add(elem.getAsString());
                        playerPending.put(uuid, s);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) { LOGGER.error("Failed to load player data", e); }
    }

    // ═══════════════ 自定义标签页 ═══════════════

    public List<String> getCustomTabs() { return customTabs; }

    public void addCustomTab(String name) {
        synchronized (customTabs) {
            if (!customTabs.contains(name) && !DataStore.isBuiltinTab(name)) customTabs.add(name);
        }
    }

    public void removeCustomTab(String name) {
        synchronized (customTabs) { customTabs.remove(name); }
        synchronized (tabOrder) { tabOrder.remove(name); }
        saveTabOrder();
        for (var adv : advancements.values()) if (name.equals(adv.getTab())) adv.setTab(null);
        saveAdvancements();
    }

    // ═══════════════ 标签顺序 ═══════════════

    public List<String> getTabOrder() { return tabOrder; }

    public void setTabOrder(List<String> order) {
        synchronized (tabOrder) { tabOrder.clear(); if (order != null) tabOrder.addAll(order); }
        saveTabOrder();
    }

    private void saveTabOrder() {
        if (dataFolder == null) return;
        String json;
        synchronized (tabOrder) {
            json = DataStore.GSON_PRETTY.toJson(new ArrayList<>(tabOrder));
        }
        asyncWrite(dataFolder.resolve(TAB_ORDER_FILE), json);
    }

    private void loadTabOrder() {
        Path f = dataFolder == null ? null : dataFolder.resolve(TAB_ORDER_FILE);
        if (f == null || !Files.exists(f)) return;
        try {
            List<String> loaded = DataStore.GSON.fromJson(Files.readString(f), new TypeToken<List<String>>() {}.getType());
            synchronized (tabOrder) { tabOrder.clear(); if (loaded != null) tabOrder.addAll(loaded); }
        } catch (Exception e) { LOGGER.error("Failed to load tab order", e); }
    }

    // ═══════════════ 维度锁定 ═══════════════

    public Map<String, DimensionLock> getDimensionLocks() { return dimensionLocks; }
    public DimensionLock getDimensionLock(String dim) { return dimensionLocks.get(dim); }
    public void setDimensionLock(String dim, DimensionLock dl) { dimensionLocks.put(dim, dl); }

    // ═══════════════ 原版启用/禁用 ═══════════════

    public Set<String> getDisabledVanilla() { return disabledVanilla; }
    public Set<String> getEnabledVanilla() { return enabledVanilla; }

    public boolean isVanillaEnabled(String id) {
        if (enabledVanilla.contains(id)) return true;
        if (disabledVanilla.contains(id)) return false;
        try {
            return Config.VANILLA_DEFAULT_ENABLED.get();
        } catch (IllegalStateException e) {
            LOGGER.debug("Config not yet loaded when checking vanilla state for '{}'", id);
            return false;
        }
    }

    public void setVanillaEnabled(String id, boolean enabled) {
        if (enabled) { disabledVanilla.remove(id); enabledVanilla.add(id); }
        else         { enabledVanilla.remove(id); disabledVanilla.add(id); }
        saveVanillaStates();
    }

    public void setVanillaDisabledBatch(Set<String> ids) {
        disabledVanilla.addAll(ids);
        enabledVanilla.removeAll(ids);
        saveVanillaStates();
    }

    public void enableAllVanilla(Set<String> allIds) {
        disabledVanilla.clear();
        enabledVanilla.clear();
        enabledVanilla.addAll(allIds);
        saveVanillaStates();
    }

    private void saveVanillaStates() {
        if (dataFolder == null) return;
        JsonObject root = new JsonObject();
        root.add("disabled", DataStore.GSON.toJsonTree(new ArrayList<>(disabledVanilla)));
        root.add("enabled",  DataStore.GSON.toJsonTree(new ArrayList<>(enabledVanilla)));
        asyncWrite(dataFolder.resolve(VANILLA_FILE), DataStore.GSON_PRETTY.toJson(root));
    }

    private void loadVanillaStates() {
        Path f = dataFolder == null ? null : dataFolder.resolve(VANILLA_FILE);
        if (f == null || !Files.exists(f)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            disabledVanilla.clear();
            enabledVanilla.clear();
            if (root.has("disabled") && root.get("disabled").isJsonArray())
                for (JsonElement e : root.getAsJsonArray("disabled"))
                    if (e.isJsonPrimitive()) disabledVanilla.add(e.getAsString());
            if (root.has("enabled") && root.get("enabled").isJsonArray())
                for (JsonElement e : root.getAsJsonArray("enabled"))
                    if (e.isJsonPrimitive()) enabledVanilla.add(e.getAsString());
        } catch (Exception e) { LOGGER.error("Failed to load vanilla states", e); }
    }

    // ═══════════════ 原版元数据 ═══════════════

    public Map<String, DataStore.VanillaAdvMeta> getVanillaMetaMap() { return vanillaMeta; }
    public DataStore.VanillaAdvMeta getVanillaMeta(String id) { return vanillaMeta.get(id); }

    public void setVanillaMeta(String id, DataStore.VanillaAdvMeta meta) {
        vanillaMeta.put(id, meta);
        saveVanillaMeta();
    }

    public void saveVanillaMeta() {
        if (dataFolder == null) return;
        asyncWrite(dataFolder.resolve(VANILLA_META_FILE), DataStore.GSON_PRETTY.toJson(vanillaMeta));
    }

    private void loadVanillaMeta() {
        Path f = dataFolder == null ? null : dataFolder.resolve(VANILLA_META_FILE);
        if (f == null || !Files.exists(f)) return;
        try {
            Map<String, DataStore.VanillaAdvMeta> loaded = DataStore.GSON.fromJson(
                    Files.readString(f), new TypeToken<Map<String, DataStore.VanillaAdvMeta>>() {}.getType());
            vanillaMeta.clear();
            if (loaded != null) vanillaMeta.putAll(loaded);
        } catch (Exception e) { LOGGER.error("Failed to load vanilla meta", e); }
    }

    // ═══════════════ 原版原始缓存 ═══════════════

    public Map<String, JsonElement> getVanillaAdvRawCache() { return vanillaAdvRawCache; }
    public void setVanillaAdvRawCache(Map<String, JsonElement> cache) { this.vanillaAdvRawCache = cache; }
    public Map<String, String> getVanillaParentMap() { return vanillaParentMap; }
    public int getVanillaAdvRawCacheSize() {
        return vanillaAdvRawCache != null ? vanillaAdvRawCache.size() : 0;
    }

    public void cacheVanillaAdvancements() {
        if (server == null) return;
        try {
            Map<String, JsonElement> cache = new HashMap<>();
            vanillaParentMap.clear();
            for (var holder : server.getAdvancements().getAllAdvancements()) {
                String id = holder.id().toString();
                if (id.startsWith(ModInfo.MOD_ID + ":")) continue;
                JsonObject obj = new JsonObject();
                obj.addProperty("id", id);
                holder.value().parent().ifPresent(p -> {
                    obj.addProperty("parent", p.toString());
                    vanillaParentMap.put(id, p.toString());
                });
                holder.value().display().ifPresent(disp -> {
                    JsonObject display = new JsonObject();
                    display.add("title", compToJson(disp.getTitle()));
                    display.add("description", compToJson(disp.getDescription()));
                    ItemStack icon = disp.getIcon();
                    if (!icon.isEmpty()) {
                        JsonObject iconObj = new JsonObject();
                        var rl = BuiltInRegistries.ITEM.getKey(icon.getItem());
                        if (rl != null) iconObj.addProperty("id", rl.toString());
                        display.add("icon", iconObj);
                    }
                    obj.add("display", display);
                });
                cache.put(id, obj);
            }
            vanillaAdvRawCache = cache;
            if (dataFolder != null) asyncWrite(dataFolder.resolve(VANILLA_RAW_FILE), DataStore.GSON_PRETTY.toJson(cache));
        } catch (Exception e) { LOGGER.error("Failed to cache vanilla advancements", e); }
    }

    private static JsonElement compToJson(Component comp) {
        if (comp == null) return new JsonPrimitive("");
        if (comp.getContents() instanceof TranslatableContents tc) {
            JsonObject o = new JsonObject(); o.addProperty("translate", tc.getKey()); return o;
        }
        return new JsonPrimitive(comp.getString());
    }

    // ═══════════════ 条件查询 ═══════════════

    public List<String> getAdvIdsByConditionType(DataStore.ConditionType type) {
        if (conditionIndexDirty) rebuildConditionIndex();
        return Collections.unmodifiableList(condTypeIndex.getOrDefault(type, List.of()));
    }

    public List<String> getAdvIdsByCondition(DataStore.ConditionType type, String targetId) {
        if (conditionIndexDirty) rebuildConditionIndex();
        String key = type.name() + ":" + (targetId != null ? targetId : "");
        return Collections.unmodifiableList(condTargetIndex.getOrDefault(key, List.of()));
    }

    private synchronized void rebuildConditionIndex() {
        if (!conditionIndexDirty) return;

        Map<DataStore.ConditionType, List<String>> typeMap = new HashMap<>();
        Map<String, List<String>> targetMap = new HashMap<>();
        Map<DataStore.ConditionType, Set<String>> wildcardMap = new EnumMap<>(DataStore.ConditionType.class);

        for (var adv : advancements.values()) {
            Set<DataStore.ConditionType> seen = EnumSet.noneOf(DataStore.ConditionType.class);
            for (var cond : adv.getConditions()) {
                if (seen.add(cond.getType()))
                    typeMap.computeIfAbsent(cond.getType(), k -> new ArrayList<>()).add(adv.getId());

                if (cond.getTargetId() == null || cond.getTargetId().isEmpty()) {
                    wildcardMap.computeIfAbsent(cond.getType(), k -> new LinkedHashSet<>()).add(adv.getId());
                } else {
                    String key = cond.getType().name() + ":" + cond.getTargetId();
                    targetMap.computeIfAbsent(key, k -> new ArrayList<>()).add(adv.getId());
                }
            }
        }

        for (var entry : wildcardMap.entrySet()) {
            DataStore.ConditionType type = entry.getKey();
            Set<String> wildcardAdvIds = entry.getValue();
            String prefix = type.name() + ":";
            for (var targetEntry : targetMap.entrySet()) {
                if (targetEntry.getKey().startsWith(prefix)) {
                    List<String> list = targetEntry.getValue();
                    Set<String> existing = new HashSet<>(list);
                    for (String wid : wildcardAdvIds) {
                        if (existing.add(wid)) {
                            list.add(wid);
                        }
                    }
                }
            }
        }

        condTypeIndex = typeMap;
        condTargetIndex = targetMap;
        conditionIndexDirty = false;
    }

    // ═══════════════ 导入/导出 ═══════════════

    public JsonObject exportAll() {
        JsonObject root = new JsonObject();
        root.add("advancements", DataStore.GSON.toJsonTree(advancements));
        root.add("customTabs", DataStore.GSON.toJsonTree(new ArrayList<>(customTabs)));
        root.add("dimensionLocks", DataStore.GSON.toJsonTree(dimensionLocks));
        root.add("vanillaMeta", DataStore.GSON.toJsonTree(vanillaMeta));
        root.add("tabOrder", DataStore.GSON.toJsonTree(new ArrayList<>(tabOrder)));
        return root;
    }

    // 在 ServerDataStore.java 中添加
    public void removeVanillaMeta(String id) {
        vanillaMeta.remove(id);
        saveVanillaMeta();
    }


    public void importAll(JsonObject data) {
        if (data.has("advancements")) {
            Map<String, DataStore.CustomAdvancement> advs = DataStore.GSON.fromJson(
                    data.get("advancements"),
                    new TypeToken<Map<String, DataStore.CustomAdvancement>>() {}.getType());
            if (advs != null) {
                for (var adv : advs.values()) {
                    String tab = adv.getTab();
                    if (tab != null && tab.startsWith("vanilla:")) adv.setTab(DataStore.TAB_VANILLA);
                }
                advancements.clear();
                advancements.putAll(advs);
            }
        }
        if (data.has("customTabs")) {
            List<String> tabs = DataStore.GSON.fromJson(data.get("customTabs"), new TypeToken<List<String>>() {}.getType());
            if (tabs != null) { synchronized (customTabs) { customTabs.clear(); customTabs.addAll(tabs); } }
        }
        if (data.has("dimensionLocks")) {
            Map<String, DimensionLock> locks = DataStore.GSON.fromJson(data.get("dimensionLocks"), new TypeToken<Map<String, DimensionLock>>() {}.getType());
            if (locks != null) {
                dimensionLocks.clear();
                dimensionLocks.putAll(locks);
            }
        }
        if (data.has("vanillaMeta")) {
            Map<String, DataStore.VanillaAdvMeta> meta = DataStore.GSON.fromJson(data.get("vanillaMeta"), new TypeToken<Map<String, DataStore.VanillaAdvMeta>>() {}.getType());
            if (meta != null) {
                vanillaMeta.clear();
                vanillaMeta.putAll(meta);
            }
        }
        if (data.has("tabOrder")) {
            List<String> order = DataStore.GSON.fromJson(data.get("tabOrder"), new TypeToken<List<String>>() {}.getType());
            if (order != null) { synchronized (tabOrder) { tabOrder.clear(); tabOrder.addAll(order); } }
        }
        saveAll();
    }
}