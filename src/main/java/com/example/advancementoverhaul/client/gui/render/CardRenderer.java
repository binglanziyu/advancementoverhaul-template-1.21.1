package com.example.advancementoverhaul.client.gui.render;

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
import com.example.advancementoverhaul.client.gui.AdvancementScreen;
import com.example.advancementoverhaul.client.gui.GuiUtils;
import com.example.advancementoverhaul.client.gui.ImageElement;
import com.example.advancementoverhaul.client.gui.state.CanvasState;
import com.example.advancementoverhaul.data.ClientDataStore;
import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.model.CustomAdvancement;
import com.example.advancementoverhaul.data.model.VanillaAdvMeta;
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

import static com.example.advancementoverhaul.client.gui.Theme.*;

public class CardRenderer {

    private final AdvancementScreen screen;

    public CardRenderer(AdvancementScreen screen) { this.screen = screen; }

    // ═══════════════ P0: 层级关系缓存（选中卡片的直系前置/后继） ═══════════════

    /** Last selectedId used to compute the layer sets — avoids recomputation when selection hasn't changed. */
    private String cachedSelId = null;
    /** Direct prerequisites of the currently selected advancement (computed once per selection change). */
    private final Set<String> selPrereqs = new HashSet<>();
    /** Advancements that have the selected advancement as a direct prerequisite (computed once per selection change). */
    private final Set<String> selChildren = new HashSet<>();

    /** Compute or refresh the layer sets when selection changes. Called once per frame if needed. */
    private void ensureLayerSets() {
        String selId = screen.selection.selectedId;
        if (Objects.equals(selId, cachedSelId)) return;
        cachedSelId = selId;
        selPrereqs.clear();
        selChildren.clear();
        if (selId == null) return;

        ClientDataStore cs = ClientDataStore.getInstance();
        // Prerequisites of the selected advancement
        CustomAdvancement selAdv = cs.getAdvancement(selId);
        if (selAdv != null && selAdv.getPrerequisites() != null) {
            for (String pid : selAdv.getPrerequisites()) {
                if (pid != null && !pid.isEmpty()) selPrereqs.add(pid);
            }
        }
        // Vanilla parent of selected
        Map<String, String> parentMap = cs.getVanillaParentMap();
        if (parentMap != null) {
            String parent = parentMap.get(selId);
            if (parent != null) selPrereqs.add(parent);
        }
        // VanillaMeta prerequisites for the selected advancement (custom prereqs added to vanilla)
        VanillaAdvMeta selMeta = cs.getVanillaMeta(selId);
        if (selMeta != null && selMeta.getPrerequisites() != null) {
            for (String pid : selMeta.getPrerequisites()) {
                if (pid != null && !pid.isEmpty()) selPrereqs.add(pid);
            }
        }

        // Children: advancements that list selId as a prerequisite
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
        // Also check vanillaMeta prerequisites (advancements that have selId as prerequisite via meta)
        for (var entry : cs.getVanillaMeta().entrySet()) {
            var metaPrqs = entry.getValue().getPrerequisites();
            if (metaPrqs != null && metaPrqs.contains(selId))
                selChildren.add(entry.getKey());
        }
    }

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

    public void markBoundsDirty() { boundsDirty = true; gridDirty = true; }

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

    // ═══════════════ PERF-18: 空间网格索引（千级卡片视口裁剪） ═══════════════

    /** 网格单元的世界坐标大小（约 20 个卡片宽度 = 480 世界单位） */
    private static final int GRID_CELL_SIZE = 480;

    /** 空间网格脏标记 */
    private boolean gridDirty = true;

    /** 空间网格：Long(cellX<<32|cellY) → 该单元格内的卡片渲染条目列表 */
    private final Map<Long, List<CardEntry>> spatialGrid = new HashMap<>();

    /** 表示一个需要渲染的卡片条目（自定义或原版），避免每帧重复查找 ClientDataStore */
    private record CardEntry(String id, String name, String icon,
                             int wx, int wy, boolean done, boolean showId, boolean enabled, boolean hidden) {}

    /** 将世界坐标编码为网格单元格 key */
    private static long cellKey(int worldX, int worldY) {
        int cx = Math.floorDiv(worldX, GRID_CELL_SIZE);
        int cy = Math.floorDiv(worldY, GRID_CELL_SIZE);
        return ((long) cx << 32) | (cy & 0xFFFFFFFFL);
    }

