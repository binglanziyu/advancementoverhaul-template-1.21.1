package com.example.advancementoverhaul.network.payload;

import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.DimensionLock;
import com.example.advancementoverhaul.data.PlayerStats;
import com.example.advancementoverhaul.data.model.CustomAdvancement;
import com.example.advancementoverhaul.data.model.VanillaAdvMeta;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.annotation.Nonnull;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record SyncPayload(int protocolVersion, String data) implements CustomPacketPayload {

    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/SyncPayload");
    public static final int PROTOCOL_VERSION = 1;
    private static final int COMPRESSION_THRESHOLD = 1024;
    public static final CustomPacketPayload.Type<SyncPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("advancementoverhaul", "sync"));
    public static final StreamCodec<FriendlyByteBuf, SyncPayload> CODEC = new StreamCodec<>() {

        @Override
        public SyncPayload decode(FriendlyByteBuf buf) {
            int version = buf.readVarInt();
            int len = buf.readVarInt();
            boolean compressed = buf.readBoolean();
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            try {
                if (compressed) {
                    bytes = decompress(bytes);
                }
                return new SyncPayload(version, new String(bytes, StandardCharsets.UTF_8));
            } catch (Exception e) {
                LOGGER.error("Failed to decompress sync payload ({} bytes, compressed={})", len, compressed, e);
                return new SyncPayload(version, "{}");
            }
        }

        @Override
        public void encode(FriendlyByteBuf buf, SyncPayload payload) {
            try {
                buf.writeVarInt(payload.protocolVersion());
                String data = payload.data();
                if (data == null) {
                    data = "{}";
                }
                byte[] raw = data.getBytes(StandardCharsets.UTF_8);
                if (raw.length > COMPRESSION_THRESHOLD) {
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
            } catch (Exception e) {
                LOGGER.error("Failed to encode SyncPayload", e);
                buf.writeVarInt(1);
                buf.writeVarInt(2);
                buf.writeBoolean(false);
                buf.writeBytes("{}".getBytes(StandardCharsets.UTF_8));
            }
        }
    };

    public SyncPayload {
        if (data == null) {
            data = "{}";
        }
    }

    private static byte[] compress(byte[] data) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(data);
        }
        return baos.toByteArray();
    }

    private static byte[] decompress(byte[] data) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data))) {
            gzip.transferTo(baos);
        }
        return baos.toByteArray();
    }

    public static SyncPayload fromServer(Map<String, CustomAdvancement> advancements, Map<String, DimensionLock> dimensionLocks, Map<String, Boolean> completions, Map<String, Integer> progress, List<String> customTabs, Set<String> disabledVanilla, Set<String> enabledVanilla, List<Map<String, String>> vanillaAdvancements, Map<String, VanillaAdvMeta> vanillaMeta, Map<String, String> vanillaParentMap, List<String> tabOrder, Set<String> pendingAdvancements, PlayerStats playerStats) {
        Map<String, Object> root = new HashMap<>();
        root.put("advancements", advancements);
        root.put("dimensionLocks", new HashMap<>(dimensionLocks));
        root.put("completions", completions);
        root.put("progress", progress);
        root.put("customTabs", customTabs);
        Map<String, Set<String>> vs = new HashMap<>();
        vs.put("disabled", disabledVanilla);
        vs.put("enabled", enabledVanilla);
        root.put("vanillaStates", vs);
        root.put("vanillaAdvancements", vanillaAdvancements);
        root.put("vanillaMeta", vanillaMeta);
        root.put("vanillaParentMap", vanillaParentMap != null ? vanillaParentMap : Map.of());
        root.put("tabOrder", tabOrder != null ? tabOrder : List.of());
        root.put("pending", pendingAdvancements != null ? pendingAdvancements : Set.of());
        root.put("playerStats", playerStats != null ? playerStats : new PlayerStats());
        try {
            return new SyncPayload(PROTOCOL_VERSION, DataStore.GSON.toJson(root));
        } catch (Exception e) {
            LOGGER.error("Failed to serialize sync payload to JSON", e);
            return new SyncPayload(PROTOCOL_VERSION, "{}");
        }
    }

    @Nonnull
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
