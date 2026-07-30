/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.latvian.mods.kubejs.event.EventGroup
 *  dev.latvian.mods.kubejs.event.EventGroupRegistry
 *  dev.latvian.mods.kubejs.event.EventHandler
 *  dev.latvian.mods.kubejs.event.KubeEvent
 *  dev.latvian.mods.kubejs.plugin.KubeJSPlugin
 *  dev.latvian.mods.kubejs.script.BindingRegistry
 *  net.minecraft.server.level.ServerPlayer
 *  net.neoforged.bus.api.SubscribeEvent
 *  net.neoforged.neoforge.common.NeoForge
 */
package com.example.advancementoverhaul.compat.kubejs;

import com.example.advancementoverhaul.compat.kubejs.KubeJSBindings;
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

public class AdvancementOverhaulKubeJSPlugin
implements KubeJSPlugin {
    public static final EventGroup GROUP = EventGroup.of((String)"AdvancementOverhaul");
    public static final EventHandler COMPLETED = GROUP.server("completed", () -> CompletedEventJS.class);
    public static final EventHandler PROGRESS = GROUP.server("progress", () -> ProgressEventJS.class);
    public static final EventHandler RESET = GROUP.server("reset", () -> ResetEventJS.class);

    public void registerEvents(EventGroupRegistry registry) {
        registry.register(GROUP);
    }

    public void registerBindings(BindingRegistry bindings) {
        bindings.add("AdvancementOverhaul", KubeJSBindings.class);
    }

    public void init() {
        NeoForge.EVENT_BUS.register((Object)this);
    }

    @SubscribeEvent
    public void onAdvCompleted(AdvCompletedEvent event) {
        COMPLETED.post((KubeEvent)new CompletedEventJS(event.getPlayer(), event.getAdvancementId(), event.getAdvancementName()));
    }

    @SubscribeEvent
    public void onAdvProgress(AdvProgressEvent event) {
        PROGRESS.post((KubeEvent)new ProgressEventJS(event.getPlayer(), event.getAdvancementId(), event.getProgress(), event.getTotal()));
    }

    @SubscribeEvent
    public void onAdvReset(AdvResetEvent event) {
        RESET.post((KubeEvent)new ResetEventJS(event.getPlayer(), event.getAdvancementId()));
    }

    public static class CompletedEventJS
    implements KubeEvent {
        private final ServerPlayer player;
        private final String advancementId;
        private final String advancementName;

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

    public static class ProgressEventJS
    implements KubeEvent {
        private final ServerPlayer player;
        private final String advancementId;
        private final int progress;
        private final int total;

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

    public static class ResetEventJS
    implements KubeEvent {
        private final ServerPlayer player;
        private final String advancementId;

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

