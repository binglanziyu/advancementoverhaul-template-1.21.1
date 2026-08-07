package com.dreamer.ao.network;

import com.dreamer.ao.data.PhaseConfigLoader;
import com.dreamer.ao.data.PhaseStore;
import com.dreamer.ao.data.model.PhaseDefinition;
import com.dreamer.ao.network.payload.PhaseSyncPayload;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collection;

/**
 * 阶段网络处理：构建同步数据包并发送给客户端。
 */
public final class PhaseNetworkHandler {

    private PhaseNetworkHandler() {}

    /**
     * 向单个玩家发送阶段同步数据。
     */
    public static void syncToPlayer(ServerPlayer player) {
        String json = buildSyncJson();
        PacketDistributor.sendToPlayer(player, new PhaseSyncPayload(json));
    }

    /**
     * 向所有在线玩家广播阶段同步数据。
     */
    public static void syncAll(net.minecraft.server.MinecraftServer server) {
        String json = buildSyncJson();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(p, new PhaseSyncPayload(json));
        }
    }

    /**
     * 构建阶段同步 JSON。
     * <pre>
     * {
     *   "global": "blood_moon",
     *   "dimensions": { "minecraft:the_nether": "hardened" },
     *   "phases": [ ... all phase definitions ... ]
     * }
     * </pre>
     */
    private static String buildSyncJson() {
        PhaseStore store = PhaseStore.getInstance();
        JsonObject obj = new JsonObject();

        String globalId = store.getGlobalPhaseId();
        obj.addProperty("global", globalId != null ? globalId : "");

        JsonObject dims = new JsonObject();
        for (var entry : store.getAllDimensionPhases().entrySet()) {
            dims.addProperty(entry.getKey(), entry.getValue());
        }
        obj.add("dimensions", dims);

        JsonArray phasesArr = new JsonArray();
        Collection<PhaseDefinition> allPhases = PhaseConfigLoader.getInstance().getAllPhases();
        for (PhaseDefinition def : allPhases) {
            phasesArr.add(def.toJson());
        }
        obj.add("phases", phasesArr);

        return obj.toString();
    }
}
