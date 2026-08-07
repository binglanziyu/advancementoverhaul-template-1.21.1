package com.dreamer.ao.data;

import com.dreamer.ao.Config;
import com.dreamer.ao.LangKeys;
import com.dreamer.ao.data.DataStore.ConditionType;
import com.dreamer.ao.data.model.CustomAdvancement;
import com.dreamer.ao.data.model.VanillaAdvMeta;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 服务端数据存储协调器（单例）。
 * <p>
 * 协调五个内部模块并委托文件 I/O 给 {@link DataStoreIO}：
 * <ul>
 *   <li>{@link AdvancementStore} — 成就 CRUD、条件索引</li>
 *   <li>{@link PlayerDataStore} — 玩家完成状态、进度、pending 标记</li>
 *   <li>{@link TabStore} — 自定义标签页、排序</li>
 *   <li>{@link VanillaStateStore} — 原版进度启用/禁用、元数据、缓存</li>
 *   <li>{@link DataStoreIO} — 文件 I/O、异步持久化、目录管理</li>
 * </ul>
 */
public class ServerDataStore implements ImportExportHandler.ImportContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerDataStore.class);

    // ═══════════════ 单例 ═══════════════
    private static final ServerDataStore INSTANCE = new ServerDataStore();
    public static ServerDataStore getInstance() { return INSTANCE; }
    private ServerDataStore() {}

    // ═══════════════ 内部模块 ═══════════════
    private final AdvancementStore   advStore    = new AdvancementStore();
    private final PlayerDataStore    playerStore = new PlayerDataStore();
    private final TabStore           tabStore    = new TabStore();
    private final VanillaStateStore  vanillaStore = new VanillaStateStore();
    private final DataStoreIO        io          = new DataStoreIO();

    // ═══════════════ 维度锁 ═══════════════
    private final Map<String, DimensionLock> dimensionLocks = new ConcurrentHashMap<>();

    // ═══════════════ 运行时状态 ═══════════════
    private volatile MinecraftServer server;
    private volatile Path dataFolder;
    private volatile boolean initFailed;
    private int lastAutoSaveTick;

    // ══════════════════════════════════════════════════════════
    // 生命周期
    // ══════════════════════════════════════════════════════════

    public synchronized void init(Path configDir) {
        if (this.dataFolder != null) return;
        this.dataFolder = io.initDir(configDir);
        if (this.dataFolder == null) {
            this.initFailed = true;
            LOGGER.error("DataStore initialization failed: could not create data directory in {}", configDir);
            throw new RuntimeException("AdvancementOverhaul data directory initialization failed in " + configDir);
        }
        try {
            io.initialLoad(advStore, vanillaStore, tabStore);
        } catch (Exception e) {
            this.initFailed = true;
            LOGGER.error("DataStore initialization failed: error loading initial data", e);
            throw new RuntimeException("AdvancementOverhaul data initialization failed", e);
        }
    }

    public synchronized void setServer(MinecraftServer server) {
        if (this.dataFolder == null) {
            LOGGER.error("setServer() called before init() — data folder not set");
            return;
        }
        this.server = server;

        if (this.initFailed) {
            LOGGER.error("DataStore is in failed state (initFailed=true) — some features will be unavailable");
            // 广播管理员警告
            for (var player : server.getPlayerList().getPlayers()) {
                if (player.hasPermissions(2)) {
                    player.sendSystemMessage(Component.translatable(LangKeys.MSG_DATASTORE_INIT_FAILED));
                }
            }
        }

        io.initPlayerDir(server);

        Path pd = io.getPlayerDataFolder();
        if (pd != null) {
            playerStore.migrateFromLegacy(pd);
            playerStore.loadFromDir(pd);
        }
        if (vanillaStore.getRawCache() == null) {
            vanillaStore.cacheFromServer(server);
            processEnabledMods();
        }
    }

    public void tick() {
        if (server == null) return;
        int interval = Config.PLAYER_DATA_SAVE_INTERVAL.get();
        if (server.getTickCount() - lastAutoSaveTick >= interval) {
            savePlayerDataIfDirty();
            lastAutoSaveTick = server.getTickCount();
        }
    }

    public void shutdown() {
        savePlayerDataIfDirty();
        io.shutdown();
    }

    // Getters
    public MinecraftServer getServer() { return server; }
    public Path getDataFolder() { return dataFolder; }

    /** 暴露子模块引用，允许外部精细操作。 */

    public AdvancementStore getAdvancementStore() { return advStore; }

    public PlayerDataStore getPlayerStore() { return playerStore; }

    public TabStore getTabStore() { return tabStore; }

    public VanillaStateStore getVanillaStore() { return vanillaStore; }

    // ═══════════════ 成就变更回调 ═══════════════
    public static void setOnAdvancementChanged(Consumer<String> callback) {
        INSTANCE.advStore.setOnChange(callback);
    }

    // ══════════════════════════════════════════════════════════
    // [区域 1] 成就数据 CRUD
    // ══════════════════════════════════════════════════════════

    public Map<String, CustomAdvancement> getAdvancements() { return advStore.getAll(); }
    public CustomAdvancement getAdvancement(String id) { return advStore.get(id); }

    public void addAdvancement(CustomAdvancement adv) { addAdvancement(adv, true); }

    public void addAdvancement(CustomAdvancement adv, boolean triggerCallback) {
        advStore.add(adv, triggerCallback, this::saveAdvancements);
    }

    public void removeAdvancement(String id) {
        advStore.remove(id, this::saveAdvancements,
                playerStore::removeAdvancementFromAll,
                this::cleanDimensionLockReferences);
    }

    public void removeAdvancementNoSave(String id) {
        advStore.removeNoSave(id,
                playerStore::removeAdvancementFromAll,
                this::cleanDimensionLockReferences);
    }

    private void cleanDimensionLockReferences(String advId) {
        for (var entry : dimensionLocks.entrySet()) {
            DimensionLock lock = entry.getValue();
            if (advId.equals(lock.getUnlockAdvancementId())) {
                lock.setUnlockAdvancementId(null);
                lock.setLocked(false);
                LOGGER.warn("Dimension lock '{}' referenced deleted advancement '{}', lock disabled",
                        entry.getKey(), advId);
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    // [区域 2] 玩家数据
    // ══════════════════════════════════════════════════════════

    public Map<String, Boolean> getPlayerCompletions(UUID uuid) { return playerStore.getCompletions(uuid); }
    public boolean isCompleted(UUID uuid, String advId) { return playerStore.isCompleted(uuid, advId); }
    public void setCompleted(UUID uuid, String advId, boolean v) { playerStore.setCompleted(uuid, advId, v); }

    public Map<String, Map<Integer, Integer>> getPlayerProgress(UUID uuid) { return playerStore.getProgress(uuid); }
    public int getConditionProgress(UUID uuid, String advId, int condIndex) { return playerStore.getConditionProgress(uuid, advId, condIndex); }
    public void setConditionProgress(UUID uuid, String advId, int condIndex, int value) { playerStore.setConditionProgress(uuid, advId, condIndex, value); }
    public void resetConditionProgress(UUID uuid, String advId) { playerStore.resetConditionProgress(uuid, advId); }

    public int getProgress(UUID uuid, String advId) {
        return playerStore.getProgressPercent(uuid, advId, getAdvancement(advId));
    }

    public Map<String, Integer> getPlayerProgressSnapshot(UUID uuid) {
        return playerStore.getProgressSnapshot(uuid, advStore.getAll());
    }

    public Set<String> getPendingAdvancements(UUID uuid) { return playerStore.getPending(uuid); }
    public boolean isPending(UUID uuid, String advId) { return playerStore.isPending(uuid, advId); }
    public void setPending(UUID uuid, String advId, boolean pending) { playerStore.setPending(uuid, advId, pending); }

    public void savePlayerDataIfDirty() { io.savePlayerDataIfDirty(playerStore); }
    public void savePlayerData() { io.savePlayerData(playerStore); }

    // ══════════════════════════════════════════════════════════
    // [区域 3] 标签页
    // ══════════════════════════════════════════════════════════

    public List<String> getCustomTabs() { return tabStore.getCustomTabs(); }
    public void addCustomTab(String name) { tabStore.addCustomTab(name); }

    public void removeCustomTab(String name) {
        tabStore.removeCustomTab(name, advStore.getAll(),
                this::saveTabOrder, this::saveAdvancements);
    }

    public List<String> getTabOrder() { return tabStore.getTabOrder(); }
    public void setTabOrder(List<String> order) {
        tabStore.setTabOrder(order);
        saveTabOrder();
    }

    // ══════════════════════════════════════════════════════════
    // [区域 4] 维度锁
    // ══════════════════════════════════════════════════════════

    public Map<String, DimensionLock> getDimensionLocks() { return dimensionLocks; }
    public DimensionLock getDimensionLock(String dim) { return dimensionLocks.get(dim); }
    public void setDimensionLock(String dim, DimensionLock dl) { dimensionLocks.put(dim, dl); }

    // ══════════════════════════════════════════════════════════
    // [区域 5] 原版进度状态
    // ══════════════════════════════════════════════════════════

    public Set<String> getDisabledVanilla() { return vanillaStore.getDisabled(); }
    public Set<String> getEnabledVanilla() { return vanillaStore.getEnabled(); }
    public boolean isVanillaEnabled(String id) { return vanillaStore.isEnabled(id); }

    public void setVanillaEnabled(String id, boolean enabled) {
        vanillaStore.setEnabled(id, enabled);
        saveVanillaStates();
    }

    public void setVanillaDisabledBatch(Set<String> ids) {
        vanillaStore.setDisabledBatch(ids);
        saveVanillaStates();
    }

    public void enableAllVanilla(Set<String> allIds) {
        vanillaStore.enableAll(allIds);
        saveVanillaStates();
    }

    public void autoAssignVanillaTabs() {
        Map<String, String> parentMap = vanillaStore.getParentMap();
        Map<String, VanillaAdvMeta> metaMap = vanillaStore.getMetaMap();
        Set<String> enabled = vanillaStore.getEnabled();
        Map<String, JsonElement> rawCache = vanillaStore.getRawCache();
        if (enabled.isEmpty()) return;

        Map<String, List<String>> rootChildren = new LinkedHashMap<>();
        for (String id : enabled) {
            String root = findRoot(id, parentMap, enabled);
            rootChildren.computeIfAbsent(root, k -> new ArrayList<>()).add(id);
        }

        int assigned = 0;
        for (var entry : rootChildren.entrySet()) {
            String rootId = entry.getKey();
            String tabName = getAdvDisplayName(rootId, rawCache);
            if (tabName == null || tabName.isEmpty()) tabName = rootId.replace(':', '_');

            boolean hasNew = false;
            for (String childId : entry.getValue()) {
                VanillaAdvMeta meta = metaMap.get(childId);
                if (meta != null && meta.getTab() != null && !meta.getTab().isEmpty()) continue;
                hasNew = true;
                if (meta == null) meta = new VanillaAdvMeta();
                meta.setTab(tabName);
                metaMap.put(childId, meta);
                assigned++;
            }
            if (hasNew) tabStore.addCustomTab(tabName);
        }

        if (assigned > 0) {
            saveVanillaMeta();
            saveTabOrder();
            LOGGER.info("Auto-assigned {} vanilla advancements to {} tabs", assigned, rootChildren.size());
        }
    }

    private static String findRoot(String id, Map<String, String> parentMap, Set<String> enabled) {
        String current = id;
        String root = id;
        Set<String> visited = new HashSet<>();
        while (true) {
            String parent = parentMap.get(current);
            if (parent == null || !enabled.contains(parent)) break;
            if (!visited.add(current)) break;
            current = parent;
            root = current;
        }
        return root;
    }

    private static String getAdvDisplayName(String id, Map<String, JsonElement> rawCache) {
        if (rawCache == null) return null;
        JsonElement elem = rawCache.get(id);
        if (elem == null || !elem.isJsonObject()) return null;
        JsonObject obj = elem.getAsJsonObject();
        if (!obj.has("display") || !obj.get("display").isJsonObject()) return null;
        JsonObject display = obj.getAsJsonObject("display");
        if (!display.has("title")) return null;
        JsonElement title = display.get("title");
        if (title.isJsonObject()) {
            JsonObject titleObj = title.getAsJsonObject();
            if (titleObj.has("translate")) {
                String key = titleObj.get("translate").getAsString();
                String[] parts = key.split("\\.");
                return parts[parts.length - 1];
            }
            if (titleObj.has("text")) return titleObj.get("text").getAsString();
        }
        if (title.isJsonPrimitive()) return title.getAsString();
        return null;
    }

    public void processEnabledMods() {
        List<? extends String> mods = Config.ENABLED_MODS.get();
        if (mods == null || mods.isEmpty()) return;
        Map<String, JsonElement> rawCache = vanillaStore.getRawCache();
        if (rawCache == null) return;

        Set<String> disabled = vanillaStore.getDisabled();
        Set<String> toEnable = new HashSet<>();
        for (String modId : mods) {
            String prefix = modId + ":";
            for (String id : rawCache.keySet()) {
                if (id.startsWith(prefix) && !disabled.contains(id)) toEnable.add(id);
            }
        }
        if (toEnable.isEmpty()) { LOGGER.warn("enabledMods configured but no advancements matched: {}", mods); return; }

        for (String id : toEnable) vanillaStore.setEnabled(id, true);
        saveVanillaStates();
        autoAssignVanillaTabs();
        LOGGER.info("Enabled {} advancements from mods: {}", toEnable.size(), mods);
    }

    // 原版元数据
    public Map<String, VanillaAdvMeta> getVanillaMetaMap() { return vanillaStore.getMetaMap(); }
    public VanillaAdvMeta getVanillaMeta(String id) { return vanillaStore.getMeta(id); }
    public void setVanillaMeta(String id, VanillaAdvMeta meta) {
        vanillaStore.setMeta(id, meta);
        saveVanillaMeta();
    }

    public void cacheVanillaAdvancements() {
        vanillaStore.cacheFromServer(server);
        io.saveVanillaRawCache(vanillaStore);
    }

    // ══════════════════════════════════════════════════════════
    // 文件持久化
    // ══════════════════════════════════════════════════════════

    public void saveAdvancements() { io.saveAdvancements(advStore); }
    private void saveVanillaStates() { io.saveVanillaStates(vanillaStore); }
    public void saveVanillaMeta() { io.saveVanillaMeta(vanillaStore); }
    private void saveTabOrder() { io.saveTabOrder(tabStore); }

    public void saveAll() {
        io.saveAll(advStore, playerStore, vanillaStore, tabStore);
    }

    public void forceReload() {
        io.loadAll(advStore, vanillaStore, tabStore, playerStore, server);
    }

    // ══════════════════════════════════════════════════════════
    // 条件索引
    // ══════════════════════════════════════════════════════════

    public List<String> getAdvIdsByConditionType(ConditionType type) { return advStore.getAdvIdsByConditionType(type); }
    public List<String> getAdvIdsByCondition(ConditionType type, String targetId) { return advStore.getAdvIdsByCondition(type, targetId); }
    public List<ConditionIndex.AdvIdCondIndex> getAdvCondIndexesByCondition(ConditionType type, String targetId) { return advStore.getAdvCondIndexesByCondition(type, targetId); }

    // ══════════════════════════════════════════════════════════
    // 原版原始缓存
    // ══════════════════════════════════════════════════════════

    public Map<String, JsonElement> getVanillaAdvRawCache() { return vanillaStore.getRawCache(); }
    public void setVanillaAdvRawCache(Map<String, JsonElement> cache) { vanillaStore.setRawCache(cache); }
    public Map<String, String> getVanillaParentMap() { return vanillaStore.getParentMap(); }

    // ══════════════════════════════════════════════════════════
    // 导入/导出
    // ══════════════════════════════════════════════════════════

    public JsonObject exportAll() {
        return ImportExportHandler.exportAll(
                advStore.getAll(), tabStore.getCustomTabs(),
                dimensionLocks, vanillaStore.getMetaMap(), tabStore.getTabOrder());
    }

    public void importAll(JsonObject data) { ImportExportHandler.importAll(data, this); }

    // ══════════════════════════════════════════════════════════
    // ImportContext 实现
    // ══════════════════════════════════════════════════════════

    @Override
    public JsonObject exportBackup() { return exportAll(); }

    @Override
    public void restoreFromBackup(JsonObject backup) {
        try {
            if (backup.has("advancements")) {
                Map<String, CustomAdvancement> advs = DataStore.GSON.fromJson(
                        backup.get("advancements"),
                        new TypeToken<Map<String, CustomAdvancement>>() {}.getType());
                if (advs != null) advStore.replaceAll(advs);
            }
            if (backup.has("customTabs")) {
                List<String> tabs = DataStore.GSON.fromJson(backup.get("customTabs"),
                        new TypeToken<List<String>>() {}.getType());
                if (tabs != null) {
                    synchronized (tabStore.getCustomTabs()) {
                        tabStore.getCustomTabs().clear();
                        tabStore.getCustomTabs().addAll(tabs);
                    }
                }
            }
            if (backup.has("dimensionLocks")) {
                Map<String, DimensionLock> locks = DataStore.GSON.fromJson(backup.get("dimensionLocks"),
                        new TypeToken<Map<String, DimensionLock>>() {}.getType());
                if (locks != null) {
                    dimensionLocks.clear();
                    dimensionLocks.putAll(locks);
                }
            }
            if (backup.has("vanillaMeta")) {
                Map<String, VanillaAdvMeta> meta = DataStore.GSON.fromJson(backup.get("vanillaMeta"),
                        new TypeToken<Map<String, VanillaAdvMeta>>() {}.getType());
                if (meta != null) {
                    vanillaStore.getMetaMap().clear();
                    vanillaStore.getMetaMap().putAll(meta);
                }
            }
            if (backup.has("tabOrder")) {
                List<String> order = DataStore.GSON.fromJson(backup.get("tabOrder"),
                        new TypeToken<List<String>>() {}.getType());
                if (order != null) setTabOrder(order);
            }
            saveAll();
        } catch (Exception e) {
            LOGGER.error("Backup restoration failed!", e);
        }
    }

    @Override
    public void replaceAdvancements(Map<String, CustomAdvancement> advs) { advStore.replaceAll(advs); }

    @Override
    public void setCustomTabs(List<String> tabs) {
        synchronized (tabStore.getCustomTabs()) {
            tabStore.getCustomTabs().clear();
            tabStore.getCustomTabs().addAll(tabs);
        }
    }

    @Override
    public void setDimensionLocks(Map<String, DimensionLock> locks) {
        dimensionLocks.clear();
        dimensionLocks.putAll(locks);
    }

    @Override
    public void setVanillaMeta(Map<String, VanillaAdvMeta> meta) {
        vanillaStore.getMetaMap().clear();
        vanillaStore.getMetaMap().putAll(meta);
    }

    @Override
    public int getAdvancementCount() { return advStore.getAll().size(); }

    @Override
    public int getCustomTabCount() { return tabStore.getCustomTabs().size(); }

    @Override
    public int getDimensionLockCount() { return dimensionLocks.size(); }
}
