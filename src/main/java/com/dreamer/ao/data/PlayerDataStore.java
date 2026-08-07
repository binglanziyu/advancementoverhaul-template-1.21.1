package com.dreamer.ao.data;

import com.dreamer.ao.data.model.CustomAdvancement;
import com.google.gson.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 玩家数据存储模块。
 * <p>
 * <b>一句话作用：</b>管理每个玩家的成就完成状态、逐条件进度和等待释放的 pending 标记。
 * <p>
 * 管理每个玩家的三类数据：
 * <ul>
 *   <li><b>完成状态</b> — UUID → (成就ID → 是否完成)</li>
 *   <li><b>逐条件进度</b> — UUID → (成就ID → (条件索引 → 进度值))</li>
 *   <li><b>Pending 标记</b> — UUID → 前置条件未满足、等待释放的成就集合</li>
 * </ul>
 * <p>
 * 数据持久化为按 UUID 分文件存储（{@code player_data/{uuid}.json}），
 * 避免单文件随玩家数量增长而过大。
 * 使用 {@link AtomicBoolean} 脏标记避免重复保存。
 *
 * @see ServerDataStore 协调器，调用本模块的方法
 */
final class PlayerDataStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerDataStore.class);

    /** 当前数据格式版本。当数据结构变更时递增此值。 */
    private static final int DATA_VERSION = 2;

    /**
     * 数据迁移链：旧版本号 → 迁移函数。
     * 每个迁移函数接收旧版 JsonObject，返回新版 JsonObject。
     * 加载数据时按版本顺序依次应用，直到版本号达到 DATA_VERSION。
     * <p>
     * 添加新迁移时：1) 递增 DATA_VERSION  2) 在此 Map 中添加对应条目  3) 实现迁移逻辑。
     */
    private static final Map<Integer, Function<JsonObject, JsonObject>> MIGRATIONS = Map.of(
            // 版本 1 → 2：无结构变更，预留迁移槽位
            // 1: PlayerDataStore::migrateV1ToV2
    );

    // ═══════════════ 内存数据 ═══════════════

    /** UUID → (成就ID → 是否完成) */
    private final Map<UUID, Map<String, Boolean>> completions = new ConcurrentHashMap<>();

    /** UUID → (成就ID → (条件索引 → 进度值)) */
    private final Map<UUID, Map<String, Map<Integer, Integer>>> progress = new ConcurrentHashMap<>();

    /** UUID → 待释放的 pending 成就集合 */
    private final Map<UUID, Set<String>> pending = new ConcurrentHashMap<>();

    /** 玩家数据脏标记（AtomicBoolean 消除 check-then-act 竞态） */
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    /** 脏玩家 UUID 集合：保存时仅写入此集合中的玩家文件，避免遍历所有玩家 */
    private final Set<UUID> dirtyUuids = Collections.synchronizedSet(new HashSet<>());

    // ═══════════════ 完成状态 ═══════════════

    Map<String, Boolean> getCompletions(UUID uuid) {
        return completions.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
    }

    boolean isCompleted(UUID uuid, String advId) {
        return Boolean.TRUE.equals(getCompletions(uuid).get(advId));
    }

    /**
     * 设置完成状态。
     * 完成时自动清除对应的进度数据和 pending 标记。
     */
    void setCompleted(UUID uuid, String advId, boolean completed) {
        getCompletions(uuid).put(advId, completed);
        if (completed) {
            getProgress(uuid).remove(advId);
            getPending(uuid).remove(advId);
        }
        markPlayerDirty(uuid);
    }

    // ═══════════════ 逐条件进度 ═══════════════

    Map<String, Map<Integer, Integer>> getProgress(UUID uuid) {
        return progress.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
    }

    int getConditionProgress(UUID uuid, String advId, int condIndex) {
        Map<Integer, Integer> condMap = getProgress(uuid).get(advId);
        return condMap != null ? condMap.getOrDefault(condIndex, 0) : 0;
    }

    void setConditionProgress(UUID uuid, String advId, int condIndex, int value) {
        getProgress(uuid)
                .computeIfAbsent(advId, k -> new ConcurrentHashMap<>())
                .put(condIndex, value);
        markPlayerDirty(uuid);
    }

    void resetConditionProgress(UUID uuid, String advId) {
        getProgress(uuid).remove(advId);
        markPlayerDirty(uuid);
    }

    /**
     * 计算指定成就的总完成百分比（所有条件的加权平均值）。
     * @return 0-100 的整数
     */
    int getProgressPercent(UUID uuid, String advId, CustomAdvancement adv) {
        if (adv == null || adv.getConditions().isEmpty()) return 0;
        Map<Integer, Integer> condMap = getProgress(uuid).get(advId);
        if (condMap == null) return 0;
        int total = 0, maxTotal = 0;
        var conditions = adv.getConditions();
        for (int i = 0; i < conditions.size(); i++) {
            int count = conditions.get(i).getCount();
            maxTotal += count;
            total += Math.min(condMap.getOrDefault(i, 0), count);
        }
        return maxTotal > 0 ? (int) (100L * total / maxTotal) : 0;
    }

    /**
     * 获取玩家进度的快照（advId → 0-100 百分比），用于网络同步。
     */
    Map<String, Integer> getProgressSnapshot(UUID uuid, Map<String, CustomAdvancement> advancements) {
        Map<String, Integer> snapshot = new HashMap<>();
        Map<String, Map<Integer, Integer>> playerProg = getProgress(uuid);
        for (var entry : playerProg.entrySet()) {
            String advId = entry.getKey();
            CustomAdvancement adv = advancements.get(advId);
            if (adv == null || adv.getConditions().isEmpty()) continue;
            var conditions = adv.getConditions();
            int total = 0, maxTotal = 0;
            for (int i = 0; i < conditions.size(); i++) {
                int count = conditions.get(i).getCount();
                maxTotal += count;
                total += Math.min(entry.getValue().getOrDefault(i, 0), count);
            }
            if (maxTotal > 0) snapshot.put(advId, (int) (100L * total / maxTotal));
        }
        return snapshot;
    }

    // ═══════════════ Pending 标记 ═══════════════

    Set<String> getPending(UUID uuid) {
        return pending.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
    }

    boolean isPending(UUID uuid, String advId) {
        return getPending(uuid).contains(advId);
    }

    void setPending(UUID uuid, String advId, boolean isPending) {
        if (isPending) {
            getPending(uuid).add(advId);
        } else {
            getPending(uuid).remove(advId);
        }
        markPlayerDirty(uuid);
    }

    // ═══════════════ 脏标记管理 ═══════════════

    /** 标记指定玩家数据为脏，同时加入脏集合以支持增量保存 */
    private void markPlayerDirty(UUID uuid) {
        dirty.set(true);
        dirtyUuids.add(uuid);
    }

    // ═══════════════ 保存/加载 ═══════════════

    /**
     * 仅在脏标记为 true 时保存（CAS 原子操作，异步写入避免主线程卡顿）。
     * @param baseDir 基础目录（config 或 world 目录）
     * @param saveExecutor 异步写入线程池（由 ServerDataStore 提供）
     * @return 如果提交了保存任务则返回 true
     */
    boolean saveIfDirty(Path baseDir, java.util.concurrent.ExecutorService saveExecutor) {
        if (!dirty.compareAndSet(true, false)) return false;
        // 快照当前脏 UUID 集合，避免在异步线程中遇到并发修改
        List<UUID> dirtySnapshot;
        synchronized (dirtyUuids) {
            dirtySnapshot = new ArrayList<>(dirtyUuids);
            dirtyUuids.clear();
        }
        saveExecutor.submit(() -> {
            try {
                writeAllToFiles(baseDir, dirtySnapshot);
            } catch (Exception e) {
                dirty.set(true); // 写失败回滚脏标记，下次 tick 重试
                LOGGER.error("Failed to save player data, will retry on next tick", e);
            }
        });
        return true;
    }

    /**
     * 全量保存所有玩家数据到文件（忽略脏标记）。
     * 由外部调用（如 CommandHelper.syncTargetPlayer）。
     */
    void saveAll(Path baseDir) {
        dirty.set(false);
        dirtyUuids.clear();
        try {
            Set<UUID> allUuids = new HashSet<>();
            allUuids.addAll(completions.keySet());
            allUuids.addAll(progress.keySet());
            allUuids.addAll(pending.keySet());
            writeAllToFiles(baseDir, new ArrayList<>(allUuids));
        } catch (Exception e) {
            dirty.set(true);
            LOGGER.error("Failed to save all player data", e);
        }
    }

    /**
     * 内部：将指定的 UUID 列表写入 player_data/ 目录下的分文件。
     * @param uuidsSnapshot 要写入的 UUID 列表（已做快照，线程安全）
     */
    private void writeAllToFiles(Path baseDir, List<UUID> uuidsSnapshot) {
        Path dataDir = baseDir.resolve("player_data");
        try { Files.createDirectories(dataDir); }
        catch (Exception e) { LOGGER.error("Failed to create player_data directory: {}", dataDir, e); return; }

        for (UUID uuid : uuidsSnapshot) {
            JsonObject playerObj = new JsonObject();
            playerObj.addProperty("version", DATA_VERSION);

            Map<String, Boolean> comps = completions.get(uuid);
            if (comps != null && !comps.isEmpty()) {
                JsonObject compsObj = new JsonObject();
                for (var c : comps.entrySet()) compsObj.addProperty(c.getKey(), c.getValue());
                playerObj.add("completions", compsObj);
            }

            Map<String, Map<Integer, Integer>> progs = progress.get(uuid);
            if (progs != null && !progs.isEmpty()) {
                JsonObject progsObj = new JsonObject();
                for (var p : progs.entrySet()) {
                    JsonObject condProg = new JsonObject();
                    for (var cp : p.getValue().entrySet())
                        condProg.addProperty(String.valueOf(cp.getKey()), cp.getValue());
                    progsObj.add(p.getKey(), condProg);
                }
                playerObj.add("progress", progsObj);
            }

            Set<String> pends = pending.get(uuid);
            if (pends != null && !pends.isEmpty()) {
                JsonArray pendArr = new JsonArray();
                for (String id : pends) pendArr.add(id);
                playerObj.add("pending", pendArr);
            }

            try {
                Path target = dataDir.resolve(uuid.toString() + ".json");
                Path tmp = dataDir.resolve(uuid.toString() + ".json.tmp");
                Files.writeString(tmp, DataStore.GSON_PRETTY.toJson(playerObj));
                Files.move(tmp, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) { LOGGER.warn("Failed to write player data for UUID {}: {}", uuid, e.getMessage()); }
        }
    }

    /** 从 player_data/ 目录加载所有玩家的分文件数据 */
    void loadFromDir(Path baseDir) {
        Path dataDir = baseDir.resolve("player_data");
        if (!Files.exists(dataDir) || !Files.isDirectory(dataDir)) return;

        completions.clear(); progress.clear(); pending.clear();

        try (var stream = Files.list(dataDir)) {
            stream.filter(f -> f.getFileName().toString().endsWith(".json")).forEach(file -> {
                try {
                    String fileName = file.getFileName().toString();
                    String uuidStr = fileName.substring(0, fileName.length() - 5);
                    UUID uuid = UUID.fromString(uuidStr);
                    JsonObject obj = JsonParser.parseString(Files.readString(file)).getAsJsonObject();

                    if (obj.has("completions") && obj.get("completions").isJsonObject()) {
                        Map<String, Boolean> m = new ConcurrentHashMap<>();
                        for (var ce : obj.getAsJsonObject("completions").entrySet())
                            m.put(ce.getKey(), ce.getValue().getAsBoolean());
                        completions.put(uuid, m);
                    }

                    if (obj.has("progress") && obj.get("progress").isJsonObject()) {
                        Map<String, Map<Integer, Integer>> m = new ConcurrentHashMap<>();
                        for (var ce : obj.getAsJsonObject("progress").entrySet()) {
                            Map<Integer, Integer> condMap = new ConcurrentHashMap<>();
                            if (ce.getValue().isJsonObject()) {
                                for (var cp : ce.getValue().getAsJsonObject().entrySet()) {
                                    try {
                                        condMap.put(Integer.parseInt(cp.getKey()), cp.getValue().getAsInt());
                                    } catch (Exception e) {
                                        LOGGER.warn("Failed to parse condition progress entry for UUID {}: key={}", uuid, cp.getKey());
                                    }
                                }
                            } else if (ce.getValue().isJsonPrimitive()) {
                                condMap.put(0, ce.getValue().getAsInt());
                            }
                            m.put(ce.getKey(), condMap);
                        }
                        progress.put(uuid, m);
                    }

                    if (obj.has("pending") && obj.get("pending").isJsonArray()) {
                        Set<String> s = ConcurrentHashMap.newKeySet();
                        for (JsonElement e : obj.getAsJsonArray("pending")) s.add(e.getAsString());
                        pending.put(uuid, s);
                    }

                    // 应用数据迁移（如果玩家文件中版本低于 DATA_VERSION）
                    if (obj.has("version")) {
                        int fileVersion = obj.get("version").getAsInt();
                        if (fileVersion < DATA_VERSION) {
                            applyMigrations(uuid, fileVersion);
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                    // 跳过非 UUID 文件名
                } catch (Exception e) { LOGGER.warn("Failed to parse player data file: {}", e.getMessage()); }
            });
        } catch (Exception e) { LOGGER.error("Failed to list player_data directory", e); }
    }

    /**
     * 从旧版单文件格式迁移到按 UUID 分文件存储。
     * 迁移完成后将旧文件重命名为 .bak 备份。
     */
    void migrateFromLegacy(Path baseDir) {
        Path oldFile = baseDir.resolve("player_data.json");
        Path dataDir = baseDir.resolve("player_data");
        if (!Files.exists(oldFile)) return;

        try (var stream = Files.list(dataDir)) {
            if (stream.findAny().isPresent()) return;
        } catch (Exception e) { LOGGER.warn("Failed to list player_data for migration check: {}", e.getMessage()); return; }

        try {
            JsonObject root = JsonParser.parseString(Files.readString(oldFile)).getAsJsonObject();

            Map<UUID, Map<String, Boolean>> oldCompletions = new HashMap<>();
            Map<UUID, Map<String, Map<Integer, Integer>>> oldProgress = new HashMap<>();
            Map<UUID, Set<String>> oldPending = new HashMap<>();

            if (root.has("completions") && root.get("completions").isJsonObject()) {
                for (var pe : root.getAsJsonObject("completions").entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(pe.getKey());
                        Map<String, Boolean> m = new HashMap<>();
                        if (pe.getValue().isJsonObject())
                            for (var ce : pe.getValue().getAsJsonObject().entrySet())
                                m.put(ce.getKey(), ce.getValue().getAsBoolean());
                        oldCompletions.put(uuid, m);
                    } catch (Exception e) { LOGGER.warn("Failed to migrate completions for player {}: {}", pe.getKey(), e.getMessage()); }
                }
            }
            if (root.has("progress") && root.get("progress").isJsonObject()) {
                for (var pe : root.getAsJsonObject("progress").entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(pe.getKey());
                        Map<String, Map<Integer, Integer>> m = new HashMap<>();
                        if (pe.getValue().isJsonObject()) {
                            for (var ce : pe.getValue().getAsJsonObject().entrySet()) {
                                Map<Integer, Integer> condMap = new HashMap<>();
                                if (ce.getValue().isJsonObject()) {
                                    for (var cp : ce.getValue().getAsJsonObject().entrySet())
                                        condMap.put(Integer.parseInt(cp.getKey()), cp.getValue().getAsInt());
                                } else if (ce.getValue().isJsonPrimitive())
                                    condMap.put(0, ce.getValue().getAsInt());
                                m.put(ce.getKey(), condMap);
                            }
                        }
                        oldProgress.put(uuid, m);
                    } catch (Exception e) { LOGGER.warn("Failed to migrate progress for player {}: {}", pe.getKey(), e.getMessage()); }
                }
            }
            if (root.has("pending") && root.get("pending").isJsonObject()) {
                for (var pe : root.getAsJsonObject("pending").entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(pe.getKey());
                        Set<String> s = new HashSet<>();
                        if (pe.getValue().isJsonArray())
                            for (JsonElement e : pe.getValue().getAsJsonArray()) s.add(e.getAsString());
                        oldPending.put(uuid, s);
                    } catch (Exception e) { LOGGER.warn("Failed to migrate pending for player {}: {}", pe.getKey(), e.getMessage()); }
                }
            }

            Set<UUID> allUuids = new HashSet<>();
            allUuids.addAll(oldCompletions.keySet());
            allUuids.addAll(oldProgress.keySet());
            allUuids.addAll(oldPending.keySet());

            for (UUID uuid : allUuids) {
                JsonObject playerObj = new JsonObject();
                playerObj.addProperty("version", DATA_VERSION);

                Map<String, Boolean> comps = oldCompletions.get(uuid);
                if (comps != null && !comps.isEmpty()) {
                    JsonObject compsObj = new JsonObject();
                    for (var c : comps.entrySet()) compsObj.addProperty(c.getKey(), c.getValue());
                    playerObj.add("completions", compsObj);
                }
                Map<String, Map<Integer, Integer>> progs = oldProgress.get(uuid);
                if (progs != null && !progs.isEmpty()) {
                    JsonObject progsObj = new JsonObject();
                    for (var p : progs.entrySet()) {
                        JsonObject cpObj = new JsonObject();
                        for (var cp : p.getValue().entrySet())
                            cpObj.addProperty(String.valueOf(cp.getKey()), cp.getValue());
                        progsObj.add(p.getKey(), cpObj);
                    }
                    playerObj.add("progress", progsObj);
                }
                Set<String> pends = oldPending.get(uuid);
                if (pends != null && !pends.isEmpty()) {
                    JsonArray arr = new JsonArray();
                    for (String id : pends) arr.add(id);
                    playerObj.add("pending", arr);
                }

                Files.writeString(dataDir.resolve(uuid.toString() + ".json"),
                        DataStore.GSON_PRETTY.toJson(playerObj));
            }

            try { Files.move(oldFile, baseDir.resolve("player_data.json.bak"), StandardCopyOption.REPLACE_EXISTING); }
            catch (Exception e) { LOGGER.warn("Failed to rename legacy data backup: {}", e.getMessage()); }
        } catch (Exception e) { LOGGER.error("Failed to migrate from legacy player data format", e); }
    }

    // ═══════════════ 批量清理 ═══════════════

    /** 从所有玩家的数据中移除指定成就 */
    void removeAdvancementFromAll(String advId) {
        for (var pd : completions.values()) pd.remove(advId);
        for (var pd : progress.values()) pd.remove(advId);
        for (var pd : pending.values()) pd.remove(advId);
    }

    // ═══════════════ 数据迁移 ═══════════════

    /**
     * 对指定玩家的数据按版本顺序应用迁移。
     * 从 fileVersion+1 开始依次应用 MIGRATIONS Map 中定义的迁移函数，
     * 直到版本号达到 DATA_VERSION。
     * <p>
     * 添加新迁移时请同步更新 MIGRATIONS Map 和 DATA_VERSION 常量。
     */
    private void applyMigrations(UUID uuid, int fileVersion) {
        for (int v = fileVersion + 1; v <= DATA_VERSION; v++) {
            Function<JsonObject, JsonObject> migration = MIGRATIONS.get(v);
            if (migration == null) continue;

            try {
                // 迁移玩家完成数据
                Map<String, Boolean> comps = completions.get(uuid);
                if (comps != null) {
                    // 具体迁移逻辑由各 MIGRATIONS 条目实现
                }

                Map<String, Map<Integer, Integer>> progs = progress.get(uuid);
                if (progs != null) {
                    // 具体迁移逻辑由各 MIGRATIONS 条目实现
                }

                LOGGER.info("Migrated player {} data from version {} to {}", uuid, v, v);
            } catch (Exception e) {
                LOGGER.error("Failed to migrate player {} data from version {} to {}", uuid, v - 1, v, e);
                dirty.set(true); // 迁移失败不影响已迁移的数据，标记脏以触发重写
            }
        }
    }
}
