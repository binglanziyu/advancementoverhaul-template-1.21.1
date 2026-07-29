package com.example.advancementoverhaul.client.gui;


import com.example.advancementoverhaul.LangKeys;
import com.example.advancementoverhaul.client.gui.cache.CircleCache;
import com.example.advancementoverhaul.client.gui.cache.RegistryCache;
import com.example.advancementoverhaul.client.gui.cache.RoundedRectCache;
import com.example.advancementoverhaul.client.gui.manager.CanvasManager;
import com.example.advancementoverhaul.client.gui.manager.InputManager;
import com.example.advancementoverhaul.client.gui.manager.TabManager;
import com.example.advancementoverhaul.client.gui.panel.DimensionPanel;
import com.example.advancementoverhaul.client.gui.panel.EditPanel;
import com.example.advancementoverhaul.client.gui.panel.ListSelector;
import com.example.advancementoverhaul.client.gui.render.CardRenderer;
import com.example.advancementoverhaul.client.gui.render.OverlayRenderer;
import com.example.advancementoverhaul.client.gui.render.TabRenderer;
import com.example.advancementoverhaul.client.gui.state.*;
import com.example.advancementoverhaul.client.gui.state.OverlayState.CtxAct;
import com.example.advancementoverhaul.client.gui.state.OverlayState.Ov;
import com.example.advancementoverhaul.data.ClientDataStore;
import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.model.CustomAdvancement;
import com.example.advancementoverhaul.data.model.VanillaAdvMeta;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.function.Consumer;

import static com.example.advancementoverhaul.client.gui.Theme.*;

/**
 * Coordinator screen: owns state fields, rendering, context menus, and panel toggling.
 * Interaction logic is delegated to InputManager / CanvasManager / TabManager.
 */
public class AdvancementScreen extends Screen {


    // ═══════════════ Inner types ═══════════════

    public record VanillaAdv(String id, String name, String desc, boolean hidden,
                             String nameKey, String descKey, String icon) {
        public String getLocalizedName() { return nameKey != null ? Component.translatable(nameKey).getString() : name; }
        public String getLocalizedDesc() { return descKey != null ? Component.translatable(descKey).getString() : desc; }
    }

    // ═══════════════ Persistence across screen opens ═══════════════

    private static final CanvasState PERSIST = new CanvasState();
    public static boolean persistEdit = false;

    // ═══════════════ State fields ═══════════════

    public final CanvasState canvas = new CanvasState();
    public final SelectionState selection = new SelectionState();
    public final DragState drag = new DragState();
    public final OverlayState overlay = new OverlayState();
    public final AnimState anim = new AnimState();
    public final TabDragState tabDrag = new TabDragState();
    public boolean editMode = persistEdit;
    public String curTab = null;
    public int frameCount = 0;
    public boolean showDim = false, showSel = false, showHelp = false;
    public boolean vanillaPositionsDirty = true;

    // ── FTB 通知模式（客户端持久化）──
    /** 0=默认（FTB 自带通知）, 1=关闭 FTB 通知, 2=替换为项目通知 */
    public static int ftbNotifMode = 1;

    // ── Vanilla advancement data ──

    public final List<VanillaAdv> vanillaAdvs = new ArrayList<>();
    public final Map<String, int[]> vanillaPos = new HashMap<>();
    public final Set<String> vanillaAdvIdSet = new HashSet<>();
    public final Map<String, VanillaAdv> vanillaAdvMap = new HashMap<>();
    // ── 图片元素 ──
    public final List<ImageElement> imageElements = new ArrayList<>();
    public String selectedImageId = null;
    // ── Filtered list cache ──

    /** Last composite key used for cache invalidation (tab + data size). */
    private String lastFilterKey = null;
    /** Explicit dirty flag — set when tabs change or data is updated externally. */
    private boolean filteredDirty = true;
    /** Cached snapshot of the current filtered advancement list for this frame. */
    public List<CustomAdvancement> frameFiltered = Collections.emptyList();

    /** Mark the filtered cache as needing refresh (called on tab switch or data update). */
    public void markFilteredDirty() { filteredDirty = true; }

    // ═══════════════ Components ═══════════════

    public final CardRenderer cardRenderer = new CardRenderer(this);
    public final TabRenderer tabRenderer = new TabRenderer(this);
    public final OverlayRenderer overlayRenderer = new OverlayRenderer(this);
    public DimensionPanel dimPanel;
    public ListSelector listSel;
    public EditPanel editPanel;
    public EditBox tabNameBox;

