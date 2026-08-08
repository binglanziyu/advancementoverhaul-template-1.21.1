package com.dreamer.ao.client.gui.render;

/**
 * 成就卡片渲染器：负责画布上所有成就卡片的视觉呈现。
 * <p>
 * 区分自定义进度和原版进度两种卡片样式：
 * <ul>
 *   <li><b>自定义进度</b> — 完整图标、条件进度条、前置条件指示线、完成闪白</li>
 *   <li><b>原版进度</b> — 简化图标、星形标记、元数据位置覆盖</li>
 * </ul>
 * 还负责绘制成就之间的依赖连线。
 */
import com.dreamer.ao.client.gui.AdvancementScreen;
import com.dreamer.ao.client.gui.GuiUtils;
import com.dreamer.ao.client.gui.ImageElement;
import com.dreamer.ao.client.gui.state.CanvasState;
import com.dreamer.ao.data.ClientDataStore;
import com.dreamer.ao.data.DataStore;
import com.dreamer.ao.data.model.CustomAdvancement;
import com.dreamer.ao.data.model.VanillaAdvMeta;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.dreamer.ao.client.gui.Theme.*;

public class CardRenderer {

    private final AdvancementScreen screen;

    /** 连线渲染器（提取自本类，专门处理依赖连线绘制） */
    private final ConnectionRenderer connectionRenderer;

    public CardRenderer(AdvancementScreen screen) {
        this.screen = screen;
        this.connectionRenderer = new ConnectionRenderer(screen);
        this.spatialIndex = new CardSpatialIndex(screen);
    }

    // ═══════════════ PERF-4: 图标缓存（委托 CardIconCache） ═══════════════

    private final CardIconCache iconCache = new CardIconCache();

    private ItemStack resolveIcon(String iconStr) {
        return iconCache.resolveIcon(iconStr);
    }

    public void clearIconCache() { iconCache.clear(); }

    // ═══════════════ PERF-7: 边界框缓存 ═══════════════

    private boolean boundsDirty = true;
    private boolean cachedBoundsFound = false;
    private double cachedMinW, cachedMaxW, cachedMinH, cachedMaxH;

    public void markBoundsDirty() { boundsDirty = true; spatialIndex.markDirty(); }

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

