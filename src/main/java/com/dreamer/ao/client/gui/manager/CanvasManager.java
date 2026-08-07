package com.dreamer.ao.client.gui.manager;

import com.dreamer.ao.client.gui.AdvancementScreen;
import com.dreamer.ao.client.gui.GuiUtils;
import com.dreamer.ao.data.ClientDataStore;
import com.dreamer.ao.data.DataStore;
import com.dreamer.ao.data.model.CustomAdvancement;
import java.util.Iterator;

import static com.dreamer.ao.client.gui.Theme.*;

/**
 * 画布交互管理：卡片命中、视图重置、框选、滚动条。
 */
public class CanvasManager {

    final AdvancementScreen screen;
    boolean scrollDragH = false, scrollDragV = false;

    public CanvasManager(AdvancementScreen screen) { this.screen = screen; }

    // ═══════════════ 卡片命中检测 ═══════════════

    /**
     * Returns the ID of the topmost card (highest z-order) at the given screen
     * coordinates, or null if no card is hit.
     *
     * <p>Checks custom advancements first (rendered on top), then vanilla
     * advancements (only when the vanilla tab is active).
     */
    /**
     * 卡片命中检测：将屏幕坐标转换到世界坐标后，通过空间网格索引做 O(1) 查找。
     * <p>
     * CardinalRenderer 的空间网格在渲染时维护，同一帧内复用于命中检测。
     * 仅检查鼠标所在位置附近单元格内的卡片，替代旧版全量线性扫描。
     */
    public String cardAt(int mx, int my) {
        ClientDataStore s = ClientDataStore.getInstance();
        int worldX = screen.canvas.toWorldX(mx);
        int worldY = screen.canvas.toWorldY(my);

        java.util.Set<String> candidates = screen.cardRenderer.queryCardIdsNear(worldX, worldY);

        for (String id : candidates) {
            int wx, wy;
            boolean isVanilla = screen.vanillaPos.containsKey(id);
            if (isVanilla) {
                if (!screen.shouldShowVanilla(id)) continue;
                int[] p = screen.vanillaPos.get(id);
                if (p == null) continue;
                wx = p[0];
                wy = p[1];
            } else {
                CustomAdvancement a = s.getAdvancement(id);
                if (a == null) continue;
                if (!screen.isCardVisible(a)) continue;
                wx = a.getX();
                wy = a.getY();
            }
            int x = screen.canvas.toScreenX(wx);
            int y = screen.canvas.toScreenY(wy);
            int cw = screen.canvas.screenW(CARD_W);
            int ch = screen.canvas.screenH(CARD_H);
            if (GuiUtils.inRect(mx, my, x, y, cw, ch)) return id;
        }
        return null;
    }

    // ═══════════════ 重置视图 ═══════════════

    /**
     * Resets the canvas to zoom 1.0 and centers on the first available card.
     * Prefers a custom advancement; falls back to a vanilla advancement if none exist.
     */
    public void resetView() {
        screen.canvas.zoom = 1.0;
        ClientDataStore cs = ClientDataStore.getInstance();

        // Find any advancement position to center on
        int targetX = 0, targetY = 0;
        boolean found = false;

        Iterator<CustomAdvancement> it = cs.getAdvancements().values().iterator();
        if (it.hasNext()) {
            var a = it.next();
            targetX = a.getX();
            targetY = a.getY();
            found = true;
        }

        if (!found) {
            for (var va : screen.vanillaAdvs) {
                int[] p = screen.vanillaPos.get(va.id());
                if (p != null) {
                    targetX = p[0];
                    targetY = p[1];
                    found = true;
                    break;
                }
            }
        }

        if (found) {
            screen.canvas.scrollX = screen.getScreenWidth() / 2.0 - targetX * screen.canvas.zoom - CARD_W * screen.canvas.zoom / 2.0;
            screen.canvas.scrollY = (screen.getScreenHeight() - BOTTOM_H + TAB_H) / 2.0 - targetY * screen.canvas.zoom - CARD_H * screen.canvas.zoom / 2.0;
        } else {
            screen.canvas.scrollX = screen.getScreenWidth() / 2.0;
            screen.canvas.scrollY = screen.getScreenHeight() / 2.0;
        }
    }

    // ═══════════════ 框选 ═══════════════

    /**
     * Applies box selection: adds all visible advancements within the drag rectangle
     * to the multi-selection set. Uses frameFiltered to stay consistent with what
     * is rendered on screen (not the raw data query).
     */
    void applyBoxSel() {
        screen.selection.multiSel.clear();
        int x1 = (int) Math.min(screen.drag.bsx, screen.drag.bex);
        int y1 = (int) Math.min(screen.drag.bsy, screen.drag.bey);
        int x2 = (int) Math.max(screen.drag.bsx, screen.drag.bex);
        int y2 = (int) Math.max(screen.drag.bsy, screen.drag.bey);
        for (var a : screen.frameFiltered) {
            int cx = screen.canvas.toScreenX(a.getX());
            int cy = screen.canvas.toScreenY(a.getY());
            int cw = screen.canvas.screenW(CARD_W);
            int ch = screen.canvas.screenH(CARD_H);
            if (cx + cw >= x1 && cx <= x2 && cy + ch >= y1 && cy <= y2)
                screen.selection.multiSel.add(a.getId());
        }
    }

    // ═══════════════ 滚动条 ═══════════════

    boolean handleScrollbarClick(double mx, double my) {
        if (screen.cardRenderer.isOnVScrollbar(mx, my)) { scrollDragV = true; updateVScrollFromMouse(my); return true; }
        if (screen.cardRenderer.isOnHScrollbar(mx, my)) { scrollDragH = true; updateHScrollFromMouse(mx); return true; }
        return false;
    }

    void updateHScrollFromMouse(double mx) {
        var cr = screen.cardRenderer;
        if (!cr.sbHActive) return;
        double ratio = Math.clamp((mx - cr.sbHX) / (double) cr.sbHLen, 0, 1);
        screen.canvas.scrollX = -(cr.sbContentL + ratio * (cr.sbContentR - cr.sbContentL)) * screen.canvas.zoom;
    }

    void updateVScrollFromMouse(double my) {
        var cr = screen.cardRenderer;
        if (!cr.sbVActive) return;
        double ratio = Math.clamp((my - cr.sbVY) / (double) cr.sbVLen, 0, 1);
        screen.canvas.scrollY = TAB_H - (cr.sbContentT + ratio * (cr.sbContentB - cr.sbContentT)) * screen.canvas.zoom;
    }

    public void resetScrollDrag() { scrollDragH = false; scrollDragV = false; }
}