package com.example.advancementoverhaul.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TimelineSyncPayload(String dataJson) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TimelineSyncPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("advancementoverhaul", "timeline_sync"));
    public static final StreamCodec<FriendlyByteBuf, TimelineSyncPayload> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(65536), TimelineSyncPayload::dataJson, TimelineSyncPayload::new);

    @Override
    public CustomPacketPayload.Type<TimelineSyncPayload> type() {
        return TYPE;
    }
}
