package com.dreamer.ao.network.payload;

import javax.annotation.Nonnull;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SCommandPayload(String command) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<C2SCommandPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("advancementoverhaul", "c2s_cmd"));
    public static final StreamCodec<FriendlyByteBuf, C2SCommandPayload> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(16384), C2SCommandPayload::command, C2SCommandPayload::new);

    public C2SCommandPayload {
        if (command == null) {
            command = "";
        }
    }

    @Nonnull
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
