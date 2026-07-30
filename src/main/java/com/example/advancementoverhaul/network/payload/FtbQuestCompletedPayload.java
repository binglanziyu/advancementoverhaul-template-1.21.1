/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.FriendlyByteBuf
 *  net.minecraft.network.codec.StreamCodec
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type
 *  net.minecraft.resources.ResourceLocation
 */
package com.example.advancementoverhaul.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FtbQuestCompletedPayload(String questName) implements CustomPacketPayload
{
    public static final CustomPacketPayload.Type<FtbQuestCompletedPayload> TYPE = new CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath((String)"advancementoverhaul", (String)"ftb_quest_completed"));
    public static final StreamCodec<FriendlyByteBuf, FtbQuestCompletedPayload> CODEC = StreamCodec.of((buf, payload) -> buf.writeUtf(payload.questName, 256), buf -> new FtbQuestCompletedPayload(buf.readUtf(256)));

    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

