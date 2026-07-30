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

public record C2SCommandPayload(String command) implements CustomPacketPayload
{
    public static final CustomPacketPayload.Type<C2SCommandPayload> TYPE = new CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath((String)"advancementoverhaul", (String)"c2s_cmd"));
    public static final StreamCodec<FriendlyByteBuf, C2SCommandPayload> CODEC = StreamCodec.composite((StreamCodec)ByteBufCodecs.stringUtf8((int)16384), C2SCommandPayload::command, C2SCommandPayload::new);

    public C2SCommandPayload {
        if (command == null) {
            command = "";
        }
    }

    @Nonnull
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

