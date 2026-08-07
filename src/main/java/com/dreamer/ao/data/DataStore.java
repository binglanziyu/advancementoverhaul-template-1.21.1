package com.dreamer.ao.data;

import com.dreamer.ao.data.model.AdvancementCondition;
import com.dreamer.ao.data.model.CustomAdvancement;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.*;

/**
 * 标签页常量 · JSON 序列化配置。
 *
 * <p>枚举类型已拆分为独立的顶层文件：
 * <ul>
 *   <li>{@link ConditionType} - 11 种条件类型</li>
 *   <li>{@link NbtMatchMode} - 4 种 NBT 匹配模式</li>
 *   <li>{@link DataSource} - 条件数据源类别</li>
 * </ul>
 *
 * <h2>数据模型分布</h2>
 * <table>
 *   <tr><th>位置</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>{@link com.dreamer.ao.data.model.CustomAdvancement model/}</td><td>类</td><td>自定义进度</td></tr>
 *   <tr><td>{@link com.dreamer.ao.data.model.AdvancementCondition model/}</td><td>类</td><td>进度条件</td></tr>
 *   <tr><td>{@link com.dreamer.ao.data.model.VanillaAdvMeta model/}</td><td>类</td><td>原版进度元数据</td></tr>
 * </table>
 */
public class DataStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataStore.class);

    // ═══════════════ 内置标签页常量 ═══════════════

    /** 英文内部标识（新格式），作为标签页存储键值 */
    public static final String TAB_DEFAULT = "default";
    public static final String TAB_VANILLA = "vanilla";

    /** 旧格式中文标识 → 新格式英文标识的迁移映射 */
    private static final String OLD_KEY_DEFAULT_CN = "默认";
    private static final String OLD_KEY_VANILLA_CN = "原有成就";
    private static final java.util.Map<String, String> TAB_KEY_MIGRATION = java.util.Map.of(
            OLD_KEY_DEFAULT_CN, TAB_DEFAULT,
            OLD_KEY_VANILLA_CN, TAB_VANILLA
    );

    /** 两个内置标签页，按默认顺序排列 */
    public static final List<String> BUILTIN_TABS = List.of(TAB_DEFAULT, TAB_VANILLA);

    /**
     * 旧格式标签页 key → 新格式，用于数据迁移。
     * 如果 key 已经是新格式则原样返回。
     */
    public static String migrateTabKey(String key) {
        if (key == null) return null;
        return TAB_KEY_MIGRATION.getOrDefault(key, key);
    }

    /**
     * 规范化标签页名称。旧版 "vanilla:xxx" 格式统一映射到 TAB_VANILLA；
     * 其他名称原样返回（兼容旧中文 key 自动迁移）。
     *
     * @param tab 原始标签页名称
     * @return 规范化后的标签页名称
     */
    public static String normalizeTabName(String tab) {
        if (tab == null) return null;
        if (tab.startsWith("vanilla:")) {
            return TAB_VANILLA;
        }
        return migrateTabKey(tab);
    }

    /** 判断是否为内置标签页（不可删除） */
    public static boolean isBuiltinTab(String tab) {
        return BUILTIN_TABS.contains(tab);
    }

    /**
     * 获取内置标签页的本地化显示名。
     * <p>
     * 内置标签页的内部标识（如"默认"、"原有成就"等）作为存储键值保留不变，
     * 但 GUI 渲染时通过此方法获取对应翻译键的本地化文本。
     * 非内置标签页直接返回原名称。
     *
     * @param tab 标签页内部标识
     * @return 本地化显示名
     */
    public static String getTabDisplayName(String tab) {
        return switch (tab) {
            case TAB_DEFAULT -> net.minecraft.network.chat.Component.translatable(
                    com.dreamer.ao.LangKeys.TAB_DEFAULT).getString();
            case TAB_VANILLA -> net.minecraft.network.chat.Component.translatable(
                    com.dreamer.ao.LangKeys.TAB_VANILLA_DISPLAY).getString();
            default -> tab;
        };
    }

    // ═══════════════ JSON 序列化配置 ═══════════════

    private static final ConditionType.ConditionTypeAdapter COND_TYPE_ADAPTER = new ConditionType.ConditionTypeAdapter();

    /** 紧凑 Gson 实例，用于网络传输 */
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(ConditionType.class, COND_TYPE_ADAPTER)
            .create();

    /** 格式化 Gson 实例，用于文件持久化（人类可读） */
    public static final Gson GSON_PRETTY = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(ConditionType.class, COND_TYPE_ADAPTER)
            .create();

    /**
     * 从 JSON 字符串反序列化成就 Map。
     * 返回空 Map 而非 null，简化调用方代码。
     */
    public static Map<String, CustomAdvancement> mapFromJson(String json) {
        Type t = new TypeToken<Map<String, CustomAdvancement>>() {}.getType();
        Map<String, CustomAdvancement> r = GSON.fromJson(json, t);
        return r != null ? r : new HashMap<>();
    }
}
