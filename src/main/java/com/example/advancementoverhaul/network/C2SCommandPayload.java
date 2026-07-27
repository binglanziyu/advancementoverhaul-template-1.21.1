package com.example.advancementoverhaul.network;

import com.example.advancementoverhaul.ModInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;

/**
 * 客户端→服务端命令负载。
 * <p>
 * 客户端 GUI 通过此 payload 将编辑命令发送给服务端执行。
 * 命令格式为完整的 /adv 命令字符串（如 "adv complete my_adv"）。
 * <p>
 * 字符串最大 16384 UTF-8 字节（与 {@link com.example.advancementoverhaul.network.NetworkHandler}
 * 服务端的长度检查保持一致）。
 */
public record C2SCommandPayload(String command) implements CustomPacketPayload {

    /**
     * 紧凑构造器：确保 command 永不为 null，防止 STRING_UTF8 编码 NPE。
     */
    public C2SCommandPayload {
        if (command == null) command = "";
    }

    /** Payload 类型标识符 */
    public static final Type<C2SCommandPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModInfo.MOD_ID, "c2s_cmd"));

    /** 编解码器：UTF-8 字符串 + Record 构造器 */
    public static final StreamCodec<FriendlyByteBuf, C2SCommandPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16384),
            C2SCommandPayload::command,
            C2SCommandPayload::new
    );

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
