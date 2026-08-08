package com.dreamer.ao.network.payload;

import javax.annotation.Nonnull;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.dreamer.ao.ModInfo;
import net.minecraft.resources.ResourceLocation;

public record StatsSyncPayload(String statsJson) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StatsSyncPayload> TYPE = new CustomPacketPayload.Type<>(ModInfo.rl("stats_sync"));
    public static final StreamCodec<FriendlyByteBuf, StatsSyncPayload> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(65536), StatsSyncPayload::statsJson, StatsSyncPayload::new);

    public StatsSyncPayload {
        if (statsJson == null) {
            statsJson = "{}";
        }
    }

    @Nonnull
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