    /** 重建空间网格索引 */
    private void ensureGrid() {
        if (!gridDirty) return;
        gridDirty = false;
        spatialGrid.clear();

        ClientDataStore cs = ClientDataStore.getInstance();

        // 索引自定义进度
        for (var a : screen.frameFiltered) {
            if (!shouldRenderCard(a, cs)) continue;
            long key = cellKey(a.getX(), a.getY());
            spatialGrid.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new CardEntry(a.getId(), a.getName(), a.getIcon(),
                            a.getX(), a.getY(), cs.isCompleted(a.getId()), false, true,
                            a.isHidden() && !cs.isCompleted(a.getId())));
        }

        // 索引原版进度
        for (var va : screen.vanillaAdvs) {
            if (!screen.shouldShowVanilla(va.id())) continue;
            int[] p = screen.vanillaPos.get(va.id());
            if (p == null) continue;
            long key = cellKey(p[0], p[1]);
            spatialGrid.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new CardEntry(va.id(), va.getLocalizedName(), va.icon(),
                            p[0], p[1], cs.isCompleted(va.id()), false,
                            cs.isVanillaEnabled(va.id()), false));
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

    // ═══════════════ 连接线（直角树状图：竖→横→竖，高性能 g.fill 绘制） ═══════════════

    public void tickFrameTime() { /* no-op */ }

