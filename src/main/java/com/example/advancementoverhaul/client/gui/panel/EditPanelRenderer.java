package com.example.advancementoverhaul.client.gui.panel;

import com.example.advancementoverhaul.LangKeys;
import com.example.advancementoverhaul.client.gui.AdvancementScreen;
import com.example.advancementoverhaul.client.gui.ConditionTypeStyle;
import com.example.advancementoverhaul.client.gui.GuiUtils;
import com.example.advancementoverhaul.client.gui.TranslatedStrings;
import com.example.advancementoverhaul.client.gui.widget.ScrollBar;
import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.DisplayNameResolver;
import com.example.advancementoverhaul.data.model.AdvancementCondition;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

import static com.example.advancementoverhaul.client.gui.Theme.*;

/**
 * Handles rendering for the EditPanel, separated from interaction logic.
 */
class EditPanelRenderer {

    private final EditPanel panel;

    EditPanelRenderer(EditPanel panel) {
        this.panel = panel;
    }

    void render(GuiGraphics g, int mx, int my, Font font, int sw, int sh) {
        if (panel.font == null) panel.font = font;
        if (panel.condSelector.isActive()) panel.condSelector.render(g, panel.font, mx, my, sw, sh);
        else renderPanel(g, mx, my, sw, sh);
    }

    void renderWidgets(GuiGraphics g, int mx, int my, float pt) {
        if (panel.nameBox != null && panel.nameBox.isVisible()) panel.nameBox.render(g, mx, my, pt);
        if (panel.descBox != null && panel.descBox.isVisible()) panel.descBox.render(g, mx, my, pt);
        if (panel.condCountBox != null && panel.condCountBox.isVisible()) panel.condCountBox.render(g, mx, my, pt);
    }

