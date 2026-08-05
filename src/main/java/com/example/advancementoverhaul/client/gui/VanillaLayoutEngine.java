package com.example.advancementoverhaul.client.gui;

import com.example.advancementoverhaul.data.ClientDataStore;
import com.example.advancementoverhaul.data.DataStore;

import java.util.*;

import static com.example.advancementoverhaul.client.gui.Theme.CARD_H;
import static com.example.advancementoverhaul.client.gui.Theme.CARD_W;

/**
 * 原版成就布局引擎。
 * <p>
 * 负责加载原版成就数据、按前置关系构建深度层级、计算每张卡片在画布上的坐标。
 * 算法包含两种布局策略：{@code TAB_VANILLA} 放置在自定义成就下方，自定义标签则紧邻已有内容右侧排列。
 * <p>
 * 从 {@link AdvancementScreen} 拆分而来，遵循现有 Manager 模式（如 CanvasManager）。
 */
final class VanillaLayoutEngine {

    private final AdvancementScreen screen;

    VanillaLayoutEngine(AdvancementScreen screen) {
        this.screen = screen;
    }

    /**
     * 加载所有原版成就数据并立即重算坐标。
     */
    void loadVanillaAdvancements() {
        screen.vanillaAdvs.clear();
        screen.vanillaPos.clear();
        screen.vanillaAdvIdSet.clear();
        screen.vanillaAdvMap.clear();

        ClientDataStore cs = ClientDataStore.getInstance();
        for (var e : cs.getVanillaAdvancements())
            screen.vanillaAdvs.add(new AdvancementScreen.VanillaAdv(
                    e.id(), e.name(), e.desc(), e.hidden(), e.nameKey(), e.descKey(), e.icon()));
        for (var va : screen.vanillaAdvs) {
            screen.vanillaAdvIdSet.add(va.id());
            screen.vanillaAdvMap.put(va.id(), va);
        }
        screen.vanillaPositionsDirty = true;
        recalcVanillaPositions();
    }

    /**
     * 按前置关系将成就分组到深度层级。
     * Depth 0 = 无前置, depth 1 = 一个父级, 以此类推。
     */
    private Map<Integer, List<String>> buildDepthLayers(List<AdvancementScreen.VanillaAdv> group,
                                                         Map<String, String> parentMap) {
        Map<Integer, List<String>> layerMap = new TreeMap<>();
        for (var va : group) {
            int depth = 0;
            String cur = va.id();
            Set<String> visited = new HashSet<>();
            visited.add(cur);
            while (parentMap != null) {
                String parent = parentMap.get(cur);
                if (parent == null || !visited.add(parent)) break;
                cur = parent;
                depth++;
            }
            layerMap.computeIfAbsent(depth, k -> new ArrayList<>()).add(va.id());
        }
        return layerMap;
    }

    /**
     * 重新计算所有原版成就卡片的画布坐标。
     * <p>
     * 策略：
     * <ol>
     *   <li>优先使用元数据中手动设置的位置</li>
     *   <li>未定位的按 displayTab 分组</li>
     *   <li>{@code TAB_VANILLA}：在自定义成就下方按深度层排列</li>
     *   <li>自定义标签：在该标签已有内容右侧紧邻排列</li>
     * </ol>
     */
    void recalcVanillaPositions() {
        if (!screen.vanillaPositionsDirty) return;
        screen.vanillaPositionsDirty = false;

        ClientDataStore cs = ClientDataStore.getInstance();
        screen.vanillaPos.clear();

        // 1. 使用元数据中已有的位置
        for (var va : screen.vanillaAdvs) {
            var meta = cs.getVanillaMeta(va.id());
            if (meta != null && meta.hasPosition())
                screen.vanillaPos.put(va.id(), new int[]{meta.getX(), meta.getY()});
        }

        // 2. 按 displayTab 分组未定位的成就
        Map<String, List<AdvancementScreen.VanillaAdv>> byTab = new LinkedHashMap<>();
        for (var va : screen.vanillaAdvs) {
            if (screen.vanillaPos.containsKey(va.id())) continue;
            String tab = cs.getVanillaDisplayTab(va.id());
            if (tab == null || tab.isEmpty()) tab = DataStore.TAB_VANILLA;
            byTab.computeIfAbsent(tab, k -> new ArrayList<>()).add(va);
        }

        // 3. 计算"原有成就"分组的起始 Y（在所有自定义成就下方）
        int vanillaTabY = 40;
        for (var a : cs.getAdvancements().values())
            vanillaTabY = Math.max(vanillaTabY, a.getY() + CARD_H + 80);

        int gapX = CARD_W + 16;
        int gapY = CARD_H + 24;
        Map<String, String> parentMap = cs.getVanillaParentMap();

        // 4. 逐组布局
        for (var entry : byTab.entrySet()) {
            String tab = entry.getKey();
            List<AdvancementScreen.VanillaAdv> group = entry.getValue();
            if (group.isEmpty()) continue;

            Map<Integer, List<String>> layerMap = buildDepthLayers(group, parentMap);
            int layerY;
            int baseX;

            if (DataStore.TAB_VANILLA.equals(tab)) {
                baseX = 20;
                layerY = vanillaTabY;
                vanillaTabY = Math.max(vanillaTabY, layerY + 40);
            } else {
                int rightEdge = 20;
                int tabTopY = Integer.MAX_VALUE;
                for (var a : cs.getAdvancementsByTab(tab)) {
                    rightEdge = Math.max(rightEdge, a.getX() + CARD_W + gapX);
                    tabTopY = Math.min(tabTopY, a.getY());
                }
                for (var va : screen.vanillaAdvs) {
                    int[] pos = screen.vanillaPos.get(va.id());
                    if (pos != null && tab.equals(cs.getVanillaDisplayTab(va.id()))) {
                        rightEdge = Math.max(rightEdge, pos[0] + CARD_W + gapX);
                        tabTopY = Math.min(tabTopY, pos[1]);
                    }
                }
                baseX = rightEdge;
                layerY = tabTopY == Integer.MAX_VALUE ? 40 : tabTopY;
            }

            for (var layer : layerMap.entrySet()) {
                List<String> ids = layer.getValue();
                int layerStartX = Math.max(baseX, 20);
                for (int i = 0; i < ids.size(); i++)
                    screen.vanillaPos.put(ids.get(i),
                            new int[]{layerStartX + i * gapX, layerY});
                layerY += gapY;
            }
        }
    }
}
