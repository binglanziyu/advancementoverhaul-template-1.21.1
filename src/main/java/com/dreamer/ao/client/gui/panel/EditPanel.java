package com.dreamer.ao.client.gui.panel;

/**
 * 成就编辑面板：创建/编辑自定义进度的完整表单界面。
 * <p>
 * 管理成就的所有可编辑字段：名称、描述、图标、隐藏状态、X/Y 坐标、
 * 标签页分类、前置条件和条件列表。
 * 表单数据序列化后通过 C2S 命令发送到服务端保存。
 * 渲染逻辑分离到 {@link EditPanelRenderer}，序列化逻辑分离到 {@link EditPanelSerializer}。
 */
import com.dreamer.ao.client.gui.AdvancementScreen;
import com.dreamer.ao.client.gui.GuiUtils;
import com.dreamer.ao.client.gui.layout.LayoutMetrics;
import com.dreamer.ao.client.gui.widget.ScrollBar;

import static com.dreamer.ao.client.gui.Theme.*;
import com.dreamer.ao.data.ClientDataStore;
import com.dreamer.ao.data.ConditionType;
import com.dreamer.ao.data.DataStore;
import com.dreamer.ao.data.model.AdvancementCondition;
import com.dreamer.ao.data.model.CustomAdvancement;
import com.dreamer.ao.data.model.VanillaAdvMeta;
import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.util.*;

public class EditPanel {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int MAX_COUNT = 999;

    // ── 状态 ──
    boolean visible;
    String edId;
    String edName, edDesc, edTab, edIcon, edLore;
    int edX, edY;
    boolean edHidden;
    List<String> edPrereqs;
    List<AdvancementCondition> edConds;

    boolean nameActive = false;
    boolean descActive = false;
    boolean loreActive = false;
    int inlineCondIdx = -1;
    boolean inlineEditingCount = false;
    final ScrollBar condScrollBar = new ScrollBar(6, 0xFF222238, 0xFF6666BB);

    final ConditionSelector condSelector = new ConditionSelector();

    int panelX, panelY, panelW, panelH, descFieldW;
    int condListStartY, condListVisibleH;

    /**
     * 一次性计算面板实际边界并写入字段，渲染与鼠标命中检测同源读取。
     * 依据 {@link LayoutMetrics#computePanel} 的夹取规则，避免在两侧
     * 分别重算导致公式漂移、点击错位。
     */
    void updateLayout(int screenW, int screenH) {
        var b = LayoutMetrics.computePanel(screenW, screenH);
        panelX = b.x();
        panelY = b.y();
        panelW = b.w();
        panelH = b.h();
        descFieldW = panelW - LayoutMetrics.DESC_AREA_X - 14;
    }

    AdvancementScreen screen;
    EditBox nameBox, descBox, loreBox, condCountBox;
    Font font;
    boolean vanillaEditMode = false;

    private static final int MAX_PREREQS = 10;
    boolean prereqDropdownOpen = false;

    private final EditPanelRenderer renderer = new EditPanelRenderer(this);
    private final EditPanelSerializer serializer = new EditPanelSerializer(this);

    public EditPanel() {
        condSelector.setOnAdd(cond -> edConds.add(cond));
        condSelector.setOnRemove(cond -> edConds.remove(cond));
    }

    public void init(Font font) { this.font = font; }
    public void setScreen(AdvancementScreen screen) { this.screen = screen; }
    public EditBox[] getWidgets() { return new EditBox[]{ nameBox, descBox, loreBox, condCountBox }; }

    // ═══════════════ 打开/关闭 ═══════════════

    public void openCreate(Font font) {
        this.font = font;
        vanillaEditMode = false;
        edId = null; edName = ""; edDesc = ""; edLore = "";
        edTab = DataStore.TAB_DEFAULT; edIcon = null;
        edX = 0; edY = 0; edHidden = false;
        edPrereqs = new ArrayList<>(); edConds = new ArrayList<>();
        nameActive = false; descActive = false; loreActive = false;
        inlineCondIdx = -1; inlineEditingCount = false; condScrollBar.setScroll(0);
        condSelector.close();
        createEditBoxes();
        if (screen != null) for (EditBox eb : getWidgets()) screen.addWidgetToScreen(eb);
        visible = true;
    }