    void renderPanel(GuiGraphics g, int mx, int my, int screenW, int screenH) {
        if (!panel.visible || panel.screen == null) return;
        Font font = panel.font;

        int pw = Math.min(EditPanel.PANEL_W, screenW - 40);
        int ph = Math.clamp(screenH - 40, 200, EditPanel.PANEL_H);
        panel.panelX = (screenW - pw) / 2;
        panel.panelY = Math.max(20, (screenH - ph) / 2);
        int descFieldW = pw - EditPanel.DESC_AREA_X - 14;

        GuiUtils.drawPanelBg(g, font, panel.panelX, panel.panelY, pw, ph,
                TranslatedStrings.get(panel.edId == null ? LangKeys.CREATE_TITLE : LangKeys.EDIT_TITLE),
                screenW, screenH);

        int ty = panel.panelY + 28;

        // ── Row 1: 名称 + 描述（vanillaEditMode 下锁定） ──
        g.drawString(font, TranslatedStrings.get(LangKeys.NAME), panel.panelX + 14, ty + 6, panel.vanillaEditMode ? TEXT_DIM : TEXT, false);
        if (!panel.vanillaEditMode && panel.nameActive) {
            panel.nameBox.setX(panel.panelX + EditPanel.NAME_AREA_X); panel.nameBox.setY(ty); panel.nameBox.setWidth(EditPanel.NAME_FIELD_W);
            panel.nameBox.setVisible(true);
        } else {
            panel.nameBox.setVisible(false);
            drawFieldArea(g, font, panel.panelX + EditPanel.NAME_AREA_X, ty, EditPanel.NAME_FIELD_W, 20, panel.edName, TranslatedStrings.get(LangKeys.NAME_PLACEHOLDER));
        }

        g.drawString(font, TranslatedStrings.get(LangKeys.DESC), panel.panelX + EditPanel.DESC_LABEL_X, ty + 6, panel.vanillaEditMode ? TEXT_DIM : TEXT, false);
        if (!panel.vanillaEditMode && panel.descActive) {
            panel.descBox.setX(panel.panelX + EditPanel.DESC_AREA_X); panel.descBox.setY(ty); panel.descBox.setWidth(descFieldW);
            panel.descBox.setVisible(true);
        } else {
            panel.descBox.setVisible(false);
            drawFieldArea(g, font, panel.panelX + EditPanel.DESC_AREA_X, ty, descFieldW, 20, panel.edDesc, TranslatedStrings.get(LangKeys.DESC_PLACEHOLDER));
        }
        ty += 28;

        // ── Row 2: 分类 + 隐 + 图标 + 前置 ──
        if (panel.vanillaEditMode) {
            int tabW = pw - EditPanel.PREREQ_BTN_W - 14 - EditPanel.BTN_GAP - 14;
            String catLbl = TranslatedStrings.get(LangKeys.TAB) + ": " + (panel.edTab != null ? DataStore.getTabDisplayName(panel.edTab) : DataStore.getTabDisplayName(DataStore.TAB_VANILLA));
            String catTrunc = GuiUtils.truncate(font, catLbl, tabW - 8);
            boolean catHov = GuiUtils.inRect(mx, my, panel.panelX + 14, ty, tabW, 20);
            g.fill(panel.panelX + 14, ty, panel.panelX + 14 + tabW, ty + 20, catHov ? BTN_HOV : BTN);
            g.renderOutline(panel.panelX + 14, ty, tabW, 20, panel.edTab != null ? ACCENT : DIVIDER);
            g.drawString(font, catTrunc, panel.panelX + 22, ty + 6, catHov ? TEXT_BR : TEXT, false);

            int pBtnX = panel.panelX + 14 + tabW + EditPanel.BTN_GAP;
            renderPrereqButton(g, font, mx, my, pBtnX, ty, EditPanel.PREREQ_BTN_W);

        } else {
            String catLbl = TranslatedStrings.get(LangKeys.TAB) + ": " + GuiUtils.truncate(font, panel.edTab != null ? DataStore.getTabDisplayName(panel.edTab) : DataStore.getTabDisplayName(DataStore.TAB_DEFAULT), 40);
            GuiUtils.drawSmallBtn(g, font, panel.panelX + 14, ty, EditPanel.CAT_BTN_W, catLbl, GuiUtils.inRect(mx, my, panel.panelX + 14, ty, EditPanel.CAT_BTN_W, 20));

            boolean hHov = GuiUtils.inRect(mx, my, panel.panelX + EditPanel.HIDDEN_BTN_X, ty, EditPanel.HIDDEN_BTN_W, 20);
            g.fill(panel.panelX + EditPanel.HIDDEN_BTN_X, ty, panel.panelX + EditPanel.HIDDEN_BTN_X + EditPanel.HIDDEN_BTN_W, ty + 20, panel.edHidden ? 0xFF3A5248 : (hHov ? BTN_HOV : BTN));
            g.renderOutline(panel.panelX + EditPanel.HIDDEN_BTN_X, ty, EditPanel.HIDDEN_BTN_W, 20, panel.edHidden ? ACCENT : DIVIDER);
            String hLbl = TranslatedStrings.get(LangKeys.HIDDEN_SHORT);
            g.drawString(font, hLbl, panel.panelX + EditPanel.HIDDEN_BTN_X + (EditPanel.HIDDEN_BTN_W - font.width(hLbl)) / 2, ty + 4, panel.edHidden ? ACCENT : (hHov ? TEXT_BR : TEXT), false);

            int iBtnX = panel.panelX + EditPanel.ICON_BTN_X;
            boolean iHov = GuiUtils.inRect(mx, my, iBtnX, ty, EditPanel.ICON_BTN_W, 20);
            g.fill(iBtnX, ty, iBtnX + EditPanel.ICON_BTN_W, ty + 20, iHov ? BTN_HOV : BTN);
            g.renderOutline(iBtnX, ty, EditPanel.ICON_BTN_W, 20, panel.edIcon != null ? ACCENT : DIVIDER);
            renderIconButton(g, font, iBtnX, ty, EditPanel.ICON_BTN_W, iHov);

            int pBtnX = panel.panelX + EditPanel.PREREQ_BTN_X_NORMAL;
            int pBtnW = panel.panelX + pw - pBtnX - 14;
            renderPrereqButton(g, font, mx, my, pBtnX, ty, pBtnW);
        }
        ty += 26;

        // ── 计算前置于下拉区域的 Y 坐标（稍后在条件列表上方渲染） ──
        int prereqDropdownY = panel.panelY + 28 + 28 + 22;

        // ── 分割线 ──
        g.fill(panel.panelX + 10, ty, panel.panelX + pw - 10, ty + 1, DIVIDER);
        ty += 6;

        // ── 条件头（vanillaEditMode 下"+"变灰） ──
        g.drawString(font, TranslatedStrings.get(LangKeys.CONDITIONS), panel.panelX + 14, ty + 2, TEXT_BR, false);
        int addCondBtnX = panel.panelX + pw - 34;
        boolean addCondHov = !panel.vanillaEditMode && GuiUtils.inRect(mx, my, addCondBtnX, ty, 20, 18);
        g.fill(addCondBtnX, ty, addCondBtnX + 20, ty + 18, panel.vanillaEditMode ? BTN : (addCondHov ? BTN_HOV : BTN));
        g.renderOutline(addCondBtnX, ty, 20, 18, panel.vanillaEditMode ? DIVIDER : (addCondHov ? ACCENT : DIVIDER));
        g.drawString(font, "+", addCondBtnX + (20 - font.width("+")) / 2, ty + 3, panel.vanillaEditMode ? TEXT_DIM : (addCondHov ? ACCENT : TEXT), false);
        ty += 22;

        // ── 条件列表 ──
        panel.condListStartY = ty;
        int condListEndY = panel.panelY + ph - 38;
        panel.condListVisibleH = condListEndY - panel.condListStartY;
        int totalCondH = panel.edConds.size() * EditPanel.COND_ROW_H;
        panel.condScrollBar.update(totalCondH, panel.condListVisibleH);

        g.enableScissor(panel.panelX + 1, panel.condListStartY, panel.panelX + pw - 1, condListEndY);
        for (int i = 0; i < panel.edConds.size(); i++) {
            int rowY = panel.condListStartY + i * EditPanel.COND_ROW_H - panel.condScrollBar.getScroll();
            if (rowY + EditPanel.COND_ROW_H < panel.condListStartY || rowY > condListEndY) continue;
            renderCondRow(g, font, mx, my, i, rowY, pw);
        }
        g.disableScissor();

        // ── 统一管理内联数量 EditBox 的位置和可见性 ──
        if (panel.inlineEditingCount && panel.inlineCondIdx >= 0 && panel.inlineCondIdx < panel.edConds.size()) {
            int editRowY = panel.condListStartY + panel.inlineCondIdx * EditPanel.COND_ROW_H - panel.condScrollBar.getScroll();
            if (editRowY + EditPanel.COND_ROW_H > panel.condListStartY && editRowY < condListEndY) {
                int delX = panel.panelX + pw - EditPanel.COND_DEL_W - 6;
                int cntX = delX - EditPanel.COND_CNT_W - 4;
                panel.condCountBox.setX(cntX);
                panel.condCountBox.setY(editRowY + 1);
                panel.condCountBox.setVisible(true);
            } else {
                panel.condCountBox.setVisible(false);
            }
        } else {
            panel.condCountBox.setVisible(false);
        }

        panel.condScrollBar.render(g, panel.panelX + pw - 8, panel.condListStartY);

        if (panel.edConds.isEmpty()) {
            String empty = TranslatedStrings.get(LangKeys.NONE);
            g.drawString(font, empty, panel.panelX + (pw - font.width(empty)) / 2, panel.condListStartY + panel.condListVisibleH / 2 - 6, TEXT_DIM, false);
        }

        // ── 底部 ──
        g.fill(panel.panelX + 10, panel.panelY + ph - 38, panel.panelX + pw - 10, panel.panelY + ph - 37, DIVIDER);
        int btnY = panel.panelY + ph - 32;
        g.drawString(font, "X:" + panel.edX + " Y:" + panel.edY, panel.panelX + 14, btnY + 4, TEXT_DIM, false);
        GuiUtils.drawSmallBtn(g, font, panel.panelX + pw - 180, btnY, 80, TranslatedStrings.get(LangKeys.SAVE), GuiUtils.inRect(mx, my, panel.panelX + pw - 180, btnY, 80, 20));
        GuiUtils.drawSmallBtn(g, font, panel.panelX + pw - 90, btnY, 80, TranslatedStrings.get(LangKeys.CANCEL), GuiUtils.inRect(mx, my, panel.panelX + pw - 90, btnY, 80, 20));

        // ── 前置条件下拉面板（在条件列表和底部按钮之后渲染，确保覆盖在它们之上） ──
        if (panel.prereqDropdownOpen) {
            renderPrereqDropdown(g, font, mx, my, prereqDropdownY, pw);
        }
    }