    public void renderConnections(GuiGraphics g) {
        // 跳过"原有成就"分类，不绘制连线
        if (DataStore.TAB_VANILLA.equals(screen.curTab)) return;

        ClientDataStore cs = ClientDataStore.getInstance();
        var cv = screen.canvas;
        String selId = screen.selection.selectedId;
        int thickness = Math.max(1, (int) (LINE_THICKNESS * cv.zoom));
        int hw = thickness / 2;

        // ── Pass 1：收集并绘制所有普通连线 ──
        // 自定义进度 → 前置
        for (var a : screen.frameFiltered) {
            if (!shouldRenderCard(a, cs)) continue;
            var prereqs = a.getPrerequisites();
            if (prereqs == null) continue;
            int cx = cv.toScreenX(a.getX()) + cv.screenW(CARD_W) / 2;
            int cy = cv.toScreenY(a.getY()) + cv.screenH(CARD_H) / 2;
            boolean done = cs.isCompleted(a.getId());
            for (String pid : prereqs) {
                if (pid == null || pid.isEmpty()) continue;
                int px, py;
                var p = cs.getAdvancement(pid);
                if (p != null) {
                    px = cv.toScreenX(p.getX()) + cv.screenW(CARD_W) / 2;
                    py = cv.toScreenY(p.getY()) + cv.screenH(CARD_H) / 2;
                } else {
                    int[] vp = screen.vanillaPos.get(pid);
                    if (vp == null || !screen.shouldShowVanilla(pid)) continue;
                    px = cv.toScreenX(vp[0]) + cv.screenW(CARD_W) / 2;
                    py = cv.toScreenY(vp[1]) + cv.screenH(CARD_H) / 2;
                }
                // 跳过：选中项的高亮在 Pass 2 绘制
                boolean isHL = selId != null && (selId.equals(a.getId()) || selId.equals(pid));
                int color = done ? LINE_DONE : LINE;
                if (!isHL) drawTreeConnection(g, px, py, cx, cy, color, thickness, hw);
            }
        }

        // 原版进度 parent map
        Map<String, String> parentMap = cs.getVanillaParentMap();
        if (parentMap != null && !parentMap.isEmpty()) {
            for (var e : parentMap.entrySet()) {
                String cid = e.getKey(), pid = e.getValue();
                int[] cp = screen.vanillaPos.get(cid), pp = screen.vanillaPos.get(pid);
                if (cp == null || pp == null) continue;
                if (!screen.shouldShowVanilla(cid) || !screen.shouldShowVanilla(pid)) continue;
                int px = cv.toScreenX(pp[0]) + cv.screenW(CARD_W) / 2;
                int py = cv.toScreenY(pp[1]) + cv.screenH(CARD_H) / 2;
                int cx = cv.toScreenX(cp[0]) + cv.screenW(CARD_W) / 2;
                int cy = cv.toScreenY(cp[1]) + cv.screenH(CARD_H) / 2;
                boolean done = cs.isCompleted(cid);
                boolean isHL = selId != null && (selId.equals(cid) || selId.equals(pid));
                int color = done ? LINE_DONE : LINE;
                if (!isHL) drawTreeConnection(g, px, py, cx, cy, color, thickness, hw);
            }
        }

        // 原版进度 → 自定义前置
        for (var va : screen.vanillaAdvs) {
            if (!screen.shouldShowVanilla(va.id())) continue;
            int[] cp = screen.vanillaPos.get(va.id());
            if (cp == null) continue;
            VanillaAdvMeta meta = cs.getVanillaMeta(va.id());
            if (meta == null) continue;
            var metaPrqs = meta.getPrerequisites();
            if (metaPrqs == null) continue;
            int cx = cv.toScreenX(cp[0]) + cv.screenW(CARD_W) / 2;
            int cy = cv.toScreenY(cp[1]) + cv.screenH(CARD_H) / 2;
            boolean done = cs.isCompleted(va.id());
            for (String pid : metaPrqs) {
                if (pid == null || pid.isEmpty()) continue;
                int px, py;
                var custP = cs.getAdvancement(pid);
                if (custP != null) {
                    px = cv.toScreenX(custP.getX()) + cv.screenW(CARD_W) / 2;
                    py = cv.toScreenY(custP.getY()) + cv.screenH(CARD_H) / 2;
                } else {
                    int[] pp = screen.vanillaPos.get(pid);
                    if (pp == null || !screen.shouldShowVanilla(pid)) continue;
                    px = cv.toScreenX(pp[0]) + cv.screenW(CARD_W) / 2;
                    py = cv.toScreenY(pp[1]) + cv.screenH(CARD_H) / 2;
                }
                boolean isHL = selId != null && (selId.equals(va.id()) || selId.equals(pid));
                int color = done ? LINE_DONE : LINE;
                if (!isHL) drawTreeConnection(g, px, py, cx, cy, color, thickness, hw);
            }
        }

        // ── Pass 2：高亮选中任务关联的连线 ──
        if (selId == null) return;

        // 2a：自定义进度中与选中 ID 相关的连线
        for (var a : screen.frameFiltered) {
            if (!shouldRenderCard(a, cs)) continue;
            var prereqs = a.getPrerequisites();
            if (prereqs == null) continue;
            boolean matches = selId.equals(a.getId());
            if (!matches) {
                boolean hasMatch = false;
                for (String pid : prereqs) {
                    if (selId.equals(pid)) { hasMatch = true; break; }
                }
                if (!hasMatch) continue;
            }
            int cx = cv.toScreenX(a.getX()) + cv.screenW(CARD_W) / 2;
            int cy = cv.toScreenY(a.getY()) + cv.screenH(CARD_H) / 2;
            boolean done = cs.isCompleted(a.getId());
            for (String pid : prereqs) {
                if (pid == null || pid.isEmpty()) continue;
                int px, py;
                var p = cs.getAdvancement(pid);
                if (p != null) {
                    px = cv.toScreenX(p.getX()) + cv.screenW(CARD_W) / 2;
                    py = cv.toScreenY(p.getY()) + cv.screenH(CARD_H) / 2;
                } else {
                    int[] vp = screen.vanillaPos.get(pid);
                    if (vp == null || !screen.shouldShowVanilla(pid)) continue;
                    px = cv.toScreenX(vp[0]) + cv.screenW(CARD_W) / 2;
                    py = cv.toScreenY(vp[1]) + cv.screenH(CARD_H) / 2;
                }
                int color = selId.equals(a.getId()) ? (done ? LINE_DONE : LINE_REQUIRES)
                          : (done ? LINE_DONE : LINE_REQUIRED_FOR);
                drawTreeConnection(g, px, py, cx, cy, color, thickness, hw);
            }
        }

        // 2b：原版 parent map 中与选中 ID 相关的连线
        if (parentMap != null && !parentMap.isEmpty()) {
            for (var e : parentMap.entrySet()) {
                String cid = e.getKey(), pid = e.getValue();
                if (!selId.equals(cid) && !selId.equals(pid)) continue;
                int[] cp = screen.vanillaPos.get(cid), pp = screen.vanillaPos.get(pid);
                if (cp == null || pp == null) continue;
                if (!screen.shouldShowVanilla(cid) || !screen.shouldShowVanilla(pid)) continue;
                int px = cv.toScreenX(pp[0]) + cv.screenW(CARD_W) / 2;
                int py = cv.toScreenY(pp[1]) + cv.screenH(CARD_H) / 2;
                int cx = cv.toScreenX(cp[0]) + cv.screenW(CARD_W) / 2;
                int cy = cv.toScreenY(cp[1]) + cv.screenH(CARD_H) / 2;
                boolean done = cs.isCompleted(cid);
                int color = selId.equals(cid) ? (done ? LINE_DONE : LINE_REQUIRES)
                          : (done ? LINE_DONE : LINE_REQUIRED_FOR);
                drawTreeConnection(g, px, py, cx, cy, color, thickness, hw);
            }
        }

        // 2c：原版进度 → 自定义前置中与选中 ID 相关的连线
        for (var va : screen.vanillaAdvs) {
            if (!screen.shouldShowVanilla(va.id())) continue;
            int[] cp = screen.vanillaPos.get(va.id());
            if (cp == null) continue;
            VanillaAdvMeta meta = cs.getVanillaMeta(va.id());
            if (meta == null) continue;
            var metaPrqs = meta.getPrerequisites();
            if (metaPrqs == null) continue;
            boolean matches = selId.equals(va.id());
            if (!matches) {
                boolean hasMatch = false;
                for (String pid : metaPrqs) {
                    if (selId.equals(pid)) { hasMatch = true; break; }
                }
                if (!hasMatch) continue;
            }
            int cx = cv.toScreenX(cp[0]) + cv.screenW(CARD_W) / 2;
            int cy = cv.toScreenY(cp[1]) + cv.screenH(CARD_H) / 2;
            boolean done = cs.isCompleted(va.id());
            for (String pid : metaPrqs) {
                if (pid == null || pid.isEmpty()) continue;
                int px, py;
                var custP = cs.getAdvancement(pid);
                if (custP != null) {
                    px = cv.toScreenX(custP.getX()) + cv.screenW(CARD_W) / 2;
                    py = cv.toScreenY(custP.getY()) + cv.screenH(CARD_H) / 2;
                } else {
                    int[] pp = screen.vanillaPos.get(pid);
                    if (pp == null || !screen.shouldShowVanilla(pid)) continue;
                    px = cv.toScreenX(pp[0]) + cv.screenW(CARD_W) / 2;
                    py = cv.toScreenY(pp[1]) + cv.screenH(CARD_H) / 2;
                }
                int color = selId.equals(va.id()) ? (done ? LINE_DONE : LINE_REQUIRES)
                          : (done ? LINE_DONE : LINE_REQUIRED_FOR);
                drawTreeConnection(g, px, py, cx, cy, color, thickness, hw);
            }
        }
    }

