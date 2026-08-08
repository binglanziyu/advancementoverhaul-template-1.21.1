package com.dreamer.ao.network.handler;

import com.dreamer.ao.Config;
import com.dreamer.ao.LangKeys;
import com.dreamer.ao.ServerConstants;
import com.dreamer.ao.phase.PhaseDefinition;
import com.dreamer.ao.phase.PhaseDefinitionLoader;
import com.dreamer.ao.phase.PhaseRegistry;
import com.dreamer.ao.phase.PhaseUnlockService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dreamer.ao.network.payload.PhaseDefEditPayload;

/**
 * 阶段（Phase）网络处理委托类。
 *
 * <p>从 {@code NetworkHandler} 中抽取出来的阶段定义编辑业务逻辑
 * （可视化编辑器保存/删除、效果移除、热重载与在线 OP 同步），
 * 对齐 {@link TimelineNetworkHandler} 的「网络入口 → 业务委托」模式，
 * 使 {@code NetworkHandler} 专注于 Payload 注册与转发桩。
 */
public final class PhaseNetworkHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PhaseNetworkHandler.class);

    private PhaseNetworkHandler() {}

    /**
     * C2S：可视化编辑器保存/删除阶段定义。
     * 校验权限 → 写回 config/phases/*.json → 热重载 PhaseRegistry → 同步所有在线 OP。
     */
    public static void handlePhaseDefEdit(PhaseDefEditPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        int requiredPerm = Config.EDIT_PERMISSION_LEVEL.get();
        if (!player.hasPermissions(requiredPerm)) {
            player.sendSystemMessage(Component.translatable(LangKeys.CMD_PERM_DENIED));
            return;
        }
        String action = payload.action();
        String id = payload.id();
        if (id == null || id.isBlank()) {
            player.sendSystemMessage(Component.translatable(LangKeys.PHASE_EDIT_INVALID_ID));
            return;
        }
        if (!id.matches("[a-z0-9_]{1,64}")) {
            player.sendSystemMessage(Component.translatable(LangKeys.PHASE_EDIT_INVALID_ID));
            return;
        }
        context.enqueueWork(() -> {
            try {
                PhaseRegistry registry = PhaseRegistry.get();
                if ("remove_effect".equals(action)) {
                    String json = payload.json();
                    if (json == null || json.isBlank()) {
                        player.sendSystemMessage(Component.translatable(LangKeys.PHASE_EDIT_EMPTY));
                        return;
                    }
                    JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                    PhaseDefinition existing = PhaseRegistry.get().getById(id).orElse(null);
                    if (existing == null) {
                        player.sendSystemMessage(Component.translatable(LangKeys.PHASE_EDIT_NOT_FOUND, id));
                        return;
                    }
                    JsonObject root = existing.toJson();
                    JsonObject effects = root.has("effects") ? root.getAsJsonObject("effects") : new JsonObject();
                    String cat = obj.has("cat") ? obj.get("cat").getAsString() : "";
                    removeEffectFrom(effects, cat, obj);
                    root.add("effects", effects);
                    PhaseDefinition def = PhaseDefinition.fromJson(root);
                    PhaseDefinitionLoader.saveDef(def);
                    registry.reload();
                    player.sendSystemMessage(Component.translatable(LangKeys.PHASE_EFFECT_REMOVED, def.getId()));
                } else if ("delete".equals(action)) {
                    PhaseDefinitionLoader.deleteDef(id);
                    registry.reload();
                    player.sendSystemMessage(Component.translatable(LangKeys.PHASE_EDIT_DELETED, id));
                } else {
                    // save：解析 JSON 并写盘
                    String json = payload.json();
                    if (json == null || json.isBlank()) {
                        player.sendSystemMessage(Component.translatable(LangKeys.PHASE_EDIT_EMPTY));
                        return;
                    }
                    if (json.length() > 8192) {
                        player.sendSystemMessage(Component.translatable(LangKeys.PHASE_EDIT_TOO_LARGE));
                        return;
                    }
                    if (!checkJsonDepth(json, ServerConstants.JSON_MAX_DEPTH)) {
                        player.sendSystemMessage(Component.translatable(LangKeys.PHASE_EDIT_INVALID_JSON));
                        return;
                    }
                    JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                    PhaseDefinition def = PhaseDefinition.fromJson(obj);
                    PhaseDefinitionLoader.saveDef(def);
                    registry.reload();
                    player.sendSystemMessage(Component.translatable(LangKeys.PHASE_EDIT_SAVED, def.getId()));
                }
                // 热重载后，向所有在线 OP 重算并同步阶段态
                registry.all(); // 触发索引刷新
                for (var p : player.server.getPlayerList().getPlayers()) {
                    if (p.hasPermissions(requiredPerm)) {
                        PhaseUnlockService.get().recomputeAndSync(p);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("阶段定义编辑失败: {}", e.getMessage());
                player.sendSystemMessage(Component.translatable(LangKeys.PHASE_EDIT_FAILED, e.getMessage()));
            }
        });
    }

    /** 从 effects JSON 中移除某条效果（按 tag 标识） */
    private static void removeEffectFrom(JsonObject effects, String cat, JsonObject tag) {
        switch (cat) {
            case "attributes", "mob_mults" -> {
                if (tag.has("key") && effects.has(cat)) {
                    effects.getAsJsonObject(cat).remove(tag.get("key").getAsString());
                }
            }
            case "mob_effects" -> {
                if (tag.has("effectId") && effects.has("mob_effects")) {
                    JsonArray arr = effects.getAsJsonArray("mob_effects");
                    String target = tag.get("effectId").getAsString();
                    JsonArray out = new JsonArray();
                    for (int i = 0; i < arr.size(); i++) {
                        if (!arr.get(i).getAsJsonObject().get("id").getAsString().equals(target)) {
                            out.add(arr.get(i));
                        }
                    }
                    effects.add("mob_effects", out);
                }
            }
            case "equipment" -> {
                if (tag.has("index") && effects.has("equipment")) {
                    JsonArray arr = effects.getAsJsonArray("equipment");
                    int idx = tag.get("index").getAsInt();
                    JsonArray out = new JsonArray();
                    for (int i = 0; i < arr.size(); i++) {
                        if (i != idx) out.add(arr.get(i));
                    }
                    effects.add("equipment", out);
                }
            }
            default -> { /* 未知类别：忽略 */ }
        }
    }

    /** 校验 JSON 嵌套深度，防止 Billion Laughs 类攻击 */
    private static boolean checkJsonDepth(String json, int maxDepth) {
        int depth = 0;
        int max = 0;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{' || c == '[') {
                depth++;
                if (depth > max) max = depth;
            } else if (c == '}' || c == ']') {
                depth--;
            }
        }
        return max <= maxDepth;
    }
}
