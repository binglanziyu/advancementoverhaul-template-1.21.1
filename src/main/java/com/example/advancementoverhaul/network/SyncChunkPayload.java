package com.example.advancementoverhaul.network;

import com.example.advancementoverhaul.ModInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;

/**
 * 分块同步负载：当全量同步数据过大时，将一个 SyncPayload 拆分为多个块传输。
 *
 * <h2>工作原理</h2>
 * <ol>
 *   <li>服务端计算完整 JSON 字符串</li>
 *   <li>若超过 {@link #CHUNK_SIZE}，拆分为多个 SyncChunkPayload（每块最多 256KB 压缩前数据）</li>
 *   <li>每块携带相同的 transferId（随机生成），客户端按 chunkIndex 拼接</li>
 *   <li>客户端收到最后一块时组装完整 JSON，走普通 handleSync 逻辑</li>
 *   <li>若超时未收齐，丢弃所有已接收的块</li>
 * </ol>
 *
 * <h2>向后兼容</h2>
 * 小数据量时仍使用普通 SyncPayload。客户端同时注册两者，
 * 服务端根据数据大小选择传输方式。
 */
public record SyncChunkPayload(
        long transferId,
        int chunkIndex,
        int totalChunks,
        byte[] data
) implements CustomPacketPayload {

    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/SyncChunk");

    /** 单块最大字节数（UTF-8 编码后的数据量，不包含包头） */
    public static final int CHUNK_SIZE = 256 * 1024; // 256 KB

    /** 客户端重组超时时间（毫秒），超时丢弃未完成的传输 */
    public static final long ASSEMBLY_TIMEOUT_MS = 30_000;

    public static final Type<SyncChunkPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModInfo.MOD_ID, "sync_chunk"));

    public static final StreamCodec<FriendlyByteBuf, SyncChunkPayload> CODEC = new StreamCodec<>() {
        @Override
        public SyncChunkPayload decode(FriendlyByteBuf buf) {
            long transferId = buf.readLong();
            int chunkIndex = buf.readVarInt();
            int totalChunks = buf.readVarInt();
            int dataLen = buf.readVarInt();
            byte[] data = new byte[dataLen];
            buf.readBytes(data);
            return new SyncChunkPayload(transferId, chunkIndex, totalChunks, data);
        }

        @Override
        public void encode(FriendlyByteBuf buf, SyncChunkPayload payload) {
            buf.writeLong(payload.transferId());
            buf.writeVarInt(payload.chunkIndex());
            buf.writeVarInt(payload.totalChunks());
            buf.writeVarInt(payload.data().length);
            buf.writeBytes(payload.data());
        }
    };

    /**
     * 将一个完整 JSON 字符串拆分为多个分块负载。
     *
     * @param transferId 传输 ID（客户端用此 ID 分组重组）
     * @param fullJson   完整的 JSON 数据字符串
     * @return 分块数组（至少 1 个元素）
     */
    public static SyncChunkPayload[] split(long transferId, String fullJson) {
        byte[] allBytes = fullJson.getBytes(StandardCharsets.UTF_8);
        int totalChunks = (int) Math.ceil((double) allBytes.length / CHUNK_SIZE);
        totalChunks = Math.max(totalChunks, 1);

        SyncChunkPayload[] chunks = new SyncChunkPayload[totalChunks];
        for (int i = 0; i < totalChunks; i++) {
            int start = i * CHUNK_SIZE;
            int end = Math.min(start + CHUNK_SIZE, allBytes.length);
            byte[] chunkData = new byte[end - start];
            System.arraycopy(allBytes, start, chunkData, 0, end - start);
            chunks[i] = new SyncChunkPayload(transferId, i, totalChunks, chunkData);
        }
        return chunks;
    }

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
