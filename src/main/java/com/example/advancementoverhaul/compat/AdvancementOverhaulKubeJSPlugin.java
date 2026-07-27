package com.example.advancementoverhaul.compat;

import com.example.advancementoverhaul.event.AdvCompletedEvent;
import com.example.advancementoverhaul.event.AdvProgressEvent;
import com.example.advancementoverhaul.event.AdvResetEvent;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.KubeEvent;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * KubeJS 插件入口。
 * <p>
 * 通过 {@code kubejs.plugins.txt} 注册，提供两类集成：
 * <ol>
 *   <li><b>Bindings</b> — 脚本中可用 {@code AdvancementOverhaul.complete(...)} 等 API</li>
 *   <li><b>Events</b> — 脚本中可监听 {@code AdvancementOverhaul.completed(...)} 等事件</li>
 * </ol>
 *
 * <h3>使用示例（KubeJS server_scripts）</h3>
 * <pre>{@code
 * // 监听成就完成
 * AdvancementOverhaul.completed(event => {
 *     console.log(`Player ${event.player.name} completed ${event.advancementId}`)
 * })
 *
 * // 监听进度更新
 * AdvancementOverhaul.progress(event => {
 *     console.log(`${event.advancementId}: ${event.progress}/${event.total}`)
 * })
 *
 * // 监听成就重置
 * AdvancementOverhaul.reset(event => {
 *     console.log(`${event.advancementId} was reset for ${event.player.name}`)
 * })
 *
 * // API 调用
 * AdvancementOverhaul.complete(event.player, "my_adv_id")
 * AdvancementOverhaul.reset(event.player, "my_adv_id")
 * let isDone = AdvancementOverhaul.isCompleted(event.player, "my_adv_id")
 * }</pre>
 */
public class AdvancementOverhaulKubeJSPlugin implements KubeJSPlugin {

    // ═══════════════ 事件组定义 ═══════════════

    /** KubeJS 事件组，脚本中以 {@code AdvancementOverhaul.xxx(...)} 形式访问 */
    public static final EventGroup GROUP = EventGroup.of("AdvancementOverhaul");

    /** 成就完成事件 */
    public static final EventHandler COMPLETED = GROUP.server(
            "completed", () -> CompletedEventJS.class);

    /** 进度更新事件 */
    public static final EventHandler PROGRESS = GROUP.server(
            "progress", () -> ProgressEventJS.class);

    /** 成就重置事件 */
    public static final EventHandler RESET = GROUP.server(
            "reset", () -> ResetEventJS.class);

    // ═══════════════ 插件生命周期 ═══════════════

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
        // 监听 NeoForge 事件总线，将模组事件转发为 KubeJS 事件
        NeoForge.EVENT_BUS.register(this);
    }

    // ═══════════════ NeoForge → KubeJS 事件转发 ═══════════════

    @SubscribeEvent
    public void onAdvCompleted(AdvCompletedEvent event) {
        COMPLETED.post(new CompletedEventJS(event.getPlayer(),
                event.getAdvancementId(), event.getAdvancementName()));
    }

    @SubscribeEvent
    public void onAdvProgress(AdvProgressEvent event) {
        PROGRESS.post(new ProgressEventJS(event.getPlayer(),
                event.getAdvancementId(), event.getProgress(), event.getTotal()));
    }

    @SubscribeEvent
    public void onAdvReset(AdvResetEvent event) {
        RESET.post(new ResetEventJS(event.getPlayer(), event.getAdvancementId()));
    }

    // ═══════════════ KubeJS 事件包装类 ═══════════════

    /**
     * 成就完成事件。
     * <p>
     * 脚本中可通过 event.player、event.advancementId、event.advancementName 访问。
     */
    public static class CompletedEventJS implements KubeEvent {
        private final ServerPlayer player;
        private final String advancementId;
        private final String advancementName;

        public CompletedEventJS(ServerPlayer player, String advancementId,
                                String advancementName) {
            this.player = player;
            this.advancementId = advancementId;
            this.advancementName = advancementName;
        }

        public ServerPlayer getPlayer()          { return player; }
        public String getAdvancementId()         { return advancementId; }
        public String getAdvancementName()       { return advancementName; }
    }

    /**
     * 进度更新事件。
     */
    public static class ProgressEventJS implements KubeEvent {
        private final ServerPlayer player;
        private final String advancementId;
        private final int progress;
        private final int total;

        public ProgressEventJS(ServerPlayer player, String advancementId,
                               int progress, int total) {
            this.player = player;
            this.advancementId = advancementId;
            this.progress = progress;
            this.total = total;
        }

        public ServerPlayer getPlayer()          { return player; }
        public String getAdvancementId()         { return advancementId; }
        public int getProgress()                 { return progress; }
        public int getTotal()                    { return total; }
        /** 是否已完成（progress >= total） */
        public boolean isCompleted()             { return progress >= total; }
    }

    /**
     * 成就重置事件。
     */
    public static class ResetEventJS implements KubeEvent {
        private final ServerPlayer player;
        private final String advancementId;

        public ResetEventJS(ServerPlayer player, String advancementId) {
            this.player = player;
            this.advancementId = advancementId;
        }

        public ServerPlayer getPlayer()          { return player; }
        public String getAdvancementId()         { return advancementId; }
    }
}
