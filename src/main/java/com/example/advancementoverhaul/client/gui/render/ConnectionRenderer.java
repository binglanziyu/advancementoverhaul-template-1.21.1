package com.example.advancementoverhaul.client.gui.render;

import com.example.advancementoverhaul.client.gui.AdvancementScreen;
import com.example.advancementoverhaul.client.gui.GuiUtils;
import com.example.advancementoverhaul.data.ClientDataStore;
import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.model.CustomAdvancement;
import com.example.advancementoverhaul.data.model.VanillaAdvMeta;
import net.minecraft.client.gui.GuiGraphics;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;

import static com.example.advancementoverhaul.client.gui.Theme.*;

/**
 * 成就卡片依赖连线渲染器。
 * <p>
 * 从 {@link CardRenderer} 中提取出来，专门负责画布上成就之间的
 * 直角树状依赖连线绘制，包括普通连线和高亮选中连线。
 */
public class ConnectionRenderer {

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
        var cv = screen.canvas;
        for (var a : screen.frameFiltered) {
            if (!shouldRenderCard.test(a, cs)) continue;
            var prereqs = a.getPrerequisites();
            if (prereqs == null) continue;
            int cx = cv.toScreenX(a.getX()) + cv.screenW(CARD_W) / 2;
            int cy = cv.toScreenY(a.getY()) + cv.screenH(CARD_H) / 2;
            boolean done = cs.isCompleted(a.getId());
            for (String pid : prereqs) {
                if (pid == null || pid.isEmpty()) continue;
                int[] pos = resolvePos(cs, pid);
                if (pos == null) continue;
                int px = cv.toScreenX(pos[0]) + cv.screenW(CARD_W) / 2;
                int py = cv.toScreenY(pos[1]) + cv.screenH(CARD_H) / 2;

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
        var cv = screen.canvas;
        Map<String, String> parentMap = cs.getVanillaParentMap();
        if (parentMap == null || parentMap.isEmpty()) return;
        for (var e : parentMap.entrySet()) {
            String cid = e.getKey(), pid = e.getValue();
            if (highlight && !selId.equals(cid) && !selId.equals(pid)) continue;
            int[] cp = screen.vanillaPos.get(cid), pp = screen.vanillaPos.get(pid);
            if (cp == null || pp == null) continue;
            if (!screen.shouldShowVanilla(cid) || !screen.shouldShowVanilla(pid)) continue;
            int px = cv.toScreenX(pp[0]) + cv.screenW(CARD_W) / 2;
            int py = cv.toScreenY(pp[1]) + cv.screenH(CARD_H) / 2;
            int cx = cv.toScreenX(cp[0]) + cv.screenW(CARD_W) / 2;
            int cy = cv.toScreenY(cp[1]) + cv.screenH(CARD_H) / 2;

            if (highlight) {
                boolean isHL = selId.equals(cid) || selId.equals(pid);
                if (!isHL) continue;
            }

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
        var cv = screen.canvas;
        for (var va : screen.vanillaAdvs) {
            if (!screen.shouldShowVanilla(va.id())) continue;
            int[] cp = screen.vanillaPos.get(va.id());
            if (cp == null) continue;
            VanillaAdvMeta meta = cs.getVanillaMeta(va.id());
            if (meta == null) continue;
            var metaPrqs = meta.getPrerequisites();
            if (metaPrqs == null) continue;

            if (highlight && !selId.equals(va.id()) && !metaPrqs.contains(selId)) continue;

            int cx = cv.toScreenX(cp[0]) + cv.screenW(CARD_W) / 2;
            int cy = cv.toScreenY(cp[1]) + cv.screenH(CARD_H) / 2;
            boolean done = cs.isCompleted(va.id());
            for (String pid : metaPrqs) {
                if (pid == null || pid.isEmpty()) continue;
                int[] pos = resolvePos(cs, pid);
                if (pos == null) continue;
                int px = cv.toScreenX(pos[0]) + cv.screenW(CARD_W) / 2;
                int py = cv.toScreenY(pos[1]) + cv.screenH(CARD_H) / 2;

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

    private int[] resolvePos(ClientDataStore cs, String id) {
        var cust = cs.getAdvancement(id);
        if (cust != null) return new int[]{cust.getX(), cust.getY()};
        int[] vp = screen.vanillaPos.get(id);
        if (vp != null && screen.shouldShowVanilla(id)) return vp;
        return null;
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

        int dotR = Math.max(1, (int) (thickness * JUNCTION_DOT_RATIO / 2f));
        if (px != cx) GuiUtils.fillCircle(g, px, midY, dotR, color);
        if (py != midY && px != cx) GuiUtils.fillCircle(g, cx, midY, dotR, color);
    }
}
