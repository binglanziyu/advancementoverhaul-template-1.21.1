package com.example.advancementoverhaul.client.gui.state;

import com.example.advancementoverhaul.Config;
import net.minecraft.Util;

import java.util.*;

public class AnimState {
    public static class Toast {
        public final String name; public final long created, dur;
        public Toast(String n) { name = n; created = System.currentTimeMillis(); dur = Config.TOAST_DURATION.get(); }
        public boolean expired() { return System.currentTimeMillis() - created > dur; }
        public int alpha() { long e = System.currentTimeMillis() - created; if (e < 300) return Math.min(255, (int)(e * 255 / 300)); if (e > dur - 300) return Math.max(0, (int)((dur - e) * 255 / 300)); return 255; }
    }

    public final Map<String, Float> progress = new HashMap<>(); public long lastTime = Util.getMillis();
    public final List<Toast> toasts = new ArrayList<>();
    public void tick() { lastTime = Util.getMillis(); }
    public void addToast(String name) { toasts.add(new Toast(name)); }
    public void prune(Set<String> ids) { progress.keySet().retainAll(ids); }
}