package com.example.advancementoverhaul.client.gui.widget;

import net.minecraft.client.gui.GuiGraphics;

/**
 * QUAL-4: Reusable scrollbar component.
 */
public class ScrollBar {

    private int scroll = 0;
    private int contentHeight = 0;
    private int viewHeight = 0;
    private int scrollStep = 30;
    private boolean dragging = false;
    private double dragStartY;
    private int dragStartScroll;
    private final int barWidth;
    private final int trackColor;
    private final int thumbColor;

    public ScrollBar(int barWidth, int trackColor, int thumbColor) {
        this.barWidth = barWidth; this.trackColor = trackColor; this.thumbColor = thumbColor;
    }

    public void update(int contentHeight, int viewHeight) {
        this.contentHeight = contentHeight; this.viewHeight = viewHeight;
        scroll = Math.min(scroll, Math.max(0, contentHeight - viewHeight));
    }

    public int getScroll() { return scroll; }
    public boolean needsScrollbar() { return contentHeight > viewHeight; }
    public void setScroll(int s) { scroll = s; }

    public void render(GuiGraphics g, int trackX, int trackY) {
        if (!needsScrollbar()) return;
        int thumbH = Math.max(16, (int)((float) viewHeight / contentHeight * viewHeight));
        int maxScroll = contentHeight - viewHeight;
        int thumbY = trackY + (maxScroll > 0 ? (int)((float) scroll / maxScroll * (viewHeight - thumbH)) : 0);
        g.fill(trackX, trackY, trackX + barWidth, trackY + viewHeight, trackColor);
        g.fill(trackX + 1, thumbY, trackX + barWidth - 1, thumbY + thumbH, thumbColor);
    }

    public boolean handleScroll(double delta) {
        if (!needsScrollbar()) return false;
        scroll = (int) Math.clamp(scroll - delta * scrollStep, 0, contentHeight - viewHeight);
        return true;
    }

    public boolean handleClick(double mx, double my, int trackX, int trackY) {
        if (!needsScrollbar() || mx < trackX || mx > trackX + barWidth
                || my < trackY || my > trackY + viewHeight) return false;
        dragging = true; dragStartY = my; dragStartScroll = scroll;
        return true;
    }

    public boolean handleRelease() { if (dragging) { dragging = false; return true; } return false; }

    public boolean handleDrag(double my) {
        if (!dragging || !needsScrollbar()) return false;
        int thumbH = Math.max(16, (int)((float) viewHeight / contentHeight * viewHeight));
        int maxScroll = contentHeight - viewHeight;
        scroll = (int) Math.clamp(dragStartScroll + (my - dragStartY) * maxScroll / (viewHeight - thumbH), 0, maxScroll);
        return true;
    }
}