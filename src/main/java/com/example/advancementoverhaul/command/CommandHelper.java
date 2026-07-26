package com.example.advancementoverhaul.command;

import com.example.advancementoverhaul.Config;
import com.example.advancementoverhaul.LangKeys;

import com.example.advancementoverhaul.data.ServerDataStore;
import com.example.advancementoverhaul.event.SyncManager;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.example.advancementoverhaul.compat.AdvancementRegistry;
import net.minecraft.server.MinecraftServer;
import java.util.HashSet;
import java.util.Set;

public final class CommandHelper {

    private CommandHelper() {}

    /**
     * Creates a translatable component with arguments.
     * Delegates to Minecraft's built-in {@code Component.translatable(key, args)}
     * which handles {@code %s}/{@code %d} placeholders natively.
     */
    public static Component translatable(String key, Object... args) {
        return Component.translatable(key, args);
    }

    public static String splitId(String data) {
        int idx = data.indexOf(' ');
        return idx < 0 ? data : data.substring(0, idx);
    }

    public static String splitRest(String data) {
        int idx = data.indexOf(' ');
        return idx < 0 ? "" : data.substring(idx + 1);
    }

    public static boolean checkPerm(CommandContext<CommandSourceStack> ctx) {
        if (!ctx.getSource().hasPermission(Config.EDIT_PERMISSION_LEVEL.get())) {
            ctx.getSource().sendFailure(Component.translatable(LangKeys.CMD_PERM_DENIED));
            return false;
        }
        return true;
    }

    public static void syncAll(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        AdvancementRegistry.syncAllRuntime(server);
        SyncManager.syncAll(server);
    }


    public static void syncTargetPlayer(ServerPlayer target) {
        ServerDataStore.getInstance().savePlayerData();
        SyncManager.syncPlayer(target);
    }

    public static Set<String> collectAllVanillaIds(CommandContext<CommandSourceStack> ctx) {
        Set<String> ids = new HashSet<>();
        try {
            for (var holder : ctx.getSource().getServer().getAdvancements().getAllAdvancements()) {
                String id = holder.id().toString();
                if (!com.example.advancementoverhaul.compat.AdvancementRegistry.isCustomAdvancement(holder.id())) {
                    ids.add(id);
                }
            }
        } catch (Exception ignored) {}
        return ids;
    }
}