    public void openEdit(Font font, CustomAdvancement adv) {
        this.font = font;
        vanillaEditMode = false;
        edId = adv.getId();
        edName = adv.getName() != null ? adv.getName() : "";
        edDesc = adv.getDescription() != null ? adv.getDescription() : "";
        edLore = adv.getLore() != null ? adv.getLore() : "";
        edTab = adv.getTab(); edIcon = adv.getIcon();
        edX = adv.getX(); edY = adv.getY(); edHidden = adv.isHidden();
        edPrereqs = new ArrayList<>(adv.getPrerequisites() != null ? adv.getPrerequisites() : List.of());
        edConds = new ArrayList<>();
        if (adv.getConditions() != null) for (var c : adv.getConditions()) edConds.add(c.deepCopy());
        if (edTab == null || edTab.isEmpty()) edTab = DataStore.TAB_DEFAULT;
        nameActive = false; descActive = false; loreActive = false;
        inlineCondIdx = -1; inlineEditingCount = false; condScrollBar.setScroll(0);
        condSelector.close();
        createEditBoxes();
        if (screen != null) for (EditBox eb : getWidgets()) screen.addWidgetToScreen(eb);
        visible = true;
    }

    /**
     * 打开原版/模组成就编辑面板（名称/描述锁定，仅可编辑标签和前置条件）
     */
    public void openVanillaEdit(Font font, String vanillaId, String displayName, String displayDesc) {
        this.font = font;
        edId = vanillaId;
        edName = displayName != null ? displayName : vanillaId;
        edDesc = displayDesc != null ? displayDesc : "";
        edLore = "";
        edIcon = null;
        edHidden = false;
        edX = 0; edY = 0;
        vanillaEditMode = true;

        // 从元数据加载当前标签分配
        ClientDataStore cs = ClientDataStore.getInstance();
        VanillaAdvMeta meta = cs.getVanillaMeta(vanillaId);
        edTab = (meta != null && meta.getTab() != null && !meta.getTab().isEmpty()) ? meta.getTab() : DataStore.TAB_VANILLA;

        // 加载已保存的前置条件（修复：之前总是 new ArrayList<>()，导致二次编辑时丢失已有前置条件）
        edPrereqs = (meta != null && meta.getPrerequisites() != null)
                ? new ArrayList<>(meta.getPrerequisites())
                : new ArrayList<>();
        edConds = new ArrayList<>();

        nameActive = false; descActive = false; loreActive = false;
        inlineCondIdx = -1; inlineEditingCount = false; condScrollBar.setScroll(0);
        prereqDropdownOpen = false;
        condSelector.close();
        createEditBoxes();
        if (screen != null) for (EditBox eb : getWidgets()) screen.addWidgetToScreen(eb);
        visible = true;
    }

    private void createEditBoxes() {
        nameBox = new EditBox(font, 0, 0, 150, 20, Component.empty());
        nameBox.setMaxLength(256); nameBox.setValue(edName != null ? edName : ""); nameBox.setTextColor(TEXT_BR);
        descBox = new EditBox(font, 0, 0, 150, 20, Component.empty());
        descBox.setMaxLength(1024); descBox.setValue(edDesc != null ? edDesc : ""); descBox.setTextColor(TEXT_BR);
        loreBox = new EditBox(font, 0, 0, 200, 20, Component.empty());
        loreBox.setMaxLength(2048); loreBox.setValue(edLore != null ? edLore : ""); loreBox.setTextColor(TEXT_BR);
        condCountBox = new EditBox(font, 0, 0, LayoutMetrics.COND_CNT_W, 18, Component.empty());
        condCountBox.setMaxLength(3); condCountBox.setValue("1"); condCountBox.setTextColor(ACCENT);
        condCountBox.setBordered(false);
        condCountBox.setVisible(false);
    }

    public void close() {
        visible = false; nameActive = false; descActive = false; loreActive = false;
        inlineCondIdx = -1; inlineEditingCount = false;
        prereqDropdownOpen = false;
        condSelector.close();
        if (screen != null) for (EditBox eb : getWidgets()) screen.removeWidgetFromScreen(eb);
    }

    // ═══════════════ 查询 ═══════════════

