package com.dreamer.ao.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FtbQuestCompletedPayload(String questName) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<FtbQuestCompletedPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("advancementoverhaul", "ftb_quest_completed"));
    public static final StreamCodec<FriendlyByteBuf, FtbQuestCompletedPayload> CODEC = StreamCodec.of((buf, payload) -> buf.writeUtf(payload.questName, 256), buf -> new FtbQuestCompletedPayload(buf.readUtf(256)));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
