package com.example.advancementoverhaul.client.gui.render;

/**
 * 标签页渲染器：绘制顶部标签栏的视觉呈现。
 * <p>
 * 负责标签页的背景、文字、选中高亮、溢出下拉菜单和新建标签页按钮的渲染。
 * 支持标签页拖拽排序的视觉反馈。
 */
import com.example.advancementoverhaul.LangKeys;
import com.example.advancementoverhaul.client.gui.AdvancementScreen;
import com.example.advancementoverhaul.client.gui.GuiUtils;
import com.example.advancementoverhaul.client.gui.TranslatedStrings;
import com.example.advancementoverhaul.client.gui.state.OverlayState.Ov;
import com.example.advancementoverhaul.data.ClientDataStore;
import com.example.advancementoverhaul.data.DataStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

import static com.example.advancementoverhaul.client.gui.Theme.*;

public class TabRenderer {

    private final AdvancementScreen screen;
    private final List<String> cachedBarTabs = new ArrayList<>();
    private final List<String> cachedOverflowTabs = new ArrayList<>();
    private int dragVisualX = -1;

    public TabRenderer(AdvancementScreen screen) { this.screen = screen; }

    public List<String> getBarTabs() { return cachedBarTabs; }
    public boolean hasOverflow() { return !cachedOverflowTabs.isEmpty(); }
    public List<String> getOverflowTabs() { return cachedOverflowTabs; }

    // ═══════════════ 标签栏 ═══════════════

