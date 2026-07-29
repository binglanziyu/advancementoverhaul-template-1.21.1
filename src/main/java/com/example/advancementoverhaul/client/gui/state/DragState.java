package com.example.advancementoverhaul.client.gui.state;

/**
 * 拖拽交互状态。
 * <p>
 * 记录画布上被拖拽的卡片/图片的 ID、起始位置、当前位置，
 * 以及框选操作（鼠标拖拽矩形选择多个卡片）的状态。
 */
public class DragState {
    public String dragCardId = null;
    public String dragImageId = null;
    public boolean dragMoved = false;
    public double dragStartMX, dragStartMY, lastDragMX, lastDragMY;
    public boolean boxSel = false; public double bsx, bsy, bex, bey;
    public void reset() { dragCardId = null; dragImageId = null; dragMoved = false; boxSel = false; }
}