    // ── Managers ──

    final CanvasManager canvasManager = new CanvasManager(this);
    public final TabManager tabManager = new TabManager(this);
    final InputManager inputManager = new InputManager(this, canvasManager, tabManager);

    // ═══════════════ Constructor / accessors ═══════════════

    public AdvancementScreen() { super(Component.translatable(LangKeys.TITLE)); }
    public net.minecraft.client.gui.Font getFont() { return this.font; }
    public boolean hasOv() { return overlay.current != Ov.NONE || showDim || showSel || showHelp || tabDrag.overDDOpen; }
    /** 冒险日志滚动偏移 */
    public int journalScrollOff = 0;
    public boolean blocksCanvas() {
        return showDim || showSel || showHelp
                || overlay.current == Ov.DETAIL || overlay.current == Ov.CREATE
                || overlay.current == Ov.EDIT || overlay.current == Ov.CONFIRM
                || overlay.current == Ov.TAB_INPUT || overlay.current == Ov.TAB_MANAGE
                || overlay.current == Ov.JOURNAL
                || overlay.current == Ov.CTX;
    }
    public int mid(int w) { return (width - w) / 2; }
    public int midY(int h) { return Math.max(20, (height - h) / 2); }
    public int getScreenWidth() { return width; }
    public int getScreenHeight() { return height; }
    public boolean isVanillaAdvId(String id) { return id != null && vanillaAdvIdSet.contains(id); }
    public CustomAdvancement adv(String id) { return id == null ? null : ClientDataStore.getInstance().getAdvancement(id); }
    public VanillaAdv getVanillaAdv(String id) { return vanillaAdvMap.get(id); }
    public String prereqDisplayName(String id) { if (id == null || id.isEmpty()) return ""; var a = adv(id); return a != null ? a.getName() : id; }
    public void addWidgetToScreen(EditBox eb) { addRenderableWidget(eb); }
    public void removeWidgetFromScreen(EditBox eb) { removeWidget(eb); }
    public void showSelector(List<ListSelector.Entry> entries, Consumer<ListSelector.Entry> cb) { listSel.show(entries, cb); showSel = true; }
    public void addToast(String name) { anim.addToast(name); }

    // ═══════════════ Filtering ═══════════════

    public Collection<CustomAdvancement> filtered() {
        ClientDataStore s = ClientDataStore.getInstance();
        if (curTab == null) return s.getAdvancements().values();
        if ("hidden".equals(curTab)) return s.getHiddenAdvancements();
        if (DataStore.TAB_VANILLA.equals(curTab)) return s.getAdvancementsByTab(DataStore.TAB_VANILLA);
        return s.getAdvancementsByTab(curTab);
    }

    /**
     * Returns true if this vanilla advancement should be shown on the current tab.
     * <ul>
     *   <li>已启用的原版进度 — 在所有分类可见（全部、自定义标签页、原版分类）</li>
     *   <li>未启用的原版进度 — 仅在"原有成就"分类可见</li>
     * </ul>
     */
    public boolean shouldShowVanilla(String id) {
        ClientDataStore cs = ClientDataStore.getInstance();
        if (cs.isVanillaEnabled(id)) return true;
        return DataStore.TAB_VANILLA.equals(curTab);
    }

    // ═══════════════ Vanilla advancement loading ═══════════════

    private void loadVanillaAdvancements() {
        vanillaAdvs.clear(); vanillaPos.clear(); vanillaAdvIdSet.clear(); vanillaAdvMap.clear();
        ClientDataStore cs = ClientDataStore.getInstance();
        for (var e : cs.getVanillaAdvancements())
            vanillaAdvs.add(new VanillaAdv(e.id(), e.name(), e.desc(), e.hidden(), e.nameKey(), e.descKey(), e.icon()));
        for (var va : vanillaAdvs) { vanillaAdvIdSet.add(va.id()); vanillaAdvMap.put(va.id(), va); }
        vanillaPositionsDirty = true;
        recalcVanillaPositions();
    }

    /**
     * Groups advancements by their depth in the prerequisite tree.
     * Depth 0 = no parent, depth 1 = one parent above, etc.
     */
    private Map<Integer, List<String>> buildDepthLayers(List<VanillaAdv> group, Map<String, String> parentMap) {
        Map<Integer, List<String>> layerMap = new TreeMap<>();
        for (var va : group) {
            int depth = 0;
            String cur = va.id();
            Set<String> visited = new HashSet<>();
            visited.add(cur);
            while (parentMap != null) {
                String parent = parentMap.get(cur);
                if (parent == null || !visited.add(parent)) break;
                cur = parent;
                depth++;
            }
            layerMap.computeIfAbsent(depth, k -> new ArrayList<>()).add(va.id());
        }
        return layerMap;
    }

