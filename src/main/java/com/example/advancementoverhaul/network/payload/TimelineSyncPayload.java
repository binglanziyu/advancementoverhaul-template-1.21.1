/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.FriendlyByteBuf
 *  net.minecraft.network.codec.ByteBufCodecs
 *  net.minecraft.network.codec.StreamCodec
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type
 *  net.minecraft.resources.ResourceLocation
 */
package com.example.advancementoverhaul.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TimelineSyncPayload(String dataJson) implements CustomPacketPayload
{
    public static final CustomPacketPayload.Type<TimelineSyncPayload> TYPE = new CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath((String)"advancementoverhaul", (String)"timeline_sync"));
    public static final StreamCodec<FriendlyByteBuf, TimelineSyncPayload> CODEC = StreamCodec.composite((StreamCodec)ByteBufCodecs.stringUtf8((int)65536), TimelineSyncPayload::dataJson, TimelineSyncPayload::new);

    public CustomPacketPayload.Type<TimelineSyncPayload> type() {
        return TYPE;
    }
}

