package com.dreamer.ao.network.payload;

import com.dreamer.ao.ModInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * 客户端 → 服务端：可视化编辑器保存/删除阶段定义。
 * <p>
 * action: "save" 表示新建或覆盖（携带定义 JSON）；"delete" 表示删除（仅需 id）。
 */
public record PhaseDefEditPayload(String action, String id, String json) implements CustomPacketPayload {

    public static final Type<PhaseDefEditPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModInfo.MOD_ID, "phase_def_edit"));

    private static final StreamCodec<RegistryFriendlyByteBuf, String> NULLABLE_STRING =
            StreamCodec.of(
                    (buf, s) -> {
                        if (s == null) {
                            buf.writeBoolean(false);
                        } else {
                            buf.writeBoolean(true);
                            ByteBufCodecs.STRING_UTF8.encode(buf, s);
                        }
                    },
                    buf -> buf.readBoolean() ? ByteBufCodecs.STRING_UTF8.decode(buf) : null
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, PhaseDefEditPayload> STREAM_CODEC =
            StreamCodec.composite(
                    NULLABLE_STRING, PhaseDefEditPayload::action,
                    NULLABLE_STRING, PhaseDefEditPayload::id,
                    NULLABLE_STRING, PhaseDefEditPayload::json,
                    PhaseDefEditPayload::new
            );

    @Override
    public @NotNull Type<PhaseDefEditPayload> type() {
        return TYPE;
    }
}
