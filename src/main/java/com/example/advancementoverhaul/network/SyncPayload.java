package com.example.advancementoverhaul.network;

import com.example.advancementoverhaul.ModInfo;
import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.DimensionLock;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.util.*;

public record SyncPayload(String data) implements CustomPacketPayload {

    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/SyncPayload");

    public static final Type<SyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModInfo.MOD_ID, "sync"));

    public static final StreamCodec<FriendlyByteBuf, SyncPayload> CODEC = new StreamCodec<>() {
        @Override public SyncPayload decode(FriendlyByteBuf buf) {
            int len = buf.readVarInt();
            boolean compressed = buf.readBoolean();
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            try {
                if (compressed) bytes = decompress(bytes);
                return new SyncPayload(new String(bytes, StandardCharsets.UTF_8));
            } catch (Exception e) {
                LOGGER.error("Failed to decompress sync payload ({} bytes, compressed={})", len, compressed, e);
                return new SyncPayload("{}");
            }
        }
        @Override public void encode(FriendlyByteBuf buf, SyncPayload payload) {
            byte[] raw = payload.data().getBytes(StandardCharsets.UTF_8);
            if (raw.length > 1024) {
                try {
                    byte[] compressed = compress(raw);
                    buf.writeVarInt(compressed.length);
                    buf.writeBoolean(true);
                    buf.writeBytes(compressed);
                    return;
                } catch (Exception e) {
                    LOGGER.warn("Compression failed ({} bytes), sending uncompressed", raw.length, e);
                }
            }
            buf.writeVarInt(raw.length);
            buf.writeBoolean(false);
            buf.writeBytes(raw);
        }
    };

    private static byte[] compress(byte[] data) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) { gzip.write(data); }
        return baos.toByteArray();
    }

    private static byte[] decompress(byte[] data) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data))) { gzip.transferTo(baos); }
        return baos.toByteArray();
    }

    public static SyncPayload fromServer(
            Map<String, DataStore.CustomAdvancement> advancements,
            Map<String, DimensionLock> dimensionLocks,
            Map<String, Boolean> completions,
            Map<String, Integer> progress,
            List<String> customTabs,
            Set<String> disabledVanilla,
            Set<String> enabledVanilla,
            List<Map<String, String>> vanillaAdvancements,
            Map<String, DataStore.VanillaAdvMeta> vanillaMeta,
            Map<String, String> vanillaParentMap,
            List<String> tabOrder,
            Set<String> pendingAdvancements
    ) {
        Map<String, Object> root = new HashMap<>();
        root.put("advancements", advancements);
        root.put("dimensionLocks", new HashMap<>(dimensionLocks));
        root.put("completions", completions);
        root.put("progress", progress);
        root.put("customTabs", customTabs);
        Map<String, Object> vs = new HashMap<>();
        vs.put("disabled", disabledVanilla);
        vs.put("enabled", enabledVanilla);
        root.put("vanillaStates", vs);
        root.put("vanillaAdvancements", vanillaAdvancements);
        root.put("vanillaMeta", vanillaMeta);
        root.put("vanillaParentMap", vanillaParentMap != null ? vanillaParentMap : Map.of());
        root.put("tabOrder", tabOrder != null ? tabOrder : List.of());
        root.put("pending", pendingAdvancements != null ? pendingAdvancements : Set.of());
        return new SyncPayload(DataStore.GSON.toJson(root));
    }

    @Override @Nonnull public Type<? extends CustomPacketPayload> type() { return TYPE; }
}