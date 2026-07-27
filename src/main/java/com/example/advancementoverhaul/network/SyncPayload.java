package com.example.advancementoverhaul.network;

import com.example.advancementoverhaul.ModInfo;
import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.DimensionLock;
import com.example.advancementoverhaul.data.model.VanillaAdvMeta;
import com.example.advancementoverhaul.data.model.CustomAdvancement;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 服务端→客户端全量数据同步负载。
 *
 * <h2>内容</h2>
 * 包含：所有自定义进度、维度锁、玩家完成状态、进度、自定义标签页、
 * 原版启用/禁用状态、原版进度列表、原版元数据、标签页顺序、pending 列表。
 *
 * <h2>压缩策略</h2>
 * 数据大于 1KB 时自动使用 GZIP 压缩以节省网络带宽。
 * 编解码器通过一个 boolean 标记是否已压缩。
 *
 * <h2>触发时机</h2>
 * <ul>
 *   <li>玩家登录</li>
 *   <li>执行 /adv reload</li>
 *   <li>数据变更后调用 {@link com.example.advancementoverhaul.network.SyncManager#syncPlayer}</li>
 * </ul>
 */
public record SyncPayload(int protocolVersion, String data) implements CustomPacketPayload {

    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/SyncPayload");

    /** 协议版本号：当同步格式变更时递增，客户端检查版本决定是否接受数据包 */
    public static final int PROTOCOL_VERSION = 1;

    /**
     * 紧凑构造器：确保 data 永不为 null，防止 encode 阶段 NPE。
     */
    public SyncPayload {
        if (data == null) data = "{}";
    }

    /** GZIP 压缩阈值（字节），超过此大小则压缩以节省网络带宽 */
    private static final int COMPRESSION_THRESHOLD = 1024;

    /** Payload 类型标识符 */
    public static final Type<SyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModInfo.MOD_ID, "sync"));

    /** 编解码器：支持 GZIP 压缩/解压缩 + 协议版本协商 */
    public static final StreamCodec<FriendlyByteBuf, SyncPayload> CODEC = new StreamCodec<>() {

        @Override
        public SyncPayload decode(FriendlyByteBuf buf) {
            int version = buf.readVarInt();
            int len = buf.readVarInt();
            boolean compressed = buf.readBoolean();
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            try {
                if (compressed) bytes = decompress(bytes);
                return new SyncPayload(version, new String(bytes, StandardCharsets.UTF_8));
            } catch (Exception e) {
                LOGGER.error("Failed to decompress sync payload ({} bytes, compressed={})", len, compressed, e);
                return new SyncPayload(version, "{}");
            }
        }

        @Override
        public void encode(FriendlyByteBuf buf, SyncPayload payload) {
            try {
                // 写入协议版本号
                buf.writeVarInt(payload.protocolVersion());
                String data = payload.data();
                if (data == null) data = "{}";
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
                // 写入空对象，避免 Netty 抛出 EncoderException 踢出玩家
                buf.writeVarInt(PROTOCOL_VERSION);
                buf.writeVarInt(2);
                buf.writeBoolean(false);
                buf.writeBytes("{}".getBytes(StandardCharsets.UTF_8));
            }
        }
    };

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

    /**
     * 从服务端数据构建全量同步 JSON。
     * <p>
     * 将所有服务端数据序列化为一个 JSON 对象。
     * 数据较大时编解码器会自动压缩。
     */
    public static SyncPayload fromServer(
            Map<String, CustomAdvancement> advancements,
            Map<String, DimensionLock> dimensionLocks,
            Map<String, Boolean> completions,
            Map<String, Integer> progress,
            List<String> customTabs,
            Set<String> disabledVanilla,
            Set<String> enabledVanilla,
            List<Map<String, String>> vanillaAdvancements,
            Map<String, VanillaAdvMeta> vanillaMeta,
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

        try {
            return new SyncPayload(PROTOCOL_VERSION, DataStore.GSON.toJson(root));
        } catch (Exception e) {
            LOGGER.error("Failed to serialize sync payload to JSON", e);
            return new SyncPayload(PROTOCOL_VERSION, "{}");
        }
    }

    @Override
    @Nonnull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
