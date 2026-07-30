/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.network.FriendlyByteBuf
 *  net.minecraft.network.codec.ByteBufCodecs
 *  net.minecraft.network.codec.StreamCodec
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload$Type
 *  net.minecraft.resources.ResourceLocation
 *  net.neoforged.neoforge.network.handling.IPayloadContext
 */
package com.example.advancementoverhaul.network.payload;

import com.example.advancementoverhaul.client.MonologueManager;
import com.example.advancementoverhaul.data.NarrativeConfigLoader;
import com.example.advancementoverhaul.data.model.EchoEntry;
import java.util.Random;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MonologuePayload(String category) implements CustomPacketPayload
{
    public static final CustomPacketPayload.Type<MonologuePayload> TYPE = new CustomPacketPayload.Type(ResourceLocation.fromNamespaceAndPath((String)"advancementoverhaul", (String)"monologue"));
    public static final StreamCodec<FriendlyByteBuf, MonologuePayload> STREAM_CODEC = StreamCodec.composite((StreamCodec)ByteBufCodecs.STRING_UTF8, MonologuePayload::category, MonologuePayload::new);

    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MonologuePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            String category = payload.category();
            if ("custom".equals(category)) {
                return;
            }
            if (category.startsWith("echo:")) {
                String echoId = category.substring(5);
                EchoEntry echo = NarrativeConfigLoader.getInstance().getEchoes().get(echoId);
                if (echo != null && echo.getTexts() != null && echo.getTexts().length > 0) {
                    String[] texts = echo.getTexts();
                    String text = texts[new Random().nextInt(texts.length)];
                    MonologueManager.showCustom(text);
                }
            } else {
                MonologueManager.tryTrigger(category);
            }
        });
    }
}

