package com.example.advancementoverhaul.network;

import com.example.advancementoverhaul.ModInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;

public record ProgressSyncPayload(
        String advancementId,
        boolean completed,
        int progress,
        boolean pending
) implements CustomPacketPayload {

    public static final Type<ProgressSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModInfo.MOD_ID, "progress_sync"));

    public static final StreamCodec<FriendlyByteBuf, ProgressSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ProgressSyncPayload::advancementId,
            ByteBufCodecs.BOOL, ProgressSyncPayload::completed,
            ByteBufCodecs.VAR_INT, ProgressSyncPayload::progress,
            ByteBufCodecs.BOOL, ProgressSyncPayload::pending,
            ProgressSyncPayload::new
    );

    /** Convenience: not-pending constructor (backward compatible call sites) */
    public ProgressSyncPayload(String advancementId, boolean completed, int progress) {
        this(advancementId, completed, progress, false);
    }

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}