    public boolean isVisible() { return visible; }
    public String getEdId() { return edId; }
    public List<String> getEdPrereqs() { return edPrereqs; }
    public EditBox getNameBox() { return nameBox; }
    public EditBox getInlineCountBox() { return condCountBox; }
    public boolean isCondSelActive() { return condSelector.isActive(); }
    public void closeCondSel() { condSelector.close(); }
    public void condSelCharTyped(char chr) { condSelector.handleChar(chr); }
    public void condSelKeyPressed(int kc) { condSelector.handleKey(kc); }

    public void saveIfVisible() { if (visible) serializer.saveEd(); }
    public void setCreatePos(int worldX, int worldY) { edX = worldX; edY = worldY; }
    public void setEdTab(String tab) { edTab = (tab != null && !tab.isEmpty()) ? tab : DataStore.TAB_DEFAULT; }

    public void addPrereq(String id) { if (id != null && !edPrereqs.contains(id) && edPrereqs.size() < MAX_PREREQS) edPrereqs.add(id); }
    public void removePrereq(int idx) { if (idx >= 0 && idx < edPrereqs.size()) edPrereqs.remove(idx); }

    public void updateVisibility(boolean editorVisible) {
        if (nameBox != null) nameBox.setVisible(nameActive && editorVisible);
        if (descBox != null) descBox.setVisible(descActive && editorVisible);
        if (loreBox != null) loreBox.setVisible(loreActive && editorVisible);
    }

    public EditBox getLastFocusedWidget() {
        if (nameBox != null && nameBox.isFocused()) return nameBox;
        if (descBox != null && descBox.isFocused()) return descBox;
        if (loreBox != null && loreBox.isFocused()) return loreBox;
        if (condCountBox != null && condCountBox.isFocused()) return condCountBox;
        return null;
    }

    // ═══════════════ RENDER 兼容接口 ═══════════════

    public void render(GuiGraphics g, int mx, int my, Font font, int sw, int sh) {
        renderer.render(g, mx, my, font, sw, sh);
    }

    public void renderWidgets(GuiGraphics g, int mx, int my, float pt) {
        renderer.renderWidgets(g, mx, my, pt);
    }

    public boolean condSelClick(double mx, double my, Font font, int sw, int sh) {
        if (!condSelector.isActive()) return false;
        return condSelector.handleClick(mx, my, font, sw, sh);
    }

    // ═══════════════ 鼠标交互 ═══════════════

