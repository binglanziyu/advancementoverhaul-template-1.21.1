package com.dreamer.ao.network.payload;

import javax.annotation.Nonnull;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.dreamer.ao.ModInfo;
import net.minecraft.resources.ResourceLocation;

public record StatsRequestPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StatsRequestPayload> TYPE = new CustomPacketPayload.Type<>(ModInfo.rl("stats_request"));
    public static final StreamCodec<FriendlyByteBuf, StatsRequestPayload> CODEC = StreamCodec.of((buf, payload) -> {}, buf -> new StatsRequestPayload());

    @Nonnull
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
