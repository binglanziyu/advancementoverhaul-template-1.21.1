package com.dreamer.ao.client.gui.panel;

import com.dreamer.ao.LangKeys;
import com.dreamer.ao.client.gui.GuiUtils;
import com.dreamer.ao.data.DataStore;
import com.dreamer.ao.data.model.AdvancementCondition;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * Handles serialization/deserialization and save logic for the EditPanel.
 */
class EditPanelSerializer {

    private final EditPanel panel;

    EditPanelSerializer(EditPanel panel) {
        this.panel = panel;
    }

    /**
     * 将单个条件序列化为 Map
     */
    Map<String, Object> conditionToMap(AdvancementCondition c) {
        Map<String, Object> cm = new LinkedHashMap<>();
        cm.put("type", c.getType().name().toLowerCase());
        if (c.getTargetId() != null && !c.getTargetId().isEmpty()) cm.put("targetId", c.getTargetId());
        cm.put("count", c.getCount());
        DataStore.NbtMatchMode nbtMode = c.getNbtMatchMode();
        if (nbtMode != null && nbtMode != DataStore.NbtMatchMode.IGNORE) cm.put("nbtMatchMode", nbtMode.getSaveName());
        if (c.getTargetNbt() != null && !c.getTargetNbt().isEmpty()) cm.put("targetNbt", c.getTargetNbt());
        return cm;
    }

    /**
     * 将条件列表序列化为 Map 列表
     */
    List<Map<String, Object>> conditionsToMapList(List<AdvancementCondition> conditions) {
        List<Map<String, Object>> condList = new ArrayList<>();
        for (AdvancementCondition c : conditions) {
            condList.add(conditionToMap(c));
        }
        return condList;
    }

    void saveEd() {
        if (panel.screen == null) return;
        if (panel.vanillaEditMode && panel.edId != null) {
            // 原版成就：单条命令保存标签和前置条件
            String tab = panel.edTab != null ? panel.edTab : DataStore.TAB_VANILLA;
            String prereqJson = DataStore.GSON.toJson(panel.edPrereqs);
            GuiUtils.sendCommand("adv vanilla save " + panel.edId + " " + "{\"tab\":\"" + tab + "\",\"prerequisites\":" + prereqJson + "}");
            panel.close();
            return;
        }
        panel.commitNameAndDesc();
        if (panel.inlineEditingCount) panel.commitInlineCountEdit();
        if (panel.edTab == null || panel.edTab.isEmpty()) panel.edTab = DataStore.TAB_DEFAULT;

        // 验证：新建成就时，图标、名称和条件为必填项
        if (panel.edId == null) {
            boolean hasName = panel.edName != null && !panel.edName.isEmpty();
            boolean hasIcon = panel.edIcon != null && !panel.edIcon.isEmpty();
            boolean hasConds = !panel.edConds.isEmpty()
                    && panel.edConds.stream().anyMatch(c ->
                        c.getTargetId() != null && !c.getTargetId().isEmpty());
            if (!hasName || !hasIcon || !hasConds) {
                if (panel.screen != null)
                    panel.screen.addToast(Component.translatable(LangKeys.VALIDATION_REQUIRED).getString());
                return;
            }
        }

        // 验证：名称不能为空（编辑已有成就时的兜底保护）
        if (panel.edName == null || panel.edName.isEmpty()) {
            panel.edName = panel.edId != null ? panel.edId : "";
            if (panel.edName.isEmpty()) {
                panel.close();
                return;
            }
        }
        // 验证：至少有一个条件（或前置条件）
        if (panel.edConds.isEmpty() && panel.edPrereqs.isEmpty()) {
            panel.edConds.add(new AdvancementCondition(
                DataStore.ConditionType.KILL_ENTITY, "", 1));
        }

        String advId;
        if (panel.edId == null) {
            advId = "custom_" + UUID.randomUUID().toString().substring(0, 8);
        } else {
            advId = panel.edId;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", advId);
        data.put("name", panel.edName);
        data.put("description", panel.edDesc);
        data.put("x", panel.edX);
        data.put("y", panel.edY);
        data.put("hidden", panel.edHidden);
        if (panel.edIcon != null) data.put("icon", panel.edIcon);
        if (panel.edLore != null && !panel.edLore.isEmpty()) data.put("lore", panel.edLore);
        data.put("tab", panel.edTab);
        if (!panel.edPrereqs.isEmpty()) data.put("prerequisites", new ArrayList<>(panel.edPrereqs));
        if (!panel.edConds.isEmpty()) {
            data.put("conditions", conditionsToMapList(panel.edConds));
        }

        String json = DataStore.GSON.toJson(data);
        String encoded = Base64.getEncoder().encodeToString(
                json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        GuiUtils.sendCommand("adv updatejson " + encoded);
        panel.close();
    }
}