    /**
     * 渲染前置条件按钮。
     * 无前置条件时显示"添加前置条件"；有前置条件时显示"已添加n个条件"。
     */
    private void renderPrereqButton(GuiGraphics g, Font font, int mx, int my, int bx, int by, int bw) {
        boolean pHov = GuiUtils.inRect(mx, my, bx, by, bw, 20);
        int color = !panel.edPrereqs.isEmpty() ? 0xFF2A4A3A : (pHov ? BTN_HOV : BTN);
        g.fill(bx, by, bx + bw, by + 20, color);
        int outlineColor = !panel.edPrereqs.isEmpty() ? ACCENT : DIVIDER;
        g.renderOutline(bx, by, bw, 20, outlineColor);

        String btnText;
        if (panel.edPrereqs.isEmpty()) {
            btnText = TranslatedStrings.get(LangKeys.PREREQ_ADD);
        } else {
            btnText = String.format(TranslatedStrings.get(LangKeys.PREREQ_ADDED), panel.edPrereqs.size());
        }
        int textColor = pHov ? TEXT_BR : (!panel.edPrereqs.isEmpty() ? ACCENT : TEXT);
        btnText = GuiUtils.truncate(font, btnText, bw - 8);
        g.drawString(font, btnText, bx + (bw - font.width(btnText)) / 2, by + 6, textColor, false);
    }

