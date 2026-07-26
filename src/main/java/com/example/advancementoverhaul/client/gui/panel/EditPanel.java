package com.example.advancementoverhaul.client.gui.panel;

import com.example.advancementoverhaul.LangKeys;
import com.example.advancementoverhaul.client.gui.AdvancementScreen;
import com.example.advancementoverhaul.client.gui.GuiUtils;
import com.example.advancementoverhaul.client.gui.TranslatedStrings;
import com.example.advancementoverhaul.client.gui.widget.ScrollBar;
import com.example.advancementoverhaul.data.ClientDataStore;
import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.DisplayNameResolver;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.example.advancementoverhaul.client.gui.ConditionTypeStyle;
import java.util.*;

import static com.example.advancementoverhaul.client.gui.Theme.*;

public class EditPanel {

    private static final int PANEL_W = 380;
    private static final int PANEL_H = 300;
    private static final int COND_ROW_H = 22;
    private static final int MAX_COUNT = 999;

    // ── Row 1 布局 ──
    private static final int NAME_AREA_X = 40;
    private static final int NAME_FIELD_W = 120;
    private static final int DESC_LABEL_X = 168;
    private static final int DESC_AREA_X = 200;

    // ── Row 2 布局 ──
    private static final int CAT_BTN_W = 80;
    private static final int HIDDEN_BTN_X = 98;
    private static final int HIDDEN_BTN_W = 22;
    private static final int ICON_BTN_X = 124;
    private static final int ICON_BTN_W = 62;
    private static final int PREREQ_BTN_X = 190;
    private static final int PREREQ_BTN_W = 22;
    private static final int TAG_START_X = 216;

    // ── 条件行布局 ──
    private static final int COND_TYPE_X = 14;
    private static final int COND_TYPE_W = 72;
    private static final int COND_TGT_X = 90;
    private static final int COND_CNT_W = 36;
    private static final int COND_DEL_W = 24;

    private boolean visible;
    private String edId;
    private String edName, edDesc, edTab, edIcon;
    private int edX, edY;
    private boolean edHidden;
    private List<String> edPrereqs;
    private List<DataStore.AdvancementCondition> edConds;

    private boolean nameActive = false;
    private boolean descActive = false;
    private int inlineCondIdx = -1;
    private boolean inlineEditingCount = false;
    private final ScrollBar condScrollBar = new ScrollBar(6, 0xFF222238, 0xFF6666BB);

    private final ConditionSelector condSelector = new ConditionSelector();

    private int panelX, panelY;
    private int condListStartY, condListVisibleH;

    private AdvancementScreen screen;
    private EditBox nameBox, descBox, condCountBox;
    private Font font;
    private boolean vanillaEditMode = false;

    public EditPanel() {
        condSelector.setOnAdd(cond -> edConds.add(cond));
        condSelector.setOnRemove(cond -> edConds.remove(cond));
    }

    public void init(Font font) { this.font = font; }
    public void setScreen(AdvancementScreen screen) { this.screen = screen; }
    public EditBox[] getWidgets() { return new EditBox[]{ nameBox, descBox, condCountBox }; }

    // ═══════════════ 打开/关闭 ═══════════════

    public void openCreate(Font font) {
        this.font = font;
        vanillaEditMode = false;
        edId = null; edName = ""; edDesc = "";
        edTab = DataStore.TAB_DEFAULT; edIcon = null;
        edX = 0; edY = 0; edHidden = false;
        edPrereqs = new ArrayList<>(); edConds = new ArrayList<>();
        nameActive = false; descActive = false;
        inlineCondIdx = -1; inlineEditingCount = false; condScrollBar.setScroll(0);
        condSelector.close();
        createEditBoxes();
        if (screen != null) for (EditBox eb : getWidgets()) screen.addWidgetToScreen(eb);
        visible = true;
    }

    public void openEdit(Font font, DataStore.CustomAdvancement adv) {
        this.font = font;
        vanillaEditMode = false;
        edId = adv.getId();
        edName = adv.getName() != null ? adv.getName() : "";
        edDesc = adv.getDescription() != null ? adv.getDescription() : "";
        edTab = adv.getTab(); edIcon = adv.getIcon();
        edX = adv.getX(); edY = adv.getY(); edHidden = adv.isHidden();
        edPrereqs = new ArrayList<>(adv.getPrerequisites() != null ? adv.getPrerequisites() : List.of());
        edConds = new ArrayList<>();
        if (adv.getConditions() != null) for (var c : adv.getConditions()) edConds.add(c.deepCopy());
        if (edTab == null || edTab.isEmpty()) edTab = DataStore.TAB_DEFAULT;
        nameActive = false; descActive = false;
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
        edIcon = null;
        edHidden = false;
        edX = 0; edY = 0;
        vanillaEditMode = true;

        // 从元数据加载当前标签分配
        ClientDataStore cs = ClientDataStore.getInstance();
        DataStore.VanillaAdvMeta meta = cs.getVanillaMeta(vanillaId);
        edTab = (meta != null && meta.getTab() != null && !meta.getTab().isEmpty()) ? meta.getTab() : DataStore.TAB_VANILLA;

        edPrereqs = new ArrayList<>();
        edConds = new ArrayList<>();

        nameActive = false; descActive = false;
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
        condCountBox = new EditBox(font, 0, 0, COND_CNT_W, 18, Component.empty());
        condCountBox.setMaxLength(3); condCountBox.setValue("1"); condCountBox.setTextColor(ACCENT);
        condCountBox.setBordered(false);
        condCountBox.setVisible(false);
    }

