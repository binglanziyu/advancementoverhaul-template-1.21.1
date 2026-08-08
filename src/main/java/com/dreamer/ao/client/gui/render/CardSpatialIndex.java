package com.dreamer.ao.client.gui.render;

import com.dreamer.ao.client.gui.AdvancementScreen;
import com.dreamer.ao.client.gui.ImageElement;
import com.dreamer.ao.client.gui.layout.LayoutMetrics;
import com.dreamer.ao.data.ClientDataStore;
import com.dreamer.ao.data.model.CustomAdvancement;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 空间网格索引（PERF-18）。
 * <p>
 * 将「千级卡片视口裁剪」所需的空间网格与卡片条目预计算从 {@code CardRenderer} 剥离。
 * {@code CardRenderer} 在需要命中检测或裁剪遍历时委托本类，对外保持
 * {@code queryCardIdsNear} 的方法签名不变。
 * <p>
 * 边界框缓存（PERF-7）仍由 {@code CardRenderer} 持有，并在其 {@code ensureBounds}
 * 末尾调用本类的 {@link #ensureGrid()}，复用同一套脏标记驱动。
 */
public final class CardSpatialIndex {

    /** 网格单元的世界坐标大小（约 20 个卡片宽度 = 480 世界单位） */
    private static final int GRID_CELL_SIZE = 480;

    /** 空间网格脏标记 */
    private boolean gridDirty = true;

    /** 空间网格：Long(cellX<<32|cellY) → 该单元格内的卡片渲染条目列表 */
    private final Map<Long, List<CardEntry>> spatialGrid = new java.util.HashMap<>();

    private final AdvancementScreen screen;

    public CardSpatialIndex(AdvancementScreen screen) {
        this.screen = screen;
    }

    public void markDirty() { gridDirty = true; }

    /** 表示一个需要渲染的卡片条目（自定义或原版），避免每帧重复查找 ClientDataStore */
    public record CardEntry(String id, String name, String icon,
                            int wx, int wy, boolean done, boolean showId, boolean enabled, boolean hidden) {}

    /** 将世界坐标编码为网格单元格 key */
    private static long cellKey(int worldX, int worldY) {
        int cx = Math.floorDiv(worldX, GRID_CELL_SIZE);
        int cy = Math.floorDiv(worldY, GRID_CELL_SIZE);
        return ((long) cx << 32) | (cy & 0xFFFFFFFFL);
    }

    /** 重建空间网格索引 */
    public void ensureGrid() {
        if (!gridDirty) return;
        gridDirty = false;
        spatialGrid.clear();

        ClientDataStore cs = ClientDataStore.getInstance();

        // 索引自定义进度
        for (var a : screen.frameFiltered) {
            if (!screen.isCardVisible(a)) continue;
            long key = cellKey(a.getX(), a.getY());
            spatialGrid.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new CardEntry(a.getId(), a.getName(), a.getIcon(),
                            a.getX(), a.getY(), cs.isCompleted(a.getId()), false, true,
                            a.isHidden() && !cs.isCompleted(a.getId())));
        }

        // 索引原版进度
        for (var va : screen.vanillaAdvs) {
            if (!screen.shouldShowVanilla(va.id())) continue;
            int[] p = screen.vanillaPos.get(va.id());
            if (p == null) continue;
            long key = cellKey(p[0], p[1]);
            spatialGrid.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new CardEntry(va.id(), va.getLocalizedName(), va.icon(),
                            p[0], p[1], cs.isCompleted(va.id()), false,
                            cs.isVanillaEnabled(va.id()), false));
        }
    }

    /**
     * 查询世界坐标附近所有卡片 ID，供 CanvasManager 命中检测使用。
     * <p>
     * 使用空间网格索引（O(1) 单元格查找 + 少量条目遍历）替代线性扫描所有卡片，
     * 在千级卡片场景下将每次鼠标点击从 O(n) 降至 O(1)。
     *
     * @param worldX 世界坐标 X
     * @param worldY 世界坐标 Y
     * @return 覆盖该位置的所有单元格内的卡片 ID 集合（含自定义和原版）
     */
    public Set<String> queryCardIdsNear(int worldX, int worldY) {
        ensureGrid();
        Set<String> ids = new LinkedHashSet<>();
        int cellMinX = Math.floorDiv(worldX - LayoutMetrics.CARD_W, GRID_CELL_SIZE);
        int cellMaxX = Math.floorDiv(worldX + LayoutMetrics.CARD_W, GRID_CELL_SIZE);
        int cellMinY = Math.floorDiv(worldY - LayoutMetrics.CARD_H, GRID_CELL_SIZE);
        int cellMaxY = Math.floorDiv(worldY + LayoutMetrics.CARD_H, GRID_CELL_SIZE);
        for (int cx = cellMinX; cx <= cellMaxX; cx++) {
            for (int cy = cellMinY; cy <= cellMaxY; cy++) {
                List<CardEntry> entries = spatialGrid.get(((long) cx << 32) | (cy & 0xFFFFFFFFL));
                if (entries == null) continue;
                for (CardEntry entry : entries) {
                    ids.add(entry.id());
                }
            }
        }
        return ids;
    }

    /** 暴露空间网格供 {@code CardRenderer.renderCards} 进行视口裁剪遍历。 */
    public Map<Long, List<CardEntry>> grid() { return spatialGrid; }

    /** 暴露单元格尺寸供 {@code CardRenderer.renderCards} 推算视口范围。 */
    public int cellSize() { return GRID_CELL_SIZE; }
}
