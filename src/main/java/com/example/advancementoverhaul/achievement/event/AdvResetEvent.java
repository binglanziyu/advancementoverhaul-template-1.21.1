package com.example.advancementoverhaul.achievement.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * 当自定义成就被重置时触发。
 */
public class AdvResetEvent extends Event {
    private final ServerPlayer player;
    private final String advancementId;

    public AdvResetEvent(ServerPlayer player, String advancementId) {
        this.player = player;
        this.advancementId = advancementId;
    }

    public ServerPlayer getPlayer() { return player; }
    public String getAdvancementId() { return advancementId; }
}