    /**
     * 绘制直角树状连线：父级竖线→水平线→子级竖线，并在拐点处添加圆角连接点。
     * 三个线段形成 L 形或 Z 形，简洁清晰的依赖关系展示。
     */
    private void drawTreeConnection(GuiGraphics g, int px, int py, int cx, int cy,
                                    int color, int thickness, int hw) {
        // 视口快速裁切
        int minX = Math.min(px, cx), maxX = Math.max(px, cx);
        int minY = Math.min(py, cy), maxY = Math.max(py, cy);
        if (maxX < 0 || minX > screen.width || maxY < TAB_H || minY > screen.height - BOTTOM_H) return;

        int midY = (py + cy) / 2;

        // 段1：父级竖线（中心 → midY）
        if (py != midY) {
            int y1 = Math.min(py, midY), y2 = Math.max(py, midY);
            g.fill(px - hw, y1, px + hw + (thickness & 1), y2, color);
        }
        // 段2：水平线（父级X → 子级X，在 midY 处）
        if (px != cx) {
            int x1 = Math.min(px, cx), x2 = Math.max(px, cx);
            g.fill(x1, midY - hw, x2 + (thickness & 1), midY + hw + (thickness & 1), color);
        }
        // 段3：子级竖线（midY → 子中心）
        if (midY != cy) {
            int y1 = Math.min(midY, cy), y2 = Math.max(midY, cy);
            g.fill(cx - hw, y1, cx + hw + (thickness & 1), y2, color);
        }

        // P1: 拐点圆角连接点（junction dots）
        int dotR = Math.max(1, (int) (thickness * JUNCTION_DOT_RATIO / 2f));
        int dotD = dotR * 2 + (thickness & 1);
        if (px != cx) {
            // 水平线与父级竖线相交处
            int jx1 = px - dotR;
            int jy1 = midY - dotR;
            GuiUtils.fillCircle(g, px, midY, dotR, color);
        }
        if (py != midY && px != cx) {
            // 水平线与子级竖线相交处
            GuiUtils.fillCircle(g, cx, midY, dotR, color);
        }
    }

    // ═══════════════ 卡片渲染 ═══════════════

    private boolean shouldRenderCard(CustomAdvancement a, ClientDataStore cs) {
        return !a.isHidden()
                || "hidden".equals(screen.curTab)
                || screen.editMode
                || cs.isCompleted(a.getId());
    }

    public void renderCards(GuiGraphics g, int mx, int my) {
        var cv = screen.canvas;
        ensureLayerSets(); // P0: 计算选中卡片的直系层级关系
        ensureGrid();      // PERF-18: 确保空间网格索引是最新的

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
                    List<CardEntry> entries = spatialGrid.get(key);
                    if (entries == null) continue;
                    for (CardEntry entry : entries) {
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
        boolean isPrereq = !isSel && selPrereqs.contains(id);
        boolean isChild  = !isSel && !isPrereq && selChildren.contains(id);

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