    /**
     * 渲染前置条件下拉面板。
     * 首行固定为"添加新条件"入口，下方按序展示已添加条件（含 × 删除按钮）。
     */
    private void renderPrereqDropdown(GuiGraphics g, Font font, int mx, int my, int ty, int pw) {
        AdvancementScreen screen = panel.screen;
        if (screen == null) return;
        int ddX = panel.panelX + 14;
        int ddY = ty;
        int ddW = pw - 28;
        int rowH = 20;

        // 下拉总高度：首行 + 所有条件行 + 上下内边距
        int ddH = (panel.edPrereqs.size() + 1) * rowH + 4;

        // 下拉背景（完全不透明确保不穿透看到后方成就面板）
        g.fill(ddX, ddY, ddX + ddW, ddY + ddH, 0xFF2E2E42);
        g.renderOutline(ddX, ddY, ddW, ddH, ACCENT);

        // 首行："添加新条件"（绿色 + 加号图标）
        int addRowY = ddY + 2;
        boolean addHov = GuiUtils.inRect(mx, my, ddX, addRowY, ddW, rowH);
        if (addHov) g.fill(ddX + 2, addRowY, ddX + ddW - 2, addRowY + rowH, BTN_HOV);
        String addLabel = TranslatedStrings.get(LangKeys.PREREQ_ADD_NEW);
        g.drawString(font, "\u271A", ddX + 8, addRowY + 5, ACCENT, false);
        g.drawString(font, GuiUtils.truncate(font, addLabel, ddW - 50), ddX + 24, addRowY + 5, addHov ? TEXT_BR : ACCENT, false);

        // 分隔线
        if (!panel.edPrereqs.isEmpty())
            g.fill(ddX + 8, addRowY + rowH, ddX + ddW - 8, addRowY + rowH + 1, DIVIDER);

        // 已添加的前置条件行
        for (int i = 0; i < panel.edPrereqs.size(); i++) {
            int rowY = ddY + 2 + (i + 1) * rowH;
            String pname = screen.prereqDisplayName(panel.edPrereqs.get(i));
            boolean rowHov = GuiUtils.inRect(mx, my, ddX + 2, rowY, ddW - 4, rowH - 2);
            if (rowHov) g.fill(ddX + 2, rowY, ddX + ddW - 2, rowY + rowH - 2, BTN_HOV);
            g.drawString(font, GuiUtils.truncate(font, pname, ddW - 30), ddX + 8, rowY + 4, TEXT, false);

            // × 删除按钮（右侧）
            int delX = ddX + ddW - 18;
            boolean delHov = GuiUtils.inRect(mx, my, delX, rowY, 14, rowH - 2);
            g.drawString(font, "\u2715", delX, rowY + 4, delHov ? PINK : TEXT_DIM, false);
        }
    }