    public void close() {
        visible = false; nameActive = false; descActive = false;
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

    public void saveIfVisible() { if (visible) saveEd(); }
    public void setCreatePos(int worldX, int worldY) { edX = worldX; edY = worldY; }
    public void setEdTab(String tab) { edTab = (tab != null && !tab.isEmpty()) ? tab : DataStore.TAB_DEFAULT; }
    private static final int MAX_PREREQS = 10;
    private static final int PREREQ_INLINE_MAX = 2;
    private boolean prereqDropdownOpen = false;

    public void addPrereq(String id) { if (id != null && !edPrereqs.contains(id) && edPrereqs.size() < MAX_PREREQS) edPrereqs.add(id); }
    public void removePrereq(int idx) { if (idx >= 0 && idx < edPrereqs.size()) edPrereqs.remove(idx); }

    public void updateVisibility(boolean editorVisible) {
        if (nameBox != null) nameBox.setVisible(nameActive && editorVisible);
        if (descBox != null) descBox.setVisible(descActive && editorVisible);
    }

    public EditBox getLastFocusedWidget() {
        if (nameBox != null && nameBox.isFocused()) return nameBox;
        if (descBox != null && descBox.isFocused()) return descBox;
        if (condCountBox != null && condCountBox.isFocused()) return condCountBox;
        return null;
    }

    // ═══════════════ RENDER 兼容接口 ═══════════════

    public void render(GuiGraphics g, int mx, int my, Font font, int sw, int sh) {
        if (this.font == null) this.font = font;
        if (condSelector.isActive()) condSelector.render(g, this.font, mx, my, sw, sh);
        else renderPanel(g, mx, my, sw, sh);
    }

    public void renderWidgets(GuiGraphics g, int mx, int my, float pt) {
        if (nameBox != null && nameBox.isVisible()) nameBox.render(g, mx, my, pt);
        if (descBox != null && descBox.isVisible()) descBox.render(g, mx, my, pt);
        if (condCountBox != null && condCountBox.isVisible()) condCountBox.render(g, mx, my, pt);
    }

    public boolean condSelClick(double mx, double my, Font font, int sw, int sh) {
        if (!condSelector.isActive()) return false;
        return condSelector.handleClick(mx, my, font, sw, sh);
    }

    // ═══════════════ RENDER 面板 ═══════════════

    private void renderPanel(GuiGraphics g, int mx, int my, int screenW, int screenH) {
        if (!visible || screen == null) return;
        Font font = this.font;

        int pw = Math.min(PANEL_W, screenW - 40);
        int ph = Math.clamp(screenH - 40, 200, PANEL_H);
        panelX = (screenW - pw) / 2;
        panelY = Math.max(20, (screenH - ph) / 2);
        int descFieldW = pw - DESC_AREA_X - 14;

        GuiUtils.drawPanelBg(g, font, panelX, panelY, pw, ph,
                TranslatedStrings.get(edId == null ? LangKeys.CREATE_TITLE : LangKeys.EDIT_TITLE),
                screenW, screenH);

        int ty = panelY + 28;

        // ── Row 1: 名称 + 描述（vanillaEditMode 下锁定） ──
        g.drawString(font, TranslatedStrings.get(LangKeys.NAME), panelX + 14, ty + 6, vanillaEditMode ? TEXT_DIM : TEXT, false);
        if (!vanillaEditMode && nameActive) {
            nameBox.setX(panelX + NAME_AREA_X); nameBox.setY(ty); nameBox.setWidth(NAME_FIELD_W);
            nameBox.setVisible(true);
        } else {
            nameBox.setVisible(false);
            drawFieldArea(g, panelX + NAME_AREA_X, ty, NAME_FIELD_W, 20, edName, TranslatedStrings.get(LangKeys.NAME_PLACEHOLDER));
        }

        g.drawString(font, TranslatedStrings.get(LangKeys.DESC), panelX + DESC_LABEL_X, ty + 6, vanillaEditMode ? TEXT_DIM : TEXT, false);
        if (!vanillaEditMode && descActive) {
            descBox.setX(panelX + DESC_AREA_X); descBox.setY(ty); descBox.setWidth(descFieldW);
            descBox.setVisible(true);
        } else {
            descBox.setVisible(false);
            drawFieldArea(g, panelX + DESC_AREA_X, ty, descFieldW, 20, edDesc, TranslatedStrings.get(LangKeys.DESC_PLACEHOLDER));
        }
        ty += 28;

        // ── Row 2: 分类 + 隐 + 图标 + 前置 ──
        if (vanillaEditMode) {
            // 原版编辑模式：标签分配更宽更突出
            int tabW = pw - PREREQ_BTN_X - 38;
            String catLbl = TranslatedStrings.get(LangKeys.TAB) + ": " + (edTab != null ? edTab : DataStore.TAB_VANILLA);
            String catTrunc = GuiUtils.truncate(font, catLbl, tabW - 8);
            boolean catHov = GuiUtils.inRect(mx, my, panelX + 14, ty, tabW, 20);
            g.fill(panelX + 14, ty, panelX + 14 + tabW, ty + 20, catHov ? BTN_HOV : BTN);
            g.renderOutline(panelX + 14, ty, tabW, 20, edTab != null ? ACCENT : DIVIDER);
            g.drawString(font, catTrunc, panelX + 22, ty + 6, catHov ? TEXT_BR : TEXT, false);

            int pBtnX = panelX + PREREQ_BTN_X;
            boolean pHov = GuiUtils.inRect(mx, my, pBtnX, ty, PREREQ_BTN_W, 20);
            g.fill(pBtnX, ty, pBtnX + PREREQ_BTN_W, ty + 20, pHov ? BTN_HOV : BTN);
            g.renderOutline(pBtnX, ty, PREREQ_BTN_W, 20, DIVIDER);
            g.drawString(font, "+", pBtnX + (PREREQ_BTN_W - font.width("+")) / 2, ty + 4, pHov ? ACCENT : TEXT, false);

            int tagX = panelX + TAG_START_X;
            int visibleCount = Math.min(edPrereqs.size(), PREREQ_INLINE_MAX);
            for (int i = 0; i < visibleCount; i++) {
                String pname = screen.prereqDisplayName(edPrereqs.get(i));
                String truncPname = GuiUtils.truncate(font, pname, 50);
                int tagW = font.width(truncPname) + 16;
                if (tagX + tagW > panelX + pw - 14) break;
                boolean tagHov = GuiUtils.inRect(mx, my, tagX, ty, tagW, 18);
                g.fill(tagX, ty, tagX + tagW, ty + 18, tagHov ? BTN_HOV : BTN);
                g.renderOutline(tagX, ty, tagW, 18, DIVIDER);
                g.drawString(font, truncPname, tagX + 3, ty + 5, TEXT, false);
                g.drawString(font, "\u2715", tagX + tagW - 12, ty + 5, tagHov ? PINK : TEXT_DIM, false);
                tagX += tagW + 4;
            }
            if (edPrereqs.size() > PREREQ_INLINE_MAX) {
                String moreLabel = "+" + (edPrereqs.size() - PREREQ_INLINE_MAX);
                int moreW = font.width(moreLabel) + 16;
                boolean moreHov = GuiUtils.inRect(mx, my, tagX, ty, moreW, 18);
                g.fill(tagX, ty, tagX + moreW, ty + 18, moreHov ? BTN_HOV : BTN);
                g.renderOutline(tagX, ty, moreW, 18, DIVIDER);
                g.drawString(font, moreLabel, tagX + 3, ty + 5, moreHov ? ACCENT : TEXT, false);
            }
        } else {
            // 自定义编辑模式：原有布局
            String catLbl = TranslatedStrings.get(LangKeys.TAB) + ": " + GuiUtils.truncate(font, edTab != null ? edTab : DataStore.TAB_DEFAULT, 40);
            GuiUtils.drawSmallBtn(g, font, panelX + 14, ty, CAT_BTN_W, catLbl, GuiUtils.inRect(mx, my, panelX + 14, ty, CAT_BTN_W, 20));

            boolean hHov = GuiUtils.inRect(mx, my, panelX + HIDDEN_BTN_X, ty, HIDDEN_BTN_W, 20);
            g.fill(panelX + HIDDEN_BTN_X, ty, panelX + HIDDEN_BTN_X + HIDDEN_BTN_W, ty + 20, edHidden ? 0xFF3A5248 : (hHov ? BTN_HOV : BTN));
            g.renderOutline(panelX + HIDDEN_BTN_X, ty, HIDDEN_BTN_W, 20, edHidden ? ACCENT : DIVIDER);
            String hLbl = TranslatedStrings.get(LangKeys.HIDDEN_SHORT);
            g.drawString(font, hLbl, panelX + HIDDEN_BTN_X + (HIDDEN_BTN_W - font.width(hLbl)) / 2, ty + 4, edHidden ? ACCENT : (hHov ? TEXT_BR : TEXT), false);

            int iBtnX = panelX + ICON_BTN_X;
            boolean iHov = GuiUtils.inRect(mx, my, iBtnX, ty, ICON_BTN_W, 20);
            g.fill(iBtnX, ty, iBtnX + ICON_BTN_W, ty + 20, iHov ? BTN_HOV : BTN);
            g.renderOutline(iBtnX, ty, ICON_BTN_W, 20, edIcon != null ? ACCENT : DIVIDER);
            renderIconButton(g, font, iBtnX, ty, ICON_BTN_W, iHov);

            int pBtnX = panelX + PREREQ_BTN_X;
            boolean pHov = GuiUtils.inRect(mx, my, pBtnX, ty, PREREQ_BTN_W, 20);
            g.fill(pBtnX, ty, pBtnX + PREREQ_BTN_W, ty + 20, pHov ? BTN_HOV : BTN);
            g.renderOutline(pBtnX, ty, PREREQ_BTN_W, 20, DIVIDER);
            g.drawString(font, "+", pBtnX + (PREREQ_BTN_W - font.width("+")) / 2, ty + 4, pHov ? ACCENT : TEXT, false);

            int tagX = panelX + TAG_START_X;
            int visibleCount = Math.min(edPrereqs.size(), PREREQ_INLINE_MAX);
            for (int i = 0; i < visibleCount; i++) {
                String pname = screen.prereqDisplayName(edPrereqs.get(i));
                String truncPname = GuiUtils.truncate(font, pname, 50);
                int tagW = font.width(truncPname) + 16;
                if (tagX + tagW > panelX + pw - 14) break;
                boolean tagHov = GuiUtils.inRect(mx, my, tagX, ty, tagW, 18);
                g.fill(tagX, ty, tagX + tagW, ty + 18, tagHov ? BTN_HOV : BTN);
                g.renderOutline(tagX, ty, tagW, 18, DIVIDER);
                g.drawString(font, truncPname, tagX + 3, ty + 5, TEXT, false);
                g.drawString(font, "\u2715", tagX + tagW - 12, ty + 5, tagHov ? PINK : TEXT_DIM, false);
                tagX += tagW + 4;
            }
            if (edPrereqs.size() > PREREQ_INLINE_MAX) {
                String moreLabel = "+" + (edPrereqs.size() - PREREQ_INLINE_MAX);
                int moreW = font.width(moreLabel) + 16;
                boolean moreHov = GuiUtils.inRect(mx, my, tagX, ty, moreW, 18);
                g.fill(tagX, ty, tagX + moreW, ty + 18, moreHov ? BTN_HOV : BTN);
                g.renderOutline(tagX, ty, moreW, 18, DIVIDER);
                g.drawString(font, moreLabel, tagX + 3, ty + 5, moreHov ? ACCENT : TEXT, false);

                if (prereqDropdownOpen) {
                    int ddX = panelX + TAG_START_X;
                    int ddY = ty + 20;
                    int ddW = pw - TAG_START_X - 14;
                    int ddH = Math.min(edPrereqs.size() - PREREQ_INLINE_MAX, 5) * 20 + 4;
                    g.fill(ddX, ddY, ddX + ddW, ddY + ddH, CTX);
                    g.renderOutline(ddX, ddY, ddW, ddH, ACCENT);
                    for (int i = PREREQ_INLINE_MAX; i < edPrereqs.size(); i++) {
                        int rowY = ddY + 2 + (i - PREREQ_INLINE_MAX) * 20;
                        String pname = screen.prereqDisplayName(edPrereqs.get(i));
                        boolean rowHov = GuiUtils.inRect(mx, my, ddX + 2, rowY, ddW - 4, 18);
                        if (rowHov) g.fill(ddX + 2, rowY, ddX + ddW - 2, rowY + 18, BTN_HOV);
                        g.drawString(font, GuiUtils.truncate(font, pname, ddW - 30), ddX + 8, rowY + 5, TEXT, false);
                        g.drawString(font, "\u2715", ddX + ddW - 16, rowY + 5, rowHov ? PINK : TEXT_DIM, false);
                    }
                }
            }
        }
        ty += 26;

        // ── 分割线 ──
        g.fill(panelX + 10, ty, panelX + pw - 10, ty + 1, DIVIDER);
        ty += 6;

        // ── 条件头（vanillaEditMode 下"+"变灰） ──
        g.drawString(font, TranslatedStrings.get(LangKeys.CONDITIONS), panelX + 14, ty + 2, TEXT_BR, false);
        int addCondBtnX = panelX + pw - 34;
        boolean addCondHov = !vanillaEditMode && GuiUtils.inRect(mx, my, addCondBtnX, ty, 20, 18);
        g.fill(addCondBtnX, ty, addCondBtnX + 20, ty + 18, vanillaEditMode ? BTN : (addCondHov ? BTN_HOV : BTN));
        g.renderOutline(addCondBtnX, ty, 20, 18, vanillaEditMode ? DIVIDER : (addCondHov ? ACCENT : DIVIDER));
        g.drawString(font, "+", addCondBtnX + (20 - font.width("+")) / 2, ty + 3, vanillaEditMode ? TEXT_DIM : (addCondHov ? ACCENT : TEXT), false);
        ty += 22;

        // ── 条件列表 ──
        condListStartY = ty;
        int condListEndY = panelY + ph - 38;
        condListVisibleH = condListEndY - condListStartY;
        int totalCondH = edConds.size() * COND_ROW_H;
        condScrollBar.update(totalCondH, condListVisibleH);

        g.enableScissor(panelX + 1, condListStartY, panelX + pw - 1, condListEndY);
        for (int i = 0; i < edConds.size(); i++) {
            int rowY = condListStartY + i * COND_ROW_H - condScrollBar.getScroll();
            if (rowY + COND_ROW_H < condListStartY || rowY > condListEndY) continue;
            renderCondRow(g, font, mx, my, i, rowY, pw);
        }
        g.disableScissor();

        // ── 统一管理内联数量 EditBox 的位置和可见性 ──
        if (inlineEditingCount && inlineCondIdx >= 0 && inlineCondIdx < edConds.size()) {
            int editRowY = condListStartY + inlineCondIdx * COND_ROW_H - condScrollBar.getScroll();
            if (editRowY + COND_ROW_H > condListStartY && editRowY < condListEndY) {
                int delX = panelX + pw - COND_DEL_W - 6;
                int cntX = delX - COND_CNT_W - 4;
                condCountBox.setX(cntX);
                condCountBox.setY(editRowY + 1);
                condCountBox.setVisible(true);
            } else {
                condCountBox.setVisible(false);
            }
        } else {
            condCountBox.setVisible(false);
        }

        condScrollBar.render(g, panelX + pw - 8, condListStartY);

        if (edConds.isEmpty()) {
            String empty = TranslatedStrings.get(LangKeys.NONE);
            g.drawString(font, empty, panelX + (pw - font.width(empty)) / 2, condListStartY + condListVisibleH / 2 - 6, TEXT_DIM, false);
        }

        // ── 底部 ──
        g.fill(panelX + 10, panelY + ph - 38, panelX + pw - 10, panelY + ph - 37, DIVIDER);
        int btnY = panelY + ph - 32;
        g.drawString(font, "X:" + edX + " Y:" + edY, panelX + 14, btnY + 4, TEXT_DIM, false);
        GuiUtils.drawSmallBtn(g, font, panelX + pw - 180, btnY, 80, TranslatedStrings.get(LangKeys.SAVE), GuiUtils.inRect(mx, my, panelX + pw - 180, btnY, 80, 20));
        GuiUtils.drawSmallBtn(g, font, panelX + pw - 90, btnY, 80, TranslatedStrings.get(LangKeys.CANCEL), GuiUtils.inRect(mx, my, panelX + pw - 90, btnY, 80, 20));
    }

    // ═══════════════ 图标按钮渲染 ═══════════════

    private void renderIconButton(GuiGraphics g, Font font, int x, int y, int w, boolean hov) {
        if (edIcon != null && !edIcon.isEmpty()) {
            boolean rendered = false;
            if (edIcon.startsWith("entity:")) {
                String entityId = edIcon.substring(7);
                ResourceLocation rl = ResourceLocation.tryParse(entityId);
                if (rl != null) {
                    var et = BuiltInRegistries.ENTITY_TYPE.get(rl);
                    if (et != null) {
                        g.drawString(font, GuiUtils.truncate(font, et.getDescription().getString(), w - 8), x + 4, y + 5, TEXT, false);
                        rendered = true;
                    }
                }
            } else {
                ResourceLocation rl = ResourceLocation.tryParse(edIcon);
                if (rl != null) {
                    var item = BuiltInRegistries.ITEM.get(rl);
                    if (item != null) {
                        g.renderItem(new ItemStack(item), x + 2, y + 2);
                        g.drawString(font, GuiUtils.truncate(font, new ItemStack(item).getHoverName().getString(), w - 22), x + 20, y + 5, TEXT, false);
                        rendered = true;
                    }
                }
            }
            if (!rendered) drawIconLabel(g, font, x, y, w, hov);
        } else {
            drawIconLabel(g, font, x, y, w, hov);
        }
    }

    // ═══════════════ 条件行 ═══════════════

    private void renderCondRow(GuiGraphics g, Font font, int mx, int my, int idx, int rowY, int pw) {
        DataStore.AdvancementCondition c = edConds.get(idx);
        String typeLabel = c.getType() != null ? ConditionTypeStyle.of(c.getType()).displayName() : "???";
        String tgt = c.getTargetId() != null ? c.getTargetId() : "";
        int typeColor = c.getType() != null ? ConditionTypeStyle.of(c.getType()).color() : TEXT_DIM;

        // 行分隔线
        g.fill(panelX + 10, rowY + COND_ROW_H - 1, panelX + pw - 10, rowY + COND_ROW_H, 0x15FFFFFF);

        // 类型（彩色标签，无背景）
        g.drawString(font, GuiUtils.truncate(font, typeLabel, COND_TYPE_W - 4),
                panelX + COND_TYPE_X, rowY + 5, typeColor, false);

        // 目标（本地化名称）
        String tgtDisp;
        if (tgt.isEmpty()) {
            tgtDisp = TranslatedStrings.get(LangKeys.COND_ANY);
        } else {
            tgtDisp = DisplayNameResolver.resolve(c.getType(), tgt);
        }
        int maxTgtW = pw - COND_TGT_X - COND_CNT_W - COND_DEL_W - 30;
        g.drawString(font, GuiUtils.truncate(font, tgtDisp, maxTgtW),
                panelX + COND_TGT_X, rowY + 5, TEXT, false);

        // 数量和删除按钮（右对齐）
        int delX = panelX + pw - COND_DEL_W - 6;
        int cntX = delX - COND_CNT_W - 4;
        int xLabelX = cntX - 12;

        g.drawString(font, "x", xLabelX, rowY + 5, TEXT_DIM, false);

        // 数量 — 编辑中的行不画文本，由 EditBox 直接替代
        if (!(inlineEditingCount && inlineCondIdx == idx)) {
            g.drawString(font, String.valueOf(c.getCount()), cntX + 4, rowY + 5, ACCENT, false);
        }

        // 删除按钮（vanillaEditMode 下灰色不可操作）
        boolean delHov = !vanillaEditMode && GuiUtils.inRect(mx, my, delX, rowY, COND_DEL_W, COND_ROW_H);
        g.drawString(font, "\u2715", delX + (COND_DEL_W - font.width("\u2715")) / 2, rowY + 5,
                vanillaEditMode ? 0xFF333333 : (delHov ? PINK : TEXT_DIM), false);
    }

    // ═══════════════ 辅助渲染 ═══════════════

    private void drawFieldArea(GuiGraphics g, int x, int y, int w, int h, String value, String placeholder) {
        g.fill(x, y, x + w, y + h, 0xFF222238);
        g.renderOutline(x, y, w, h, DIVIDER);
        if (value != null && !value.isEmpty()) g.drawString(font, GuiUtils.truncate(font, value, w - 8), x + 4, y + 6, TEXT, false);
        else g.drawString(font, GuiUtils.truncate(font, placeholder, w - 8), x + 4, y + 6, TEXT_DIM, false);
    }

    private void drawIconLabel(GuiGraphics g, Font font, int x, int y, int w, boolean hov) {
        String lbl = TranslatedStrings.get(LangKeys.ICON);
        g.drawString(font, lbl, x + (w - font.width(lbl)) / 2, y + 4, hov ? TEXT_BR : TEXT, false);
    }

    // ═══════════════ 鼠标交互 ═══════════════

    public boolean handleClick(double mx, double my, int screenW, int screenH) {
        if (!visible || screen == null) return false;
        if (condSelector.isActive()) { condSelector.handleClick(mx, my, font, screenW, screenH); return true; }
        if (inlineEditingCount) commitInlineCountEdit();
        commitNameAndDesc();
        int pw = Math.min(PANEL_W, screenW - 40);
        int ph = Math.clamp(screenH - 40, 200, PANEL_H);
        int px = (screenW - pw) / 2;
        int py = Math.max(20, (screenH - ph) / 2);
        int descFieldW = pw - DESC_AREA_X - 14;

        if (GuiUtils.closeHit(mx, my, px, py, pw)) { close(); return true; }
        if (GuiUtils.outsidePanel(mx, my, px, py, pw, ph)) return false;

        int ty = py + 28;

        // ── Row 1: 名称/描述（vanillaEditMode 下不可编辑） ──
        if (!vanillaEditMode && GuiUtils.inRect(mx, my, px + NAME_AREA_X, ty, NAME_FIELD_W, 20)) { activateName(); return true; }
        if (!vanillaEditMode && GuiUtils.inRect(mx, my, px + DESC_AREA_X, ty, descFieldW, 20)) { activateDesc(); return true; }
        commitNameAndDesc();
        ty += 28;

        // ── Row 2 ──
        if (vanillaEditMode) {
            int tabW = pw - PREREQ_BTN_X - 38;
            if (GuiUtils.inRect(mx, my, px + 14, ty, tabW, 20)) { if (screen != null) screen.openTabSel(); return true; }
            if (GuiUtils.inRect(mx, my, px + PREREQ_BTN_X, ty, PREREQ_BTN_W, 20)) { if (screen != null) screen.openPrereqSel(null); return true; }
        } else {
            if (GuiUtils.inRect(mx, my, px + 14, ty, CAT_BTN_W, 20)) { if (screen != null) screen.openTabSel(); return true; }
            if (GuiUtils.inRect(mx, my, px + HIDDEN_BTN_X, ty, HIDDEN_BTN_W, 20)) { edHidden = !edHidden; return true; }
            if (GuiUtils.inRect(mx, my, px + ICON_BTN_X, ty, ICON_BTN_W, 20)) { openIconPicker(); return true; }
            if (GuiUtils.inRect(mx, my, px + PREREQ_BTN_X, ty, PREREQ_BTN_W, 20)) { if (screen != null) screen.openPrereqSel(null); return true; }
        }

        // 前置标签删除 + 下拉交互
        int tagX = px + TAG_START_X;
        int visibleCount = Math.min(edPrereqs.size(), PREREQ_INLINE_MAX);
        for (int i = 0; i < visibleCount; i++) {
            String pname = screen.prereqDisplayName(edPrereqs.get(i));
            String truncPname = GuiUtils.truncate(font, pname, 50);
            int tagW = font.width(truncPname) + 16;
            if (tagX + tagW > px + pw - 14) break;
            if (GuiUtils.inRect(mx, my, tagX, ty, tagW, 18)) {
                if (mx >= tagX + tagW - 14) { removePrereq(i); return true; }
                return true;
            }
            tagX += tagW + 4;
        }
        if (edPrereqs.size() > PREREQ_INLINE_MAX) {
            String moreLabel = "+" + (edPrereqs.size() - PREREQ_INLINE_MAX);
            int moreW = font.width(moreLabel) + 16;
            if (GuiUtils.inRect(mx, my, tagX, ty, moreW, 18)) { prereqDropdownOpen = !prereqDropdownOpen; return true; }
            if (prereqDropdownOpen) {
                int ddX = px + TAG_START_X;
                int ddY = ty + 20;
                int ddW = pw - TAG_START_X - 14;
                for (int i = PREREQ_INLINE_MAX; i < edPrereqs.size(); i++) {
                    int rowY = ddY + 2 + (i - PREREQ_INLINE_MAX) * 20;
                    if (GuiUtils.inRect(mx, my, ddX + 2, rowY, ddW - 4, 18)) {
                        if (mx >= ddX + ddW - 18) { removePrereq(i); prereqDropdownOpen = false; return true; }
                        return true;
                    }
                }
            }
        }
        prereqDropdownOpen = false;
        ty += 26;

        // ── 条件 "+" 按钮（vanillaEditMode 下禁用） ──
        if (!vanillaEditMode && GuiUtils.inRect(mx, my, px + pw - 34, ty + 6, 20, 18)) {
            condSelector.setExistingConditions(edConds);
            condSelector.open(DataStore.ConditionType.KILL_ENTITY);
            return true;
        }

        // ── 条件列表（vanillaEditMode 下禁用数量编辑和删除） ──
        if (mx >= px + 14 && mx < px + pw - 14 && my >= condListStartY && my < condListStartY + condListVisibleH) {
            int idx = (int) ((my - condListStartY + condScrollBar.getScroll()) / COND_ROW_H);
            if (idx >= 0 && idx < edConds.size()) {
                int rowY = condListStartY + idx * COND_ROW_H - condScrollBar.getScroll();
                int delX = px + pw - COND_DEL_W - 6;
                int cntX = delX - COND_CNT_W - 4;
                if (!vanillaEditMode && GuiUtils.inRect(mx, my, cntX, rowY, COND_CNT_W, COND_ROW_H)) { startInlineCountEdit(idx); return true; }
                if (!vanillaEditMode && GuiUtils.inRect(mx, my, delX, rowY, COND_DEL_W, COND_ROW_H)) { edConds.remove(idx); return true; }
            }
            return true;
        }

        // ── 保存/取消 ──
        int btnY = py + ph - 32;
        if (GuiUtils.inRect(mx, my, px + pw - 180, btnY, 80, 20)) { saveEd(); return true; }
        if (GuiUtils.inRect(mx, my, px + pw - 90, btnY, 80, 20)) { close(); return true; }

        return true;
    }

    public boolean handleScroll(double mx, double my, double sy, int sw, int sh) {
        if (!visible) return false;
        if (condSelector.isActive()) { condSelector.handleScroll(mx, my, sy, sw, sh); return true; }
        if (my >= condListStartY && my < condListStartY + condListVisibleH) {
            return condScrollBar.handleScroll(sy);
        }
        return false;
    }

    // ═══════════════ 名称/描述编辑 ═══════════════

    private void activateName() {
        nameActive = true; descActive = false;
        if (nameBox != null) { nameBox.setVisible(true); nameBox.setFocused(true); if (screen != null) screen.setFocused(nameBox); }
    }

    private void activateDesc() {
        descActive = true; nameActive = false;
        if (descBox != null) { descBox.setVisible(true); descBox.setFocused(true); if (screen != null) screen.setFocused(descBox); }
    }

    private void commitNameAndDesc() {
        if (nameActive && nameBox != null) { edName = nameBox.getValue().trim(); nameActive = false; nameBox.setFocused(false); nameBox.setVisible(false); }
        if (descActive && descBox != null) { edDesc = descBox.getValue().trim(); descActive = false; descBox.setFocused(false); descBox.setVisible(false); }
    }

    // ═══════════════ 内联数量 ═══════════════

    public void startInlineCountEdit(int condIdx) {
        if (condIdx < 0 || condIdx >= edConds.size()) return;
        inlineCondIdx = condIdx; inlineEditingCount = true;
        condCountBox.setValue(String.valueOf(edConds.get(condIdx).getCount()));
        condCountBox.setFocused(true);
        if (screen != null) screen.setFocused(condCountBox);
    }

    public void commitInlineCountEdit() {
        if (!inlineEditingCount || inlineCondIdx < 0 || inlineCondIdx >= edConds.size()) return;
        try { int v = Integer.parseInt(condCountBox.getValue().trim()); edConds.get(inlineCondIdx).setCount(Math.clamp(v, 1, MAX_COUNT)); } catch (NumberFormatException ignored) {}
        inlineEditingCount = false; inlineCondIdx = -1;
        if (condCountBox != null) { condCountBox.setFocused(false); condCountBox.setVisible(false); }
    }

    public boolean handleInlineCountKey(int kc) {
        if (!inlineEditingCount) return false;
        if (kc == GuiUtils.KEY_ENTER || kc == GuiUtils.KEY_ESCAPE) { commitInlineCountEdit(); return true; }
        return false;
    }

    // ═══════════════ 保存 ═══════════════

    /**
     * 将单个条件序列化为 Map
     */
    private Map<String, Object> conditionToMap(DataStore.AdvancementCondition c) {
        Map<String, Object> cm = new LinkedHashMap<>();
        cm.put("type", c.getType().name().toLowerCase());
        if (c.getTargetId() != null && !c.getTargetId().isEmpty()) cm.put("targetId", c.getTargetId());
        cm.put("count", c.getCount());
        DataStore.NbtMatchMode nbtMode = c.getNbtMatchMode();
        if (nbtMode != null && nbtMode != DataStore.NbtMatchMode.IGNORE) cm.put("nbtMatchMode", nbtMode.getSaveName());
        if (c.getTargetNbt() != null && !c.getTargetNbt().isEmpty()) cm.put("targetNbt", c.getTargetNbt());
        return cm;
    }

    /**
     * 将条件列表序列化为 Map 列表
     */
    private List<Map<String, Object>> conditionsToMapList(List<DataStore.AdvancementCondition> conditions) {
        List<Map<String, Object>> condList = new ArrayList<>();
        for (DataStore.AdvancementCondition c : conditions) {
            condList.add(conditionToMap(c));
        }
        return condList;
    }

    private void saveEd() {
        if (screen == null) return;
        if (vanillaEditMode && edId != null) {
            // 原版成就：单条命令保存标签和前置条件
            String tab = edTab != null ? edTab : DataStore.TAB_VANILLA;
            String prereqJson = DataStore.GSON.toJson(edPrereqs);
            GuiUtils.sendCommand("adv vanilla save " + edId + " " + "{\"tab\":\"" + tab + "\",\"prerequisites\":" + prereqJson + "}");
            close();
            return;
        }
        commitNameAndDesc();
        if (inlineEditingCount) commitInlineCountEdit();
        if (edTab == null || edTab.isEmpty()) edTab = DataStore.TAB_DEFAULT;
        if (edName.isEmpty()) edName = TranslatedStrings.get(LangKeys.ICON_DEFAULT);

        String advId;
        if (edId == null) {
            advId = "custom_" + UUID.randomUUID().toString().substring(0, 8);
        } else {
            advId = edId;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", advId);
        data.put("name", edName);
        data.put("description", edDesc);
        data.put("x", edX);
        data.put("y", edY);
        data.put("hidden", edHidden);
        if (edIcon != null) data.put("icon", edIcon);
        data.put("tab", edTab);
        if (!edPrereqs.isEmpty()) data.put("prerequisites", new ArrayList<>(edPrereqs));
        if (!edConds.isEmpty()) {
            data.put("conditions", conditionsToMapList(edConds));
        }

        String json = DataStore.GSON.toJson(data);
        String encoded = java.util.Base64.getEncoder().encodeToString(
                json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        GuiUtils.sendCommand("adv updatejson " + encoded);
        close();
    }
    // ═══════════════ 图标选择器 ═══════════════

    private void openIconPicker() {
        if (screen == null) return;
        IconPickerHelper.open(screen, icon -> edIcon = icon);
    }
}