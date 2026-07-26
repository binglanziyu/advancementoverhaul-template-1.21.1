package com.example.advancementoverhaul.command;

import com.example.advancementoverhaul.LangKeys;
import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.ServerDataStore;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import com.example.advancementoverhaul.event.ConditionEvaluator;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

final class AdvCrudExecutor {

    // ═══════════════ DELETE / BATCH DELETE ═══════════════

    static int deleteAdvancement(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String id = StringArgumentType.getString(ctx, "id");
        if (ServerDataStore.getInstance().getAdvancement(id) == null) {
            ctx.getSource().sendFailure(CommandHelper.translatable(LangKeys.CMD_ADV_NOT_FOUND, id));
            return 0;
        }
        ServerDataStore.getInstance().removeAdvancement(id);
        CommandHelper.syncAll(ctx);
        ctx.getSource().sendSuccess(() -> CommandHelper.translatable(LangKeys.CMD_ADV_DELETED, id), true);
        return Command.SINGLE_SUCCESS;
    }

    static int batchDelete(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String idsStr = StringArgumentType.getString(ctx, "ids");
        ServerDataStore store = ServerDataStore.getInstance();
        int count = 0;
        List<String> toDelete = new ArrayList<>();
        for (String id : idsStr.split(",")) {
            id = id.trim();
            if (store.getAdvancement(id) != null) {
                toDelete.add(id);
                count++;
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
        ctx.getSource().sendSuccess(() -> CommandHelper.translatable(LangKeys.CMD_ADV_BATCH_DELETED, fc), true);
        return Command.SINGLE_SUCCESS;
    }

    // ═══════════════ SET NAME / DESCRIPTION / ICON / HIDDEN / PREREQ ═══════════════

    static int setName(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String data = StringArgumentType.getString(ctx, "data");
        String id = CommandHelper.splitId(data);
        String name = CommandHelper.splitRest(data);
        if (name.isEmpty()) {
            ctx.getSource().sendFailure(CommandHelper.translatable(LangKeys.CMD_PARSE_FAILED, "Expected: <id> <name>"));
            return 0;
        }
        DataStore.CustomAdvancement adv = ServerDataStore.getInstance().getAdvancement(id);
        if (adv == null) { ctx.getSource().sendFailure(CommandHelper.translatable(LangKeys.CMD_ADV_NOT_FOUND, id)); return 0; }
        adv.setName(name);
        ServerDataStore.getInstance().addAdvancement(adv);
        CommandHelper.syncAll(ctx);
        ctx.getSource().sendSuccess(() -> CommandHelper.translatable(LangKeys.CMD_ADV_NAME_CHANGED, name), true);
        return Command.SINGLE_SUCCESS;
    }

    static int setDescription(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String data = StringArgumentType.getString(ctx, "data");
        String id = CommandHelper.splitId(data);
        String desc = CommandHelper.splitRest(data);
        DataStore.CustomAdvancement adv = ServerDataStore.getInstance().getAdvancement(id);
        if (adv == null) { ctx.getSource().sendFailure(CommandHelper.translatable(LangKeys.CMD_ADV_NOT_FOUND, id)); return 0; }
        adv.setDescription(desc);
        ServerDataStore.getInstance().addAdvancement(adv);
        CommandHelper.syncAll(ctx);
        ctx.getSource().sendSuccess(() -> Component.translatable(LangKeys.CMD_ADV_DESC_SET), true);
        return Command.SINGLE_SUCCESS;
    }

    static int setIcon(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String data = StringArgumentType.getString(ctx, "data");
        String id = CommandHelper.splitId(data);
        String icon = CommandHelper.splitRest(data);
        DataStore.CustomAdvancement adv = ServerDataStore.getInstance().getAdvancement(id);
        if (adv == null) { ctx.getSource().sendFailure(CommandHelper.translatable(LangKeys.CMD_ADV_NOT_FOUND, id)); return 0; }
        adv.setIcon(icon.isEmpty() ? null : icon);
        ServerDataStore.getInstance().addAdvancement(adv);
        CommandHelper.syncAll(ctx);
        ctx.getSource().sendSuccess(() -> Component.translatable(LangKeys.CMD_ICON_SET), true);
        return Command.SINGLE_SUCCESS;
    }

    static int toggleHidden(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String id = StringArgumentType.getString(ctx, "id");
        DataStore.CustomAdvancement adv = ServerDataStore.getInstance().getAdvancement(id);
        if (adv == null) { ctx.getSource().sendFailure(CommandHelper.translatable(LangKeys.CMD_ADV_NOT_FOUND, id)); return 0; }
        adv.setHidden(!adv.isHidden());
        ServerDataStore.getInstance().addAdvancement(adv);
        CommandHelper.syncAll(ctx);
        ctx.getSource().sendSuccess(() -> CommandHelper.translatable(LangKeys.CMD_ADV_HIDDEN_STATE, adv.isHidden()), true);
        return Command.SINGLE_SUCCESS;
    }

    static int setPrereq(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String data = StringArgumentType.getString(ctx, "data");
        String id = CommandHelper.splitId(data);
        String prereqStr = CommandHelper.splitRest(data);
        ServerDataStore store = ServerDataStore.getInstance();

        boolean isCustom = store.getAdvancement(id) != null;
        boolean isVanilla = !isCustom && CommandHelper.collectAllVanillaIds(ctx).contains(id);

        if (!isCustom && !isVanilla) {
            ctx.getSource().sendFailure(CommandHelper.translatable(LangKeys.CMD_ADV_NOT_FOUND, id));
            return 0;
        }

        // 解析前置列表
        List<String> prereqs = new ArrayList<>();
        if (!prereqStr.isEmpty()) {
            for (String p : prereqStr.split(",")) {
                p = p.trim(); if (p.isEmpty()) continue;
                boolean exists = store.getAdvancement(p) != null
                        || CommandHelper.collectAllVanillaIds(ctx).contains(p);
                if (!exists) {
                    ctx.getSource().sendFailure(CommandHelper.translatable(LangKeys.CMD_PREREQ_NOT_FOUND, p));
                    return 0;
                }
                prereqs.add(p);
            }
        }

        if (isVanilla) {
            DataStore.VanillaAdvMeta meta = store.getVanillaMeta(id);
            if (meta == null) meta = new DataStore.VanillaAdvMeta();
            meta.setPrerequisites(prereqs);
            store.setVanillaMeta(id, meta);
        } else {
            DataStore.CustomAdvancement adv = store.getAdvancement(id);
            adv.setPrerequisites(prereqs);
            store.addAdvancement(adv);
        }

        // Re-evaluate: if new prerequisites are all met, release pending for online players
        if (ctx.getSource().getServer() != null) {
            for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                ConditionEvaluator.releasePendingDependents(player);
            }
        }
        CommandHelper.syncAll(ctx);
        ctx.getSource().sendSuccess(() -> Component.translatable(LangKeys.CMD_ADV_PREREQ_SET), true);
        return Command.SINGLE_SUCCESS;
    }
    // ═══════════════ CREATE / UPDATE FROM JSON ═══════════════

    static int createFromJson(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String json = StringArgumentType.getString(ctx, "json");
        try {
            Map<String, Object> data = DataStore.GSON_PRETTY.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
            if (data == null) {
                ctx.getSource().sendFailure(Component.translatable(LangKeys.CMD_JSON_EMPTY));
                return 0;
            }
            String id = data.get("id") instanceof String s ? s : null;
            if (id == null || id.isEmpty()) {
                ctx.getSource().sendFailure(Component.translatable(LangKeys.CMD_JSON_MISSING_NAME));
                return 0;
            }

            // 创建新成就
            DataStore.CustomAdvancement adv = new DataStore.CustomAdvancement();
            adv.setId(id);
            applyJsonToAdvancement(adv, data);
            ServerDataStore.getInstance().addAdvancement(adv);
            CommandHelper.syncAll(ctx);
            final String fname = adv.getName();
            ctx.getSource().sendSuccess(() -> CommandHelper.translatable(LangKeys.CMD_ADV_NAME_CHANGED, fname), true);
            return Command.SINGLE_SUCCESS;
        } catch (JsonSyntaxException e) {
            ctx.getSource().sendFailure(CommandHelper.translatable(LangKeys.CMD_JSON_ERROR, e.getMessage()));
        } catch (Exception e) {
            ctx.getSource().sendFailure(CommandHelper.translatable(LangKeys.CMD_PARSE_FAILED, e.getMessage()));
        }
        return 0;
    }
    static int updateFromJson(CommandContext<CommandSourceStack> ctx) {
        if (!CommandHelper.checkPerm(ctx)) return 0;
        String raw = StringArgumentType.getString(ctx, "json");
        try {
            // 解码Base64（兼容旧格式）
            String json;
            try {
                json = new String(java.util.Base64.getDecoder().decode(raw),
                        java.nio.charset.StandardCharsets.UTF_8);
            } catch (IllegalArgumentException notBase64) {
                json = raw; // 不是Base64则当作原始JSON
            }
            Map<String, Object> data = DataStore.GSON_PRETTY.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
            if (data == null) {
                ctx.getSource().sendFailure(Component.translatable(LangKeys.CMD_JSON_EMPTY));
                return 0;
            }
            String id = data.get("id") instanceof String s ? s : null;
            if (id == null || id.isEmpty()) {
                ctx.getSource().sendFailure(Component.translatable(LangKeys.CMD_JSON_MISSING_NAME));
                return 0;
            }

            // ★ 不存在则创建，存在则更新
            DataStore.CustomAdvancement adv = ServerDataStore.getInstance().getAdvancement(id);
            if (adv == null) {
                adv = new DataStore.CustomAdvancement();
                adv.setId(id);
            }

            applyJsonToAdvancement(adv, data);
            ServerDataStore.getInstance().addAdvancement(adv);
            CommandHelper.syncAll(ctx);
            final String fname = adv.getName();
            ctx.getSource().sendSuccess(() -> CommandHelper.translatable(LangKeys.CMD_ADV_NAME_CHANGED, fname), true);
            return Command.SINGLE_SUCCESS;
        } catch (JsonSyntaxException e) {
            ctx.getSource().sendFailure(CommandHelper.translatable(LangKeys.CMD_JSON_ERROR, e.getMessage()));
        } catch (Exception e) {
            ctx.getSource().sendFailure(CommandHelper.translatable(LangKeys.CMD_PARSE_FAILED, e.getMessage()));
        }
        return 0;
    }

    // ═══════════════ JSON HELPERS ═══════════════

    @SuppressWarnings("unchecked")
    static void applyJsonToAdvancement(DataStore.CustomAdvancement adv, Map<String, Object> data) {
        if (data.get("name") instanceof String name) adv.setName(name);
        if (data.get("description") instanceof String desc) adv.setDescription(desc);
        if (data.get("hidden") instanceof Boolean h) adv.setHidden(h);
        if (data.get("icon") instanceof String icon) adv.setIcon(icon.isEmpty() ? null : icon);
        if (data.get("prerequisites") instanceof List<?> prereqs) adv.setPrerequisites(parsePrereqs(prereqs));
        else if (data.get("prerequisite") instanceof String p)
            adv.setPrerequisites(p.isEmpty() ? new ArrayList<>() : new ArrayList<>(List.of(p)));
        if (data.get("tab") instanceof String tab) adv.setTab(tab);
        if (data.get("x") instanceof Number x) adv.setX(x.intValue());
        if (data.get("y") instanceof Number y) adv.setY(y.intValue());
        if (data.get("conditions") instanceof List<?> condRaw) {
            List<DataStore.AdvancementCondition> newConds = new ArrayList<>();
            for (Object obj : condRaw) {
                if (!(obj instanceof Map<?, ?> cmRaw)) continue;
                try {
                    Map<String, Object> cm = (Map<String, Object>) cmRaw;
                    if (!(cm.get("type") instanceof String typeStr)) continue;
                    DataStore.ConditionType ct = DataStore.ConditionType.valueOf(typeStr.toUpperCase());
                    String targetId = cm.get("targetId") instanceof String tid ? tid : "";
                    int count = cm.get("count") instanceof Number n ? n.intValue() : 1;
                    DataStore.AdvancementCondition cond = new DataStore.AdvancementCondition(ct, targetId, count);
                    if (cm.get("nbtMatchMode") instanceof String mode) cond.setNbtMatchMode(mode);
                    if (cm.get("targetNbt") instanceof String nbt) cond.setTargetNbt(nbt);
                    newConds.add(cond);
                } catch (IllegalArgumentException ignored) {}
            }
            adv.setConditions(newConds);
        }
    }

    @SuppressWarnings("unchecked")
    static List<String> parsePrereqs(Object raw) {
        List<String> result = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) if (o instanceof String s && !s.isEmpty()) result.add(s);
        } else if (raw instanceof String s && !s.isEmpty()) result.add(s);
        return result;
    }
}