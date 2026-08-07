package com.dreamer.ao.data;

import com.dreamer.ao.data.model.CustomAdvancement;
import com.dreamer.ao.data.model.VanillaAdvMeta;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据导入/导出处理器。
 * <p>
 * 负责模组配置数据的完整导出和导入，支持成就、标签页、维度锁、原版元数据等。
 * 导入时自动创建备份并提供失败回滚机制。
 * <p>
 * 从 {@link ServerDataStore} 中提取出来以减轻其职责，
 * 通过回调函数与 ServerDataStore 各子模块协作。
 */
public class ImportExportHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportExportHandler.class);

    private ImportExportHandler() {}

    /**
     * 导出所有配置数据为 JSON。
     */
    public static JsonObject exportAll(
            Map<String, CustomAdvancement> advancements,
            List<String> customTabs,
            Map<String, DimensionLock> dimensionLocks,
            Map<String, VanillaAdvMeta> vanillaMeta,
            List<String> tabOrder) {
        JsonObject root = new JsonObject();
        root.add("advancements", DataStore.GSON.toJsonTree(advancements));
        root.add("customTabs", DataStore.GSON.toJsonTree(new ArrayList<>(customTabs)));
        root.add("dimensionLocks", DataStore.GSON.toJsonTree(dimensionLocks));
        root.add("vanillaMeta", DataStore.GSON.toJsonTree(vanillaMeta));
        root.add("tabOrder", DataStore.GSON.toJsonTree(new ArrayList<>(tabOrder)));
        return root;
    }

    /**
     * 从 JSON 导入配置数据（覆盖现有数据并自动保存）。
     * 导入前先创建备份，如果导入数据格式不完整则拒绝导入。
     *
     * @param data      导入的 JSON 数据
     * @param context   提供对各子模块数据访问的回调上下文
     * @throws IllegalArgumentException 如果导入数据格式不完整
     * @throws RuntimeException         如果导入过程中发生错误（含回滚失败的风险）
     */
    public static void importAll(JsonObject data, ImportContext context) {
        // 1. 验证必需字段
        if (!data.has("advancements") && !data.has("customTabs")
                && !data.has("dimensionLocks") && !data.has("vanillaMeta")
                && !data.has("tabOrder")) {
            LOGGER.warn("Import rejected: JSON contains no recognized data sections");
            throw new IllegalArgumentException("Import data contains no recognized sections");
        }

        // 2. 创建备份（用于导入失败时回滚）
        JsonObject backup = context.exportBackup();

        try {
            // 3. 验证并应用各部分
            if (data.has("advancements")) {
                Map<String, CustomAdvancement> advs = DataStore.GSON.fromJson(
                        data.get("advancements"),
                        new TypeToken<Map<String, CustomAdvancement>>() {}.getType());
                if (advs == null || advs.isEmpty()) {
                    LOGGER.warn("Import: advancements section is empty or invalid");
                } else {
                    for (CustomAdvancement adv : advs.values()) {
                        if (adv.getId() == null || adv.getId().isEmpty()) {
                            throw new IllegalArgumentException("Import contains advancement with empty ID");
                        }
                        String tab = adv.getTab();
                        if (tab != null) adv.setTab(DataStore.normalizeTabName(tab));
                    }
                    context.replaceAdvancements(advs);
                }
            }
            if (data.has("customTabs")) {
                List<String> tabs = DataStore.GSON.fromJson(data.get("customTabs"),
                        new TypeToken<List<String>>() {}.getType());
                if (tabs != null) context.setCustomTabs(tabs);
            }
            if (data.has("dimensionLocks")) {
                Map<String, DimensionLock> locks = DataStore.GSON.fromJson(
                        data.get("dimensionLocks"),
                        new TypeToken<Map<String, DimensionLock>>() {}.getType());
                if (locks != null) context.setDimensionLocks(locks);
            }
            if (data.has("vanillaMeta")) {
                Map<String, VanillaAdvMeta> meta = DataStore.GSON.fromJson(
                        data.get("vanillaMeta"),
                        new TypeToken<Map<String, VanillaAdvMeta>>() {}.getType());
                if (meta != null) context.setVanillaMeta(meta);
            }
            if (data.has("tabOrder")) {
                List<String> order = DataStore.GSON.fromJson(data.get("tabOrder"),
                        new TypeToken<List<String>>() {}.getType());
                if (order != null) context.setTabOrder(order);
            }
            context.saveAll();
            LOGGER.info("Import successful: {} advancements, {} tabs, {} dimension locks",
                    context.getAdvancementCount(),
                    context.getCustomTabCount(),
                    context.getDimensionLockCount());
        } catch (Exception e) {
            // 4. 导入失败时直接还原备份，避免递归调用
            LOGGER.error("Import failed, rolling back to backup. Error: {}", e.getMessage());
            try {
                context.restoreFromBackup(backup);
                LOGGER.info("Rollback successful");
            } catch (Exception rollbackError) {
                LOGGER.error("CRITICAL: Rollback also failed! Data may be inconsistent. Error: {}",
                        rollbackError.getMessage());
            }
            throw new RuntimeException("Import failed: " + e.getMessage(), e);
        }
    }

}
