package com.dreamer.ao.network.payload;

import javax.annotation.Nonnull;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.dreamer.ao.ModInfo;
import net.minecraft.resources.ResourceLocation;

public record ImportFilePayload(String content) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ImportFilePayload> TYPE = new CustomPacketPayload.Type<>(ModInfo.rl("import_file"));
    public static final StreamCodec<FriendlyByteBuf, ImportFilePayload> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(0x100000), ImportFilePayload::content, ImportFilePayload::new);

    @Nonnull
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
