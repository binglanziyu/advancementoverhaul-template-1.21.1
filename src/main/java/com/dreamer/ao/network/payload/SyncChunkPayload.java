package com.dreamer.ao.network.payload;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import javax.annotation.Nonnull;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.dreamer.ao.ModInfo;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record SyncChunkPayload(long transferId, int chunkIndex, int totalChunks, byte[] data, byte[] dataHash) implements CustomPacketPayload {

    private static final Logger LOGGER = LoggerFactory.getLogger(SyncChunkPayload.class);
    public static final int CHUNK_SIZE = 262144;
    public static final long ASSEMBLY_TIMEOUT_MS = 30000L;
    public static final CustomPacketPayload.Type<SyncChunkPayload> TYPE = new CustomPacketPayload.Type<>(ModInfo.rl("sync_chunk"));
    public static final StreamCodec<FriendlyByteBuf, SyncChunkPayload> CODEC = new StreamCodec<>() {

        @Override
        public SyncChunkPayload decode(FriendlyByteBuf buf) {
            long transferId = buf.readLong();
            int chunkIndex = buf.readVarInt();
            int totalChunks = buf.readVarInt();
            int dataLen = buf.readVarInt();
            byte[] data = new byte[dataLen];
            buf.readBytes(data);
            int hashLen = buf.readVarInt();
            byte[] dataHash = new byte[hashLen];
            buf.readBytes(dataHash);
            return new SyncChunkPayload(transferId, chunkIndex, totalChunks, data, dataHash);
        }

        @Override
        public void encode(FriendlyByteBuf buf, SyncChunkPayload payload) {
            buf.writeLong(payload.transferId());
            buf.writeVarInt(payload.chunkIndex());
            buf.writeVarInt(payload.totalChunks());
            buf.writeVarInt(payload.data().length);
            buf.writeBytes(payload.data());
            buf.writeVarInt(payload.dataHash().length);
            buf.writeBytes(payload.dataHash());
        }
    };

    /**
     * Verifies that this chunk's SHA-256 data hash matches the stored hash.
     * @return true if hash matches or hash verification is unavailable
     */
    public boolean verifyHash() {
        if (dataHash == null || dataHash.length == 0) return true;
        byte[] computed = sha256(data);
        return computed != null && Arrays.equals(computed, dataHash);
    }

    public static SyncChunkPayload[] split(long transferId, String fullJson) {
        byte[] allBytes = fullJson.getBytes(StandardCharsets.UTF_8);
        int totalChunks = (int) Math.ceil(allBytes.length / 262144.0);
        totalChunks = Math.max(totalChunks, 1);
        SyncChunkPayload[] chunks = new SyncChunkPayload[totalChunks];
        for (int i = 0; i < totalChunks; i++) {
            int start = i * CHUNK_SIZE;
            int end = Math.min(start + CHUNK_SIZE, allBytes.length);
            byte[] chunkData = new byte[end - start];
            System.arraycopy(allBytes, start, chunkData, 0, end - start);
            byte[] hash = sha256(chunkData);
            chunks[i] = new SyncChunkPayload(transferId, i, totalChunks, chunkData, hash);
        }
        return chunks;
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("SHA-256 not available", e);
            return null;
        }
    }

    @Nonnull
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
