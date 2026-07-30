package com.example.advancementoverhaul.achievement.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * 当自定义成就完成时触发的 NeoForge 事件。
 * KubeJS 可通过 ForgeEvents.onJava() 监听此事件。
 */
public class AdvCompletedEvent extends Event {
    private final ServerPlayer player;
    private final String advancementId;
    private final String advancementName;

    public AdvCompletedEvent(ServerPlayer player, String advancementId, String advancementName) {
        this.player = player;
        this.advancementId = advancementId;
        this.advancementName = advancementName;
    }

    public ServerPlayer getPlayer() { return player; }
    public String getAdvancementId() { return advancementId; }
    public String getAdvancementName() { return advancementName; }
}
