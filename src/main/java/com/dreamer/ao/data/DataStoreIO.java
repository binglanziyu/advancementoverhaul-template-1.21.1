package com.dreamer.ao.data;

import com.dreamer.ao.ServerConstants;
import com.dreamer.ao.data.model.VanillaAdvMeta;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
    private static final String DIM_LOCK_FILE     = "dimension_locks.json";
    private static final String PHASE_STATE_FILE  = "phase_state.json";
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
        asyncWrite(path, content, ServerConstants.BACKUP_GENERATIONS);
    }

    /**
     * 异步原子写入文件，并按指定代数保留滚动备份。
     *
     * @param generations 保留的备份代数，{@code <= 0} 表示不备份
     */
    void asyncWrite(Path path, String content, int generations) {
        if (saveExecutor.isShutdown()) {
            writeSync(path, content, generations);
            return;
        }
        saveExecutor.submit(() -> writeSync(path, content, generations));
    }

    /**
     * 同步原子写入，写入前先轮转滚动备份。
     * <p>
     * 全部 I/O（含备份复制）都在 {@code saveExecutor} 单线程内串行执行，
     * 不阻塞服务端主线程，也不会与其它写入任务产生竞态。
     */
    private static void writeSync(Path path, String content, int generations) {
        rotateBackups(path, generations);
        try {
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(tmp, content);
            try {
                Files.move(tmp, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                // ATOMIC_MOVE not supported (e.g. cross-filesystem) — 退化为两步移动。
                // 此处的 .bak 是移动过程中的中转文件，与下方滚动备份的 .bakN 互不冲突。
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

    // ── 滚动备份与回滚 ──

    /** 构造第 {@code gen} 代备份文件路径（gen 从 1 开始，1 为最新）。 */
    private static Path backupPath(Path path, int gen) {
        return path.resolveSibling(path.getFileName() + ".bak" + gen);
    }

    /**
     * 写入前轮转备份：{@code .bak(N-1) → .bakN}，最后将现有主文件复制为 {@code .bak1}。
     * <p>
     * 使用复制而非移动保存主文件，确保轮转过程中主文件始终可读——
     * 即便进程在此刻崩溃，也不会出现主文件缺失的窗口。
     * <p>
     * 备份失败仅记录警告，不阻断主写入流程：备份是可靠性增强而非写入前置条件。
     */
    private static void rotateBackups(Path path, int generations) {
        if (generations <= 0 || !Files.exists(path)) return;
        try {
            for (int gen = generations; gen > 1; gen--) {
                Path older = backupPath(path, gen);
                Path newer = backupPath(path, gen - 1);
                if (Files.exists(newer)) {
                    Files.move(newer, older, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Files.copy(path, backupPath(path, 1),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Failed to rotate backups for {}: {}", path.getFileName(), e.getMessage());
        }
    }

    /**
     * 读取文件内容，主文件损坏时自动回退到滚动备份。
     * <p>
     * 依次尝试主文件、{@code .bak1}、{@code .bak2}……由 {@code validator} 判定内容是否可用
     * （通常为 JSON 可解析性检查）。任一候选通过即返回其内容。
     *
     * @param path      主文件路径
     * @param validator 内容有效性校验，返回 false 表示该候选损坏
     * @return 可用的文件内容；全部候选均不可用时返回 {@code null}
     */
    static String readWithFallback(Path path, java.util.function.Predicate<String> validator) {
        for (int gen = 0; gen <= ServerConstants.BACKUP_GENERATIONS; gen++) {
            Path candidate = gen == 0 ? path : backupPath(path, gen);
            if (!Files.exists(candidate)) continue;
            try {
                String content = Files.readString(candidate);
                if (!validator.test(content)) {
                    LOGGER.warn("Corrupted data file {}, trying next backup", candidate.getFileName());
                    continue;
                }
                if (gen > 0) {
                    LOGGER.warn("Recovered {} from backup generation {}", path.getFileName(), gen);
                }
                return content;
            } catch (IOException e) {
                LOGGER.warn("Failed to read {}: {}", candidate.getFileName(), e.getMessage());
            }
        }
        return null;
    }

    /** 校验内容为可解析的 JSON 对象。 */
    static boolean isValidJsonObject(String content) {
        try {
            JsonElement parsed = com.google.gson.JsonParser.parseString(content);
            return parsed != null && parsed.isJsonObject();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 校验内容为可解析的 JSON 数组。 */
    static boolean isValidJsonArray(String content) {
        try {
            JsonElement parsed = com.google.gson.JsonParser.parseString(content);
            return parsed != null && parsed.isJsonArray();
        } catch (RuntimeException e) {
            return false;
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
        // 原始缓存体积较大且可从服务端注册表重新生成，损坏代价低，只保留 1 代备份
        asyncWrite(dataFolder.resolve(VANILLA_RAW_FILE), store.rawCacheToJson(),
                ServerConstants.BACKUP_GENERATIONS_LARGE);
    }

    void saveTabOrder(TabStore store) {
        if (dataFolder == null) return;
        asyncWrite(dataFolder.resolve(TAB_ORDER_FILE), store.getOrderJson());
    }

    void saveDimensionLocks(String json) {
        if (dataFolder == null) return;
        asyncWrite(dataFolder.resolve(DIM_LOCK_FILE), json);
    }

    void savePhaseState(String json) {
        if (dataFolder == null) return;
        asyncWrite(dataFolder.resolve(PHASE_STATE_FILE), json);
    }

    void saveAll(AdvancementStore advStore, PlayerDataStore playerStore,
                 VanillaStateStore vanillaStore, TabStore tabStore,
                 String dimensionLocksJson) {
        saveAll(advStore, playerStore, vanillaStore, tabStore, dimensionLocksJson, null);
    }

    void saveAll(AdvancementStore advStore, PlayerDataStore playerStore,
                 VanillaStateStore vanillaStore, TabStore tabStore,
                 String dimensionLocksJson, String phaseStateJson) {
        saveAdvancements(advStore);
        savePlayerDataIfDirty(playerStore);
        saveVanillaStates(vanillaStore);
        saveVanillaMeta(vanillaStore);
        saveTabOrder(tabStore);
        if (dimensionLocksJson != null) {
            saveDimensionLocks(dimensionLocksJson);
        }
        if (phaseStateJson != null) {
            savePhaseState(phaseStateJson);
        }
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

    // ── 维度锁加载 ──

    void loadDimensionLocks(Map<String, DimensionLock> into) {
        Path file = dataFolder.resolve(DIM_LOCK_FILE);
        if (!Files.exists(file)) return;
        String content = readWithFallback(file, DataStoreIO::isValidJsonObject);
        if (content == null) {
            LOGGER.warn("Failed to load dimension locks: no readable file or backup");
            return;
        }
        try {
            JsonObject obj = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
            if (obj == null || obj.size() == 0) return;
            var type = new com.google.gson.reflect.TypeToken<Map<String, DimensionLock>>() {}.getType();
            Map<String, DimensionLock> loaded = DataStore.GSON.fromJson(obj, type);
            if (loaded != null) {
                into.clear();
                into.putAll(loaded);
                LOGGER.info("Loaded {} dimension locks", into.size());
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load dimension locks: {}", e.getMessage());
        }
    }

    // ── 阶段状态加载（全局 + 维度 + 已解锁集合） ──

    /**
     * 读取持久化的阶段状态文件。
     *
     * <p>返回原始 {@link JsonObject} 交由 {@code ServerDataStore} 解析，避免 IO 层依赖阶段模型。
     * 文件不存在时返回 {@code null}（首次运行属正常情况，不告警）。
     */
    JsonObject loadPhaseState() {
        if (dataFolder == null) return null;
        Path file = dataFolder.resolve(PHASE_STATE_FILE);
        if (!Files.exists(file)) return null;
        String content = readWithFallback(file, DataStoreIO::isValidJsonObject);
        if (content == null) {
            LOGGER.warn("Failed to load phase state: no readable file or backup");
            return null;
        }
        try {
            JsonObject obj = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
            if (obj == null || obj.size() == 0) return null;
            return obj;
        } catch (Exception e) {
            LOGGER.warn("Failed to load phase state: {}", e.getMessage());
            return null;
        }
    }

    // ── 加载 ──

    void loadAll(AdvancementStore advStore, VanillaStateStore vanillaStore,
                 TabStore tabStore, PlayerDataStore playerStore,
                 Map<String, DimensionLock> dimensionLocks,
                 MinecraftServer server) {
        advStore.loadFromFile(dataFolder.resolve(ADV_FILE), DataStore.GSON);
        vanillaStore.loadStates(dataFolder.resolve(VANILLA_FILE));
        vanillaStore.loadMeta(dataFolder.resolve(VANILLA_META_FILE));
        tabStore.loadOrder(dataFolder.resolve(TAB_ORDER_FILE));
        loadDimensionLocks(dimensionLocks);
        if (playerDataFolder != null) playerStore.loadFromDir(playerDataFolder);
        if (server != null && vanillaStore.getRawCache() == null) vanillaStore.cacheFromServer(server);
    }

    // ── 初始化加载 ──

    void initialLoad(AdvancementStore advStore, VanillaStateStore vanillaStore,
                     TabStore tabStore, Map<String, DimensionLock> dimensionLocks) {
        advStore.loadFromFile(dataFolder.resolve(ADV_FILE), DataStore.GSON);
        vanillaStore.loadStates(dataFolder.resolve(VANILLA_FILE));
        vanillaStore.loadMeta(dataFolder.resolve(VANILLA_META_FILE));
        tabStore.loadOrder(dataFolder.resolve(TAB_ORDER_FILE));
        loadDimensionLocks(dimensionLocks);
    }

    // ── Getters ──

    Path getDataFolder() { return dataFolder; }
    Path getPlayerDataFolder() { return playerDataFolder; }
    ExecutorService getSaveExecutor() { return saveExecutor; }
    Path resolveDataFile(String name) { return dataFolder.resolve(name); }
}
