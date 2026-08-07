package com.dreamer.ao.client.gui;

import net.minecraft.network.chat.Component;
import java.util.HashMap;
import java.util.Map;

/**
 * PERF-5: Cache translation results. Call invalidate() on language switch.
 */
public final class TranslatedStrings {

    private static final Map<String, String> cache = new HashMap<>();

    private TranslatedStrings() {}

    public static String get(String key) {
        return cache.computeIfAbsent(key, k -> Component.translatable(k).getString());
    }

    public static String get(String key, Object... args) {
        String cacheKey = key + "|" + args.length;
        return cache.computeIfAbsent(cacheKey, k -> {
            try { return String.format(Component.translatable(key).getString(), args); }
            catch (Exception e) { return key; }
        });
    }

    public static void invalidate() { cache.clear(); }
}