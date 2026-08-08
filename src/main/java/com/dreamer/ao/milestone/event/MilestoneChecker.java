package com.dreamer.ao.milestone.event;

import com.dreamer.ao.milestone.bridge.BridgeRegistry;
import com.dreamer.ao.milestone.model.MilestoneDefinition;
import com.dreamer.ao.milestone.model.MilestoneTrigger;
import com.dreamer.ao.milestone.model.TimeMilestone;
import com.dreamer.ao.milestone.store.TimelineDefinitionLoader;
import com.dreamer.ao.milestone.store.TimelineStore;
import com.dreamer.ao.network.payload.TimelineSyncPayload;
import com.google.gson.JsonArray;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class MilestoneChecker {
    private static final Logger LOGGER = LoggerFactory.getLogger(MilestoneChecker.class);

    private MilestoneChecker() {
    }

    static void checkAndUnlock(ServerPlayer player, UUID uuid, int gameDay, long gameTick, Predicate<MilestoneDefinition> matcher, String context) {
        TimelineStore store = TimelineStore.getInstance();
        TimelineDefinitionLoader loader = TimelineDefinitionLoader.getInstance();
        for (MilestoneDefinition def : loader.getAllMilestones()) {
            if (!matcher.test(def)) continue;
            MilestoneChecker.tryUnlock(player, uuid, gameDay, gameTick, def.getId(), def.getRequiredAdvancement(), def.getLinkedAdvancement(), def.isAutoAdvancement(), def);
        }
        for (TimeMilestone ct : loader.getCustomMilestones()) {
            MilestoneDefinition tmpDef;
            MilestoneTrigger trig;
            if (ct.unlocked() || store.isUnlocked(uuid, ct.id()) || (trig = MilestoneChecker.parseCustomTrigger(ct.customTrigger())) == null || !matcher.test(tmpDef = MilestoneChecker.makeTempDef(ct, trig))) continue;
            MilestoneChecker.tryUnlock(player, uuid, gameDay, gameTick, ct.id(), null, null, false, tmpDef);
        }
    }

    static void checkCounter(ServerPlayer player, UUID uuid, int gameDay, long gameTick, String statKey, long currentValue) {
        TimelineStore store = TimelineStore.getInstance();
        TimelineDefinitionLoader loader = TimelineDefinitionLoader.getInstance();
        for (MilestoneDefinition def : loader.getAllMilestones()) {
            // 以下任一条件不满足则跳过（原单行复合条件，拆解为显式早退以提升可维护性）
            boolean triggerMatches = def.getTrigger() == MilestoneTrigger.COUNTER_REACH
                    || def.getTrigger() == MilestoneTrigger.DISTANCE_REACH;
            if (!triggerMatches) continue;
            if (store.isUnlocked(uuid, def.getId())) continue;
            if (!statKey.equals(def.getTriggerParam())) continue;
            if (currentValue < def.getTriggerThreshold()) continue;
            String requiredAdv = def.getRequiredAdvancement();
            boolean hasUnmetRequirement = requiredAdv != null && !requiredAdv.isEmpty()
                    && !BridgeRegistry.getAchievementBridge().isAdvancementCompleted(uuid, requiredAdv);
            if (hasUnmetRequirement) continue;
            MilestoneChecker.tryUnlockCounter(player, uuid, gameDay, gameTick, def.getId(), def.getLinkedAdvancement(), def.isAutoAdvancement(), def, currentValue);
        }
        for (TimeMilestone ct : loader.getCustomMilestones()) {
            MilestoneTrigger trig;
            if (ct.unlocked() || store.isUnlocked(uuid, ct.id()) || (trig = MilestoneChecker.parseCustomTrigger(ct.customTrigger())) != MilestoneTrigger.COUNTER_REACH && trig != MilestoneTrigger.DISTANCE_REACH || !statKey.equals(ct.customParam()) || currentValue < ct.customThreshold()) continue;
            MilestoneDefinition tmpDef = MilestoneChecker.makeTempDef(ct, trig);
            MilestoneChecker.tryUnlockCounter(player, uuid, gameDay, gameTick, ct.id(), null, false, tmpDef, currentValue);
        }
    }

    static void scanInventory(ServerPlayer player, UUID uuid, int gameDay, long gameTick, Set<String> heldItems) {
        TimelineStore store = TimelineStore.getInstance();
        TimelineDefinitionLoader loader = TimelineDefinitionLoader.getInstance();
        for (MilestoneDefinition def : loader.getAllMilestones()) {
            String targetItem;
            if (def.getTrigger() != MilestoneTrigger.FIRST_OBTAIN || store.isUnlocked(uuid, def.getId()) || (targetItem = def.getTriggerParam()) == null || !heldItems.contains(targetItem) || def.getRequiredAdvancement() != null && !def.getRequiredAdvancement().isEmpty() && !BridgeRegistry.getAchievementBridge().isAdvancementCompleted(uuid, def.getRequiredAdvancement())) continue;
            MilestoneChecker.tryUnlockFirstObtain(player, uuid, gameDay, gameTick, def.getId(), def.getLinkedAdvancement(), def.isAutoAdvancement(), def);
        }
        for (TimeMilestone ct : loader.getCustomMilestones()) {
            String targetItem;
            MilestoneTrigger trig;
            if (ct.unlocked() || store.isUnlocked(uuid, ct.id()) || (trig = MilestoneChecker.parseCustomTrigger(ct.customTrigger())) != MilestoneTrigger.FIRST_OBTAIN || (targetItem = ct.customParam()) == null || targetItem.isEmpty() || !heldItems.contains(targetItem)) continue;
            MilestoneDefinition tmpDef = MilestoneChecker.makeTempDef(ct, trig);
            MilestoneChecker.tryUnlockFirstObtain(player, uuid, gameDay, gameTick, ct.id(), null, false, tmpDef);
        }
    }

    private static boolean tryUnlock(ServerPlayer player, UUID uuid, int gameDay, long gameTick, String id, String requiredAdv, String linkedAdv, boolean autoAdv, MilestoneDefinition def) {
        if (MilestoneChecker.isBlocked(uuid, id, requiredAdv)) {
            return false;
        }
        if (MilestoneChecker.doUnlock(uuid, id, gameDay, gameTick)) {
            LOGGER.info("Unlocked milestone: {} for {} on day {}", id, player.getName().getString(), gameDay);
            MilestoneChecker.onUnlocked(player, id, linkedAdv, autoAdv, def, 0L);
            return true;
        }
        return false;
    }

    private static void tryUnlockCounter(ServerPlayer player, UUID uuid, int gameDay, long gameTick, String id, String linkedAdv, boolean autoAdv, MilestoneDefinition def, long currentValue) {
        if (MilestoneChecker.doUnlock(uuid, id, gameDay, gameTick)) {
            LOGGER.info("Unlocked counter milestone: {} for player {} at value {}", id, player.getName().getString(), currentValue);
            MilestoneChecker.onUnlocked(player, id, linkedAdv, autoAdv, def, currentValue);
        }
    }

    private static void tryUnlockFirstObtain(ServerPlayer player, UUID uuid, int gameDay, long gameTick, String id, String linkedAdv, boolean autoAdv, MilestoneDefinition def) {
        if (MilestoneChecker.doUnlock(uuid, id, gameDay, gameTick)) {
            LOGGER.info("Unlocked first_obtain milestone: {} for player {} on day {}", id, player.getName().getString(), gameDay);
            MilestoneChecker.onUnlocked(player, id, linkedAdv, autoAdv, def, 0L);
        }
    }

    private static boolean isBlocked(UUID uuid, String id, String requiredAdv) {
        if (TimelineStore.getInstance().isUnlocked(uuid, id)) {
            return true;
        }
        if (requiredAdv != null && !requiredAdv.isEmpty()) {
            return !BridgeRegistry.getAchievementBridge().isAdvancementCompleted(uuid, requiredAdv);
        }
        return false;
    }

    private static boolean doUnlock(UUID uuid, String id, int gameDay, long gameTick) {
        return TimelineStore.getInstance().unlockMilestone(uuid, id, gameDay, gameTick);
    }

    private static void onUnlocked(ServerPlayer player, String id, String linkedAdv, boolean autoAdv, MilestoneDefinition def, long currentValue) {
        if (linkedAdv != null && !linkedAdv.isEmpty()) {
            BridgeRegistry.getAchievementBridge().checkLinkedAdvancement(player, id, currentValue);
        }
        if (autoAdv) {
            BridgeRegistry.getAchievementBridge().triggerAutoAdvancement(player, def.getId(),
                    def.getNameKey(), def.getDescriptionKey(), def.getIconItem());
        }
        MilestoneChecker.syncTimelineToPlayer(player);
        // 阶段解锁：完成里程碑后解锁关联阶段
        com.dreamer.ao.phase.PhaseUnlockService.get().onMilestoneCompleted(player, id);
    }

    private static MilestoneTrigger parseCustomTrigger(String triggerName) {
        if (triggerName == null) {
            return null;
        }
        try {
            return MilestoneTrigger.valueOf(triggerName.toUpperCase());
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static MilestoneDefinition makeTempDef(TimeMilestone ct, MilestoneTrigger trig) {
        return new MilestoneDefinition(ct.id(), ct.nameKey(), ct.descriptionKey(), ct.iconItem(), ct.category(), trig, ct.customParam(), ct.customThreshold(), null, false, null);
    }

    static void syncTimelineToPlayer(ServerPlayer player) {
        UUID uuid = player.getUUID();
        JsonArray data = TimelineStore.getInstance().toSyncJson(uuid);
        PacketDistributor.sendToPlayer(player, new TimelineSyncPayload(data.toString()));
    }

    static void syncTimelineToAll(List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            MilestoneChecker.syncTimelineToPlayer(player);
        }
    }
}
