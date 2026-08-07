package com.dreamer.ao.client.gui.state;

import static com.dreamer.ao.client.gui.Theme.*;

/**
 * 画布视图状态：滚动偏移、缩放级别与坐标系转换。
 * <p>
 * 提供世界坐标（成就位置）与屏幕坐标（像素）之间的双向转换方法。
 * 缩放以鼠标位置为中心点进行（zoomAt），保证直觉化的缩放体验。
 */
public class CanvasState {
    public double scrollX = 80, scrollY = 80, zoom = 1.0;
    public boolean panning = false;
    public void zoomAt(double mx, double my, double delta) { double old = zoom; double step = Math.max(0.02, zoom * 0.1); zoom = Math.clamp(zoom + delta * step, ZOOM_MIN, ZOOM_MAX); double r = zoom / old; scrollX = mx - (mx - scrollX) * r; scrollY = my - (my - scrollY) * r; }
    public int toScreenX(double wx) { return (int)(wx * zoom + scrollX); }
    public int toScreenY(double wy) { return (int)(wy * zoom + scrollY); }
    public int screenW(int w) { return (int)(w * zoom); }
    public int screenH(int h) { return (int)(h * zoom); }
    public int toWorldX(double sx) { return (int)((sx - scrollX) / zoom); }
    public int toWorldY(double sy) { return (int)((sy - scrollY) / zoom); }
}