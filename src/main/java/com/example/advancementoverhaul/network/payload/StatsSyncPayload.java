/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.annotation.Nonnull
 *  net.minecraft.network.FriendlyByteBuf
 *  net.minecraft.network.codec.ByteBufCodecs
 *  net.minecraft.network.codec.StreamCodec
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type
 *  net.minecraft.resources.ResourceLocation
 */
package com.example.advancementoverhaul.network.payload;

import javax.annotation.Nonnull;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record StatsSyncPayload(String statsJson) implements CustomPacketPayload
{
    public static final CustomPacketPayload.Type<StatsSyncPayload> TYPE = new CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath((String)"advancementoverhaul", (String)"stats_sync"));
    public static final StreamCodec<FriendlyByteBuf, StatsSyncPayload> CODEC = StreamCodec.composite((StreamCodec)ByteBufCodecs.stringUtf8((int)65536), StatsSyncPayload::statsJson, StatsSyncPayload::new);

    public StatsSyncPayload {
        if (statsJson == null) {
            statsJson = "{}";
        }
    }

    @Nonnull
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

