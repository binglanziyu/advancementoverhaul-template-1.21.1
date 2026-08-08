package com.dreamer.ao.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 统一的网络数据包发送封装。
 *
 * <p>消除散落在各处的 {@code PacketDistributor.sendToPlayer/sendToServer} 直接调用，
 * 集中维护发送语义，便于后续统一调整通道策略（如接入压缩、限速等）。</p>
 *
 * <p>分块（chunked）下发逻辑仍保留在 {@link SyncManager}，因其依赖字节级分块 +
 * 数据摘要校验（见 {@code SyncChunkPayload}），不适合用字符串切片方式重构。</p>
 */
public final class NetworkSender {

    private static final Logger LOGGER = LogManager.getLogger(NetworkSender.class);

    private NetworkSender() {
    }

    /** 服务端 → 指定玩家 */
    public static void toPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (player == null) return;
        PacketDistributor.sendToPlayer(player, payload);
    }

    /** 客户端 → 服务端 */
    public static void toServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    /** 服务端 → 所有在线玩家 */
    public static void toAll(MinecraftServer server, CustomPacketPayload payload) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /** 服务端 → 满足谓词的所有在线玩家 */
    public static void toAllIf(MinecraftServer server, CustomPacketPayload payload,
            java.util.function.Predicate<ServerPlayer> predicate) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (predicate.test(player)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
