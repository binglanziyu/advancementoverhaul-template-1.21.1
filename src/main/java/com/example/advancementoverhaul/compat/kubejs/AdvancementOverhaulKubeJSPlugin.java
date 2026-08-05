package com.example.advancementoverhaul.compat.kubejs;

import com.example.advancementoverhaul.achievement.event.AdvCompletedEvent;
import com.example.advancementoverhaul.achievement.event.AdvProgressEvent;
import com.example.advancementoverhaul.achievement.event.AdvResetEvent;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.KubeEvent;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;

public class AdvancementOverhaulKubeJSPlugin implements KubeJSPlugin {
    public static final EventGroup GROUP = EventGroup.of("AdvancementOverhaul");
    public static final EventHandler COMPLETED = GROUP.server("completed", () -> CompletedEventJS.class);
    public static final EventHandler PROGRESS = GROUP.server("progress", () -> ProgressEventJS.class);
    public static final EventHandler RESET = GROUP.server("reset", () -> ResetEventJS.class);

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(GROUP);
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        bindings.add("AdvancementOverhaul", KubeJSBindings.class);
    }

    @Override
    public void init() {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onAdvCompleted(AdvCompletedEvent event) {
        COMPLETED.post(new CompletedEventJS(event.getPlayer(), event.getAdvancementId(), event.getAdvancementName()));
    }

    @SubscribeEvent
    public void onAdvProgress(AdvProgressEvent event) {
        PROGRESS.post(new ProgressEventJS(event.getPlayer(), event.getAdvancementId(), event.getProgress(), event.getTotal()));
    }

    @SubscribeEvent
    public void onAdvReset(AdvResetEvent event) {
        RESET.post(new ResetEventJS(event.getPlayer(), event.getAdvancementId()));
    }

    public static class CompletedEventJS implements KubeEvent {
        private ServerPlayer player;
        private String advancementId;
        private String advancementName;

        public CompletedEventJS() {
        }

        public CompletedEventJS(ServerPlayer player, String advancementId, String advancementName) {
            this.player = player;
            this.advancementId = advancementId;
            this.advancementName = advancementName;
        }

        public ServerPlayer getPlayer() {
            return this.player;
        }

        public String getAdvancementId() {
            return this.advancementId;
        }

        public String getAdvancementName() {
            return this.advancementName;
        }
    }

    public static class ProgressEventJS implements KubeEvent {
        private ServerPlayer player;
        private String advancementId;
        private int progress;
        private int total;

        public ProgressEventJS() {
        }

        public ProgressEventJS(ServerPlayer player, String advancementId, int progress, int total) {
            this.player = player;
            this.advancementId = advancementId;
            this.progress = progress;
            this.total = total;
        }

        public ServerPlayer getPlayer() {
            return this.player;
        }

        public String getAdvancementId() {
            return this.advancementId;
        }

        public int getProgress() {
            return this.progress;
        }

        public int getTotal() {
            return this.total;
        }

        public boolean isCompleted() {
            return this.progress >= this.total;
        }
    }

    public static class ResetEventJS implements KubeEvent {
        private ServerPlayer player;
        private String advancementId;

        public ResetEventJS() {
        }

        public ResetEventJS(ServerPlayer player, String advancementId) {
            this.player = player;
            this.advancementId = advancementId;
        }

        public ServerPlayer getPlayer() {
            return this.player;
        }

        public String getAdvancementId() {
            return this.advancementId;
        }
    }
}
