package com.dreamer.ao.network.payload;

import com.dreamer.ao.ModInfo;
import com.dreamer.ao.phase.PhaseDefinition;
import com.dreamer.ao.phase.PhaseEffectSet;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * 服务端 → 客户端：推送当前阶段态摘要。
 * <p>
 * 包含世界/维度/玩家/临时阶段、已解锁集合，以及每个阶段的简要信息（含 effects 的 JSON 字符串，供面板展示真实效果）。
 */
public record PhaseSyncPayload(String worldPhase,
                               Map<String, String> dimensionPhases,
                               String playerPhase,
                               String tempPhase,
                               List<String> unlockedPhases,
                               List<String> defBriefs) implements CustomPacketPayload {

    private static final Gson GSON = new GsonBuilder().create();

    public static final Type<PhaseSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModInfo.MOD_ID, "phase_sync"));

    /** 可空字符串 codec：用 1 字节标志位区分 null / 非 null，避免 ByteBufCodecs.STRING_UTF8 对 null 触发 NPE */
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

    public static final StreamCodec<RegistryFriendlyByteBuf, PhaseSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    NULLABLE_STRING, PhaseSyncPayload::worldPhase,
                    ByteBufCodecs.map(java.util.HashMap::new, ByteBufCodecs.STRING_UTF8, NULLABLE_STRING),
                    PhaseSyncPayload::dimensionPhases,
                    NULLABLE_STRING, PhaseSyncPayload::playerPhase,
                    NULLABLE_STRING, PhaseSyncPayload::tempPhase,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), PhaseSyncPayload::unlockedPhases,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), PhaseSyncPayload::defBriefs,
                    PhaseSyncPayload::new
            );

    @Override
    public @NotNull Type<PhaseSyncPayload> type() {
        return TYPE;
    }

    /** 把一个阶段定义序列化为 brief JSON（id/name/scope/dimension/effects） */
    public static String defToBrief(PhaseDefinition def) {
        JsonObject o = new JsonObject();
        o.addProperty("id", def.getId());
        o.addProperty("name", def.getName());
        o.addProperty("scope", def.getScope());
        if (def.getDimension() != null) {
            o.addProperty("dimension", def.getDimension());
        }
        o.add("effects", def.getEffects().toJson());
        if (def.getUnlockMilestone() != null) {
            o.addProperty("unlockMilestone", def.getUnlockMilestone());
        }
        return GSON.toJson(o);
    }

    /** 把 brief JSON 解析回（用于面板） */
    public static JsonObject briefToJson(String brief) {
        return GSON.fromJson(brief, JsonObject.class);
    }

    public static List<JsonObject> briefsToJson(List<String> briefs) {
        List<JsonObject> out = new ArrayList<>();
        for (String b : briefs) {
            out.add(briefToJson(b));
        }
        return out;
    }
}
