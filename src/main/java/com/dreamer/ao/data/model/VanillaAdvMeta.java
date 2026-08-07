package com.dreamer.ao.data.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 原版（非自定义）进度在画布上的元数据。
 *
 * <h2>关键区分</h2>
 * <ul>
 *   <li>{@code prerequisites} — 模组作者设置的自定义前置条件（跨进度依赖）。
 *       独立于原版进度的 {@code parent} 字段。</li>
 *   <li>原版 {@code parent} — 由原版数据决定，不可通过此元数据修改。</li>
 * </ul>
 */
public class VanillaAdvMeta {
    private int x = -1, y = -1;
    private String tab;
    private List<String> prerequisites;

    public VanillaAdvMeta() {
        this.prerequisites = new ArrayList<>();
    }

    public VanillaAdvMeta(int x, int y) {
        this.x = x;
        this.y = y;
        this.prerequisites = new ArrayList<>();
    }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }
    public int getY() { return y; }
    public void setY(int y) { this.y = y; }
    public String getTab() { return tab; }
    public void setTab(String tab) { this.tab = tab; }

    /** 是否有有效的画布位置（x >= 0 且 y >= 0 表示已设置） */
    public boolean hasPosition() { return x >= 0 && y >= 0; }

    public List<String> getPrerequisites() {
        return prerequisites != null ? prerequisites : new ArrayList<>();
    }

    public void setPrerequisites(List<String> prerequisites) {
        this.prerequisites = prerequisites;
    }
}