    public boolean handleClick(double mx, double my, int screenW, int screenH) {
        if (!visible || screen == null) return false;
        if (condSelector.isActive()) { condSelector.handleClick(mx, my, font, screenW, screenH); return true; }
        if (inlineEditingCount) commitInlineCountEdit();
        commitNameAndDesc();
        updateLayout(screenW, screenH);
        int px = panelX, py = panelY, pw = panelW, ph = panelH;

        if (GuiUtils.closeHit(mx, my, px, py, pw)) { close(); return true; }
        if (GuiUtils.outsidePanel(mx, my, px, py, pw, ph)) return false;

        int ty = py + 28;

        // ── Row 1: 名称/描述（vanillaEditMode 下不可编辑） ──
        if (!vanillaEditMode && GuiUtils.inRect(mx, my, px + LayoutMetrics.NAME_AREA_X, ty, LayoutMetrics.NAME_FIELD_W, 20)) { activateName(); return true; }
        if (!vanillaEditMode && GuiUtils.inRect(mx, my, px + LayoutMetrics.DESC_AREA_X, ty, descFieldW, 20)) { activateDesc(); return true; }
        commitNameAndDesc();
        ty += 28;

        // ── Row 2 ──
        if (vanillaEditMode) {
            int tabW = pw - LayoutMetrics.PREREQ_BTN_W - 14 - LayoutMetrics.BTN_GAP - 14;
            boolean clickedTab = GuiUtils.inRect(mx, my, px + 14, ty, tabW, 20);
            if (clickedTab) { if (screen != null) screen.openTabSel(); return true; }
            int pBtnX = px + 14 + tabW + LayoutMetrics.BTN_GAP;
            boolean clickedPrereq = GuiUtils.inRect(mx, my, pBtnX, ty, LayoutMetrics.PREREQ_BTN_W, 20);
            if (clickedPrereq) { handlePrereqBtnClick(); return true; }
        } else {
            if (GuiUtils.inRect(mx, my, px + 14, ty, LayoutMetrics.CAT_BTN_W, 20)) { if (screen != null) screen.openTabSel(); return true; }
            if (GuiUtils.inRect(mx, my, px + LayoutMetrics.HIDDEN_BTN_X, ty, LayoutMetrics.HIDDEN_BTN_W, 20)) { edHidden = !edHidden; return true; }
            if (GuiUtils.inRect(mx, my, px + LayoutMetrics.ICON_BTN_X, ty, LayoutMetrics.ICON_BTN_W, 20)) { openIconPicker(); return true; }
            int pBtnX = px + LayoutMetrics.PREREQ_BTN_X_NORMAL;
            int pBtnW = px + pw - pBtnX - 14;
            if (GuiUtils.inRect(mx, my, pBtnX, ty, pBtnW, 20)) { handlePrereqBtnClick(); return true; }
        }

        // 前置条件下拉面板交互
        if (prereqDropdownOpen) {
            int ddY = ty + 22;
            int totalRows = 1 + edPrereqs.size();
            int ddH = totalRows * 20 + 4;
            if (GuiUtils.inRect(mx, my, px + 14, ddY, pw - 28, ddH)) {
                handlePrereqDropdownClick(mx, my, ty, pw, px);
            } else {
                prereqDropdownOpen = false;
            }
            return true;
        }
        ty += 26;

        // Row 2.5: lore text
        if (!vanillaEditMode && GuiUtils.inRect(mx, my, px + 14, ty, pw - 28, 20)) {
            activateLore(); return true;
        }
        ty += 24;

        // ── 条件 "+" 按钮（vanillaEditMode 下禁用） ──
        if (!vanillaEditMode && GuiUtils.inRect(mx, my, px + pw - 34, ty + 6, 20, 18)) {
            condSelector.setExistingConditions(edConds);
            condSelector.open(ConditionType.KILL_ENTITY);
            return true;
        }

        // ── 条件滚动条点击 ──
        if (condScrollBar.needsScrollbar()) {
            int sbX = px + pw - 8;
            if (condScrollBar.handleClick(mx, my, sbX, condListStartY)) return true;
        }

        // ── 条件列表（vanillaEditMode 下禁用数量编辑和删除） ──
        if (mx >= px + 14 && mx < px + pw - 14 && my >= condListStartY && my < condListStartY + condListVisibleH) {
            int idx = (int) ((my - condListStartY + condScrollBar.getScroll()) / LayoutMetrics.COND_ROW_H);
            if (idx >= 0 && idx < edConds.size()) {
                int rowY = condListStartY + idx * LayoutMetrics.COND_ROW_H - condScrollBar.getScroll();
                int delX = px + pw - LayoutMetrics.COND_DEL_W - 6;
                int cntX = delX - LayoutMetrics.COND_CNT_W - 4;
                if (!vanillaEditMode && GuiUtils.inRect(mx, my, cntX, rowY, LayoutMetrics.COND_CNT_W, LayoutMetrics.COND_ROW_H)) { startInlineCountEdit(idx); return true; }
                if (!vanillaEditMode && GuiUtils.inRect(mx, my, delX, rowY, LayoutMetrics.COND_DEL_W, LayoutMetrics.COND_ROW_H)) { edConds.remove(idx); return true; }
            }
            return true;
        }

        // ── 保存/取消 ──
        int btnY = py + ph - 32;
        if (GuiUtils.inRect(mx, my, px + pw - 180, btnY, 80, 20)) { serializer.saveEd(); return true; }
        if (GuiUtils.inRect(mx, my, px + pw - 90, btnY, 80, 20)) { close(); return true; }

        return true;
    }

    /**
     * 点击前置条件按钮：
     * - 无前置条件时：直接打开选择器
     * - 已有前置条件时：展开/收起下拉面板
     */
    private void handlePrereqBtnClick() {
        if (edPrereqs.isEmpty()) {
            if (screen != null) screen.openPrereqSel(null);
        } else {
            prereqDropdownOpen = !prereqDropdownOpen;
        }
    }

