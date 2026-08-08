package com.dreamer.ao.network.payload;

import javax.annotation.Nonnull;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.dreamer.ao.ModInfo;
import net.minecraft.resources.ResourceLocation;

public record ProgressSyncPayload(String advancementId, boolean completed, int progress, boolean pending) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ProgressSyncPayload> TYPE = new CustomPacketPayload.Type<>(ModInfo.rl("progress_sync"));
    public static final StreamCodec<FriendlyByteBuf, ProgressSyncPayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ProgressSyncPayload::advancementId, ByteBufCodecs.BOOL, ProgressSyncPayload::completed, ByteBufCodecs.VAR_INT, ProgressSyncPayload::progress, ByteBufCodecs.BOOL, ProgressSyncPayload::pending, ProgressSyncPayload::new);

    public ProgressSyncPayload {
        if (advancementId == null) {
            advancementId = "";
        }
    }

    public ProgressSyncPayload(String advancementId, boolean completed, int progress) {
        this(advancementId, completed, progress, false);
    }

    @Nonnull
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
