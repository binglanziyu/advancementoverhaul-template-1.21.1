package com.dreamer.ao.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.dreamer.ao.ModInfo;
import net.minecraft.resources.ResourceLocation;

public record TimelineRequestPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TimelineRequestPayload> TYPE = new CustomPacketPayload.Type<>(ModInfo.rl("timeline_request"));
    public static final StreamCodec<FriendlyByteBuf, TimelineRequestPayload> CODEC = StreamCodec.of((buf, payload) -> {}, buf -> new TimelineRequestPayload());

    @Override
    public CustomPacketPayload.Type<TimelineRequestPayload> type() {
        return TYPE;
    }
}
