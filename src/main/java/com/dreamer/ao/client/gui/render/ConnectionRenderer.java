package com.dreamer.ao.client.gui.render;

import com.dreamer.ao.client.gui.AdvancementScreen;
import com.dreamer.ao.client.gui.GuiUtils;
import com.dreamer.ao.data.ClientDataStore;
import com.dreamer.ao.data.DataStore;
import com.dreamer.ao.data.model.CustomAdvancement;
import com.dreamer.ao.data.model.VanillaAdvMeta;
import net.minecraft.client.gui.GuiGraphics;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;

import static com.dreamer.ao.client.gui.Theme.*;

/**
 * 成就卡片依赖连线渲染器。
 * <p>
 * 从 {@link CardRenderer} 中提取出来，专门负责画布上成就之间的
 * 直角树状依赖连线绘制，包括普通连线和高亮选中连线。
 */
public class ConnectionRenderer {

    /** 低于此缩放时省略连线拐点圆点（半径过小会整数偏移、偏离中点）。 */
    private static final double ZOOM_DOT_MIN = 0.6;

    private final AdvancementScreen screen;

    private String cachedSelId = null;
    private final Set<String> selPrereqs = new HashSet<>();
    private final Set<String> selChildren = new HashSet<>();

    public ConnectionRenderer(AdvancementScreen screen) {
        this.screen = screen;
    }

    // ═══════════════ 层级关系缓存 ═══════════════

    public void ensureLayerSets() {
        String selId = screen.selection.selectedId;
        if (Objects.equals(selId, cachedSelId)) return;
        cachedSelId = selId;
        selPrereqs.clear();
        selChildren.clear();
        if (selId == null) return;

        ClientDataStore cs = ClientDataStore.getInstance();
        var selAdv = cs.getAdvancement(selId);
        if (selAdv != null && selAdv.getPrerequisites() != null) {
            for (String pid : selAdv.getPrerequisites()) {
                if (pid != null && !pid.isEmpty()) selPrereqs.add(pid);
            }
        }
        Map<String, String> parentMap = cs.getVanillaParentMap();
        if (parentMap != null) {
            String parent = parentMap.get(selId);
            if (parent != null) selPrereqs.add(parent);
        }
        VanillaAdvMeta selMeta = cs.getVanillaMeta(selId);
        if (selMeta != null && selMeta.getPrerequisites() != null) {
            for (String pid : selMeta.getPrerequisites()) {
                if (pid != null && !pid.isEmpty()) selPrereqs.add(pid);
            }
        }

        for (var a : cs.getAdvancements().values()) {
            var prereqs = a.getPrerequisites();
            if (prereqs != null && prereqs.contains(selId))
                selChildren.add(a.getId());
        }
        if (parentMap != null) {
            for (var e : parentMap.entrySet()) {
                if (selId.equals(e.getValue())) selChildren.add(e.getKey());
            }
        }
        for (var entry : cs.getVanillaMeta().entrySet()) {
            var metaPrqs = entry.getValue().getPrerequisites();
            if (metaPrqs != null && metaPrqs.contains(selId))
                selChildren.add(entry.getKey());
        }
    }

    public boolean isPrereq(String id) { return selPrereqs.contains(id); }
    public boolean isChild(String id) { return selChildren.contains(id); }

    // ═══════════════ 连接线渲染 ═══════════════

    public void renderConnections(GuiGraphics g,
                                   BiPredicate<CustomAdvancement, ClientDataStore> shouldRenderCard) {
        if (DataStore.TAB_VANILLA.equals(screen.curTab)) return;

        ClientDataStore cs = ClientDataStore.getInstance();
        var cv = screen.canvas;
        String selId = screen.selection.selectedId;
        int t = Math.max(1, (int) (LINE_THICKNESS * cv.zoom));
        int hw = t / 2;

        // Pass 1: Normal connections
        drawCustomPrereqLines(g, cs, selId, t, hw, shouldRenderCard, false);
        drawVanillaParentLines(g, cs, selId, t, hw, false);
        drawVanillaMetaLines(g, cs, selId, t, hw, false);

        // Pass 2: Highlighted connections
        if (selId == null) return;
        drawCustomPrereqLines(g, cs, selId, t, hw, shouldRenderCard, true);
        drawVanillaParentLines(g, cs, selId, t, hw, true);
        drawVanillaMetaLines(g, cs, selId, t, hw, true);
    }

    // ── 自定义进度 → 前置 ──

    private void drawCustomPrereqLines(GuiGraphics g, ClientDataStore cs,
            String selId, int t, int hw,
            BiPredicate<CustomAdvancement, ClientDataStore> shouldRenderCard, boolean highlight) {
        for (var a : screen.frameFiltered) {
            if (!shouldRenderCard.test(a, cs)) continue;
            var prereqs = a.getPrerequisites();
            if (prereqs == null) continue;
            long centerA = cardCenter(cs, a.getId());
            if (centerA < 0) continue;
            int cx = (int) (centerA >>> 32);
            int cy = (int) (centerA & 0xFFFFFFFFL);
            boolean done = cs.isCompleted(a.getId());
            for (String pid : prereqs) {
                if (pid == null || pid.isEmpty()) continue;
                long centerP = cardCenter(cs, pid);
                if (centerP < 0) continue;
                int px = (int) (centerP >>> 32);
                int py = (int) (centerP & 0xFFFFFFFFL);

                boolean isHL = selId != null && (selId.equals(a.getId()) || selId.equals(pid));
                if (highlight != isHL) continue;

                int color;
                if (highlight) {
                    color = selId.equals(a.getId()) ? (done ? LINE_DONE : LINE_REQUIRES)
                          : (done ? LINE_DONE : LINE_REQUIRED_FOR);
                } else {
                    color = done ? LINE_DONE : LINE;
                }
                drawTreeConnection(g, px, py, cx, cy, color, t, hw);
            }
        }
    }

