package com.example.advancementoverhaul.network;

import com.example.advancementoverhaul.ModInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;

/**
 * 客户端→服务端导入文件负载。
 * <p>
 * 客户端通过文件选择器选择 .json 文件后，将文件内容通过此 payload
 * 发送给服务端进行处理。服务端接收后调用
 * {@link com.example.advancementoverhaul.data.ServerDataStore#importAll} 执行导入。
 * <p>
 * 最大文件大小：1MB（1048576 UTF-8 字节）。
 */
public record ImportFilePayload(String content) implements CustomPacketPayload {

    /** Payload 类型标识符 */
    public static final Type<ImportFilePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModInfo.MOD_ID, "import_file"));

    /** 编解码器：UTF-8 字符串，最大 1MB */
    public static final StreamCodec<FriendlyByteBuf, ImportFilePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(1048576),
            ImportFilePayload::content,
            ImportFilePayload::new
    );

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
