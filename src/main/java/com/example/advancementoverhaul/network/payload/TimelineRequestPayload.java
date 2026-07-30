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

public record TimelineRequestPayload() implements CustomPacketPayload
{
    public static final CustomPacketPayload.Type<TimelineRequestPayload> TYPE = new CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath((String)"advancementoverhaul", (String)"timeline_request"));
    public static final StreamCodec<FriendlyByteBuf, TimelineRequestPayload> CODEC = StreamCodec.of((buf, payload) -> {}, buf -> new TimelineRequestPayload());

    public CustomPacketPayload.Type<TimelineRequestPayload> type() {
        return TYPE;
    }
}

