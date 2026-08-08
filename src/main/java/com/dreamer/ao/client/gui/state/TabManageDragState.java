package com.dreamer.ao.client.gui.state;

/**
 * 标签管理界面（TAB_MANAGE）内，自定义标签行的拖拽排序状态。
 */
public class TabManageDragState {

    /** 被拖拽的行索引（对应自定义标签列表），-1 表示未在拖拽。 */
    public int dragFrom = -1;

    /** 鼠标 Y 起点，用于判断是否超过拖拽阈值。 */
    public double dragStartY = 0;

    /** 是否已超过拖拽阈值（区别于单纯点击）。 */
    public boolean dragging = false;

    /** 拖拽时行的实时 Y（用于渲染时跟随鼠标）。 */
    public double dragVisualY = 0;

    public void reset() {
        dragFrom = -1;
        dragStartY = 0;
        dragging = false;
        dragVisualY = 0;
    }
}
