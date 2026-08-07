package com.dreamer.ao.compat;

import com.dreamer.ao.data.ConditionType;
import com.dreamer.ao.data.DataStore;
import com.dreamer.ao.data.ServerDataStore;
import com.dreamer.ao.data.model.AdvancementCondition;
import com.dreamer.ao.data.model.CustomAdvancement;
import com.dreamer.ao.achievement.event.AdvResetEvent;
import com.dreamer.ao.logic.ConditionEvaluator;
import com.dreamer.ao.network.SyncManager;
import com.dreamer.ao.network.payload.ProgressSyncPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Static API for KubeJS scripts (via Java.loadClass()).
 */
public final class AdvancementAPI {

    private AdvancementAPI() {}

    // ═══════════════ BUILDER ═══════════════

    public static AdvancementBuilder builder(String id) {
        return new AdvancementBuilder(id);
    }

    public static class AdvancementBuilder {
        private final String id;
        private String name = "";
        private String description = "";
        private String tab = null;
        private int x = 80, y = 80;
        private boolean hidden = false;
        private final List<AdvancementCondition> conditions = new ArrayList<>();
        private final List<String> prerequisites = new ArrayList<>();

        AdvancementBuilder(String id) { this.id = id; }

        public AdvancementBuilder name(String name) { this.name = name; return this; }
        public AdvancementBuilder description(String desc) { this.description = desc; return this; }
        public AdvancementBuilder tab(String tab) { this.tab = tab; return this; }
        public AdvancementBuilder pos(int x, int y) { this.x = x; this.y = y; return this; }
        public AdvancementBuilder hidden(boolean hidden) { this.hidden = hidden; return this; }

        public AdvancementBuilder condition(String type, String target, int count) {
            ConditionType ct;
            try { ct = ConditionType.valueOf(type.toUpperCase()); }
            catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown condition type: " + type
                        + ". Valid: kill_entity, craft_item, get_item, break_block, place_block, change_dimension, deal_damage, take_damage, fish_item, ftb_quest_complete, stat_reach");
            }
            conditions.add(new AdvancementCondition(ct, target != null ? target : "", count));
            return this;
        }

        public AdvancementBuilder conditionNbt(String type, String target, int count,
                                               String nbtJson, String matchMode) {
            ConditionType ct;
            try { ct = ConditionType.valueOf(type.toUpperCase()); }
            catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown condition type: " + type);
            }
            AdvancementCondition cond = new AdvancementCondition(ct, target != null ? target : "", count);
            cond.setTargetNbt(nbtJson);
            cond.setNbtMatchMode(matchMode);
            conditions.add(cond);
            return this;
        }

        public AdvancementBuilder prerequisite(String advId) {
            prerequisites.add(advId);
            return this;
        }

        public void register() {
            CustomAdvancement adv = build();
            ServerDataStore store = ServerDataStore.getInstance();
            store.addAdvancement(adv);
            MinecraftServer server = store.getServer();
            if (server != null) {
                AdvancementRegistry.syncAllRuntime(server);
                SyncManager.syncAll(server);
            }
        }

        private CustomAdvancement build() {
            CustomAdvancement adv = new CustomAdvancement(id, name, description, x, y);
            adv.setTab(tab);
            adv.setHidden(hidden);
            adv.setConditions(new ArrayList<>(conditions));
            adv.setPrerequisites(new ArrayList<>(prerequisites));
            return adv;
        }
    }

    // ═══════════════ QUERY ═══════════════

    public static List<String> getAllIds() {
        return new ArrayList<>(ServerDataStore.getInstance().getAdvancements().keySet());
    }

    @Nullable
    public static String getName(String advId) {
        var adv = ServerDataStore.getInstance().getAdvancement(advId);
        return adv != null ? adv.getName() : null;
    }

    public static boolean isCompleted(UUID playerUUID, String advId) {
        return ServerDataStore.getInstance().isCompleted(playerUUID, advId);
    }

    public static int getProgress(UUID playerUUID, String advId) {
        return ServerDataStore.getInstance().getProgress(playerUUID, advId);
    }

    /**
     * Programmatic completion (fires events + vanilla grant + client sync).
     */
    public static void complete(ServerPlayer player, String advId) {
        ServerDataStore store = ServerDataStore.getInstance();
        if (store.getAdvancement(advId) == null || store.isCompleted(player.getUUID(), advId)) return;
        ConditionEvaluator.tryCompleteForce(player, advId);
    }

    /**
     * Programmatic reset (reverts completion + progress + pending + vanilla grant).
     * Does not cascade — dependent advancements remain completed.
     */
    public static void reset(ServerPlayer player, String advId) {
        ServerDataStore store = ServerDataStore.getInstance();
        store.setCompleted(player.getUUID(), advId, false);
        store.resetConditionProgress(player.getUUID(), advId);
        store.setPending(player.getUUID(), advId, false);
        store.savePlayerDataIfDirty();

        PacketDistributor.sendToPlayer(player, new ProgressSyncPayload(advId, false, 0));
        AdvancementRegistry.revokeAdvancement(player, advId);
        NeoForge.EVENT_BUS.post(new AdvResetEvent(player, advId));
    }
}