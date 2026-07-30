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

public record ProgressSyncPayload(String advancementId, boolean completed, int progress, boolean pending) implements CustomPacketPayload
{
    public static final CustomPacketPayload.Type<ProgressSyncPayload> TYPE = new CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath((String)"advancementoverhaul", (String)"progress_sync"));
    public static final StreamCodec<FriendlyByteBuf, ProgressSyncPayload> CODEC = StreamCodec.composite((StreamCodec)ByteBufCodecs.STRING_UTF8, ProgressSyncPayload::advancementId, (StreamCodec)ByteBufCodecs.BOOL, ProgressSyncPayload::completed, (StreamCodec)ByteBufCodecs.VAR_INT, ProgressSyncPayload::progress, (StreamCodec)ByteBufCodecs.BOOL, ProgressSyncPayload::pending, ProgressSyncPayload::new);

    public ProgressSyncPayload {
        if (advancementId == null) {
            advancementId = "";
        }
    }

    public ProgressSyncPayload(String advancementId, boolean completed, int progress) {
        this(advancementId, completed, progress, false);
    }

    @Nonnull
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

