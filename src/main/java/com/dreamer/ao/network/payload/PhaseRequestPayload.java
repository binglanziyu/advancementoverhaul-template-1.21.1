package com.dreamer.ao.network.payload;

import com.dreamer.ao.ModInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * 客户端 → 服务端：请求当前阶段态。
 * <p>
 * 仿 {@link TimelineRequestPayload}。客户端打开阶段面板时发送，服务端回推 {@link PhaseSyncPayload}。
 */
public record PhaseRequestPayload() implements CustomPacketPayload {

    public static final Type<PhaseRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModInfo.MOD_ID, "phase_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PhaseRequestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> { /* 无字段 */ },
                    buf -> new PhaseRequestPayload()
            );

    @Override
    public @NotNull Type<PhaseRequestPayload> type() {
        return TYPE;
    }
}
