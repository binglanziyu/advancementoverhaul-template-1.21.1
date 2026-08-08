package com.dreamer.ao.client.gui.render;

/**
 * 标签页渲染器：绘制顶部标签栏的视觉呈现。
 * <p>
 * 负责标签页的背景、文字、选中高亮、溢出下拉菜单和新建标签页按钮的渲染。
 * 支持标签页拖拽排序的视觉反馈。
 */
import com.dreamer.ao.LangKeys;
import com.dreamer.ao.client.gui.AdvancementScreen;
import com.dreamer.ao.client.gui.GuiUtils;
import com.dreamer.ao.client.gui.TranslatedStrings;
import com.dreamer.ao.client.gui.state.OverlayType;
import com.dreamer.ao.data.ClientDataStore;
import com.dreamer.ao.data.DataStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

import static com.dreamer.ao.client.gui.Theme.*;

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
        if (!screen.editMode) allTabs.remove(com.dreamer.ao.data.DataStore.TAB_VANILLA);

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

            boolean selected = tab.equals(screen.curTab);
            boolean hov = GuiUtils.inRect(mx, my, x, 0, w, TAB_H);
            g.fill(x, 0, x + w, TAB_H, selected ? BTN_HOV : (hov ? BTN : PANEL));
            g.renderOutline(x, 0, w, TAB_H, selected ? ACCENT : DIVIDER);
            g.drawString(font, label, x + 6, (TAB_H - 8) / 2, selected ? ACCENT : TEXT_DIM, false);
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

        // 左下角：当前标签（仅非"全部"时显示）+ 编辑模式提示（浅色，靠左）
        String leftText;
        if (screen.curTab == null) {
            leftText = TranslatedStrings.get(screen.editMode ? LangKeys.UI_HINT_CLOSE_EDIT : LangKeys.UI_HINT_OPEN_EDIT);
        } else {
            String tabName = "hidden".equals(screen.curTab)
                    ? TranslatedStrings.get(LangKeys.UI_TAB_HIDDEN)
                    : DataStore.getTabDisplayName(screen.curTab);
            String tabLabel = TranslatedStrings.get(LangKeys.UI_LABEL_TAB) + ": " + tabName;
            String editHint = TranslatedStrings.get(screen.editMode ? LangKeys.UI_HINT_CLOSE_EDIT : LangKeys.UI_HINT_OPEN_EDIT);
            leftText = tabLabel + "   " + editHint;
        }
        g.drawString(font, leftText, 8, y + (BOTTOM_H - 8) / 2, TEXT_DIM, false);

        // 右下角：自定义 / 原版 计数
        int totalV = 0, doneV = 0;
        for (var va : screen.vanillaAdvs) {
            if (store.isVanillaEnabled(va.id())) {
                totalV++;
                if (store.isCompleted(va.id())) doneV++;
            }
        }
        String vanillaText = TranslatedStrings.get(LangKeys.STAT_VANILLA) + ": " + doneV + "/" + totalV;

        int totalC = store.getAdvancements().size();
        int doneC = 0;
        for (String id : store.getAdvancements().keySet()) {
            if (store.isCompleted(id)) doneC++;
        }
        String customText = TranslatedStrings.get(LangKeys.STAT_CUSTOM) + ": " + doneC + "/" + totalC;

        int vW = font.width(vanillaText);
        int cW = font.width(customText);
        int gap = 16;
        int rightX = screen.width - 8 - cW - gap - vW;
        g.drawString(font, vanillaText, rightX, y + (BOTTOM_H - 8) / 2, TEXT_DIM, false);
        g.drawString(font, customText, rightX + vW + gap, y + (BOTTOM_H - 8) / 2, TEXT_DIM, false);
    }

    // ═══════════════ 按钮 ═══════════════

    /** 工具栏按钮布局条目：坐标、图标、是否高亮、tooltip 文本 key、点击标识。 */
    public static final class TBtn {
        public final int x, y, s;
        public final String icon;
        public final boolean active;
        public final String tooltipKey;
        public final String clickId;
        public TBtn(int x, int y, int s, String icon, boolean active, String tooltipKey, String clickId) {
            this.x = x; this.y = y; this.s = s; this.icon = icon;
            this.active = active; this.tooltipKey = tooltipKey; this.clickId = clickId;
        }
    }

    /** 工具栏按钮的点击标识常量（供渲染/命中共用，避免重复坐标）。 */
    public static final String
            C_CLOSE = "close", C_STATS = "stats", C_JOURNAL = "journal", C_TABS = "tabs",
            C_RESET = "reset", C_EXPORT = "export", C_HELP = "help", C_IMPORT = "import",
            C_DIM = "dim", C_AUTOLAYOUT = "autolayout", C_FTB = "ftb", C_EDIT = "edit";

    /**
     * 生成右侧工具栏的按钮布局（顶部一列 + 底部一列），
     * 渲染、tooltip、点击命中均使用同一份布局，确保按钮与文字始终一一对应。
     */
    public List<TBtn> buildToolbar() {
        int s = ICON_S, p = ICON_PAD, gap = ICON_GAP;
        boolean canEdit = Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasPermissions(2);
        List<TBtn> btns = new ArrayList<>();

        // 顶部列（从右上往左排）
        int cx = screen.width - p - s, cy = p;
        btns.add(new TBtn(cx, cy, s, "\u2715", false, LangKeys.BTN_TT_CLOSE, C_CLOSE)); cx -= s + gap;
        btns.add(new TBtn(cx, cy, s, "\u2691", false, LangKeys.BTN_TT_STATS, C_STATS)); cx -= s + gap;
        btns.add(new TBtn(cx, cy, s, "\uD83D\uDCD6", screen.overlay.current == OverlayType.JOURNAL, LangKeys.JOURNAL_BTN_TT, C_JOURNAL)); cx -= s + gap;
        btns.add(new TBtn(cx, cy, s, "\u2630", false, LangKeys.BTN_TT_TABS, C_TABS)); cx -= s + gap;
        btns.add(new TBtn(cx, cy, s, "\u21BB", false, LangKeys.BTN_TT_RESET, C_RESET));

        // 底部列（从右下往上排）
        int by = screen.height - BOTTOM_H - p - s;
        cx = screen.width - p - s;
        btns.add(new TBtn(cx, by, s, "\u2B07", false, LangKeys.BTN_TT_EXPORT, C_EXPORT)); by -= s + gap;
        btns.add(new TBtn(cx, by, s, "\u003F", screen.showHelp, LangKeys.BTN_TT_HELP, C_HELP)); by -= s + gap;
        btns.add(new TBtn(cx, by, s, "\u2B06", false, LangKeys.BTN_TT_IMPORT, C_IMPORT)); by -= s + gap;
        btns.add(new TBtn(cx, by, s, "\u2742", screen.showDim, LangKeys.BTN_TT_DIMENSION, C_DIM)); by -= s + gap;
        if (canEdit && screen.editMode) {
            btns.add(new TBtn(cx, by, s, "\u2605", false, LangKeys.BTN_TT_AUTOLAYOUT, C_AUTOLAYOUT)); by -= s + gap;
        }
        if (canEdit && screen.editMode && com.dreamer.ao.compat.ftb.FtbQuestsBridge.isLoaded()) {
            String ftbIcon = switch (com.dreamer.ao.client.gui.AdvancementScreen.ftbNotifMode) {
                case 1 -> "\u2205";
                case 2 -> "\u21C4";
                default -> "\u25C9";
            };
            btns.add(new TBtn(cx, by, s, ftbIcon, com.dreamer.ao.client.gui.AdvancementScreen.ftbNotifMode != 0, LangKeys.BTN_TT_FTB_MODE, C_FTB)); by -= s + gap;
        }
        if (canEdit) {
            btns.add(new TBtn(cx, by, s, "\u270E", screen.editMode, LangKeys.BTN_TT_EDITMODE, C_EDIT));
        }
        return btns;
    }

    public void renderButtons(GuiGraphics g, int mx, int my) {
        Font font = screen.getFont();
        for (TBtn b : buildToolbar()) {
            boolean hov = GuiUtils.inRect(mx, my, b.x, b.y, b.s, b.s);
            GuiUtils.drawIconBtn(g, font, b.x, b.y, b.s, b.icon, hov, b.active);
        }
        // 复位按钮图标用缩放绘制（其余均为普通字符）
        for (TBtn b : buildToolbar()) {
            if (b.clickId.equals(C_RESET)) {
                boolean hov = GuiUtils.inRect(mx, my, b.x, b.y, b.s, b.s);
                g.pose().pushPose();
                g.pose().translate(b.x + b.s / 2.0, b.y + b.s / 2.0, 0);
                g.pose().scale(1.6f, 1.6f, 1);
                g.drawString(font, b.icon, -font.width(b.icon) / 2, -4, hov ? ACCENT : TEXT, false);
                g.pose().popPose();
            }
        }
        renderButtonTooltips(g, mx, my);
    }

    private void renderButtonTooltips(GuiGraphics g, int mx, int my) {
        if (screen.hasOv()) return;
        Font font = screen.getFont();
        int sw = screen.width, sh = screen.height;
        for (TBtn b : buildToolbar()) {
            GuiUtils.drawHoverTooltip(g, font, mx, my, b.x, b.y, b.s, b.s,
                    Component.translatable(b.tooltipKey).getString(), sw, sh);
        }
    }

    public void setDragVisualX(int x) { this.dragVisualX = x; }
}