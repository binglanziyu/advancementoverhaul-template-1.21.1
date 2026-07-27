package com.example.advancementoverhaul.client.gui.state;

import com.example.advancementoverhaul.Config;
import net.minecraft.Util;

import java.util.*;

/**
 * 动画与 UI 状态管理。
 * <p>
 * 管理画布上的三类视觉状态：
 * <ul>
 *   <li><b>Toast</b> — 成就完成时的浮动消息通知（淡入淡出）</li>
 *   <li><b>FlashEntry</b> — 完成卡片闪白动画（400ms 后自动消失）</li>
 *   <li><b>progress</b> — 各成就的进度条平滑过渡值</li>
 * </ul>
 */
public class AnimState {
    public static class Toast {
        public final String name; public final long created, dur;
        public Toast(String n) { name = n; created = System.currentTimeMillis(); dur = Config.TOAST_DURATION.get(); }
        public boolean expired() { return System.currentTimeMillis() - created > dur; }
        public int alpha() { long e = System.currentTimeMillis() - created; if (e < 300) return Math.min(255, (int)(e * 255 / 300)); if (e > dur - 300) return Math.max(0, (int)((dur - e) * 255 / 300)); return 255; }
    }

    // P2: 完成闪白追踪 — 记录最近完成的成就 ID 及其完成时间戳
    public static class FlashEntry {
        public final long time;
        public FlashEntry() { this.time = System.currentTimeMillis(); }
    }

    public final Map<String, Float> progress = new HashMap<>(); public long lastTime = Util.getMillis();
    public final List<Toast> toasts = new ArrayList<>();
    /** Recently completed advancement IDs → completion timestamp (P2: flash animation). */
    public final Map<String, FlashEntry> completionFlashes = new HashMap<>();
    public void tick() {
        lastTime = Util.getMillis();
        // 清理过期闪白（超过 FLASH_DURATION_MS）
        long now = System.currentTimeMillis();
        completionFlashes.values().removeIf(e -> now - e.time > 400L);
    }
    public void addToast(String name) { toasts.add(new Toast(name)); }
    /** P2: 标记某个成就刚完成，触发闪白动画 */
    public void markCompleted(String id) { completionFlashes.put(id, new FlashEntry()); }
    public void prune(Set<String> ids) { progress.keySet().retainAll(ids); }
}