package com.dreamer.ao.network.payload;

import com.dreamer.ao.ModInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 阶段数据同步包（Server → Client）。
 * <p>
 * 携带当前全局阶段、维度阶段和玩家阶段的 JSON 数据。
 */
public record PhaseSyncPayload(String dataJson) implements CustomPacketPayload {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ModInfo.MOD_ID, "phase_sync");

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(dataJson, 65536);
    }

    public static PhaseSyncPayload read(FriendlyByteBuf buf) {
        return new PhaseSyncPayload(buf.readUtf(65536));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return new Type<>(ID);
    }
}
