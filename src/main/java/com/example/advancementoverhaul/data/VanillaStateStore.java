package com.example.advancementoverhaul.data;

import com.example.advancementoverhaul.Config;
import com.example.advancementoverhaul.ModInfo;
import com.example.advancementoverhaul.data.model.VanillaAdvMeta;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 原版/模组进度的状态管理模块。
 * <p>
 * 负责三类数据的存储与查询：
 * <ul>
 *   <li><b>启用/禁用状态</b> — 控制哪些原版/模组进度在 runtime Map 中可见</li>
 *   <li><b>元数据</b> — 原版进度的自定义位置（x/y）、标签页分配、图标</li>
 *   <li><b>原始 JSON 缓存</b> — 服务端 AdvancementManager 的快照，用于注入和恢复</li>
 *   <li><b>父子关系</b> — 原版进度的 parent 映射（用于 BFS 遍历）</li>
 * </ul>
 *
 * @see ServerDataStore 使用本类的单例协调器
 */
final class VanillaStateStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/VanillaState");

    // ═══════════════ 启用/禁用状态 ═══════════════

    private final Set<String> disabled = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> enabled = Collections.synchronizedSet(new HashSet<>());

    // ═══════════════ 原版元数据 ═══════════════

    private final Map<String, VanillaAdvMeta> meta = new ConcurrentHashMap<>();

    // ═══════════════ 原始 JSON 缓存 ═══════════════

    private volatile Map<String, JsonElement> rawCache = null;
    private final Map<String, String> parentMap = new ConcurrentHashMap<>();

    // ═══════════════ 启用/禁用 ═══════════════

    Set<String> getDisabled() { return disabled; }
    Set<String> getEnabled() { return enabled; }

    /**
     * 判断某个原版/模组进度是否启用。
     * 优先级：enabled 列表 > disabled 列表 > 配置默认值。
     */
    boolean isEnabled(String id) {
        if (enabled.contains(id)) return true;
        if (disabled.contains(id)) return false;
        try { return Config.VANILLA_DEFAULT_ENABLED.get(); }
        catch (IllegalStateException e) { return false; }
    }

    void setEnabled(String id, boolean value) {
        if (value) { disabled.remove(id); enabled.add(id); }
        else       { enabled.remove(id); disabled.add(id); }
    }

    void setDisabledBatch(Set<String> ids) {
        disabled.addAll(ids);
        enabled.removeAll(ids);
    }

    void enableAll(Set<String> allIds) {
        disabled.clear();
        enabled.clear();
        enabled.addAll(allIds);
    }

    // ═══════════════ 原版元数据 ═══════════════

    Map<String, VanillaAdvMeta> getMetaMap() { return meta; }
    VanillaAdvMeta getMeta(String id) { return meta.get(id); }
    void setMeta(String id, VanillaAdvMeta m) { meta.put(id, m); }

    // ═══════════════ 原始 JSON 缓存 ═══════════════

    Map<String, JsonElement> getRawCache() { return rawCache; }
    void setRawCache(Map<String, JsonElement> cache) { this.rawCache = cache; }
    Map<String, String> getParentMap() { return parentMap; }

    /**
     * 从服务端 AdvancementManager 缓存所有非自定义进度的原始 JSON。
     * 排除本模组自己的进度（以 MOD_ID 为命名空间前缀）。
     */
    void cacheFromServer(MinecraftServer server) {
        if (server == null) return;
        try {
            Map<String, JsonElement> cache = new HashMap<>();
            parentMap.clear();
            for (var holder : server.getAdvancements().getAllAdvancements()) {
                String id = holder.id().toString();
                if (id.startsWith(ModInfo.MOD_ID + ":")) continue;

                JsonObject obj = new JsonObject();
                obj.addProperty("id", id);
                holder.value().parent().ifPresent(p -> {
                    obj.addProperty("parent", p.toString());
                    parentMap.put(id, p.toString());
                });
                holder.value().display().ifPresent(disp -> {
                    JsonObject display = new JsonObject();
                    display.add("title", componentToJson(disp.getTitle()));
                    display.add("description", componentToJson(disp.getDescription()));
                    ItemStack icon = disp.getIcon();
                    if (!icon.isEmpty()) {
                        JsonObject iconObj = new JsonObject();
                        var rl = BuiltInRegistries.ITEM.getKey(icon.getItem());
                        if (rl != null) iconObj.addProperty("id", rl.toString());
                        display.add("icon", iconObj);
                    }
                    obj.add("display", display);
                });
                cache.put(id, obj);
            }
            rawCache = cache;
        } catch (Exception e) { LOGGER.warn("Failed to cache vanilla advancements from server: {}", e.getMessage()); }
    }

    /** 将 Component 转为 JSON：可翻译文本 → {"translate":"key"}，其他 → 纯文本字符串 */
    private static JsonElement componentToJson(Component comp) {
        if (comp == null) return new JsonPrimitive("");
        if (comp.getContents() instanceof TranslatableContents tc) {
            JsonObject o = new JsonObject();
            o.addProperty("translate", tc.getKey());
            return o;
        }
        return new JsonPrimitive(comp.getString());
    }

    // ═══════════════ 持久化 ═══════════════

    /** 保存启用/禁用状态为 JSON */
    JsonObject statesToJson() {
        JsonObject root = new JsonObject();
        root.add("disabled", DataStore.GSON.toJsonTree(new ArrayList<>(disabled)));
        root.add("enabled", DataStore.GSON.toJsonTree(new ArrayList<>(enabled)));
        return root;
    }

    /** 从 JSON 加载启用/禁用状态 */
    void loadStates(Path file) {
        if (file == null || !Files.exists(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            disabled.clear(); enabled.clear();
            if (root.has("disabled") && root.get("disabled").isJsonArray())
                for (JsonElement e : root.getAsJsonArray("disabled"))
                    if (e.isJsonPrimitive()) disabled.add(e.getAsString());
            if (root.has("enabled") && root.get("enabled").isJsonArray())
                for (JsonElement e : root.getAsJsonArray("enabled"))
                    if (e.isJsonPrimitive()) enabled.add(e.getAsString());
        } catch (Exception e) { LOGGER.warn("Failed to load vanilla states: {}", e.getMessage()); }
    }

    /** 保存原版元数据为 JSON */
    String metaToJson() { return DataStore.GSON_PRETTY.toJson(meta); }

    /** 从 JSON 加载原版元数据 */
    void loadMeta(Path file) {
        if (file == null || !Files.exists(file)) return;
        try {
            Map<String, VanillaAdvMeta> loaded = DataStore.GSON.fromJson(
                    Files.readString(file),
                    new TypeToken<Map<String, VanillaAdvMeta>>() {}.getType());
            meta.clear();
            if (loaded != null) meta.putAll(loaded);
        } catch (Exception e) { LOGGER.warn("Failed to load vanilla meta: {}", e.getMessage()); }
    }

    /** 保存原始缓存为 JSON */
    String rawCacheToJson() { return rawCache != null ? DataStore.GSON_PRETTY.toJson(rawCache) : "{}"; }
}
