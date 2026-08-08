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
}
