/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.annotation.Nonnull
 *  net.minecraft.network.FriendlyByteBuf
 *  net.minecraft.network.codec.StreamCodec
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type
 *  net.minecraft.resources.ResourceLocation
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.example.advancementoverhaul.network.payload;

import java.nio.charset.StandardCharsets;
import javax.annotation.Nonnull;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record SyncChunkPayload(long transferId, int chunkIndex, int totalChunks, byte[] data) implements CustomPacketPayload
{
    private static final Logger LOGGER = LoggerFactory.getLogger((String)"AdvancementOverhaul/SyncChunk");
    public static final int CHUNK_SIZE = 262144;
    public static final long ASSEMBLY_TIMEOUT_MS = 30000L;
    public static final CustomPacketPayload.Type<SyncChunkPayload> TYPE = new CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath((String)"advancementoverhaul", (String)"sync_chunk"));
    public static final StreamCodec<FriendlyByteBuf, SyncChunkPayload> CODEC = new StreamCodec<FriendlyByteBuf, SyncChunkPayload>(){

        public SyncChunkPayload decode(FriendlyByteBuf buf) {
            long transferId = buf.readLong();
            int chunkIndex = buf.readVarInt();
            int totalChunks = buf.readVarInt();
            int dataLen = buf.readVarInt();
            byte[] data = new byte[dataLen];
            buf.readBytes(data);
            return new SyncChunkPayload(transferId, chunkIndex, totalChunks, data);
        }

        public void encode(FriendlyByteBuf buf, SyncChunkPayload payload) {
            buf.writeLong(payload.transferId());
            buf.writeVarInt(payload.chunkIndex());
            buf.writeVarInt(payload.totalChunks());
            buf.writeVarInt(payload.data().length);
            buf.writeBytes(payload.data());
        }
    };

    public static SyncChunkPayload[] split(long transferId, String fullJson) {
        byte[] allBytes = fullJson.getBytes(StandardCharsets.UTF_8);
        int totalChunks = (int)Math.ceil((double)allBytes.length / 262144.0);
        totalChunks = Math.max(totalChunks, 1);
        SyncChunkPayload[] chunks = new SyncChunkPayload[totalChunks];
        for (int i = 0; i < totalChunks; ++i) {
            int start = i * 262144;
            int end = Math.min(start + 262144, allBytes.length);
            byte[] chunkData = new byte[end - start];
            System.arraycopy(allBytes, start, chunkData, 0, end - start);
            chunks[i] = new SyncChunkPayload(transferId, i, totalChunks, chunkData);
        }
        return chunks;
    }

    @Nonnull
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