    public void renderTabs(GuiGraphics g, int mx, int my) {
        ClientDataStore store = ClientDataStore.getInstance();
        Font font = screen.getFont();

        g.fill(0, 0, screen.width, TAB_H, PANEL);
        g.fill(0, TAB_H - 1, screen.width, TAB_H, DIVIDER);

        List<String> allTabs = new ArrayList<>(store.getTabs());
        // 非编辑模式下隐藏"原有成就"分类
        if (!screen.editMode) allTabs.remove(com.example.advancementoverhaul.data.DataStore.TAB_VANILLA);

        String allLabel = TranslatedStrings.get(LangKeys.ALL);
        int allW = font.width(allLabel) + 12;

        int[] tabWidths = new int[allTabs.size()];
        for (int i = 0; i < allTabs.size(); i++) {
            String tab = allTabs.get(i);
            int d = store.getTabTotalCount(tab);
            String displayName = DataStore.getTabDisplayName(tab);
            String label = d > 0 ? displayName + " " + store.getTabCompletedCount(tab) + "/" + d : displayName;
            tabWidths[i] = font.width(label) + 12;
        }
        int hiddenW = screen.editMode ? font.width(Component.translatable(LangKeys.HIDDEN).getString()) + 12 : 0;
        int addBtnW = 20;
        int editBtnsW = screen.editMode ? (hiddenW + 3 + addBtnW + 3) : 0;
        int overflowBtnW = 20;

        // FIX: 预留右上角按钮空间（关闭/统计/标签管理/复位 四个按钮 + 边距）
        int topRightReserved = 4 * ICON_S + 3 * ICON_GAP + ICON_PAD + 4;
        int totalAvail = screen.width - 4 - topRightReserved - editBtnsW;

        cachedBarTabs.clear();
        cachedOverflowTabs.clear();

        int totalNeeded = allW + 3;
        for (int w : tabWidths) totalNeeded += w + 3;

        if (totalNeeded <= totalAvail) {
            cachedBarTabs.addAll(allTabs);
        } else {
            // 需要预留下拉按钮空间
            int availForBarTabs = totalAvail - (overflowBtnW + 3);
            int usedW = allW + 3;
            boolean overflowStarted = false;
            for (int i = 0; i < allTabs.size(); i++) {
                int tw = tabWidths[i] + 3;
                if (!overflowStarted && usedW + tw <= availForBarTabs) {
                    cachedBarTabs.add(allTabs.get(i));
                    usedW += tw;
                } else {
                    overflowStarted = true;
                    cachedOverflowTabs.add(allTabs.get(i));
                }
            }
        }

        // ── 从左到右累加 x（保证渲染和点击位置一致）──
        int x = 4;

        // "全部"按钮
        boolean allSel = screen.curTab == null;
        boolean allHov = GuiUtils.inRect(mx, my, x, 0, allW, TAB_H);
        g.fill(x, 0, x + allW, TAB_H, allSel ? BTN_HOV : (allHov ? BTN : PANEL));
        g.renderOutline(x, 0, allW, TAB_H, allSel ? ACCENT : DIVIDER);
        g.drawString(font, allLabel, x + 6, (TAB_H - 8) / 2, allSel ? ACCENT : TEXT_DIM, false);
        x += allW + 3;

        // 标签按钮
        for (int i = 0; i < cachedBarTabs.size(); i++) {
            String tab = cachedBarTabs.get(i);
            int d = store.getTabTotalCount(tab);
            String displayName = DataStore.getTabDisplayName(tab);
            String label = d > 0 ? displayName + " " + store.getTabCompletedCount(tab) + "/" + d : displayName;
            int w = font.width(label) + 12;

            int drawX = x;
            if (screen.tabDrag.dragIdx == i && screen.tabDrag.dragMoved) drawX = dragVisualX;

            boolean selected = tab.equals(screen.curTab);
            boolean hov = GuiUtils.inRect(mx, my, drawX, 0, w, TAB_H);
            g.fill(drawX, 0, drawX + w, TAB_H, selected ? BTN_HOV : (hov ? BTN : PANEL));
            g.renderOutline(drawX, 0, w, TAB_H, selected ? ACCENT : DIVIDER);
            g.drawString(font, label, drawX + 6, (TAB_H - 8) / 2, selected ? ACCENT : TEXT_DIM, false);
            x += w + 3;
        }

        // 溢出下拉按钮
        if (!cachedOverflowTabs.isEmpty()) {
            screen.tabDrag.overflowDDX = x;
            // FIX: 下拉菜单宽度=160，钳位防止超出屏幕右边缘
            if (screen.tabDrag.overflowDDX + 160 > screen.width) {
                screen.tabDrag.overflowDDX = screen.width - 162;
            }
            boolean ddHov = GuiUtils.inRect(mx, my, x, 0, overflowBtnW, TAB_H);
            g.fill(x, 0, x + overflowBtnW, TAB_H, screen.tabDrag.overDDOpen ? BTN_HOV : (ddHov ? BTN : PANEL));
            g.renderOutline(x, 0, overflowBtnW, TAB_H, screen.tabDrag.overDDOpen ? ACCENT : DIVIDER);
            g.drawString(font, "\u25BE", x + 6, (TAB_H - 8) / 2, screen.tabDrag.overDDOpen ? ACCENT : TEXT_DIM, false);
            x += overflowBtnW + 3;
        } else {
            screen.tabDrag.overflowDDX = -1;
        }

        // 编辑模式右侧按钮
        if (screen.editMode) {
            boolean hiddenHov = GuiUtils.inRect(mx, my, x, 0, hiddenW, TAB_H);
            boolean hiddenSel = "hidden".equals(screen.curTab);
            g.fill(x, 0, x + hiddenW, TAB_H, hiddenSel ? BTN_HOV : (hiddenHov ? BTN : PANEL));
            g.renderOutline(x, 0, hiddenW, TAB_H, hiddenSel ? ACCENT : DIVIDER);
            g.drawString(font, Component.translatable(LangKeys.HIDDEN).getString(),
                    x + 6, (TAB_H - 8) / 2, hiddenSel ? ACCENT : TEXT_DIM, false);
            x += hiddenW + 3;

            boolean addHov = GuiUtils.inRect(mx, my, x, 0, addBtnW, TAB_H);
            g.fill(x, 0, x + addBtnW, TAB_H, addHov ? BTN_HOV : PANEL);
            g.renderOutline(x, 0, addBtnW, TAB_H, DIVIDER);
            g.drawString(font, "+", x + 6, (TAB_H - 8) / 2, addHov ? ACCENT : TEXT_DIM, false);
        }

        if (screen.tabDrag.overDDOpen && !cachedOverflowTabs.isEmpty())
            renderOverflowDropdown(g, mx, my);
    }

    private void renderOverflowDropdown(GuiGraphics g, int mx, int my) {
        Font font = screen.getFont();
        int ddX = screen.tabDrag.overflowDDX, mw = 160;
        int availH = screen.height - TAB_H - 4;
        int maxVisible = Math.min(cachedOverflowTabs.size(), availH / 22);
        int showH = maxVisible * 22 + 4;

        g.fill(ddX, TAB_H, ddX + mw, TAB_H + showH, PANEL);
        g.renderOutline(ddX, TAB_H, mw, showH, DIVIDER);
        g.enableScissor(ddX + 1, TAB_H + 2, ddX + mw - 1, TAB_H + showH - 2);
        int iy = TAB_H + 2 - screen.tabDrag.overflowScroll;
        for (String tab : cachedOverflowTabs) {
            if (iy + 22 > TAB_H && iy < TAB_H + showH) {
                boolean hov = GuiUtils.inRect(mx, my, ddX + 2, iy, mw - 4, 22);
                boolean selected = tab.equals(screen.curTab);
                if (hov) g.fill(ddX + 2, iy, ddX + mw - 2, iy + 22, CTX_HOV);
                String overflowDisplayName = DataStore.getTabDisplayName(tab);
                g.drawString(font, GuiUtils.truncate(font, overflowDisplayName, mw - 20),
                        ddX + 8, iy + 7, selected ? ACCENT : TEXT, false);
            }
            iy += 22;
        }
        g.disableScissor();
    }

