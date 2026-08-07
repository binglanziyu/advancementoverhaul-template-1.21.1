package com.dreamer.ao.data;

import com.dreamer.ao.data.model.PhaseDefinition;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 阶段状态存储（服务端单例）。
 * <p>
 * 管理三类阶段状态：
 * <ul>
 *   <li><b>全局阶段</b> — 整个服务器当前所处的阶段</li>
 *   <li><b>维度阶段</b> — 每个维度独立的阶段（覆盖/叠加全局）</li>
 *   <li><b>玩家阶段</b> — 每个玩家独立的阶段效果（叠加）</li>
 * </ul>
 * 最终效果 = 全局倍率 + 维度倍率 + 玩家倍率。
 */
public class PhaseStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(PhaseStore.class);
    private static final PhaseStore INSTANCE = new PhaseStore();
    private static final String SAVE_FILE = "phase_state.json";

    public static PhaseStore getInstance() { return INSTANCE; }
    private PhaseStore() {}

    // ═══════════════ 运行时状态 ═══════════════

    /** 全局阶段 ID */
    private volatile String globalPhaseId = null;

    /** 维度阶段 ID 映射（dimensionKey → phaseId） */
    private final Map<String, String> dimensionPhases = new ConcurrentHashMap<>();

    /** 玩家阶段效果映射（playerUUID → phaseId） */
    private final Map<UUID, String> playerPhases = new ConcurrentHashMap<>();

    private Path dataDir;

    // ═══════════════ 生命周期 ═══════════════

    public void init(Path configDir) {
        this.dataDir = configDir.resolve("advancement_overhaul");
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create phase data directory", e);
        }
        loadState();
    }

    // ═══════════════ 全局阶段 ═══════════════

    public String getGlobalPhaseId() {
        return globalPhaseId;
    }

    public void setGlobalPhaseId(String phaseId) {
        this.globalPhaseId = phaseId;
        saveState();
    }

    public PhaseDefinition getGlobalPhase() {
        if (globalPhaseId == null) return null;
        return PhaseConfigLoader.getInstance().getPhase(globalPhaseId);
    }

    // ═══════════════ 维度阶段 ═══════════════

    public String getDimensionPhaseId(String dimensionKey) {
        return dimensionPhases.get(dimensionKey);
    }

    public void setDimensionPhase(String dimensionKey, String phaseId) {
        if (phaseId == null) {
            dimensionPhases.remove(dimensionKey);
        } else {
            dimensionPhases.put(dimensionKey, phaseId);
        }
        saveState();
    }

    public PhaseDefinition getDimensionPhase(String dimensionKey) {
        String id = dimensionPhases.get(dimensionKey);
        if (id == null) return null;
        return PhaseConfigLoader.getInstance().getPhase(id);
    }

    public Map<String, String> getAllDimensionPhases() {
        return Collections.unmodifiableMap(dimensionPhases);
    }

    // ═══════════════ 玩家阶段 ═══════════════

    public String getPlayerPhaseId(UUID playerUuid) {
        return playerPhases.get(playerUuid);
    }

    public void setPlayerPhase(UUID playerUuid, String phaseId) {
        if (phaseId == null) {
            playerPhases.remove(playerUuid);
        } else {
            playerPhases.put(playerUuid, phaseId);
        }
        saveState();
    }

    public PhaseDefinition getPlayerPhase(UUID playerUuid) {
        String id = playerPhases.get(playerUuid);
        if (id == null) return null;
        return PhaseConfigLoader.getInstance().getPhase(id);
    }

    // ═══════════════ 效果叠加计算 ═══════════════

    /**
     * 计算指定属性在指定维度的总倍率。
     * 最终倍率 = 全局倍率 + 维度倍率（不含玩家，因为怪物不受玩家阶段影响）。
     */
    public double getCombinedMultiplier(String dimensionKey, String attributeId) {
        double total = 0.0;
        PhaseDefinition global = getGlobalPhase();
        if (global != null) {
            total += global.getEffectMultiplier(attributeId) - 1.0;
        }
        PhaseDefinition dim = getDimensionPhase(dimensionKey);
        if (dim != null) {
            total += dim.getEffectMultiplier(attributeId) - 1.0;
        }
        return 1.0 + total;
    }

    /**
     * 计算指定玩家在指定维度的总倍率（含玩家阶段）。
     */
    public double getCombinedMultiplierForPlayer(UUID playerUuid, String dimensionKey, String attributeId) {
        double total = getCombinedMultiplier(dimensionKey, attributeId) - 1.0;
        PhaseDefinition player = getPlayerPhase(playerUuid);
        if (player != null) {
            total += player.getEffectMultiplier(attributeId) - 1.0;
        }
        return 1.0 + total;
    }

    // ═══════════════ 持久化 ═══════════════

    private void saveState() {
        if (dataDir == null) return;
        try {
            JsonObject obj = new JsonObject();
            if (globalPhaseId != null) obj.addProperty("global", globalPhaseId);

            JsonObject dims = new JsonObject();
            dimensionPhases.forEach(dims::addProperty);
            obj.add("dimensions", dims);

            JsonObject players = new JsonObject();
            playerPhases.forEach((k, v) -> players.addProperty(k.toString(), v));
            obj.add("players", players);

            Path file = dataDir.resolve(SAVE_FILE);
            Files.writeString(file, obj.toString());
        } catch (IOException e) {
            LOGGER.error("Failed to save phase state", e);
        }
    }

    private void loadState() {
        if (dataDir == null) return;
        Path file = dataDir.resolve(SAVE_FILE);
        if (!Files.exists(file)) return;
        try {
            String content = Files.readString(file);
            JsonObject obj = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
            if (obj.has("global")) globalPhaseId = obj.get("global").getAsString();
            if (obj.has("dimensions")) {
                for (var entry : obj.getAsJsonObject("dimensions").entrySet()) {
                    dimensionPhases.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
            if (obj.has("players")) {
                for (var entry : obj.getAsJsonObject("players").entrySet()) {
                    playerPhases.put(UUID.fromString(entry.getKey()), entry.getValue().getAsString());
                }
            }
            LOGGER.info("Phase state loaded: global={}, {} dims, {} players",
                    globalPhaseId, dimensionPhases.size(), playerPhases.size());
        } catch (Exception e) {
            LOGGER.error("Failed to load phase state", e);
        }
    }

    // ═══════════════ 清理 ═══════════════

    public void onPlayerLogout(UUID uuid) {
        playerPhases.remove(uuid);
    }
}
