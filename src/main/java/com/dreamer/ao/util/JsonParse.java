package com.dreamer.ao.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * JSON 解析辅助工具。
 *
 * <p>消除各模块中重复的「检查字段存在 → Gson 反序列化 → 设置到目标」三段式模板
 * （典型如 {@code NetworkHandlerClient} 中的十余个 {@code parseXxx} 方法），
 * 统一处理字段缺失、反序列化异常与空值回退。</p>
 */
public final class JsonParse {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonParse.class);

    private JsonParse() {
    }

    /**
     * 若 {@code root} 含指定字段，则按给定类型反序列化并交给 {@code consumer}；
     * 字段缺失视为成功（跳过），反序列化异常记录日志并返回 false。
     *
     * @return true 表示无需处理或处理成功
     */
    public static <T> boolean parseField(Gson gson, JsonObject root, String key,
            Type type, java.util.function.Consumer<T> consumer) {
        if (!root.has(key)) {
            return true;
        }
        try {
            T value = gson.fromJson(root.get(key), type);
            if (value != null) {
                consumer.accept(value);
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse '{}': {}", key, e.getMessage());
            return false;
        }
    }

    /** 便捷重载：基于 {@link TypeToken} 推断类型（避免调用处手写 {@code new TypeToken<...>(){}.getType()}） */
    public static <T> boolean parseField(Gson gson, JsonObject root, String key,
            TypeToken<T> token, java.util.function.Consumer<T> consumer) {
        return parseField(gson, root, key, token.getType(), consumer);
    }

    /**
     * 将 JSON 元素解析为 {@code Set<String>}，失败时回退为空集合。
     */
    public static Set<String> parseStringSet(Gson gson, JsonElement elem) {
        if (elem == null || elem.isJsonNull()) {
            return Collections.emptySet();
        }
        try {
            Set<String> result = gson.fromJson(elem, new TypeToken<Set<String>>(){}.getType());
            return result != null ? new LinkedHashSet<>(result) : Collections.emptySet();
        } catch (Exception e) {
            LOGGER.debug("Failed to parse string set: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    // ═══════════════ 类型安全标量取值 ═══════════════
    // 关键：Gson 的 JsonObject.getAsString()/getAsInt() 等会对非 primitive 元素
    // 抛出 UnsupportedOperationException("JsonObject")。以下方法在元素非预期类型时
    // 返回默认值，避免解析异常导致崩溃（如网络推送的 phase brief 结构不符时）。

    public static String optString(JsonElement elem, String def) {
        if (elem == null || elem.isJsonNull()) return def;
        if (elem.isJsonPrimitive()) return elem.getAsString();
        return def;
    }

    public static int optInt(JsonElement elem, int def) {
        if (elem == null || elem.isJsonNull()) return def;
        if (elem.isJsonPrimitive()) {
            try {
                return elem.getAsInt();
            } catch (NumberFormatException e) {
                return def;
            }
        }
        return def;
    }

    public static long optLong(JsonElement elem, long def) {
        if (elem == null || elem.isJsonNull()) return def;
        if (elem.isJsonPrimitive()) {
            try {
                return elem.getAsLong();
            } catch (NumberFormatException e) {
                return def;
            }
        }
        return def;
    }

    public static double optDouble(JsonElement elem, double def) {
        if (elem == null || elem.isJsonNull()) return def;
        if (elem.isJsonPrimitive()) {
            try {
                return elem.getAsDouble();
            } catch (NumberFormatException e) {
                return def;
            }
        }
        return def;
    }

    public static boolean optBoolean(JsonElement elem, boolean def) {
        if (elem == null || elem.isJsonNull()) return def;
        if (elem.isJsonPrimitive()) return elem.getAsBoolean();
        return def;
    }

    /** 从 JsonObject 中按 key 取值（字段缺失或非预期类型时返回默认值） */
    public static String optString(JsonObject obj, String key, String def) {
        return obj != null && obj.has(key) ? optString(obj.get(key), def) : def;
    }

    public static int optInt(JsonObject obj, String key, int def) {
        return obj != null && obj.has(key) ? optInt(obj.get(key), def) : def;
    }

    public static long optLong(JsonObject obj, String key, long def) {
        return obj != null && obj.has(key) ? optLong(obj.get(key), def) : def;
    }

    public static double optDouble(JsonObject obj, String key, double def) {
        return obj != null && obj.has(key) ? optDouble(obj.get(key), def) : def;
    }

    public static boolean optBoolean(JsonObject obj, String key, boolean def) {
        return obj != null && obj.has(key) ? optBoolean(obj.get(key), def) : def;
    }
}
