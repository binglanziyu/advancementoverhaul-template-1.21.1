package com.dreamer.ao.client.gui;


import com.dreamer.ao.AdvancementOverhaul;
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
import com.dreamer.ao.client.gui.render.ScreenRenderCoordinator;
import com.dreamer.ao.client.gui.render.TabRenderer;
import com.dreamer.ao.client.gui.state.*;
import com.dreamer.ao.client.gui.state.OverlayState.CtxAct;
import com.dreamer.ao.client.gui.state.OverlayType;
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
    public final TabManageDragState tabManageDrag = new TabManageDragState();
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
    // 这些集合是本 screen 与其同模块子包（render / manager）协作的 UI 数据源，
    // 由同包的 VanillaLayoutEngine 负责写入，子包仅做只读遍历/查询，
    // 因此保持 public final，访问约束交由包内协作约定。

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

    // ═══════════════ Components ═══════════════

    public final CardRenderer cardRenderer = new CardRenderer(this);
    public final TabRenderer tabRenderer = new TabRenderer(this);
    public final OverlayRenderer overlayRenderer = new OverlayRenderer(this);
    public final ScreenRenderCoordinator renderCoordinator = new ScreenRenderCoordinator(this);
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
    public boolean hasOv() { return overlay.current != OverlayType.NONE || showDim || showSel || showHelp || tabDrag.overDDOpen; }
    /** 冒险日志滚动偏移 */
    public int journalScrollOff = 0;
    public boolean blocksCanvas() {
        return showDim || showSel || showHelp
                || overlay.current == OverlayType.DETAIL || overlay.current == OverlayType.CREATE
                || overlay.current == OverlayType.EDIT || overlay.current == OverlayType.CONFIRM
                || overlay.current == OverlayType.TAB_INPUT || overlay.current == OverlayType.TAB_MANAGE
                || overlay.current == OverlayType.JOURNAL
                || overlay.current == OverlayType.CTX;
    }
    public int mid(int w) { return (width - w) / 2; }
    public int midY(int h) { return Math.max(20, (height - h) / 2); }
    public int getScreenWidth() { return width; }
    public int getScreenHeight() { return height; }

    /** 暴露画布交互管理器给渲染协调器（位于子包 render）。 */
    public CanvasManager getCanvasManager() { return canvasManager; }
    /** 暴露可渲染组件列表给渲染协调器（位于子包 render）。 */
    public Iterable<net.minecraft.client.gui.components.Renderable> getRenderablesView() { return renderables; }
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
        if (overlay.current != OverlayType.NONE) overlay.close();
        showDim = false; showSel = false; showHelp = false;
        screenState.reset();
        tabDrag.reset();
        tabManageDrag.reset();
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

        // 客户端初始化曾失败时提示玩家，避免功能不完整却无任何反馈
        if (AdvancementOverhaul.isClientInitDegraded() && !degradedNoticeShown) {
            degradedNoticeShown = true;
            addToast(Component.translatable(LangKeys.CLIENT_INIT_DEGRADED).getString());
        }
    }

    /** 降级提示每次游戏会话只显示一次，避免每次开启界面都打扰玩家。 */
    private static boolean degradedNoticeShown = false;

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
        renderCoordinator.render(g, mx, my, pt);
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
        overlay.current = OverlayType.CREATE;
        setFocused(editPanel.getNameBox());
    }

    public void openEdit(String id) {
        var a = adv(id);
        if (a == null) return;
        editPanel.openEdit(font, a);
        overlay.current = OverlayType.EDIT;
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
        tabManageDrag.reset();
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