    // ═══════════════ 底栏 ═══════════════

    public void renderBottom(GuiGraphics g, int mx, int my) {
        Font font = screen.getFont();
        ClientDataStore store = ClientDataStore.getInstance();
        int y = screen.height - BOTTOM_H;

        g.fill(0, y, screen.width, screen.height, PANEL);
        g.fill(0, y, screen.width, y + 1, DIVIDER);

        int x = 8;
        int totalC = store.getAdvancements().size();
        int doneC = 0;
        for (String id : store.getAdvancements().keySet()) {
            if (store.isCompleted(id)) doneC++;
        }
        String customText = TranslatedStrings.get(LangKeys.STAT_CUSTOM) + ": " + doneC + "/" + totalC;
        g.drawString(font, customText, x, y + (BOTTOM_H - 8) / 2, TEXT_DIM, false);
        x += font.width(customText) + 16;

        int totalV = 0, doneV = 0;
        for (var va : screen.vanillaAdvs) {
            if (store.isVanillaEnabled(va.id())) {
                totalV++;
                if (store.isCompleted(va.id())) doneV++;
            }
        }
        String vanillaText = TranslatedStrings.get(LangKeys.STAT_VANILLA) + ": " + doneV + "/" + totalV;
        g.drawString(font, vanillaText, x, y + (BOTTOM_H - 8) / 2, TEXT_DIM, false);

        // 帮助按钮（右下角）
        String helpIcon = "?";
        int helpW = font.width(helpIcon) + 10;
        int helpX = screen.width - helpW - 8;
        boolean helpHov = GuiUtils.inRect(mx, my, helpX, y, helpW, BOTTOM_H);
        boolean helpActive = screen.showHelp;
        g.fill(helpX, y, helpX + helpW, y + BOTTOM_H, helpActive ? BTN_HOV : (helpHov ? BTN : PANEL));
        g.renderOutline(helpX, y, helpW, BOTTOM_H, helpActive ? ACCENT : DIVIDER);
        g.drawString(font, helpIcon, helpX + 5, y + (BOTTOM_H - 8) / 2, helpActive ? ACCENT : (helpHov ? ACCENT : TEXT_DIM), false);
    }

    // ═══════════════ 按钮 ═══════════════

    public void renderButtons(GuiGraphics g, int mx, int my) {
        Font font = screen.getFont();
        int s = ICON_S, p = ICON_PAD, gap = ICON_GAP;

        int cx = screen.width - p - s, cy = p;
        GuiUtils.drawIconBtn(g, font, cx, cy, s, "\u2715",
                GuiUtils.inRect(mx, my, cx, cy, s, s), false);
        cx -= s + gap;
        GuiUtils.drawIconBtn(g, font, cx, cy, s, "\u2691",
                GuiUtils.inRect(mx, my, cx, cy, s, s), screen.overlay.current == Ov.STATS);
        cx -= s + gap;
        // 标签管理按钮
        GuiUtils.drawIconBtn(g, font, cx, cy, s, "\u2630",
                GuiUtils.inRect(mx, my, cx, cy, s, s), false);
        cx -= s + gap;

        boolean resetHov = GuiUtils.inRect(mx, my, cx, cy, s, s);
        g.fill(cx, cy, cx + s, cy + s, resetHov ? BTN_HOV : BTN);
        g.renderOutline(cx, cy, s, s, DIVIDER);
        g.pose().pushPose();
        g.pose().translate(cx + s / 2.0, cy + s / 2.0, 0);
        g.pose().scale(1.6f, 1.6f, 1);
        String resetIcon = "\u21BB";
        g.drawString(font, resetIcon, -font.width(resetIcon) / 2, -4, resetHov ? ACCENT : TEXT, false);
        g.pose().popPose();

        boolean canEdit = Minecraft.getInstance().player != null && !Minecraft.getInstance().player.isSpectator();
        int by = screen.height - BOTTOM_H - p - s;
        cx = screen.width - p - s;

        GuiUtils.drawIconBtn(g, font, cx, by, s, "\u2B07",
                GuiUtils.inRect(mx, my, cx, by, s, s), false);
        by -= s + gap;
        GuiUtils.drawIconBtn(g, font, cx, by, s, "\u2B06",
                GuiUtils.inRect(mx, my, cx, by, s, s), false);
        by -= s + gap;
        GuiUtils.drawIconBtn(g, font, cx, by, s, "\u2742",
                GuiUtils.inRect(mx, my, cx, by, s, s), screen.showDim);
        by -= s + gap;
        if (canEdit && screen.editMode) {
            GuiUtils.drawIconBtn(g, font, cx, by, s, "\u2605",
                    GuiUtils.inRect(mx, my, cx, by, s, s), false);
            by -= s + gap;
        }
        // FTB 通知模式切换（仅编辑模式 + FTB Quests 已加载）
        if (canEdit && screen.editMode && com.example.advancementoverhaul.compat.FtbQuestsBridge.isLoaded()) {
            String ftbLabel = switch (com.example.advancementoverhaul.client.gui.AdvancementScreen.ftbNotifMode) {
                case 1 -> "\u2205";   // ⊘ 关闭
                case 2 -> "\u21C4";   // ⇄ 替换
                default -> "\u25C9";  // ◎ 默认
            };
            GuiUtils.drawIconBtn(g, font, cx, by, s, ftbLabel,
                    GuiUtils.inRect(mx, my, cx, by, s, s), com.example.advancementoverhaul.client.gui.AdvancementScreen.ftbNotifMode != 0);
            by -= s + gap;
        }
        if (canEdit)
            GuiUtils.drawIconBtn(g, font, cx, by, s, "\u270E",
                    GuiUtils.inRect(mx, my, cx, by, s, s), screen.editMode);

        renderButtonTooltips(g, mx, my);
    }