    private void recalcVanillaPositions() {
        if (!vanillaPositionsDirty) return;
        vanillaPositionsDirty = false;

        ClientDataStore cs = ClientDataStore.getInstance();
        vanillaPos.clear();

        // Use positions from metadata where available
        for (var va : vanillaAdvs) {
            var meta = cs.getVanillaMeta(va.id());
            if (meta != null && meta.hasPosition())
                vanillaPos.put(va.id(), new int[]{meta.getX(), meta.getY()});
        }

        // Group unpositioned advancements by display tab
        Map<String, List<VanillaAdv>> byTab = new LinkedHashMap<>();
        for (var va : vanillaAdvs) {
            if (vanillaPos.containsKey(va.id())) continue;
            String tab = cs.getVanillaDisplayTab(va.id());
            if (tab == null || tab.isEmpty()) tab = DataStore.TAB_VANILLA;
            byTab.computeIfAbsent(tab, k -> new ArrayList<>()).add(va);
        }

        // Start Y for "原有成就" tab groups — below the lowest custom advancement
        int vanillaTabY = 40;
        for (var a : cs.getAdvancements().values())
            vanillaTabY = Math.max(vanillaTabY, a.getY() + CARD_H + 80);

        int gapX = CARD_W + 16;
        int gapY = CARD_H + 24;
        Map<String, String> parentMap = cs.getVanillaParentMap();

        for (var entry : byTab.entrySet()) {
            String tab = entry.getKey();
            List<VanillaAdv> group = entry.getValue();
            if (group.isEmpty()) continue;

            Map<Integer, List<String>> layerMap = buildDepthLayers(group, parentMap);
            int layerY;
            int baseX;

            if (DataStore.TAB_VANILLA.equals(tab)) {
                // "原有成就" 分类：保持在所有自定义进度下方居中
                baseX = 20;
                layerY = vanillaTabY;
                // Bug 3 修复：仅 TAB_VANILLA 更新全局起始 Y
                vanillaTabY = Math.max(vanillaTabY, layerY + 40);
            } else {
                // 自定义分类：放在该分类已有自定义进度的右侧
                int rightEdge = 20;
                int tabTopY = Integer.MAX_VALUE;
                for (var a : cs.getAdvancementsByTab(tab)) {
                    rightEdge = Math.max(rightEdge, a.getX() + CARD_W + gapX);
                    tabTopY = Math.min(tabTopY, a.getY());
                }
                // 也考虑该分类中已有位置的原版进度
                for (var va : vanillaAdvs) {
                    int[] pos = vanillaPos.get(va.id());
                    if (pos != null && tab.equals(cs.getVanillaDisplayTab(va.id()))) {
                        rightEdge = Math.max(rightEdge, pos[0] + CARD_W + gapX);
                        tabTopY = Math.min(tabTopY, pos[1]);
                    }
                }
                baseX = rightEdge;
                layerY = tabTopY == Integer.MAX_VALUE ? 40 : tabTopY;
            }

            for (var layer : layerMap.entrySet()) {
                List<String> ids = layer.getValue();
                int layerStartX = Math.max(baseX, 20);
                for (int i = 0; i < ids.size(); i++)
                    vanillaPos.put(ids.get(i), new int[]{layerStartX + i * gapX, layerY});
                layerY += gapY;
            }
        }
    }

    // ═══════════════ INIT ═══════════════

    @Override
    protected void init() {
        if (editPanel != null && editPanel.isVisible()) editPanel.close();
        if (overlay.current != Ov.NONE) overlay.close();
        showDim = false; showSel = false; showHelp = false;
        tabDrag.reset();
        canvasManager.resetScrollDrag();

        editPanel = new EditPanel();
        editPanel.init(font);
        editPanel.setScreen(this);

        tabNameBox = new EditBox(font, 0, 0, 150, 24, Component.empty());
        tabNameBox.setMaxLength(256); tabNameBox.setVisible(false);
        tabNameBox.setTextColor(0xFFFFFFFF); tabNameBox.setTextColorUneditable(0xFFAAAAAA);
        tabNameBox.setBordered(true);
        addRenderableWidget(tabNameBox);

        dimPanel = new DimensionPanel(this);
        listSel = new ListSelector();
        editMode = persistEdit && minecraft.player != null && minecraft.player.hasPermissions(2);
        canvas.scrollX = PERSIST.scrollX; canvas.scrollY = PERSIST.scrollY; canvas.zoom = PERSIST.zoom;
        anim.lastTime = Util.getMillis();
        vanillaPositionsDirty = true;
        filteredDirty = true;

        CircleCache.init();
        RoundedRectCache.init();
        RegistryCache.init();
        cardRenderer.clearIconCache();
        imageElements.clear();
        imageElements.addAll(ImageManager.load());
        for (ImageElement img : imageElements) {
            ResourceLocation texId = ImageManager.loadTexture(img.getId(), img.getPath());
            if (texId != null) {
                img.setTextureId(texId);
                int[] size = ImageManager.getTextureSize(img.getId());
                img.setOriginalSize(size[0], size[1]);
            }
        }
        loadVanillaAdvancements();
    }

