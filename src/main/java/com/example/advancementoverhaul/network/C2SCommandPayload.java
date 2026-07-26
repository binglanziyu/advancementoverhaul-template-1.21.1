package com.example.advancementoverhaul.network;

import com.example.advancementoverhaul.ModInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nonnull;

public record C2SCommandPayload(String command) implements CustomPacketPayload {

    public static final Type<C2SCommandPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModInfo.MOD_ID, "c2s_cmd"));

    public static final StreamCodec<FriendlyByteBuf, C2SCommandPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16384), C2SCommandPayload::command,
            C2SCommandPayload::new
    );

    @Override @Nonnull
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}