    // ── 原版 parent map ──

    private void drawVanillaParentLines(GuiGraphics g, ClientDataStore cs,
            String selId, int t, int hw, boolean highlight) {
        Map<String, String> parentMap = cs.getVanillaParentMap();
        if (parentMap == null || parentMap.isEmpty()) return;
        for (var e : parentMap.entrySet()) {
            String cid = e.getKey(), pid = e.getValue();
            if (highlight && !selId.equals(cid) && !selId.equals(pid)) continue;
            long centerC = cardCenter(cs, cid);
            if (centerC < 0) continue;
            long centerP = cardCenter(cs, pid);
            if (centerP < 0) continue;
            int px = (int) (centerP >>> 32);
            int py = (int) (centerP & 0xFFFFFFFFL);
            int cx = (int) (centerC >>> 32);
            int cy = (int) (centerC & 0xFFFFFFFFL);

            boolean done = cs.isCompleted(cid);
            int color;
            if (highlight) {
                color = selId.equals(cid) ? (done ? LINE_DONE : LINE_REQUIRES)
                      : (done ? LINE_DONE : LINE_REQUIRED_FOR);
            } else {
                color = done ? LINE_DONE : LINE;
            }
            drawTreeConnection(g, px, py, cx, cy, color, t, hw);
        }
    }

    // ── 原版进度 → 自定义前置 ──

    private void drawVanillaMetaLines(GuiGraphics g, ClientDataStore cs,
            String selId, int t, int hw, boolean highlight) {
        for (var va : screen.vanillaAdvs) {
            VanillaAdvMeta meta = cs.getVanillaMeta(va.id());
            if (meta == null) continue;
            var metaPrqs = meta.getPrerequisites();
            if (metaPrqs == null) continue;

            if (highlight && !selId.equals(va.id()) && !metaPrqs.contains(selId)) continue;
            if (!screen.shouldShowVanilla(va.id())) continue;
            long centerC = cardCenter(cs, va.id());
            if (centerC < 0) continue;
            int cx = (int) (centerC >>> 32);
            int cy = (int) (centerC & 0xFFFFFFFFL);
            boolean done = cs.isCompleted(va.id());
            for (String pid : metaPrqs) {
                if (pid == null || pid.isEmpty()) continue;
                long centerP = cardCenter(cs, pid);
                if (centerP < 0) continue;
                int px = (int) (centerP >>> 32);
                int py = (int) (centerP & 0xFFFFFFFFL);

                int color;
                if (highlight) {
                    color = selId.equals(va.id()) ? (done ? LINE_DONE : LINE_REQUIRES)
                          : (done ? LINE_DONE : LINE_REQUIRED_FOR);
                } else {
                    color = done ? LINE_DONE : LINE;
                }
                drawTreeConnection(g, px, py, cx, cy, color, t, hw);
            }
        }
    }

    // ═══════════════ Helpers ═══════════════

    /**
     * 解析卡片屏幕中心点，消除各 draw 方法中重复的
     * {@code cv.toScreenX(x) + cv.screenW(CARD_W)/2} 换算。
     * 返回打包的 long（高 32 位为屏幕 X，低 32 位为屏幕 Y）；
     * 找不到有效位置（自定义进度不存在或原版未显示）时返回 -1。
     */
    private long cardCenter(ClientDataStore cs, String id) {
        int wx, wy;
        var cust = cs.getAdvancement(id);
        if (cust != null) {
            wx = cust.getX();
            wy = cust.getY();
        } else {
            int[] vp = screen.vanillaPos.get(id);
            if (vp == null || !screen.shouldShowVanilla(id)) return -1L;
            wx = vp[0];
            wy = vp[1];
        }
        var cv = screen.canvas;
        int sx = cv.toScreenX(wx) + cv.screenW(CARD_W) / 2;
        int sy = cv.toScreenY(wy) + cv.screenH(CARD_H) / 2;
        return ((long) sx << 32) | (sy & 0xFFFFFFFFL);
    }

    private void drawTreeConnection(GuiGraphics g, int px, int py, int cx, int cy,
                                    int color, int thickness, int hw) {
        int minX = Math.min(px, cx), maxX = Math.max(px, cx);
        int minY = Math.min(py, cy), maxY = Math.max(py, cy);
        if (maxX < 0 || minX > screen.width || maxY < TAB_H || minY > screen.height - BOTTOM_H) return;

        int midY = (py + cy) / 2;

        if (py != midY) {
            int y1 = Math.min(py, midY), y2 = Math.max(py, midY);
            g.fill(px - hw, y1, px + hw + (thickness & 1), y2, color);
        }
        if (px != cx) {
            int x1 = Math.min(px, cx), x2 = Math.max(px, cx);
            g.fill(x1, midY - hw, x2 + (thickness & 1), midY + hw + (thickness & 1), color);
        }
        if (midY != cy) {
            int y1 = Math.min(midY, cy), y2 = Math.max(midY, cy);
            g.fill(cx - hw, y1, cx + hw + (thickness & 1), y2, color);
        }

        // 缩放较小时圆点半径仅 1px，整数定位会导致圆心偏离连线中点，
        // 此时直接省略圆点，仅保留已正确居中的连线本身。
        if (screen.canvas.zoom >= ZOOM_DOT_MIN) {
            int dotR = Math.max(2, (int) (thickness * JUNCTION_DOT_RATIO / 2f));
            if (px != cx) GuiUtils.fillCircle(g, px, midY, dotR, color);
            if (py != midY && px != cx) GuiUtils.fillCircle(g, cx, midY, dotR, color);
        }
    }
}