    /**
     * 前置条件下拉面板的点击处理：
     * - 首行"添加新条件" → 打开选择器
     * - 其余行 × 按钮 → 删除对应前置条件
     */
    private void handlePrereqDropdownClick(double mx, double my, int ty, int pw, int px) {
        if (!prereqDropdownOpen || screen == null) return;
        int ddX = px + 14;
        int ddY = ty + 22;
        int ddW = pw - 28;
        int rowH = 20;

        // 首行："添加新条件"
        int addRowY = ddY + 2;
        if (GuiUtils.inRect(mx, my, ddX, addRowY, ddW, rowH)) {
            screen.openPrereqSel(null);
            return;
        }

        // 后续行：已添加的前置条件
        for (int i = 0; i < edPrereqs.size(); i++) {
            int rowY = ddY + 2 + (i + 1) * rowH;
            if (GuiUtils.inRect(mx, my, ddX + 2, rowY, ddW - 4, rowH - 2)) {
                // 点击 × 删除按钮（右侧 16px 区域）
                if (mx >= ddX + ddW - 18) {
                    removePrereq(i);
                    if (edPrereqs.isEmpty()) prereqDropdownOpen = false;
                    return;
                }
                return;
            }
        }
    }

    public boolean handleScroll(double mx, double my, double sy, int sw, int sh) {
        if (!visible) return false;
        if (condSelector.isActive()) { condSelector.handleScroll(mx, my, sy, sw, sh); return true; }
        if (my >= condListStartY && my < condListStartY + condListVisibleH) {
            return condScrollBar.handleScroll(sy);
        }
        return false;
    }

    public void mouseReleased(int screenW, int screenH) {
        if (!visible) return;
        condScrollBar.handleRelease();
    }

    public void mouseDragged(double my, int screenW, int screenH) {
        if (!visible) return;
        updateLayout(screenW, screenH);
        condScrollBar.handleDrag(my);
    }

    // ═══════════════ 名称/描述编辑 ═══════════════

    private void activateName() {
        nameActive = true; descActive = false; loreActive = false;
        if (nameBox != null) { nameBox.setVisible(true); nameBox.setFocused(true); if (screen != null) screen.setFocused(nameBox); }
    }

    private void activateDesc() {
        descActive = true; nameActive = false; loreActive = false;
        if (descBox != null) { descBox.setVisible(true); descBox.setFocused(true); if (screen != null) screen.setFocused(descBox); }
    }

    private void activateLore() {
        loreActive = true; nameActive = false; descActive = false;
        if (loreBox != null) { loreBox.setVisible(true); loreBox.setFocused(true); if (screen != null) screen.setFocused(loreBox); }
    }

    void commitNameAndDesc() {
        if (nameActive && nameBox != null) { edName = nameBox.getValue().trim(); nameActive = false; nameBox.setFocused(false); nameBox.setVisible(false); }
        if (descActive && descBox != null) { edDesc = descBox.getValue().trim(); descActive = false; descBox.setFocused(false); descBox.setVisible(false); }
        if (loreActive && loreBox != null) { edLore = loreBox.getValue().trim(); loreActive = false; loreBox.setFocused(false); loreBox.setVisible(false); }
    }

    // ═══════════════ 内联数量 ═══════════════

    public void startInlineCountEdit(int condIdx) {
        if (condIdx < 0 || condIdx >= edConds.size()) return;
        inlineCondIdx = condIdx; inlineEditingCount = true;
        condCountBox.setValue(String.valueOf(edConds.get(condIdx).getCount()));
        condCountBox.setFocused(true);
        if (screen != null) screen.setFocused(condCountBox);
    }

    void commitInlineCountEdit() {
        if (!inlineEditingCount || inlineCondIdx < 0 || inlineCondIdx >= edConds.size()) return;
        try { int v = Integer.parseInt(condCountBox.getValue().trim()); edConds.get(inlineCondIdx).setCount(Math.clamp(v, 1, MAX_COUNT)); } catch (NumberFormatException e) { LOGGER.debug("Invalid inline count value: {}", condCountBox.getValue()); }
        inlineEditingCount = false; inlineCondIdx = -1;
        if (condCountBox != null) { condCountBox.setFocused(false); condCountBox.setVisible(false); }
    }

    public boolean handleInlineCountKey(int kc) {
        if (!inlineEditingCount) return false;
        if (kc == GuiUtils.KEY_ENTER || kc == GuiUtils.KEY_ESCAPE) { commitInlineCountEdit(); return true; }
        return false;
    }

    // ═══════════════ 图标选择器 ═══════════════

    private void openIconPicker() {
        if (screen == null) return;
        IconPickerHelper.open(screen, icon -> edIcon = icon);
    }
}
