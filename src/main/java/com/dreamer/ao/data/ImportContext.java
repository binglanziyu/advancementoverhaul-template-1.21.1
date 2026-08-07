package com.dreamer.ao.data;

import com.dreamer.ao.data.model.CustomAdvancement;
import com.dreamer.ao.data.model.VanillaAdvMeta;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

/**
 * 导入操作的上下文接口，由 {@link ServerDataStore} 实现，
 * 提供对各子模块的访问和修改能力。
 */
public interface ImportContext {
    JsonObject exportBackup();
    void restoreFromBackup(JsonObject backup);
    void replaceAdvancements(Map<String, CustomAdvancement> advs);
    void setCustomTabs(List<String> tabs);
    void setDimensionLocks(Map<String, DimensionLock> locks);
    void setVanillaMeta(Map<String, VanillaAdvMeta> meta);
    void setTabOrder(List<String> order);
    void saveAll();
    int getAdvancementCount();
    int getCustomTabCount();
    int getDimensionLockCount();
}