    // ═══════════════ TICK (non-rendering updates) ═══════════════

    @Override
    public void tick() {
        super.tick();
        frameCount++;

        // Periodic cleanup of stale toast animations
        if (frameCount % 100 == 0)
            anim.prune(ClientDataStore.getInstance().getAdvancements().keySet());

        // Lazy-load vanilla advancements when server data arrives
        if (vanillaAdvs.isEmpty() && !ClientDataStore.getInstance().getVanillaAdvancements().isEmpty())
            loadVanillaAdvancements();

        // Recalculate vanilla positions if needed
        if (vanillaPositionsDirty) recalcVanillaPositions();

        // Update filtered list cache when data or tab changes
        updateFilterCache();
    }

    /**
     * Refreshes the cached filtered advancement list when data or the active tab changes.
     * Uses advancement count as a cheap proxy for "data changed" — won't catch in-place
     * edits that preserve count. Call {@link #markFilteredDirty()} explicitly for those.
     */
    private void updateFilterCache() {
        String filterKey = (curTab == null ? "null" : curTab)
                + "|" + ClientDataStore.getInstance().getAdvancements().size();
        if (filteredDirty || !filterKey.equals(lastFilterKey)) {
            frameFiltered = new ArrayList<>(filtered());
            lastFilterKey = filterKey;
            filteredDirty = false;
            cardRenderer.markBoundsDirty();
        }
    }

