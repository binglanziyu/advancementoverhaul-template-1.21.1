package com.dreamer.ao.client.gui;


import com.dreamer.ao.LangKeys;
import com.dreamer.ao.client.gui.cache.CircleCache;
import com.dreamer.ao.client.gui.cache.RegistryCache;
import com.dreamer.ao.client.gui.cache.RoundedRectCache;
import com.dreamer.ao.client.gui.manager.CanvasManager;
import com.dreamer.ao.client.gui.manager.InputManager;
import com.dreamer.ao.client.gui.manager.TabManager;
import com.dreamer.ao.client.gui.panel.DimensionPanel;
import com.dreamer.ao.client.gui.panel.EditPanel;
import com.dreamer.ao.client.gui.panel.ListSelector;
import com.dreamer.ao.client.gui.render.CardRenderer;
import com.dreamer.ao.client.gui.render.OverlayRenderer;
import com.dreamer.ao.client.gui.render.TabRenderer;
import com.dreamer.ao.client.gui.state.*;
import com.dreamer.ao.client.gui.state.OverlayState.CtxAct;
import com.dreamer.ao.client.gui.state.OverlayState.Ov;
import com.dreamer.ao.data.ClientDataStore;
import com.dreamer.ao.data.DataStore;
import com.dreamer.ao.data.model.CustomAdvancement;
import com.dreamer.ao.data.model.VanillaAdvMeta;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.function.Consumer;

import static com.dreamer.ao.client.gui.Theme.*;

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

    /** 统一状态容器：面板可见性 + 脏标记位图 */
    public final ScreenState screenState = new ScreenState();

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
    /** Cached snapshot of the current filtered advancement list for this frame. */
    public List<CustomAdvancement> frameFiltered = Collections.emptyList();

    /** Mark the filtered cache as needing refresh (called on tab switch or data update). */
    public void markFilteredDirty() { screenState.markDirty(ScreenState.DIRTY_FILTERED); }

    /** @deprecated use {@link #screenState}{@code .isDirty(ScreenState.DIRTY_VANILLA_POS)} + {@link ScreenState#markDirty(int)} */
    @Deprecated
    public boolean vanillaPositionsDirty() { return screenState.isDirty(ScreenState.DIRTY_VANILLA_POS); }

    /** @deprecated use {@link #screenState}{@code .markDirty(ScreenState.DIRTY_VANILLA_POS)} */
    @Deprecated
    public void setVanillaPositionsDirty(boolean v) {
        if (v) screenState.markDirty(ScreenState.DIRTY_VANILLA_POS);
        else screenState.clearDirty(ScreenState.DIRTY_VANILLA_POS);
    }

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
    final VanillaLayoutEngine vanillaEngine = new VanillaLayoutEngine(this);
    final ContextMenuHandler ctxMenu = new ContextMenuHandler(this);

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

    public boolean shouldShowVanilla(String id) {
        ClientDataStore cs = ClientDataStore.getInstance();
        if (cs.isVanillaEnabled(id)) return true;
        return DataStore.TAB_VANILLA.equals(curTab);
    }

    /**
     * 统一的卡片可见性判断。
     * <p>
     * 替代各处重复的 {@code isHidden() && !hiddenTab && !editMode && !completed} 模式。
     * CardRenderer、CanvasManager、ConnectionRenderer 均通过此方法统一判断。
     */
    public boolean isCardVisible(CustomAdvancement a) {
        return !a.isHidden()
                || "hidden".equals(curTab)
                || editMode
                || ClientDataStore.getInstance().isCompleted(a.getId());
    }

    // ═══════════════ INIT ═══════════════

    @Override
    protected void init() {
        if (editPanel != null && editPanel.isVisible()) editPanel.close();
        if (overlay.current != Ov.NONE) overlay.close();
        showDim = false; showSel = false; showHelp = false;
        screenState.reset();
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
        screenState.markDirty(ScreenState.DIRTY_VANILLA_POS);
        screenState.markDirty(ScreenState.DIRTY_FILTERED);

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
        vanillaEngine.loadVanillaAdvancements();
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
            vanillaEngine.loadVanillaAdvancements();

        // Recalculate vanilla positions if needed
        if (screenState.isDirty(ScreenState.DIRTY_VANILLA_POS)) vanillaEngine.recalcVanillaPositions();

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
        if (screenState.isDirty(ScreenState.DIRTY_FILTERED) || !filterKey.equals(lastFilterKey)) {
            frameFiltered = new ArrayList<>(filtered());
            lastFilterKey = filterKey;
            screenState.clearDirty(ScreenState.DIRTY_FILTERED);
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
            // 全屏完全不透明遮罩（选择器弹窗需要完全遮挡底层卡片）
            g.fill(0, 0, width, height, 0xFF1A1A2E);
        } else if (hasOv()) {
            if (overlay.current == Ov.CTX) {
                // CTX 浮动小菜单无需遮罩，让用户仍能看到底层画布
                return;
            } else if (overlay.current == Ov.CREATE || overlay.current == Ov.EDIT) {
                // 编辑/创建面板使用半透明遮罩，保持对底层画布的可见性
                g.fill(0, TAB_H, width, height - BOTTOM_H, 0xA01A1A2E);
            } else {
                // DETAIL/CONFIRM/TAB_INPUT 等保持完全不透明遮罩
                g.fill(0, TAB_H, width, height - BOTTOM_H, 0xFF1A1A2E);
            }
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
    @Override public boolean keyPressed(int kc, int sc, int mod) {
        if (inputManager.onKeyPressed(kc, sc, mod)) return true;
        // Tab 键切换编辑模式（仅当无 EditBox 聚焦时）
        if (kc == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB && (mod & 1) == 0
                && getFocused() == null && minecraft != null && minecraft.player != null
                && minecraft.player.hasPermissions(2)) {
            editMode = !editMode;
            persistEdit = editMode;
            addToast(Component.translatable(editMode ? LangKeys.TOGGLE_EDIT_ON : LangKeys.TOGGLE_EDIT_OFF).getString());
            return true;
        }
        return super.keyPressed(kc, sc, mod);
    }

    // ═══════════════ Context menus (delegated to ContextMenuHandler) ═══════════════

    public void showCtx(double mx, double my, String id) { ctxMenu.showCtx(mx, my, id); }
    public void showVanillaCtx(double mx, double my, String id) { ctxMenu.showVanillaCtx(mx, my, id); }
    public void showBatchCtx(double mx, double my) { ctxMenu.showBatchCtx(mx, my); }
    public void requestDelete(String id) { ctxMenu.requestDelete(id); }
    public void requestBatchDelete() { ctxMenu.requestBatchDelete(); }
    public void requestImageDelete(String imageId) { ctxMenu.requestImageDelete(imageId); }

    public void showCanvasCtx(double mx, double my) { ctxMenu.showCanvasCtx(mx, my); }
    public void showImageCtx(double mx, double my, String imageId) { ctxMenu.showImageCtx(mx, my, imageId); }
    public void showViewCtx(double mx, double my, String id) { ctxMenu.showViewCtx(mx, my, id); }

    public ImageElement findImageById(String id) { return ctxMenu.findImageById(id); }
    public ImageElement imageAt(double mx, double my) { return ctxMenu.imageAt(mx, my); }

    public void openTabManage() { ctxMenu.openTabManage(); }
    public void cascadeDeleteTab(String tabName) { ctxMenu.cascadeDeleteTab(tabName); }

    // ═══════════════ Create / edit ═══════════════

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