    private void renderButtonTooltips(GuiGraphics g, int mx, int my) {
        if (screen.hasOv()) return;
        Font font = screen.getFont();
        int s = ICON_S, p = ICON_PAD, gap = ICON_GAP;
        int sw = screen.width, sh = screen.height;

        int cx = sw - p - s, cy = p;
        GuiUtils.drawHoverTooltip(g, font, mx, my, cx, cy, s, s,
                TranslatedStrings.get(LangKeys.BTN_TT_CLOSE), sw, sh);
        cx -= s + gap;
        GuiUtils.drawHoverTooltip(g, font, mx, my, cx, cy, s, s,
                Component.translatable(LangKeys.BTN_TT_STATS).getString(), sw, sh);
        cx -= s + gap;
        // 标签管理 tooltip
        GuiUtils.drawHoverTooltip(g, font, mx, my, cx, cy, s, s,
                Component.translatable(LangKeys.BTN_TT_TABS).getString(), sw, sh);
        cx -= s + gap;
        GuiUtils.drawHoverTooltip(g, font, mx, my, cx, cy, s, s,
                Component.translatable(LangKeys.BTN_TT_RESET).getString(), sw, sh);

        boolean canEdit = Minecraft.getInstance().player != null && !Minecraft.getInstance().player.isSpectator();
        int by = sh - BOTTOM_H - p - s;
        cx = sw - p - s;
        GuiUtils.drawHoverTooltip(g, font, mx, my, cx, by, s, s,
                Component.translatable(LangKeys.BTN_TT_EXPORT).getString(), sw, sh);
        by -= s + gap;
        GuiUtils.drawHoverTooltip(g, font, mx, my, cx, by, s, s,
                Component.translatable(LangKeys.BTN_TT_IMPORT).getString(), sw, sh);
        by -= s + gap;
        GuiUtils.drawHoverTooltip(g, font, mx, my, cx, by, s, s,
                Component.translatable(LangKeys.BTN_TT_DIMENSION).getString(), sw, sh);
        by -= s + gap;
        if (canEdit && screen.editMode) {
            GuiUtils.drawHoverTooltip(g, font, mx, my, cx, by, s, s,
                    Component.translatable(LangKeys.BTN_TT_AUTOLAYOUT).getString(), sw, sh);
            by -= s + gap;
        }
        // FTB 通知模式 tooltip
        if (canEdit && screen.editMode && com.example.advancementoverhaul.compat.FtbQuestsBridge.isLoaded()) {
            String modeKey = switch (com.example.advancementoverhaul.client.gui.AdvancementScreen.ftbNotifMode) {
                case 1 -> LangKeys.FTB_MODE_DISABLE;
                case 2 -> LangKeys.FTB_MODE_REPLACE;
                default -> LangKeys.FTB_MODE_DEFAULT;
            };
            GuiUtils.drawHoverTooltip(g, font, mx, my, cx, by, s, s,
                    Component.translatable(modeKey).getString(), sw, sh);
            by -= s + gap;
        }
        if (canEdit)
            GuiUtils.drawHoverTooltip(g, font, mx, my, cx, by, s, s,
                    Component.translatable(LangKeys.BTN_TT_EDITMODE).getString(), sw, sh);
    }

    public void setDragVisualX(int x) { this.dragVisualX = x; }
}