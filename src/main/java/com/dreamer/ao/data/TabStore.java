package com.dreamer.ao.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 标签页（分类）管理模块。
 * <p>
 * 管理自定义标签页的增删和排序。内置标签页（默认、原有成就等 7 个）不可删除，
 * 自定义标签页可以增删。标签页顺序通过 {@code tab_order.json} 持久化。
 * <p>
 * 使用 {@code synchronizedList} 保证列表的线程安全。
 *
 * @see ServerDataStore 使用本类的单例协调器
 */
final class TabStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(TabStore.class);

    // ═══════════════ 自定义标签页 ═══════════════

    private final List<String> customTabs = Collections.synchronizedList(new ArrayList<>());

    // ═══════════════ 标签页排序 ═══════════════

    private final List<String> tabOrder = Collections.synchronizedList(new ArrayList<>());

    // ═══════════════ 查询 ═══════════════

    List<String> getCustomTabs() { return customTabs; }

    boolean isCustomTab(String name) {
        synchronized (customTabs) { return customTabs.contains(name); }
    }

    void addCustomTab(String name) {
        synchronized (customTabs) {
            if (!customTabs.contains(name) && !DataStore.isBuiltinTab(name)) {
                customTabs.add(name);
            }
        }
    }

    /**
     * 删除自定义标签页，并清理所有引用该标签页的成就（将 tab 字段置为 null）。
     * @param name          标签页名称
     * @param advancements  所有成就的 Map（用于清理引用）
     * @param saveTabsFn    保存标签页的回调
     * @param saveAdvsFn    保存成就的回调
     */
    void removeCustomTab(String name, Map<String, ? extends com.dreamer.ao.data.model.CustomAdvancement> advancements,
                         Runnable saveTabsFn, Runnable saveAdvsFn) {
        synchronized (customTabs) { customTabs.remove(name); }
        synchronized (tabOrder) { tabOrder.remove(name); }
        saveTabsFn.run();
        for (var adv : advancements.values()) {
            if (name.equals(adv.getTab())) adv.setTab(null);
        }
        saveAdvsFn.run();
    }

    // ═══════════════ 标签页排序 ═══════════════

    List<String> getTabOrder() { return tabOrder; }

    void setTabOrder(List<String> order) {
        synchronized (tabOrder) {
            tabOrder.clear();
            if (order != null) tabOrder.addAll(order);
        }
    }

    // ═══════════════ 持久化 ═══════════════

    String getOrderJson() {
        synchronized (tabOrder) {
            return DataStore.GSON_PRETTY.toJson(new ArrayList<>(tabOrder));
        }
    }

    void loadOrder(Path file) {
        if (file == null || !Files.exists(file)) return;
        // 标签页顺序存储为 JSON 数组而非对象，需使用数组校验器
        String content = DataStoreIO.readWithFallback(file, DataStoreIO::isValidJsonArray);
        if (content == null) {
            LOGGER.warn("Failed to load tab order: no readable file or backup");
            return;
        }
        try {
            List<String> loaded = DataStore.GSON.fromJson(content,
                    new TypeToken<List<String>>() {}.getType());
            synchronized (tabOrder) {
                tabOrder.clear();
                if (loaded != null) {
                    for (String key : loaded) {
                        tabOrder.add(DataStore.migrateTabKey(key));
                    }
                }
            }
        } catch (Exception e) { LOGGER.warn("Failed to load tab order: {}", e.getMessage()); }
    }
}
