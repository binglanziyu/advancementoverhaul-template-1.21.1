package com.example.advancementoverhaul.network;

import com.example.advancementoverhaul.ModInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * FTB 任务完成通知 Payload（Server → Client）。
 * <p>
 * 当 FTB Quests 任务完成时，服务端通过此包通知客户端。
 * 客户端根据当前 FTB 通知模式决定是否展示牌匾和音效。
 */
public record FtbQuestCompletedPayload(String questName) implements CustomPacketPayload {

    public static final Type<FtbQuestCompletedPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModInfo.MOD_ID, "ftb_quest_completed"));

    public static final StreamCodec<FriendlyByteBuf, FtbQuestCompletedPayload> CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeUtf(payload.questName, 256),
                    buf -> new FtbQuestCompletedPayload(buf.readUtf(256))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