    private void renderIconButton(GuiGraphics g, Font font, int x, int y, int w, boolean hov) {
        if (panel.edIcon != null && !panel.edIcon.isEmpty()) {
            boolean rendered = false;
            if (panel.edIcon.startsWith("entity:")) {
                String entityId = panel.edIcon.substring(7);
                ResourceLocation rl = ResourceLocation.tryParse(entityId);
                if (rl != null) {
                    var et = BuiltInRegistries.ENTITY_TYPE.get(rl);
                    if (et != null) {
                        g.drawString(font, GuiUtils.truncate(font, et.getDescription().getString(), w - 8), x + 4, y + 5, TEXT, false);
                        rendered = true;
                    }
                }
            } else {
                ResourceLocation rl = ResourceLocation.tryParse(panel.edIcon);
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

    private void renderCondRow(GuiGraphics g, Font font, int mx, int my, int idx, int rowY, int pw) {
        AdvancementCondition c = panel.edConds.get(idx);
        String typeLabel = c.getType() != null ? ConditionTypeStyle.of(c.getType()).displayName() : "???";
        String tgt = c.getTargetId() != null ? c.getTargetId() : "";
        int typeColor = c.getType() != null ? ConditionTypeStyle.of(c.getType()).color() : TEXT_DIM;

        // 行分隔线
        g.fill(panel.panelX + 10, rowY + EditPanel.COND_ROW_H - 1, panel.panelX + pw - 10, rowY + EditPanel.COND_ROW_H, 0x15FFFFFF);

        // 类型（彩色标签，无背景）
        g.drawString(font, GuiUtils.truncate(font, typeLabel, EditPanel.COND_TYPE_W - 4),
                panel.panelX + EditPanel.COND_TYPE_X, rowY + 5, typeColor, false);

        // 目标（本地化名称）
        String tgtDisp;
        if (tgt.isEmpty()) {
            tgtDisp = TranslatedStrings.get(LangKeys.COND_ANY);
        } else {
            tgtDisp = DisplayNameResolver.resolve(c.getType(), tgt);
        }
        int maxTgtW = pw - EditPanel.COND_TGT_X - EditPanel.COND_CNT_W - EditPanel.COND_DEL_W - 30;
        g.drawString(font, GuiUtils.truncate(font, tgtDisp, maxTgtW),
                panel.panelX + EditPanel.COND_TGT_X, rowY + 5, TEXT, false);

        // 数量和删除按钮（右对齐）
        int delX = panel.panelX + pw - EditPanel.COND_DEL_W - 6;
        int cntX = delX - EditPanel.COND_CNT_W - 4;
        int xLabelX = cntX - 12;

        g.drawString(font, "x", xLabelX, rowY + 5, TEXT_DIM, false);

        // 数量 — 编辑中的行不画文本，由 EditBox 直接替代
        if (!(panel.inlineEditingCount && panel.inlineCondIdx == idx)) {
            g.drawString(font, String.valueOf(c.getCount()), cntX + 4, rowY + 5, ACCENT, false);
        }

        // 删除按钮（vanillaEditMode 下灰色不可操作）
        boolean delHov = !panel.vanillaEditMode && GuiUtils.inRect(mx, my, delX, rowY, EditPanel.COND_DEL_W, EditPanel.COND_ROW_H);
        g.drawString(font, "\u2715", delX + (EditPanel.COND_DEL_W - font.width("\u2715")) / 2, rowY + 5,
                panel.vanillaEditMode ? 0xFF333333 : (delHov ? PINK : TEXT_DIM), false);
    }

    private void drawFieldArea(GuiGraphics g, Font font, int x, int y, int w, int h, String value, String placeholder) {
        g.fill(x, y, x + w, y + h, 0xFF222238);
        g.renderOutline(x, y, w, h, DIVIDER);
        if (value != null && !value.isEmpty()) g.drawString(font, GuiUtils.truncate(font, value, w - 8), x + 4, y + 6, TEXT, false);
        else g.drawString(font, GuiUtils.truncate(font, placeholder, w - 8), x + 4, y + 6, TEXT_DIM, false);
    }

    private void drawIconLabel(GuiGraphics g, Font font, int x, int y, int w, boolean hov) {
        String lbl = TranslatedStrings.get(LangKeys.ICON);
        g.drawString(font, lbl, x + (w - font.width(lbl)) / 2, y + 4, hov ? TEXT_BR : TEXT, false);
    }
}
