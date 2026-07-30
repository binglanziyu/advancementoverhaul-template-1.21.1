package com.example.advancementoverhaul.achievement.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * 当自定义成就进度更新时触发。
 */
public class AdvProgressEvent extends Event {
    private final ServerPlayer player;
    private final String advancementId;
    private final int progress;
    private final int total;

    public AdvProgressEvent(ServerPlayer player, String advancementId, int progress, int total) {
        this.player = player;
        this.advancementId = advancementId;
        this.progress = progress;
        this.total = total;
    }

    public ServerPlayer getPlayer() { return player; }
    public String getAdvancementId() { return advancementId; }
    public int getProgress() { return progress; }
    public int getTotal() { return total; }
    public boolean isCompleted() { return progress >= total; }
}