        // PERF-18: 边界更新后重建空间网格
        ensureGrid();
    }

    // ═══════════════ PERF-18: 空间网格索引（委托 CardSpatialIndex） ═══════════════

    /** 空间网格协作类实例 */
    private final CardSpatialIndex spatialIndex;

    /** 网格单元的世界坐标大小（约 20 个卡片宽度 = 480 世界单位） */
    private static final int GRID_CELL_SIZE = 480;

    /** 空间网格：Long(cellX<<32|cellY) → 该单元格内的卡片渲染条目列表（同步自索引） */
    private Map<Long, List<CardSpatialIndex.CardEntry>> spatialGrid = new HashMap<>();

    /** 重建空间网格索引（委托协作类，并同步引用供 renderCards 遍历） */
    private void ensureGrid() {
        spatialIndex.ensureGrid();
        spatialGrid = spatialIndex.grid();
    }

    /**
     * 查询世界坐标附近所有卡片 ID，供 CanvasManager 命中检测使用。
     * <p>
     * 使用空间网格索引（O(1) 单元格查找 + 少量条目遍历）替代线性扫描所有卡片，
     * 在千级卡片场景下将每次鼠标点击从 O(n) 降至 O(1)。
     * <p>
     * 内部自动维护网格脏标记，确保查询结果与当前帧渲染状态一致。
     *
     * @param worldX 世界坐标 X
     * @param worldY 世界坐标 Y
     * @return 覆盖该位置的所有单元格内的卡片 ID 集合（含自定义和原版）
     */
    public java.util.Set<String> queryCardIdsNear(int worldX, int worldY) {
        return spatialIndex.queryCardIdsNear(worldX, worldY);
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

    // ═══════════════ 连接线（委托给 ConnectionRenderer） ═══════════════

    public void tickFrameTime() { /* no-op */ }

    public void renderConnections(GuiGraphics g) {
        connectionRenderer.renderConnections(g, this::shouldRenderCard);
    }

    // ═══════════════ 卡片渲染 ═══════════════

    /** 委托给 screen 的统一可见性判断，去重 */
    private boolean shouldRenderCard(CustomAdvancement a, ClientDataStore cs) {
        return screen.isCardVisible(a);
    }

    public void renderCards(GuiGraphics g, int mx, int my) {
        var cv = screen.canvas;
        connectionRenderer.ensureLayerSets(); // P0: 计算选中卡片的直系层级关系
        ensureGrid();                         // PERF-18: 确保空间网格索引是最新的

        // PERF-18: 计算视口范围（世界坐标），仅迭代可见单元格内的卡片
        // 视口扩大一个网格单元作为容差，确保边缘卡片不被过早裁剪
        int vpLeft = cv.toWorldX(-GRID_CELL_SIZE);
        int vpRight = cv.toWorldX(screen.width + GRID_CELL_SIZE);
        int vpTop = cv.toWorldY(TAB_H - GRID_CELL_SIZE);
        int vpBottom = cv.toWorldY(screen.height - BOTTOM_H + GRID_CELL_SIZE);

        int cellMinX = Math.floorDiv(vpLeft, GRID_CELL_SIZE);
        int cellMaxX = Math.floorDiv(vpRight, GRID_CELL_SIZE);
        int cellMinY = Math.floorDiv(vpTop, GRID_CELL_SIZE);
        int cellMaxY = Math.floorDiv(vpBottom, GRID_CELL_SIZE);

        // 使用网格索引：仅遍历视口范围内的单元格
        if (!spatialGrid.isEmpty()) {
            for (int cx = cellMinX; cx <= cellMaxX; cx++) {
                for (int cy = cellMinY; cy <= cellMaxY; cy++) {
                    long key = ((long) cx << 32) | (cy & 0xFFFFFFFFL);
                    List<CardSpatialIndex.CardEntry> entries = spatialGrid.get(key);
                    if (entries == null) continue;
                    for (CardSpatialIndex.CardEntry entry : entries) {
                        renderIconCard(g, mx, my, cv, entry.id(), entry.name(), entry.icon(),
                                entry.wx(), entry.wy(), entry.done(), entry.showId(), entry.enabled(), entry.hidden());
                    }
                }
            }
        } else {
            // 回退：网格为空时（极少情况），遍历全部
            ClientDataStore cs = ClientDataStore.getInstance();
            for (var a : screen.frameFiltered) {
                if (!shouldRenderCard(a, cs)) continue;
                renderIconCard(g, mx, my, cv, a.getId(), a.getName(), a.getIcon(),
                        a.getX(), a.getY(), cs.isCompleted(a.getId()), false, true,
                        a.isHidden() && !cs.isCompleted(a.getId()));
            }
            for (var va : screen.vanillaAdvs) {
                if (!screen.shouldShowVanilla(va.id())) continue;
                int[] p = screen.vanillaPos.get(va.id());
                if (p == null) continue;
                boolean enabled = cs.isVanillaEnabled(va.id());
                renderIconCard(g, mx, my, cv, va.id(), va.getLocalizedName(), va.icon(),
                        p[0], p[1], cs.isCompleted(va.id()), false, enabled, false);
            }
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
                                int wx, int wy, boolean done, boolean showId, boolean enabled, boolean hidden) {
        int cardW = cv.screenW(CARD_W);
        int cardH = cv.screenH(CARD_H);
        int x = cv.toScreenX(wx);
        int y = cv.toScreenY(wy);
        int cx = x + cardW / 2;
        int cy = y + cardH / 2;
        if (x + cardW < 0 || x > screen.width || y + cardH < TAB_H || y > screen.height - BOTTOM_H) return;

        boolean hov = GuiUtils.inRect(mx, my, x, y, cardW, cardH) && !screen.hasOv();
        boolean isSel = id.equals(screen.selection.selectedId) || screen.selection.multiSel.contains(id);

        // P0: 层级颜色
        boolean isPrereq = !isSel && connectionRenderer.isPrereq(id);
        boolean isChild  = !isSel && !isPrereq && connectionRenderer.isChild(id);

        int bgCol;
        if (isSel) bgCol = CARD_SEL;
        else if (isPrereq) bgCol = CARD_PREREQ;
        else if (isChild) bgCol = CARD_CHILD;
        else bgCol = hov ? CARD_HOV : (done ? CARD_DONE : (enabled ? CARD : CARD_VANILLA));

        int borderCol;
        if (isSel) borderCol = BLUE;
        else if (isPrereq) borderCol = BORDER_PREREQ;
        else if (isChild) borderCol = BORDER_CHILD;
        else borderCol = done ? ACCENT : (hov ? 0xFF6A6A90 : DIVIDER);

        // P2: 完成闪白动画 — 最近完成的成就边框从白色过渡到 ACCENT
        var flash = screen.anim.completionFlashes.get(id);
        if (flash != null && done) {
            long elapsed = System.currentTimeMillis() - flash.time;
            if (elapsed < FLASH_DURATION_MS) {
                float t = 1f - (float) elapsed / FLASH_DURATION_MS; // 1→0 over FLASH_DURATION_MS
                borderCol = lerpColor(0xFFFFFFFF, borderCol, t);
            }
        }

        // P2: Hover zoom — 卡片身体 + 图标轻微放大
        g.pose().pushPose();
        if (hov) {
            g.pose().translate(cx, cy, 0);
            g.pose().scale(1f + HOVER_ZOOM, 1f + HOVER_ZOOM, 1f);
            g.pose().translate(-cx, -cy, 0);
        }

        // P1: 阴影 → 圆角矩形背景 → 圆角边框
        GuiUtils.drawCardShadow(g, x, y, cardW, cardH);
        GuiUtils.fillRoundedCard(g, x, y, cardW, cardH, bgCol);
        GuiUtils.drawRoundedBorder(g, x, y, cardW, cardH, borderCol, bgCol);

        ItemStack iconStack = resolveIcon(iconStr);
        if (hidden) {
            // 隐藏且未完成的成就：显示 "?" 代替真实图标和名称
            String q = "?";
            g.drawString(screen.getFont(), q, cx - screen.getFont().width(q) / 2, cy - 4, TEXT_DIM, false);
        } else if (!iconStack.isEmpty()) {
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

        g.pose().popPose(); // end hover zoom scope

        // ID 文字在缩放范围外绘制，保持原始大小
        if (showId) {
            String truncId = GuiUtils.truncate(screen.getFont(), id, cardW);
            g.drawString(screen.getFont(), truncId, cx - screen.getFont().width(truncId) / 2, y + cardH + 2, TEXT_DIM, false);
        }

        if (!enabled && !done) {
            g.drawString(screen.getFont(), "\u2717", x + 2, y + 2, PINK, false);
        }
    }

    /** P2: 线性插值两个 ARGB 颜色。 */
    private static int lerpColor(int c1, int c2, float t) {
        if (t <= 0) return c1;
        if (t >= 1) return c2;
        int a = (int)(((c1 >>> 24) & 0xFF) + (((c2 >>> 24) & 0xFF) - ((c1 >>> 24) & 0xFF)) * t);
        int r = (int)(((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g_ = (int)(((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int)((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g_ << 8) | b;
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