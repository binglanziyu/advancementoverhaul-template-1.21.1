package com.example.advancementoverhaul.client.gui.render;

import com.example.advancementoverhaul.client.gui.AdvancementScreen;
import com.example.advancementoverhaul.client.gui.GuiUtils;
import com.example.advancementoverhaul.client.gui.ImageElement;
import com.example.advancementoverhaul.client.gui.state.CanvasState;
import com.example.advancementoverhaul.data.ClientDataStore;
import com.example.advancementoverhaul.data.DataStore;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.example.advancementoverhaul.client.gui.Theme.*;

public class CardRenderer {

    private final AdvancementScreen screen;

    public CardRenderer(AdvancementScreen screen) { this.screen = screen; }

    // ═══════════════ PERF-4: 图标缓存 ═══════════════

    private ItemStack cachedDefaultIcon;
    private final Map<String, ItemStack> iconCache = new HashMap<>();

    private ItemStack getCachedDefaultIcon() {
        if (cachedDefaultIcon == null) {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse("minecraft:nether_star"));
            cachedDefaultIcon = item != null ? new ItemStack(item) : ItemStack.EMPTY;
        }
        return cachedDefaultIcon;
    }

    private ItemStack resolveIcon(String iconStr) {
        if (iconStr == null || iconStr.isEmpty()) return getCachedDefaultIcon();
        if (iconStr.startsWith("entity:")) return ItemStack.EMPTY;
        return iconCache.computeIfAbsent(iconStr, key -> {
            ResourceLocation rl = ResourceLocation.tryParse(key);
            if (rl == null) return ItemStack.EMPTY;
            var item = BuiltInRegistries.ITEM.get(rl);
            return item != null ? new ItemStack(item) : ItemStack.EMPTY;
        });
    }

    public void clearIconCache() { iconCache.clear(); }

    // ═══════════════ PERF-7: 边界框缓存 ═══════════════

    private boolean boundsDirty = true;
    private boolean cachedBoundsFound = false;
    private double cachedMinW, cachedMaxW, cachedMinH, cachedMaxH;

    public void markBoundsDirty() { boundsDirty = true; }

    private void ensureBounds() {
        if (!boundsDirty) return;
        boundsDirty = false;
        cachedBoundsFound = false;
        cachedMinW = Double.MAX_VALUE; cachedMaxW = Double.MIN_VALUE;
        cachedMinH = Double.MAX_VALUE; cachedMaxH = Double.MIN_VALUE;

        for (var a : screen.frameFiltered) {
            cachedMinW = Math.min(cachedMinW, a.getX()); cachedMaxW = Math.max(cachedMaxW, a.getX());
            cachedMinH = Math.min(cachedMinH, a.getY()); cachedMaxH = Math.max(cachedMaxH, a.getY());
            cachedBoundsFound = true;
        }
        for (var va : screen.vanillaAdvs) {
            if (!screen.shouldShowVanilla(va.id())) continue;
            int[] p = screen.vanillaPos.get(va.id());
            if (p == null) continue;
            cachedMinW = Math.min(cachedMinW, p[0]); cachedMaxW = Math.max(cachedMaxW, p[0]);
            cachedMinH = Math.min(cachedMinH, p[1]); cachedMaxH = Math.max(cachedMaxH, p[1]);
            cachedBoundsFound = true;
        }
        for (ImageElement img : screen.imageElements) {
            cachedMinW = Math.min(cachedMinW, img.getX());
            cachedMaxW = Math.max(cachedMaxW, img.getX() + img.getRenderWidth());
            cachedMinH = Math.min(cachedMinH, img.getY());
            cachedMaxH = Math.max(cachedMaxH, img.getY() + img.getRenderHeight());
            cachedBoundsFound = true;
        }
    }

    // ═══════════════ 滚动条缓存 ═══════════════

    public boolean sbHActive = false, sbVActive = false;
    public double sbContentL, sbContentR, sbContentT, sbContentB;
    public int sbHX, sbHY, sbHLen, sbHH;
    public int sbHTX, sbHTW;
    public int sbVX, sbVY, sbVLen, sbVW;
    public int sbVTY, sbVTH;

    public boolean isOnHScrollbar(double mx, double my) {
        if (!sbHActive) return false;
        return mx >= sbHX && mx <= sbHX + sbHLen && my >= sbHY - 6 && my <= sbHY + sbHH + 6;
    }

    public boolean isOnVScrollbar(double mx, double my) {
        if (!sbVActive) return false;
        return mx >= sbVX - 6 && mx <= sbVX + sbVW + 6 && my >= sbVY && my <= sbVY + sbVLen;
    }

    // ═══════════════ 网格 ═══════════════

    public void renderGrid(GuiGraphics g) {
        var cv = screen.canvas;
        int gs = Math.max(4, (int) (24 * cv.zoom));
        int ox = (int) (cv.scrollX % gs); if (ox < 0) ox += gs;
        int oy = (int) (cv.scrollY % gs); if (oy < 0) oy += gs;
        for (int wy = TAB_H - oy; wy < screen.height - BOTTOM_H; wy += gs)
            if (wy >= TAB_H) g.fill(0, wy, screen.width, wy + 1, GRID);
        for (int wx = -ox; wx < screen.width; wx += gs)
            g.fill(wx, TAB_H, wx + 1, screen.height - BOTTOM_H, GRID);
    }

    // ═══════════════ 连接线（电路板风格：总线合并 + 焊点） ═══════════════

    private static final int SOLDER_SZ = 4;
    private static final int SOLDER_HI = SOLDER_SZ / 2;
    private static final int MAX_CONN = 512;

    private static final int COL_SOLDER       = 0xFF505066;
    private static final int COL_SOLDER_HI    = 0xBBFFFFFF;
    private static final int COL_TRACE        = 0xFF707088;
    private static final int COL_END_DONE     = 0xFF7EC8A0;
    private static final int COL_END_PENDING  = 0xFF8B8BA0;

    // 并行数组（零分配）
    private final int[]     connPX   = new int[MAX_CONN];
    private final int[]     connPY   = new int[MAX_CONN];
    private final int[]     connCX   = new int[MAX_CONN];
    private final int[]     connCY   = new int[MAX_CONN];
    private final boolean[] connDone = new boolean[MAX_CONN];
    private final boolean[] connDrawn = new boolean[MAX_CONN];
    private final int[]     grpIdx   = new int[MAX_CONN];
    private int connCount = 0;

    public void tickFrameTime() {
        // no-op, kept for AdvancementScreen compatibility
    }

    public void renderConnections(GuiGraphics g) {
        ClientDataStore cs = ClientDataStore.getInstance();
        connCount = 0;

        // ── 收集所有连线 ──
        for (var a : screen.frameFiltered) {
            if (!shouldRenderCard(a, cs)) continue;
            var prereqs = a.getPrerequisites();
            if (prereqs == null) continue;
            for (String pid : prereqs) {
                if (pid == null || pid.isEmpty()) continue;
                var p = cs.getAdvancement(pid);
                if (p != null) {
                    enqueueConn(p.getX(), p.getY(), a.getX(), a.getY(), cs.isCompleted(a.getId()));
                    continue;
                }
                int[] vp = screen.vanillaPos.get(pid);
                if (vp != null && screen.shouldShowVanilla(pid))
                    enqueueConn(vp[0], vp[1], a.getX(), a.getY(), cs.isCompleted(a.getId()));
            }
        }

        Map<String, String> parentMap = cs.getVanillaParentMap();
        if (parentMap != null && !parentMap.isEmpty()) {
            for (var entry : parentMap.entrySet()) {
                String childId = entry.getKey();
                String parentId = entry.getValue();
                int[] cp = screen.vanillaPos.get(childId);
                int[] pp = screen.vanillaPos.get(parentId);
                if (cp == null || pp == null) continue;
                if (!screen.shouldShowVanilla(childId) || !screen.shouldShowVanilla(parentId)) continue;
                enqueueConn(pp[0], pp[1], cp[0], cp[1], cs.isCompleted(childId));
            }
        }

        for (var va : screen.vanillaAdvs) {
            if (!screen.shouldShowVanilla(va.id())) continue;
            int[] childPos = screen.vanillaPos.get(va.id());
            if (childPos == null) continue;
            DataStore.VanillaAdvMeta meta = cs.getVanillaMeta(va.id());
            if (meta == null) continue;
            var metaPrereqs = meta.getPrerequisites();
            if (metaPrereqs == null) continue;
            for (String pid : metaPrereqs) {
                if (pid == null || pid.isEmpty()) continue;
                boolean done = cs.isCompleted(va.id());
                var customP = cs.getAdvancement(pid);
                if (customP != null) {
                    enqueueConn(customP.getX(), customP.getY(), childPos[0], childPos[1], done);
                    continue;
                }
                int[] parentPos = screen.vanillaPos.get(pid);
                if (parentPos != null && screen.shouldShowVanilla(pid))
                    enqueueConn(parentPos[0], parentPos[1], childPos[0], childPos[1], done);
            }
        }

        if (connCount == 0) return;

        // ── 按父节点分组绘制 ──
        Arrays.fill(connDrawn, 0, connCount, false);
        var cv = screen.canvas;

        for (int i = 0; i < connCount; i++) {
            if (connDrawn[i]) continue;
            int px = connPX[i], py = connPY[i];

            int gs = 0;
            for (int j = i; j < connCount; j++) {
                if (!connDrawn[j] && connPX[j] == px && connPY[j] == py) {
                    grpIdx[gs++] = j;
                    connDrawn[j] = true;
                }
            }

            if (gs == 1) {
                drawSingleTrace(g, cv, grpIdx[0]);
            } else {
                drawBusTrace(g, cv, px, py, gs);
            }
        }
    }

    private void enqueueConn(int px, int py, int cx, int cy, boolean done) {
        if (connCount >= MAX_CONN) return;
        connPX[connCount] = px;
        connPY[connCount] = py;
        connCX[connCount] = cx;
        connCY[connCount] = cy;
        connDone[connCount] = done;
        connCount++;
    }

    // ═══════════════ 单条L形走线 ═══════════════

    private void drawSingleTrace(GuiGraphics g, CanvasState cv, int idx) {
        int x1 = cv.toScreenX(connPX[idx]) + cv.screenW(CARD_W) / 2;
        int y1 = cv.toScreenY(connPY[idx]) + cv.screenH(CARD_H);
        int x2 = cv.toScreenX(connCX[idx]) + cv.screenW(CARD_W) / 2;
        int y2 = cv.toScreenY(connCY[idx]);

        if (!traceVisible(x1, y1, x2, y2)) return;

        boolean done = connDone[idx];
        int endCol = done ? COL_END_DONE : COL_END_PENDING;
        int mY = y1 + (y2 - y1) / 3;

        // 走线
        g.fill(x1 - 1, Math.min(y1, mY), x1 + 2, Math.max(y1, mY) + 1, COL_TRACE);
        g.fill(Math.min(x1, x2) - 1, mY - 1, Math.max(x1, x2) + 2, mY + 2, COL_TRACE);
        g.fill(x2 - 1, Math.min(mY, y2), x2 + 2, Math.max(mY, y2) + 1, COL_TRACE);

        // 焊点
        drawSolder(g, x1, mY);
        drawSolder(g, x2, mY);

        // 起点：方块（输出端口），在走线上方
        g.fill(x1 - 2, y1 - 4, x1 + 2, y1, endCol);

        // 终点：圆圈（目标点），在走线上方
        GuiUtils.fillCircle(g, x2, y2 - 3, 3, endCol);
    }

    // ═══════════════ 总线走线（同一父节点 → 多子节点） ═══════════════

    private void drawBusTrace(GuiGraphics g, CanvasState cv, int px, int py, int gs) {
        int parentX = cv.toScreenX(px) + cv.screenW(CARD_W) / 2;
        int parentY = cv.toScreenY(py) + cv.screenH(CARD_H);

        int[] cxs = new int[gs];
        int[] cys = new int[gs];
        boolean[] dones = new boolean[gs];
        int minX = parentX, maxX = parentX;
        int closestChildY = Integer.MAX_VALUE;

        for (int i = 0; i < gs; i++) {
            int idx = grpIdx[i];
            cxs[i]  = cv.toScreenX(connCX[idx]) + cv.screenW(CARD_W) / 2;
            cys[i]  = cv.toScreenY(connCY[idx]);
            dones[i] = connDone[idx];
            minX = Math.min(minX, cxs[i]);
            maxX = Math.max(maxX, cxs[i]);
            closestChildY = Math.min(closestChildY, cys[i]);
        }

        if (maxX + 10 < 0 || minX - 10 > screen.width) return;
        if (Math.max(parentY, closestChildY) + 10 < TAB_H) return;
        if (parentY - 10 > screen.height - BOTTOM_H) return;

        int trunkY = parentY + (closestChildY - parentY) / 3;

        // 父 → 总线（垂直）
        g.fill(parentX - 1, Math.min(parentY, trunkY), parentX + 2, Math.max(parentY, trunkY) + 1, COL_TRACE);

        // 总线（水平）
        g.fill(minX - 1, trunkY - 1, maxX + 2, trunkY + 2, COL_TRACE);
        drawSolder(g, parentX, trunkY);

        // 起点：方块（输出端口），在走线上方
        g.fill(parentX - 2, parentY - 4, parentX + 2, parentY, COL_END_DONE);

        // 各分支
        for (int i = 0; i < gs; i++) {
            g.fill(cxs[i] - 1, Math.min(trunkY, cys[i]), cxs[i] + 2, Math.max(trunkY, cys[i]) + 1, COL_TRACE);
            drawSolder(g, cxs[i], trunkY);
            // 终点：圆圈（目标点），在走线上方
            GuiUtils.fillCircle(g, cxs[i], cys[i] - 3, 3, dones[i] ? COL_END_DONE : COL_END_PENDING);
        }
    }

    // ═══════════════ PCB元素 ═══════════════

    private boolean traceVisible(int x1, int y1, int x2, int y2) {
        if (Math.max(x1, x2) + 10 < 0 || Math.min(x1, x2) - 10 > screen.width) return false;
        if (Math.max(y1, y2) + 10 < TAB_H || Math.min(y1, y2) - 10 > screen.height - BOTTOM_H) return false;
        return true;
    }

    private void drawSolder(GuiGraphics g, int x, int y) {
        g.fill(x - SOLDER_HI, y - SOLDER_HI, x + SOLDER_HI, y + SOLDER_HI, COL_SOLDER);
        g.fill(x - 1, y - 1, x, y, COL_SOLDER_HI);
    }

    // ═══════════════ 卡片渲染 ═══════════════

    private boolean shouldRenderCard(DataStore.CustomAdvancement a, ClientDataStore cs) {
        return !a.isHidden()
                || "hidden".equals(screen.curTab)
                || screen.editMode
                || cs.isCompleted(a.getId());
    }

    public void renderCards(GuiGraphics g, int mx, int my) {
        ClientDataStore cs = ClientDataStore.getInstance();
        var cv = screen.canvas;

        for (var a : screen.frameFiltered) {
            if (!shouldRenderCard(a, cs)) continue;
            renderIconCard(g, mx, my, cv, a.getId(), a.getName(), a.getIcon(),
                    a.getX(), a.getY(), cs.isCompleted(a.getId()), screen.editMode, true);
        }

        for (var va : screen.vanillaAdvs) {
            if (!screen.shouldShowVanilla(va.id())) continue;
            int[] p = screen.vanillaPos.get(va.id());
            if (p == null) continue;
            boolean enabled = cs.isVanillaEnabled(va.id());
            renderIconCard(g, mx, my, cv, va.id(), va.getLocalizedName(), va.icon(),
                    p[0], p[1], cs.isCompleted(va.id()), false, enabled);
        }

        for (ImageElement img : screen.imageElements) {
            renderImageElement(g, cv, img);
        }
    }

    private void renderImageElement(GuiGraphics g, CanvasState cv, ImageElement img) {
        if (img.getTextureId() == null) return;
        int sx = cv.toScreenX(img.getX());
        int sy = cv.toScreenY(img.getY());
        int sw = (int) (img.getRenderWidth() * cv.zoom);
        int sh = (int) (img.getRenderHeight() * cv.zoom);

        if (sx + sw < 0 || sx > screen.width || sy + sh < TAB_H || sy > screen.height - BOTTOM_H) return;

        g.blit(img.getTextureId(), sx, sy, sw, sh, 0, 0,
                img.getOriginalWidth(), img.getOriginalHeight(),
                img.getOriginalWidth(), img.getOriginalHeight());

        if (img.isLocked()) {
            g.drawString(screen.getFont(), "\uD83D\uDD12", sx + 2, sy + 2, 0xFFFFFF00, false);
        }

        if (img.getId().equals(screen.selectedImageId)) {
            g.renderOutline(sx, sy, sw, sh, BLUE);
        }
    }

    private void renderIconCard(GuiGraphics g, int mx, int my,
                                CanvasState cv,
                                String id, String name, String iconStr,
                                int wx, int wy, boolean done, boolean showId, boolean enabled) {
        int cx = cv.toScreenX(wx) + cv.screenW(CARD_W) / 2;
        int cy = cv.toScreenY(wy) + cv.screenH(CARD_H) / 2;
        int r = (int) (ICON_RADIUS * cv.zoom);
        if (cx + r < 0 || cx - r > screen.width || cy + r < TAB_H || cy - r > screen.height - BOTTOM_H) return;

        boolean hov = GuiUtils.inCircle(mx, my, cx, cy, r) && !screen.hasOv();
        boolean isSel = id.equals(screen.selection.selectedId) || screen.selection.multiSel.contains(id);

        int bgCol = isSel ? CARD_SEL : (hov ? CARD_HOV : (done ? CARD_DONE : (enabled ? CARD : CARD_VANILLA)));
        GuiUtils.fillCircle(g, cx, cy, r, bgCol);

        int borderCol = isSel ? BLUE : (done ? ACCENT : (hov ? 0xFF6A6A90 : DIVIDER));
        GuiUtils.drawCircleOutline(g, cx, cy, r, borderCol);

        ItemStack iconStack = resolveIcon(iconStr);
        if (!iconStack.isEmpty()) {
            int iconSize = (int) (16 * cv.zoom);
            g.pose().pushPose();
            g.pose().translate(cx - iconSize / 2.0, cy - iconSize / 2.0, 0);
            g.pose().scale((float) cv.zoom, (float) cv.zoom, 1f);
            g.renderItem(iconStack, 0, 0);
            g.pose().popPose();
        } else {
            String ch = name.isEmpty() ? "?" : name.substring(0, 1);
            g.drawString(screen.getFont(), ch, cx - screen.getFont().width(ch) / 2, cy - 4, TEXT_BR, false);
        }

        if (showId) {
            String truncId = GuiUtils.truncate(screen.getFont(), id, cv.screenW(CARD_W));
            g.drawString(screen.getFont(), truncId, cx - screen.getFont().width(truncId) / 2, cy + r + 2, TEXT_DIM, false);
        }

        if (done && !showId) {
            g.drawString(screen.getFont(), "\u2713", cx + r - 8, cy - r, ACCENT, false);
        }

        if (!enabled && !done) {
            g.drawString(screen.getFont(), "\u2717", cx - r, cy - r, PINK, false);
        }
    }

    // ═══════════════ 框选 ═══════════════

    public void renderBoxSel(GuiGraphics g) {
        var d = screen.drag;
        int x1 = (int) Math.min(d.bsx, d.bex), y1 = (int) Math.min(d.bsy, d.bey);
        int x2 = (int) Math.max(d.bsx, d.bex), y2 = (int) Math.max(d.bsy, d.bey);
        g.fill(x1, y1, x2, y2, 0x2542A5F5);
        g.renderOutline(x1, y1, x2 - x1, y2 - y1, BLUE);
    }

    // ═══════════════ 滚动条 ═══════════════

    public void renderScrollIndicators(GuiGraphics g) {
        var cv = screen.canvas;
        int canvasBottom = screen.height - BOTTOM_H;
        int canvasH = canvasBottom - TAB_H;
        if (canvasH <= 0) { sbHActive = false; sbVActive = false; return; }

        ensureBounds();

        if (!cachedBoundsFound) { sbHActive = false; sbVActive = false; return; }

        double contentL = cachedMinW - 20, contentR = cachedMaxW + CARD_W + 20;
        double contentT = cachedMinH - 20, contentB = cachedMaxH + CARD_H + 20;
        double contentW = contentR - contentL, contentH = contentB - contentT;
        if (contentW <= 0 || contentH <= 0) { sbHActive = false; sbVActive = false; return; }

        double vpL = -cv.scrollX / cv.zoom;
        double vpR = (screen.width - cv.scrollX) / cv.zoom;
        double vpT = (TAB_H - cv.scrollY) / cv.zoom;
        double vpB = (canvasBottom - cv.scrollY) / cv.zoom;

        if (vpL <= contentL && vpR >= contentR && vpT <= contentT && vpB >= contentB) {
            sbHActive = false; sbVActive = false; return;
        }

        sbContentL = contentL; sbContentR = contentR;
        sbContentT = contentT; sbContentB = contentB;

        int barH = 4, barW = 4;
        int canvasR = screen.width;

        if (contentW > (vpR - vpL)) {
            int barY = canvasBottom - barH - 2, barX = 2, barLen = canvasR - 4;
            g.fill(barX, barY, barX + barLen, barY + barH, 0xFF222238);
            double hRatio = Math.clamp((vpL - contentL) / contentW, 0, 1);
            double hSize = Math.clamp((vpR - vpL) / contentW, 0.05, 1);
            int thumbX = barX + (int) (hRatio * barLen);
            int thumbW = Math.max(20, (int) (hSize * barLen));
            thumbX = Math.min(thumbX, barX + barLen - thumbW);
            g.fill(thumbX, barY, thumbX + thumbW, barY + barH, 0xFF6666BB);

            sbHActive = true;
            sbHX = barX; sbHY = barY; sbHLen = barLen; sbHH = barH;
            sbHTX = thumbX; sbHTW = thumbW;
        } else { sbHActive = false; }

        if (contentH > (vpB - vpT)) {
            int barX = canvasR - barW - 2, barY = TAB_H + 2, barLen = canvasH - 4;
            g.fill(barX, barY, barX + barW, barY + barLen, 0xFF222238);
            double vRatio = Math.clamp((vpT - contentT) / contentH, 0, 1);
            double vSize = Math.clamp((vpB - vpT) / contentH, 0.05, 1);
            int thumbY = barY + (int) (vRatio * barLen);
            int thumbH = Math.max(20, (int) (vSize * barLen));
            thumbY = Math.min(thumbY, barY + barLen - thumbH);
            g.fill(barX, thumbY, barX + barW, thumbY + thumbH, 0xFF6666BB);

            sbVActive = true;
            sbVX = barX; sbVY = barY; sbVLen = barLen; sbVW = barW;
            sbVTY = thumbY; sbVTH = thumbH;
        } else { sbVActive = false; }
    }

    // ═══════════════ 进度动画 ═══════════════

    public float getAnimatedProgress(String id, float target) {
        var anim = screen.anim;
        float current = anim.progress.getOrDefault(id, target);
        float next = current + (target - current) * Math.min(1f, (float) (Util.getMillis() - anim.lastTime) / 1000f * 6f);
        if (Math.abs(next - target) < 0.005f) next = target;
        anim.progress.put(id, next);
        return next;
    }
}