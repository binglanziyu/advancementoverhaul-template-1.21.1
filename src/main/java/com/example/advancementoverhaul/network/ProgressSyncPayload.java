package com.example.advancementoverhaul.network;

import com.example.advancementoverhaul.ModInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;

/**
 * 服务端→客户端增量进度同步负载。
 * <p>
 * 当玩家的某个进度条件被更新或完成时，服务端发送此包通知客户端
 * 更新完成状态、进度百分比和 pending 标记。
 * <p>
 * 相比全量同步（{@link SyncPayload}），此包极小，适合高频发送。
 */
public record ProgressSyncPayload(
        String advancementId,
        boolean completed,
        int progress,
        boolean pending
) implements CustomPacketPayload {

    /**
     * 紧凑构造器：确保 advancementId 永不为 null，防止 STRING_UTF8 编码 NPE。
     */
    public ProgressSyncPayload {
        if (advancementId == null) advancementId = "";
    }

    /** Payload 类型标识符 */
    public static final Type<ProgressSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModInfo.MOD_ID, "progress_sync"));

    /** 编解码器：UTF-8 字符串 + boolean + varint + boolean */
    public static final StreamCodec<FriendlyByteBuf, ProgressSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,   ProgressSyncPayload::advancementId,
            ByteBufCodecs.BOOL,          ProgressSyncPayload::completed,
            ByteBufCodecs.VAR_INT,       ProgressSyncPayload::progress,
            ByteBufCodecs.BOOL,          ProgressSyncPayload::pending,
            ProgressSyncPayload::new
    );

    /** 便捷构造器：非 pending 状态（向后兼容旧调用点） */
    public ProgressSyncPayload(String advancementId, boolean completed, int progress) {
        this(advancementId, completed, progress, false);
    }

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
