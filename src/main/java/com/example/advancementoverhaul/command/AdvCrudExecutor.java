package com.example.advancementoverhaul.command;

import com.example.advancementoverhaul.LangKeys;
import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.DataStore.*;
import com.example.advancementoverhaul.data.model.AdvancementCondition;
import com.example.advancementoverhaul.data.model.CustomAdvancement;
import com.example.advancementoverhaul.data.model.VanillaAdvMeta;
import com.example.advancementoverhaul.data.ServerDataStore;
import com.example.advancementoverhaul.logic.ConditionEvaluator;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * /adv 命令 — CRUD 操作执行器。
 *
 * <h2>包含的子命令</h2>
 * <ul>
 *   <li>delete / batchdelete — 删除自定义进度</li>
 *   <li>setname / setdescription / seticon / togglehidden / setprereq — 修改进度属性</li>
 *   <li>createjson / updatejson — JSON 创建/更新进度</li>
 * </ul>
 * <p>
 * 所有方法要求调用 {@link CommandHelper#checkPerm} 权限检查。
 * setprereq 包含循环依赖检测（BFS 可达性），防止 A→B→A 的循环。
 */
final class AdvCrudExecutor {

    // ═══════════════ 删除 ═══════════════

    /** 删除单个进度（自动保存 + 同步） */
    static int deleteAdvancement(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String id = StringArgumentType.getString(ctx, "id");
        if (ServerDataStore.getInstance().getAdvancement(id) == null) {
            ctx.getSource().sendFailure(
                    CommandHelper.translatable(LangKeys.CMD_ADV_NOT_FOUND, id));
            return 0;
        }
        ServerDataStore.getInstance().removeAdvancement(id);
        CommandHelper.syncAll(ctx);
        ctx.getSource().sendSuccess(
                () -> CommandHelper.translatable(LangKeys.CMD_ADV_DELETED, id), true);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * 批量删除进度。
     * 逐个调用 removeAdvancementNoSave（避免重复文件 I/O），最后统一保存。
     * 不存在的 ID 会在结果中反馈给用户。
     */
    static int batchDelete(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String idsStr = StringArgumentType.getString(ctx, "ids");
        ServerDataStore store = ServerDataStore.getInstance();
        int count = 0;
        List<String> toDelete = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        for (String id : idsStr.split(",")) {
            id = id.trim();
            if (id.isEmpty()) continue;
            if (store.getAdvancement(id) != null) {
                toDelete.add(id);
                count++;
            } else {
                notFound.add(id);
            }
        }
        for (String id : toDelete) {
            store.removeAdvancementNoSave(id);
        }
        if (count > 0) {
            store.saveAdvancements();
            CommandHelper.syncAll(ctx);
        }
        final int fc = count;
        ctx.getSource().sendSuccess(
                () -> CommandHelper.translatable(LangKeys.CMD_ADV_BATCH_DELETED, fc), true);

        // 反馈跳过的无效 ID
        if (!notFound.isEmpty()) {
            ctx.getSource().sendFailure(
                    Component.translatable(LangKeys.CMD_BATCH_SKIPPED_NOT_FOUND,
                            String.join(", ", notFound)));
        }
        return Command.SINGLE_SUCCESS;
    }

    // ═══════════════ 属性修改 ═══════════════

    /** 设置进度名称 */
    static int setName(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String data = StringArgumentType.getString(ctx, "data");
        String id = CommandHelper.splitId(data);
        String name = CommandHelper.splitRest(data);
        if (name.isEmpty()) {
            ctx.getSource().sendFailure(
                    CommandHelper.translatable(LangKeys.CMD_PARSE_FAILED, "Expected: <id> <name>"));
            return 0;
        }
        CustomAdvancement adv = ServerDataStore.getInstance().getAdvancement(id);
        if (adv == null) {
            ctx.getSource().sendFailure(CommandHelper.translatable(LangKeys.CMD_ADV_NOT_FOUND, id));
            return 0;
        }
        adv.setName(name);
        ServerDataStore.getInstance().addAdvancement(adv);
        CommandHelper.syncAll(ctx);
        ctx.getSource().sendSuccess(
                () -> CommandHelper.translatable(LangKeys.CMD_ADV_NAME_CHANGED, name), true);
        return Command.SINGLE_SUCCESS;
    }

    /** 设置进度描述 */
    static int setDescription(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String data = StringArgumentType.getString(ctx, "data");
        String id = CommandHelper.splitId(data);
        String desc = CommandHelper.splitRest(data);
        CustomAdvancement adv = ServerDataStore.getInstance().getAdvancement(id);
        if (adv == null) {
            ctx.getSource().sendFailure(CommandHelper.translatable(LangKeys.CMD_ADV_NOT_FOUND, id));
            return 0;
        }
        adv.setDescription(desc);
        ServerDataStore.getInstance().addAdvancement(adv);
        CommandHelper.syncAll(ctx);
        ctx.getSource().sendSuccess(
                () -> Component.translatable(LangKeys.CMD_ADV_DESC_SET), true);
        return Command.SINGLE_SUCCESS;
    }

    /** 设置进度图标 */
    static int setIcon(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String data = StringArgumentType.getString(ctx, "data");
        String id = CommandHelper.splitId(data);
        String icon = CommandHelper.splitRest(data);
        CustomAdvancement adv = ServerDataStore.getInstance().getAdvancement(id);
        if (adv == null) {
            ctx.getSource().sendFailure(CommandHelper.translatable(LangKeys.CMD_ADV_NOT_FOUND, id));
            return 0;
        }
        adv.setIcon(icon.isEmpty() ? null : icon);
        ServerDataStore.getInstance().addAdvancement(adv);
        CommandHelper.syncAll(ctx);
        ctx.getSource().sendSuccess(() -> Component.translatable(LangKeys.CMD_ICON_SET), true);
        return Command.SINGLE_SUCCESS;
    }

    /** 切换进度隐藏状态 */
    static int toggleHidden(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String id = StringArgumentType.getString(ctx, "id");
        CustomAdvancement adv = ServerDataStore.getInstance().getAdvancement(id);
        if (adv == null) {
            ctx.getSource().sendFailure(CommandHelper.translatable(LangKeys.CMD_ADV_NOT_FOUND, id));
            return 0;
        }
        adv.setHidden(!adv.isHidden());
        ServerDataStore.getInstance().addAdvancement(adv);
        CommandHelper.syncAll(ctx);
        ctx.getSource().sendSuccess(
                () -> CommandHelper.translatable(LangKeys.CMD_ADV_HIDDEN_STATE, adv.isHidden()), true);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * 设置前置条件。
     * 支持自定义进度和原版进度。包含循环依赖检测。
     * 设置后对所有在线玩家执行级联释放。
     */
    static int setPrereq(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String data = StringArgumentType.getString(ctx, "data");
        String id = CommandHelper.splitId(data);
        String prereqStr = CommandHelper.splitRest(data);
        ServerDataStore store = ServerDataStore.getInstance();

        boolean isCustom = store.getAdvancement(id) != null;
        boolean isVanilla = !isCustom
                && CommandHelper.collectAllVanillaIds(ctx).contains(id);

        if (!isCustom && !isVanilla) {
            ctx.getSource().sendFailure(
                    CommandHelper.translatable(LangKeys.CMD_ADV_NOT_FOUND, id));
            return 0;
        }

        // 解析 + 验证前置条件列表
        List<String> prereqs = new ArrayList<>();
        if (!prereqStr.isEmpty()) {
            for (String p : prereqStr.split(",")) {
                p = p.trim();
                if (p.isEmpty()) continue;
                // 不能将自己设为自己的前置条件
                if (p.equals(id)) {
                    ctx.getSource().sendFailure(
                            Component.translatable(LangKeys.CMD_PREREQ_SELF_REFERENCE));
                    return 0;
                }
                boolean exists = store.getAdvancement(p) != null
                        || CommandHelper.collectAllVanillaIds(ctx).contains(p);
                if (!exists) {
                    ctx.getSource().sendFailure(
                            CommandHelper.translatable(LangKeys.CMD_PREREQ_NOT_FOUND, p));
                    return 0;
                }
                prereqs.add(p);
            }
        }

        // 循环依赖检测（BFS）
        if (!prereqs.isEmpty() && wouldCreateCycle(id, prereqs, store)) {
            ctx.getSource().sendFailure(
                    Component.translatable(LangKeys.CMD_PREREQ_CYCLE_DETECTED));
            return 0;
        }

        // 设置
        if (isVanilla) {
            VanillaAdvMeta meta = store.getVanillaMeta(id);
            if (meta == null) meta = new VanillaAdvMeta();
            meta.setPrerequisites(prereqs);
            store.setVanillaMeta(id, meta);
        } else {
            CustomAdvancement adv = store.getAdvancement(id);
            adv.setPrerequisites(prereqs);
            store.addAdvancement(adv);
        }

        // 级联释放（新前置条件可能立刻满足某些 pending 进度）
        if (ctx.getSource().getServer() != null) {
            for (ServerPlayer player :
                    ctx.getSource().getServer().getPlayerList().getPlayers()) {
                ConditionEvaluator.releasePendingDependents(player);
            }
        }
        CommandHelper.syncAll(ctx);
        ctx.getSource().sendSuccess(
                () -> Component.translatable(LangKeys.CMD_ADV_PREREQ_SET), true);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * 循环依赖检测：如果设置 id 依赖 prereqs 中的任何项，
     * 而这些项又（直接或间接）依赖 id，则形成循环。
     * 使用 BFS 从每个 prereq 出发，检查是否能到达 id。
     */
    private static boolean wouldCreateCycle(String id, List<String> prereqs, ServerDataStore store) {
        for (String prereq : prereqs) {
            Set<String> visited = new HashSet<>();
            Deque<String> queue = new ArrayDeque<>();
            queue.add(prereq);
            visited.add(id); // 从 id 出发，不能回到 id
            while (!queue.isEmpty()) {
                String current = queue.poll();
                if (current.equals(id)) return true;
                if (!visited.add(current)) continue;
                CustomAdvancement adv = store.getAdvancement(current);
                if (adv != null) {
                    for (String p : adv.getPrerequisites()) {
                        if (!visited.contains(p)) queue.add(p);
                    }
                }
            }
        }
        return false;
    }

    // ═══════════════ JSON CRUD ═══════════════

    /** 从 JSON 创建新进度 */
    static int createFromJson(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String json = StringArgumentType.getString(ctx, "json");
        try {
            Map<String, Object> data = DataStore.GSON_PRETTY.fromJson(json,
                    new TypeToken<Map<String, Object>>() {}.getType());
            if (data == null) {
                ctx.getSource().sendFailure(
                        Component.translatable(LangKeys.CMD_JSON_EMPTY));
                return 0;
            }
            String id = data.get("id") instanceof String s ? s : null;
            if (id == null || id.isEmpty()) {
                ctx.getSource().sendFailure(
                        Component.translatable(LangKeys.CMD_JSON_MISSING_NAME));
                return 0;
            }

            CustomAdvancement adv = new CustomAdvancement();
            adv.setId(id);
            applyJsonToAdvancement(adv, data);
            ServerDataStore.getInstance().addAdvancement(adv);
            CommandHelper.syncAll(ctx);

            final String fname = adv.getName();
            ctx.getSource().sendSuccess(
                    () -> CommandHelper.translatable(LangKeys.CMD_ADV_NAME_CHANGED, fname), true);
            return Command.SINGLE_SUCCESS;
        } catch (JsonSyntaxException e) {
            ctx.getSource().sendFailure(
                    CommandHelper.translatable(LangKeys.CMD_JSON_ERROR, friendlyJsonError(e.getMessage())));
        } catch (Exception e) {
            ctx.getSource().sendFailure(
                    CommandHelper.translatable(LangKeys.CMD_PARSE_FAILED, e.getMessage()));
        }
        return 0;
    }

    /**
     * 从 JSON 更新已有进度（不存在则创建）。
     * 支持 Base64 编码（兼容旧格式）和原始 JSON。
     * <p>
     * 如果 JSON 仅包含位置（x/y）或分类（tab）字段而无其他属性变更，
     * 则跳过 FTB Quests KnownServerRegistries 同步。
     */
    static int updateFromJson(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String raw = StringArgumentType.getString(ctx, "json");
        try {
            // Base64 解码（兼容旧格式）
            String json;
            try {
                json = new String(java.util.Base64.getDecoder().decode(raw),
                        java.nio.charset.StandardCharsets.UTF_8);
            } catch (IllegalArgumentException notBase64) {
                json = raw;
            }

            Map<String, Object> data = DataStore.GSON_PRETTY.fromJson(json,
                    new TypeToken<Map<String, Object>>() {}.getType());
            if (data == null) {
                ctx.getSource().sendFailure(
                        Component.translatable(LangKeys.CMD_JSON_EMPTY));
                return 0;
            }
            String id = data.get("id") instanceof String s ? s : null;
            if (id == null || id.isEmpty()) {
                ctx.getSource().sendFailure(
                        Component.translatable(LangKeys.CMD_JSON_MISSING_NAME));
                return 0;
            }

            // 不存在则创建，存在则更新
            CustomAdvancement adv = ServerDataStore.getInstance().getAdvancement(id);
            if (adv == null) {
                adv = new CustomAdvancement();
                adv.setId(id);
            }

            // 判断是否包含属性级变更（非位置、非分类）
            boolean hasAttrChanges = hasAttributeChanges(data);

            applyJsonToAdvancement(adv, data);
            ServerDataStore.getInstance().addAdvancement(adv, hasAttrChanges);

            // 位置/分类变更不同步 FTB Quests，属性变更才同步
            CommandHelper.syncAll(ctx, hasAttrChanges);

            final String fname = adv.getName();
            ctx.getSource().sendSuccess(
                    () -> CommandHelper.translatable(LangKeys.CMD_ADV_NAME_CHANGED, fname), true);
            return Command.SINGLE_SUCCESS;
        } catch (JsonSyntaxException e) {
            ctx.getSource().sendFailure(
                    CommandHelper.translatable(LangKeys.CMD_JSON_ERROR, friendlyJsonError(e.getMessage())));
        } catch (Exception e) {
            ctx.getSource().sendFailure(
                    CommandHelper.translatable(LangKeys.CMD_PARSE_FAILED, e.getMessage()));
        }
        return 0;
    }

    // ═══════════════ JSON 辅助方法 ═══════════════

    /**
     * 判断 JSON 数据中是否包含属性级变更（非位置、非分类）。
     * <p>
     * 纯位置（x/y）或分类（tab）变更被视为"非属性变更"，
     * 不应触发 FTB Quests KnownServerRegistries 同步。
     *
     * @param data JSON 解析后的 Map
     * @return true 如果包含 name, description, hidden, icon, prerequisites
     *         或 conditions 等属性字段
     */
    static boolean hasAttributeChanges(Map<String, Object> data) {
        if (data.containsKey("name") && data.get("name") instanceof String) return true;
        if (data.containsKey("description") && data.get("description") instanceof String) return true;
        if (data.containsKey("hidden") && data.get("hidden") instanceof Boolean) return true;
        if (data.containsKey("icon") && data.get("icon") instanceof String) return true;
        if (data.containsKey("prerequisites")) return true;
        if (data.containsKey("prerequisite")) return true;
        if (data.containsKey("conditions")) return true;
        // x, y, tab 是位置/分类变更，不算属性变更
        return false;
    }

    /**
     * 将 JSON 数据字段应用到 CustomAdvancement 对象。
     * 支持所有字段：name, description, hidden, icon, prerequisites, tab, x, y, conditions。
     */
    @SuppressWarnings("unchecked")
    static void applyJsonToAdvancement(CustomAdvancement adv, Map<String, Object> data) {
        if (data.get("name") instanceof String name) adv.setName(name);
        if (data.get("description") instanceof String desc) adv.setDescription(desc);
        if (data.get("hidden") instanceof Boolean h) adv.setHidden(h);
        if (data.get("icon") instanceof String icon) adv.setIcon(icon.isEmpty() ? null : icon);
        if (data.get("tab") instanceof String tab) adv.setTab(tab);
        if (data.get("x") instanceof Number x) adv.setX(x.intValue());
        if (data.get("y") instanceof Number y) adv.setY(y.intValue());

        // prerequisites: 支持数组和单字符串两种格式
        if (data.get("prerequisites") instanceof List<?> prereqs) {
            adv.setPrerequisites(parsePrereqs(prereqs));
        } else if (data.get("prerequisite") instanceof String p) {
            adv.setPrerequisites(p.isEmpty() ? new ArrayList<>()
                    : new ArrayList<>(List.of(p)));
        }

        // conditions 解析
        if (data.get("conditions") instanceof List<?> condRaw) {
            List<com.example.advancementoverhaul.data.model.AdvancementCondition> newConds = new ArrayList<>();
            for (Object obj : condRaw) {
                if (!(obj instanceof Map<?, ?> cmRaw)) continue;
                try {
                    Map<String, Object> cm = (Map<String, Object>) cmRaw;
                    if (!(cm.get("type") instanceof String typeStr)) continue;
                    ConditionType ct = ConditionType.valueOf(typeStr.toUpperCase());
                    String targetId = cm.get("targetId") instanceof String tid ? tid : "";
                    int count = cm.get("count") instanceof Number n ? n.intValue() : 1;
                    AdvancementCondition cond = new AdvancementCondition(ct, targetId, count);
                    if (cm.get("nbtMatchMode") instanceof String mode) cond.setNbtMatchMode(mode);
                    if (cm.get("targetNbt") instanceof String nbt) cond.setTargetNbt(nbt);
                    newConds.add(cond);
                } catch (IllegalArgumentException ignored) {
                    // 非法条件类型，跳过
                }
            }
            adv.setConditions(newConds);
        }
    }

    /**
     * 从 JSON 解析前置条件列表。
     * 支持 {@code ["id1", "id2"]} 和 {@code "id1"} 两种格式。
     */
    @SuppressWarnings("unchecked")
    static List<String> parsePrereqs(Object raw) {
        List<String> result = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof String s && !s.isEmpty()) result.add(s);
            }
        } else if (raw instanceof String s && !s.isEmpty()) {
            result.add(s);
        }
        return result;
    }

    /**
     * 将 Gson 的原始错误消息转换为用户友好的提示。
     */
    private static String friendlyJsonError(String rawMessage) {
        if (rawMessage == null) return "Unknown JSON error";
        String msg = rawMessage.toLowerCase();
        if (msg.contains("expected begin_object"))
            return "JSON 格式错误：需要以 { 开头的对象";
        if (msg.contains("expected begin_array"))
            return "JSON 格式错误：需要以 [ 开头的数组";
        if (msg.contains("unterminated"))
            return "JSON 格式错误：字符串或对象未正确闭合";
        if (msg.contains("malformed"))
            return "JSON 格式错误：数值或转义字符格式不正确";
        return rawMessage;
    }
}
