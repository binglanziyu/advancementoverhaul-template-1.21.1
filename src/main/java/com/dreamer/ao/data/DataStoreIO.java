package com.dreamer.ao.data;

import com.dreamer.ao.data.model.VanillaAdvMeta;
import com.google.gson.JsonElement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 数据文件 I/O 与异步持久化层。
 * <p>
 * 负责所有文件路径管理、目录创建、临时文件原子写入、批量保存和重新加载。
 * 线程安全：异步写入通过单线程 {@code saveExecutor} 完成。
 * <p>
 * 从 {@link ServerDataStore} 拆分而来，使协调器专注于业务委托。
 */
final class DataStoreIO {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataStoreIO.class);

    // ── 文件名常量 ──
    private static final String ADV_FILE          = "advancements.json";
    private static final String VANILLA_FILE      = "vanilla_states.json";
    private static final String VANILLA_RAW_FILE  = "vanilla_raw_cache.json";
    private static final String VANILLA_META_FILE = "vanilla_meta.json";
    private static final String TAB_ORDER_FILE    = "tab_order.json";
    private static final String DATA_DIR  = "advancement_overhaul";

    // ── 基础设施 ──
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AO-Save");
        t.setDaemon(true);
        return t;
    });

    private volatile Path dataFolder;
    private volatile Path playerDataFolder;

    // ── 生命周期 ──

    /** 初始化数据目录（仅执行一次） */
    Path initDir(Path configDir) {
        if (dataFolder != null) return dataFolder;
        Path folder = configDir.resolve(DATA_DIR);
        try { Files.createDirectories(folder); }
        catch (IOException e) { LOGGER.error("Failed to create config data folder", e); return null; }
        dataFolder = folder;
        return folder;
    }

    /** 设置服务端引用并初始化玩家数据目录 */
    void initPlayerDir(MinecraftServer server) {
        if (dataFolder == null) {
            LOGGER.error("DataStoreIO.initPlayerDir() called before initDir()");
            return;
        }
        if (playerDataFolder != null) return;
        Path root = server.getWorldPath(LevelResource.ROOT).resolve(DATA_DIR);
        try { Files.createDirectories(root); }
        catch (IOException e) { LOGGER.error("Failed to create player data folder", e); return; }
        Path playerDir = root.resolve("player_data");
        try { Files.createDirectories(playerDir); }
        catch (IOException e) { LOGGER.error("Failed to create player_data directory", e); return; }
        playerDataFolder = root;
    }

    void shutdown() {
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

    // ── 异步写入核心 ──

    /** 异步（fallback 同步）原子写入文件 */
    void asyncWrite(Path path, String content) {
        if (saveExecutor.isShutdown()) {
            writeSync(path, content);
            return;
        }
        saveExecutor.submit(() -> writeSync(path, content));
    }

    private static void writeSync(Path path, String content) {
        try {
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(tmp, content);
            try {
                Files.move(tmp, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                // ATOMIC_MOVE not supported (e.g. cross-filesystem) — use .bak fallback
                Path bak = path.resolveSibling(path.getFileName() + ".bak");
                Files.move(path, bak, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Files.move(tmp, path);
                Files.deleteIfExists(bak);
                LOGGER.debug("Saved {} using .bak fallback (ATOMIC_MOVE unsupported)", path.getFileName());
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save {}", path.getFileName(), e);
        }
    }

    // ── 各模块持久化 ──

    void saveAdvancements(AdvancementStore store) {
        if (dataFolder == null) return;
        asyncWrite(dataFolder.resolve(ADV_FILE), store.toJson(DataStore.GSON_PRETTY));
    }

    void saveVanillaStates(VanillaStateStore store) {
        if (dataFolder == null) return;
        asyncWrite(dataFolder.resolve(VANILLA_FILE),
                DataStore.GSON_PRETTY.toJson(store.statesToJson()));
    }

    void saveVanillaMeta(VanillaStateStore store) {
        if (dataFolder == null) return;
        asyncWrite(dataFolder.resolve(VANILLA_META_FILE), store.metaToJson());
    }

    void saveVanillaRawCache(VanillaStateStore store) {
        if (dataFolder == null || store.getRawCache() == null) return;
        asyncWrite(dataFolder.resolve(VANILLA_RAW_FILE), store.rawCacheToJson());
    }

    void saveTabOrder(TabStore store) {
        if (dataFolder == null) return;
        asyncWrite(dataFolder.resolve(TAB_ORDER_FILE), store.getOrderJson());
    }

    void saveAll(AdvancementStore advStore, PlayerDataStore playerStore,
                 VanillaStateStore vanillaStore, TabStore tabStore) {
        saveAdvancements(advStore);
        savePlayerDataIfDirty(playerStore);
        saveVanillaStates(vanillaStore);
        saveVanillaMeta(vanillaStore);
        saveTabOrder(tabStore);
    }

    // ── 玩家数据 ──

    void savePlayerDataIfDirty(PlayerDataStore store) {
        Path dir = playerDataFolder != null ? playerDataFolder : dataFolder;
        if (dir != null) store.saveIfDirty(dir, saveExecutor);
    }

    void savePlayerData(PlayerDataStore store) {
        Path dir = playerDataFolder != null ? playerDataFolder : dataFolder;
        if (dir != null) store.saveAll(dir);
    }

    // ── 加载 ──

    void loadAll(AdvancementStore advStore, VanillaStateStore vanillaStore,
                 TabStore tabStore, PlayerDataStore playerStore,
                 MinecraftServer server) {
        advStore.loadFromFile(dataFolder.resolve(ADV_FILE), DataStore.GSON);
        vanillaStore.loadStates(dataFolder.resolve(VANILLA_FILE));
        vanillaStore.loadMeta(dataFolder.resolve(VANILLA_META_FILE));
        tabStore.loadOrder(dataFolder.resolve(TAB_ORDER_FILE));
        if (playerDataFolder != null) playerStore.loadFromDir(playerDataFolder);
        if (server != null && vanillaStore.getRawCache() == null) vanillaStore.cacheFromServer(server);
    }

    // ── 初始化加载 ──

    void initialLoad(AdvancementStore advStore, VanillaStateStore vanillaStore, TabStore tabStore) {
        advStore.loadFromFile(dataFolder.resolve(ADV_FILE), DataStore.GSON);
        vanillaStore.loadStates(dataFolder.resolve(VANILLA_FILE));
        vanillaStore.loadMeta(dataFolder.resolve(VANILLA_META_FILE));
        tabStore.loadOrder(dataFolder.resolve(TAB_ORDER_FILE));
    }

    // ── Getters ──

    Path getDataFolder() { return dataFolder; }
    Path getPlayerDataFolder() { return playerDataFolder; }
    ExecutorService getSaveExecutor() { return saveExecutor; }
    Path resolveDataFile(String name) { return dataFolder.resolve(name); }
}
