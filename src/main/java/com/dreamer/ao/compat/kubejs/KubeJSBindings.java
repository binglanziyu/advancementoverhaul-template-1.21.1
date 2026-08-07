package com.dreamer.ao.compat.kubejs;

import com.dreamer.ao.compat.AdvancementAPI;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;

public final class KubeJSBindings {
    private KubeJSBindings() {
    }

    public static List<String> getAllIds() {
        return AdvancementAPI.getAllIds();
    }

    @Nullable
    public static String getName(String advId) {
        return AdvancementAPI.getName(advId);
    }

    public static boolean isCompleted(ServerPlayer player, String advId) {
        if (player == null) {
            return false;
        }
        return AdvancementAPI.isCompleted(player.getUUID(), advId);
    }

    public static int getProgress(ServerPlayer player, String advId) {
        if (player == null) {
            return 0;
        }
        return AdvancementAPI.getProgress(player.getUUID(), advId);
    }

    public static void complete(ServerPlayer player, String advId) {
        if (player == null) {
            return;
        }
        AdvancementAPI.complete(player, advId);
    }

    public static void reset(ServerPlayer player, String advId) {
        if (player == null) {
            return;
        }
        AdvancementAPI.reset(player, advId);
    }

    public static AdvancementAPI.AdvancementBuilder builder(String id) {
        return AdvancementAPI.builder(id);
    }
}