    // ═══════════════ RENDER (pure rendering, no data mutation) ═══════════════

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderCanvasBackground(g);
        renderCanvasContent(g, mx, my);
        renderBackgroundMask(g);
        renderChrome(g, mx, my);
        renderOverlayLayer(g, mx, my, pt);
        anim.tick();
    }

    /** Background fill for the entire screen. */
    private void renderCanvasBackground(GuiGraphics g) {
        g.fill(0, 0, width, height, BG);
        cardRenderer.tickFrameTime();
    }

    /** Scrolled canvas region: grid, connections, cards, box selection, scroll indicators. */
    private void renderCanvasContent(GuiGraphics g, int mx, int my) {
        // Bug 4 修复：扩展 scissor 区域，防止 hover zoom 时边缘卡片被截断
        int hovPad = (int) (CARD_W * canvas.zoom * HOVER_ZOOM);
        g.enableScissor(-hovPad, TAB_H - hovPad, width + hovPad * 2, height - BOTTOM_H + hovPad);
        cardRenderer.renderGrid(g);
        if (!blocksCanvas()) {
            cardRenderer.renderConnections(g);
            cardRenderer.renderCards(g, mx, my);
            if (drag.boxSel && editMode) cardRenderer.renderBoxSel(g);
        }
        cardRenderer.renderScrollIndicators(g);
        g.disableScissor();
    }

    /**
     * Opaque mask to prevent {@code renderItem} 3D quads from penetrating
     * through popup overlays (ListSelector / detail/edit panels).
     */
    private void renderBackgroundMask(GuiGraphics g) {
        if (showSel) {
            g.fill(0, 0, width, height, 0xFF1A1A2E);
        } else if (hasOv()) {
            // 完全不透明遮罩，防止 renderItem 3D 四边形穿透覆盖层面板（Bug 1 修复）
            g.fill(0, TAB_H, width, height - BOTTOM_H, 0xFF1A1A2E);
        }
    }

    /** Tab bar, bottom status bar, and toolbar buttons. */
    private void renderChrome(GuiGraphics g, int mx, int my) {
        tabRenderer.renderTabs(g, mx, my);
        tabRenderer.renderBottom(g, mx, my);
        tabRenderer.renderButtons(g, mx, my);
    }

    /**
     * Overlay layer rendered at z=300: edit/detail/create panels,
     * dimension panel, list selector, help screen, managed EditBoxes,
     * tab-name input, card tooltips, and toast notifications.
     */
    private void renderOverlayLayer(GuiGraphics g, int mx, int my, float pt) {
        boolean ebVis = (overlay.current == Ov.CREATE || overlay.current == Ov.EDIT)
                && !showSel && !editPanel.isCondSelActive();
        editPanel.updateVisibility(ebVis);

        g.pose().pushPose();
        g.pose().translate(0, 0, 300);

        overlayRenderer.renderOv(g, mx, my);
        if (overlay.current == Ov.JOURNAL) overlayRenderer.renderJournal(g, mx, my, font, width, height);
        if (showDim) dimPanel.render(g, mx, my);
        if (showSel) listSel.render(g, mx, my);
        if (showHelp) overlayRenderer.renderHelp(g, mx, my, font, width, height);

        // Managed EditBoxes
        for (var w : renderables) {
            if (w instanceof EditBox eb && eb.isVisible()) {
                boolean isInlineCount = eb == editPanel.getInlineCountBox();
                if (ebVis || isInlineCount) eb.render(g, mx, my, pt);
            }
        }

        // Tab name input popup
        if (overlay.current == Ov.TAB_INPUT) {
            int px = mid(OverlayLayout.TAB_INPUT_W);
            int py = midY(OverlayLayout.TAB_INPUT_H);
            tabNameBox.setX(px + OverlayLayout.TAB_INPUT_INNER_PAD);
            tabNameBox.setY(py + OverlayLayout.TAB_INPUT_BOX_Y);
            tabNameBox.setWidth(OverlayLayout.TAB_INPUT_W - 2 * OverlayLayout.TAB_INPUT_INNER_PAD);
            tabNameBox.setVisible(true);
            tabNameBox.render(g, mx, my, pt);
        } else {
            tabNameBox.setVisible(false);
        }

        // Hover tooltip (only when no overlay is active)
        if (!hasOv()) {
            // 图片元素覆盖时不显示卡片 tooltip（避免穿透显示）
            if (imageAt(mx, my) == null) {
                String hoverCard = canvasManager.cardAt(mx, my);
                if (hoverCard != null) overlayRenderer.renderTooltip(g, mx, my, hoverCard);
            }
        }

        overlayRenderer.renderToasts(g);
        g.pose().popPose();
    }

    // ═══════════════ Event delegation ═══════════════

    @Override public boolean mouseClicked(double mx, double my, int btn) { return inputManager.onMouseClicked(mx, my, btn); }
    @Override public boolean mouseReleased(double mx, double my, int btn) { if (inputManager.onMouseReleased(mx, my, btn)) return true; return super.mouseReleased(mx, my, btn); }
    @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) { if (inputManager.onMouseDragged(mx, my, btn, dx, dy)) return true; return super.mouseDragged(mx, my, btn, dx, dy); }
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) { return inputManager.onMouseScrolled(mx, my, sx, sy); }
    @Override public boolean charTyped(char chr, int mod) { if (inputManager.onCharTyped(chr, mod)) return true; return super.charTyped(chr, mod); }
    @Override public boolean keyPressed(int kc, int sc, int mod) { if (inputManager.onKeyPressed(kc, sc, mod)) return true; return super.keyPressed(kc, sc, mod); }

    // ═══════════════ Context menus ═══════════════

    public void showCtx(double mx, double my, String id) {
        overlay.ctxX = (int) mx; overlay.ctxY = (int) my; overlay.ctxActions.clear();
        overlay.ctxActions.add(new CtxAct(Component.translatable(LangKeys.DETAIL_TITLE).getString(), () -> openDetail(id)));
        overlay.ctxActions.add(new CtxAct(Component.translatable(LangKeys.EDIT_TITLE).getString(), () -> openEdit(id)));
        overlay.ctxActions.add(new CtxAct(Component.translatable(LangKeys.DELETE).getString(), () -> requestDelete(id)));
        overlay.ctxActions.add(new CtxAct(Component.translatable(LangKeys.RESET).getString(), () -> GuiUtils.sendCommand("adv reset @s " + id)));
        var a = adv(id);
        if (a != null) {
            String key = a.isHidden() ? LangKeys.SHOW : LangKeys.HIDE;
            overlay.ctxActions.add(new CtxAct(Component.translatable(key).getString(), () -> GuiUtils.sendCommand("adv togglehidden " + id)));
        }
        overlay.current = Ov.CTX;
    }

    public void showVanillaCtx(double mx, double my, String id) {
        overlay.ctxX = (int) mx; overlay.ctxY = (int) my; overlay.ctxActions.clear();
        boolean enabled = ClientDataStore.getInstance().isVanillaEnabled(id);

        overlay.ctxActions.add(new OverlayState.CtxAct(
                Component.translatable(LangKeys.DETAIL_TITLE).getString(),
                () -> openDetail(id)));

        if (enabled) {
            // 已启用：显示禁用（完全重置回原样）
            overlay.ctxActions.add(new OverlayState.CtxAct(
                    Component.translatable(LangKeys.ADV_TT_DISABLE_BTN).getString(),
                    () -> GuiUtils.sendCommand("adv vanilla disable " + id)));
            // 已启用：可分配/更换分类
            overlay.ctxActions.add(new OverlayState.CtxAct(
                    Component.translatable(LangKeys.VANILLA_ASSIGN_TAB).getString(),
                    () -> tabManager.openVanillaTabSel(id)));
            // 已启用：可编辑前置条件
            overlay.ctxActions.add(new OverlayState.CtxAct(
                    Component.translatable(LangKeys.EDIT_TITLE).getString(),
                    () -> openVanillaEdit(id)));
        } else {
            // 未启用：仅提供分配分类（分配即启用）
            overlay.ctxActions.add(new OverlayState.CtxAct(
                    Component.translatable(LangKeys.VANILLA_ASSIGN_TAB).getString(),
                    () -> tabManager.openVanillaTabSel(id)));
        }

        overlay.current = Ov.CTX;
    }

    /**
     * 打开原版/模组成就的编辑面板（仅可编辑标签和前置条件）
     */
    private void openVanillaEdit(String id) {
        VanillaAdv va = vanillaAdvMap.get(id);
        String name = va != null ? va.getLocalizedName() : id;
        String desc = va != null ? va.getLocalizedDesc() : "";
        editPanel.openVanillaEdit(font, id, name, desc);
        overlay.current = Ov.EDIT;
    }

    public void showBatchCtx(double mx, double my) {
        overlay.ctxX = (int) mx; overlay.ctxY = (int) my; overlay.ctxActions.clear();
        int n = selection.multiSel.size();
        overlay.ctxActions.add(new CtxAct(String.format(Component.translatable(LangKeys.BATCH_DELETE).getString(), n), this::requestBatchDelete));
        overlay.ctxActions.add(new CtxAct(String.format(Component.translatable(LangKeys.BATCH_HIDE).getString(), n), () -> {
            for (String id : selection.multiSel) {
                if (isVanillaAdvId(id)) GuiUtils.sendCommand("adv vanilla disable " + id);
                else { var a = adv(id); if (a != null && !a.isHidden()) GuiUtils.sendCommand("adv togglehidden " + id); }
            }
        }));
        overlay.ctxActions.add(new CtxAct(String.format(Component.translatable(LangKeys.BATCH_SHOW).getString(), n), () -> {
            for (String id : selection.multiSel) {
                if (isVanillaAdvId(id)) GuiUtils.sendCommand("adv vanilla enable " + id);
                else { var a = adv(id); if (a != null && a.isHidden()) GuiUtils.sendCommand("adv togglehidden " + id); }
            }
        }));
        overlay.current = Ov.CTX;
    }

    public void requestDelete(String id) {
        var a = adv(id);
        overlay.confirmText = Component.translatable(LangKeys.CONFIRM_DELETE_PREFIX, a != null ? a.getName() : id).getString();
        overlay.confirmAction = () -> GuiUtils.sendCommand("adv delete " + id);
        overlay.current = Ov.CONFIRM;
    }

    public void requestBatchDelete() {
        overlay.confirmText = Component.translatable(LangKeys.CONFIRM_BATCH_DELETE, selection.multiSel.size()).getString();
        overlay.confirmAction = () -> { GuiUtils.sendCommand("adv batchdelete " + String.join(",", selection.multiSel)); selection.clear(); };
        overlay.current = Ov.CONFIRM;
    }

    // ═══════════════ Create / edit ═══════════════
    /**
     * 右键空白画布：显示选项菜单（创建进度/创建图片）
     */
    public void showCanvasCtx(double mx, double my) {
        overlay.ctxX = (int) mx; overlay.ctxY = (int) my; overlay.ctxActions.clear();
        overlay.ctxActions.add(new OverlayState.CtxAct(
                Component.translatable(LangKeys.CREATE_TITLE).getString(),
                () -> openCreateAt(mx, my)));
        overlay.ctxActions.add(new OverlayState.CtxAct(
                Component.translatable(LangKeys.CREATE_IMAGE).getString(),
                () -> openImageCreator(mx, my)));
        overlay.current = Ov.CTX;
    }

    /**
     * 在画布指定位置创建图片元素
     */
    private void openImageCreator(double mx, double my) {
        List<String> files = ImageManager.listImageFiles();
        if (files.isEmpty()) {
            overlay.confirmText = Component.translatable(LangKeys.IMAGE_NO_FILES).getString();
            overlay.confirmAction = null;
            overlay.current = Ov.CONFIRM;
            return;
        }
        List<ListSelector.Entry> entries = new ArrayList<>();
        for (String f : files) entries.add(new ListSelector.Entry(f, f));
        showSelector(entries, e -> placeImage(e.id(), canvas.toWorldX(mx), canvas.toWorldY(my)));
    }

    private void placeImage(String filename, int worldX, int worldY) {
        String imgId = "img_" + UUID.randomUUID().toString().substring(0, 8);
        ImageElement img = new ImageElement(imgId, filename, worldX, worldY);

        ResourceLocation texId = ImageManager.loadTexture(imgId, filename);
        if (texId != null) {
            img.setTextureId(texId);
            int[] size = ImageManager.getTextureSize(imgId);
            img.setOriginalSize(size[0], size[1]);
            imageElements.add(img);
            ImageManager.save(imageElements);
        } else {
            // 图片加载失败，弹出错误提示，不创建元素
            String error = ImageManager.getLastError();
            overlay.confirmText = Component.translatable(LangKeys.IMAGE_LOAD_FAIL,
                    error != null ? error : "未知错误").getString();
            overlay.confirmAction = null;
            overlay.current = Ov.CONFIRM;
        }
    }

    public void showImageCtx(double mx, double my, String imageId) {
        overlay.ctxX = (int) mx; overlay.ctxY = (int) my; overlay.ctxActions.clear();
        ImageElement img = findImageById(imageId);
        if (img == null) return;

        overlay.ctxActions.add(new OverlayState.CtxAct(
                Component.translatable(LangKeys.IMAGE_SCALE_UP).getString(),
                () -> { img.setScale(img.getScale() * 1.25f); ImageManager.save(imageElements); }));
        overlay.ctxActions.add(new OverlayState.CtxAct(
                Component.translatable(LangKeys.IMAGE_SCALE_DOWN).getString(),
                () -> { img.setScale(img.getScale() * 0.8f); ImageManager.save(imageElements); }));
        if (img.getScale() != 1.0f) {
            overlay.ctxActions.add(new OverlayState.CtxAct(
                    Component.translatable(LangKeys.IMAGE_SCALE_RESET).getString(),
                    () -> { img.setScale(1.0f); ImageManager.save(imageElements); }));
        }
        overlay.ctxActions.add(new OverlayState.CtxAct(
                Component.translatable(img.isLocked() ? LangKeys.IMAGE_UNLOCK : LangKeys.IMAGE_LOCK).getString(),
                () -> { img.setLocked(!img.isLocked()); ImageManager.save(imageElements); }));

        // 层级调整
        int idx = imageElements.indexOf(img);
        if (idx < imageElements.size() - 1) {
            overlay.ctxActions.add(new OverlayState.CtxAct(
                    Component.translatable(LangKeys.IMAGE_TO_FRONT).getString(),
                    () -> { imageElements.remove(img); imageElements.add(img); ImageManager.save(imageElements); }));
        }
        if (idx > 0) {
            overlay.ctxActions.add(new OverlayState.CtxAct(
                    Component.translatable(LangKeys.IMAGE_TO_BACK).getString(),
                    () -> { imageElements.remove(img); imageElements.add(0, img); ImageManager.save(imageElements); }));
        }

        overlay.ctxActions.add(new OverlayState.CtxAct(
                Component.translatable(LangKeys.IMAGE_DELETE).getString(),
                () -> requestImageDelete(imageId)));
        overlay.current = Ov.CTX;
    }

    /** 图片删除确认对话框 */
    private void requestImageDelete(String imageId) {
        ImageElement img = findImageById(imageId);
        if (img == null) return;
        overlay.confirmText = Component.translatable(LangKeys.CONFIRM_IMAGE_DELETE, img.getPath()).getString();
        overlay.confirmAction = () -> {
            imageElements.removeIf(i -> i.getId().equals(imageId));
            if (imageId.equals(selectedImageId)) selectedImageId = null;
            ImageManager.save(imageElements);
        };
        overlay.current = Ov.CONFIRM;
    }

    /** 非编辑模式下右键卡片：仅显示"详情"选项 */
    public void showViewCtx(double mx, double my, String id) {
        overlay.ctxX = (int) mx; overlay.ctxY = (int) my; overlay.ctxActions.clear();
        overlay.ctxActions.add(new OverlayState.CtxAct(
                Component.translatable(LangKeys.DETAIL_TITLE).getString(),
                () -> openDetail(id)));
        overlay.current = Ov.CTX;
    }

    private void openDetail(String id) {
        selection.select(id);
        overlay.detailId = id;
        overlay.detailOpenTime = System.currentTimeMillis();
        overlay.current = Ov.DETAIL;
    }

    public ImageElement findImageById(String id) {
        for (ImageElement img : imageElements) if (img.getId().equals(id)) return img;
        return null;
    }

    public ImageElement imageAt(double mx, double my) {
        for (int i = imageElements.size() - 1; i >= 0; i--) {
            ImageElement img = imageElements.get(i);
            if (img.getTextureId() == null) continue;
            int sx = canvas.toScreenX(img.getX());
            int sy = canvas.toScreenY(img.getY());
            int sw = (int) (img.getRenderWidth() * canvas.zoom);
            int sh = (int) (img.getRenderHeight() * canvas.zoom);
            if (mx >= sx && mx < sx + sw && my >= sy && my < sy + sh) return img;
        }
        return null;
    }
    /**
     * 打开分类管理面板
     */
    public void openTabManage() {
        overlay.current = Ov.TAB_MANAGE;
    }

    /**
     * 级联删除标签：删除标签下所有自定义成就，原版成就回到原版标签并禁用
     */
    public void cascadeDeleteTab(String tabName) {
        ClientDataStore cs = ClientDataStore.getInstance();

        // 1. 删除该标签下的自定义成就
        List<String> customToDelete = new ArrayList<>();
        for (var adv : cs.getAdvancements().values()) {
            if (tabName.equals(adv.getTab())) customToDelete.add(adv.getId());
        }
        if (!customToDelete.isEmpty()) {
            GuiUtils.sendCommand("adv batchdelete " + String.join(",", customToDelete));
        }

        // 2. 清除原版/模组成就的标签分配并禁用
        for (var va : vanillaAdvs) {
            VanillaAdvMeta meta = cs.getVanillaMeta(va.id());
            if (meta != null && tabName.equals(meta.getTab())) {
                GuiUtils.sendCommand("adv vanilla cleartab " + va.id());
                GuiUtils.sendCommand("adv vanilla disable " + va.id());
            }
        }

        // 3. 删除标签
        GuiUtils.sendCommand("adv tab delete " + tabName);
    }
    public void openCreateAt(double mx, double my) {
        editPanel.openCreate(font);
        editPanel.setCreatePos(canvas.toWorldX(mx), canvas.toWorldY(my));
        if (curTab != null && !DataStore.TAB_VANILLA.equals(curTab) && !"hidden".equals(curTab)) editPanel.setEdTab(curTab);
        overlay.current = Ov.CREATE;
        setFocused(editPanel.getNameBox());
    }

    public void openEdit(String id) {
        var a = adv(id);
        if (a == null) return;
        editPanel.openEdit(font, a);
        overlay.current = Ov.EDIT;
    }

    // ═══════════════ Delegation methods ═══════════════

    public void openTabSel() { tabManager.openTabSel(); }
    public void openPrereqSel(String forId) { tabManager.openPrereqSel(forId); }
    public void closeTabInput() { tabManager.closeTabInput(); }

    // ═══════════════ Close ═══════════════

    @Override
    public void onClose() {
        // 关闭前保存编辑面板中未保存的修改内容
        if (editPanel != null && editPanel.isVisible()) {
            editPanel.saveIfVisible();
        }
        persistEdit = editMode;
        PERSIST.scrollX = canvas.scrollX;
        PERSIST.scrollY = canvas.scrollY;
        PERSIST.zoom = canvas.zoom;
        tabDrag.reset();
        drag.reset();
        journalScrollOff = 0;
        canvasManager.resetScrollDrag();
        editPanel.closeCondSel();
        markFilteredDirty();

        // 清理图片纹理缓存，防止 DynamicTexture 泄漏
        ImageManager.clearCache();

        if (this.minecraft != null) this.minecraft.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}