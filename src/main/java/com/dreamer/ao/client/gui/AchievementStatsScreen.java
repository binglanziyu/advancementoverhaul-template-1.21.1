package com.dreamer.ao.client.gui;

import com.dreamer.ao.LangKeys;
import com.dreamer.ao.data.ClientDataStore;
import com.dreamer.ao.data.DataStore;
import com.dreamer.ao.data.model.CustomAdvancement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

import static com.dreamer.ao.client.gui.Theme.*;

/**
 * 成就统计面板：按标签页展示成就完成情况。
 * <p>
 * 左侧边栏显示各标签页及完成数量，右侧卡片列表展示对应成就详情。
 * 使用项目统一的 LangKeys 体系进行国际化，颜色常量采用可读的十六进制格式。
 */
public class AchievementStatsScreen extends Screen {

    // ── Layout constants ──
    private static final int SIDEBAR_W = 120;
    private static final int HEADER_H = 50;
    private static final int BOTTOM_H = 20;
    private static final int PADDING = 14;
    private static final int CARD_H = 36;
    private static final int CARD_GAP = 6;

    // ── Color palette (dark theme, unified with Theme.java) ──
    private static final int COLOR_BG            = BG;
    private static final int COLOR_OVERLAY_TOP   = 0x60323242;
    private static final int COLOR_DIVIDER       = DIVIDER;
    private static final int COLOR_TEXT_PRIMARY  = TEXT;
    private static final int COLOR_TEXT_SECONDARY= TEXT_DIM;
    private static final int COLOR_TEXT_EMPHASIS = TEXT_BR;
    private static final int COLOR_TEXT_WHITE    = TEXT_BR;
    private static final int COLOR_ACCENT_GREEN  = ACCENT;
    private static final int COLOR_PROGRESS_BG   = BTN;
    private static final int COLOR_SIDEBAR_BG    = BAR;
    private static final int COLOR_SIDEBAR_HOV   = BTN_HOV;
    private static final int COLOR_CARD_BG       = CARD;
    private static final int COLOR_CLOSE_BG      = 0x303A3A52;
    private static final int COLOR_CLOSE_HOV     = 0x604A4A68;
    private static final int COLOR_SCROLL_TRACK  = 0x20242438;
    private static final int COLOR_SCROLL_THUMB  = DIVIDER;
    private static final int COLOR_UNAVAILABLE   = TEXT_DIM;

    private final ClientDataStore store = ClientDataStore.getInstance();
    private String selectedTab;
    private final List<String> tabList = new ArrayList<>();
    private int sidebarScrollOff;
    private int cardsScrollOff;
    private int sidebarMaxScroll;
    private int cardsMaxScroll;

    public AchievementStatsScreen() {
        super(Component.translatable(LangKeys.STATS_TITLE));
    }

    @Override
    protected void init() {
        super.init();
        tabList.clear();
        for (String tab : store.getTabs()) {
            if (DataStore.TAB_VANILLA.equals(tab) || DataStore.TAB_DEFAULT.equals(tab)) continue;
            tabList.add(tab);
        }
        if (!tabList.isEmpty() && (selectedTab == null || !tabList.contains(selectedTab))) {
            selectedTab = tabList.get(0);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, COLOR_BG);
        // Gradient overlays top and bottom
        g.fillGradient(0, 0, width, 60, COLOR_OVERLAY_TOP, 0x00000000);
        g.fillGradient(0, height - BOTTOM_H - 10, width, height, 0x00000000, COLOR_OVERLAY_TOP);

        renderHeader(g, mouseX, mouseY);
        renderSidebar(g, mouseX, mouseY);
        renderCards(g, mouseX, mouseY);
        renderFooter(g);
        renderScrollbars(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    // ═══════════════ Header ═══════════════

    private void renderHeader(GuiGraphics g, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        String title = getTitle().getString();
        int titleW = font.width(title);
        g.drawString(font, title, (width - titleW) / 2, 14, COLOR_TEXT_EMPHASIS, false);

        int totalDone = store.getCompletedCount();
        int totalAll = store.getTotalCount();
        String summary = totalDone + " / " + totalAll;
        int summaryW = font.width(summary);
        g.drawString(font, summary, (width - summaryW) / 2, 28, COLOR_TEXT_SECONDARY, false);

        // Close button
        int closeX = width - 30;
        boolean hov = GuiUtils.inRect(mouseX, mouseY, closeX - 2, 2, 20, 20);
        g.fill(closeX, 4, closeX + 16, 20, hov ? COLOR_CLOSE_HOV : COLOR_CLOSE_BG);
        g.drawString(font, "\u2715", closeX + 4, 6, hov ? COLOR_TEXT_EMPHASIS : COLOR_TEXT_SECONDARY, false);

        // Header divider
        g.fill(SIDEBAR_W, HEADER_H - 1, width, HEADER_H, COLOR_DIVIDER);
    }

    // ═══════════════ Sidebar ═══════════════

    private void renderSidebar(GuiGraphics g, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        int contentTop = HEADER_H;
        int contentBottom = height - BOTTOM_H;

        g.fill(0, contentTop, SIDEBAR_W, contentBottom, COLOR_SIDEBAR_BG);
        g.fill(SIDEBAR_W - 1, contentTop, SIDEBAR_W, contentBottom, COLOR_DIVIDER);

        g.enableScissor(0, contentTop, SIDEBAR_W, contentBottom);
        int y = contentTop + PADDING - sidebarScrollOff;

        // "All" tab
        renderSidebarItem(g, font, mouseX, mouseY, y, null, "\u2601",
                Component.translatable(LangKeys.STATS_ALL_TAB).getString());
        y += 36;

        for (String tab : tabList) {
            renderSidebarItem(g, font, mouseX, mouseY, y, tab, "\u25cf", DataStore.getTabDisplayName(tab));
            y += 36;
        }

        int totalH = (tabList.size() + 1) * 36 + PADDING;
        sidebarMaxScroll = Math.max(0, totalH - (contentBottom - contentTop));
        if (sidebarScrollOff > sidebarMaxScroll) sidebarScrollOff = sidebarMaxScroll;
        g.disableScissor();
    }

    private void renderSidebarItem(GuiGraphics g, Font font, int mouseX, int mouseY,
                                    int y, String tab, String icon, String name) {
        boolean sel = Objects.equals(tab, selectedTab);
        boolean hov = GuiUtils.inRect(mouseX, mouseY, 4, y, 112, 32);
        if (sel) {
            g.fill(6, y + 2, 114, y + 30, COLOR_DIVIDER);
            g.fill(6, y + 6, 10, y + 26, COLOR_ACCENT_GREEN);
        } else if (hov) {
            g.fill(6, y + 2, 114, y + 30, COLOR_SIDEBAR_HOV);
        }
        int textColor = sel ? COLOR_TEXT_EMPHASIS : COLOR_TEXT_PRIMARY;
        g.drawString(font, icon, 16, y + 8, textColor, false);
        g.drawString(font, GuiUtils.truncate(font, name, 60), 32, y + 8, textColor, false);

        int done = tab == null ? store.getCompletedCount() : store.getTabCompletedCount(tab);
        int total = tab == null ? store.getTotalCount() : store.getTabTotalCount(tab);
        String prog = done + "/" + total;
        int progW = font.width(prog);
        g.drawString(font, prog, 104 - progW, y + 8, COLOR_TEXT_SECONDARY, false);
    }

    // ═══════════════ Cards ═══════════════

    private void renderCards(GuiGraphics g, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        int contentX = SIDEBAR_W + PADDING;
        int contentW = width - contentX - PADDING;
        int contentTop = HEADER_H + PADDING;
        int contentBottom = height - BOTTOM_H - PADDING;

        // Progress bar
        int done = selectedTab == null ? store.getCompletedCount() : store.getTabCompletedCount(selectedTab);
        int total = selectedTab == null ? store.getTotalCount() : store.getTabTotalCount(selectedTab);
        float pct = total > 0 ? (float) done / (float) total : 0f;
        int barH = 10;
        int barY = contentTop;

        g.fill(contentX, barY, contentX + contentW, barY + barH, COLOR_PROGRESS_BG);
        if (pct > 0f) {
            int fillW = (int) (contentW * pct);
            g.fill(contentX, barY, contentX + fillW, barY + barH, COLOR_ACCENT_GREEN);
        }
        String pctStr = String.format("%.0f%%", pct * 100f);
        int pctW = font.width(pctStr);
        g.drawString(font, pctStr, contentX + (contentW - pctW) / 2, barY + 1, COLOR_TEXT_WHITE, false);

        // Card list
        List<CustomAdvancement> advs = getAdvancementsForTab(selectedTab);
        int cardY = barY + barH + PADDING - cardsScrollOff;

        g.enableScissor(contentX, contentTop, contentX + contentW, contentBottom);
        if (advs.isEmpty()) {
            String empty = Component.translatable(LangKeys.STATS_EMPTY).getString();
            int ew = font.width(empty);
            g.drawString(font, empty, contentX + (contentW - ew) / 2, contentTop + 40, COLOR_TEXT_SECONDARY, false);
        } else {
            for (CustomAdvancement adv : advs) {
                if (cardY + CARD_H < contentTop || cardY > contentBottom) {
                    cardY += CARD_H + CARD_GAP;
                    continue;
                }
                renderAchievementCard(g, font, contentX, cardY, contentW, adv,
                        store.isCompleted(adv.getId()));
                cardY += CARD_H + CARD_GAP;
            }
        }

        int totalH = advs.size() * (CARD_H + CARD_GAP) + barH + PADDING;
        cardsMaxScroll = Math.max(0, totalH - (contentBottom - contentTop));
        if (cardsScrollOff > cardsMaxScroll) cardsScrollOff = cardsMaxScroll;
        g.disableScissor();
    }

    private void renderAchievementCard(GuiGraphics g, Font font, int x, int y, int w,
                                        CustomAdvancement adv, boolean completed) {
        int cardBg = COLOR_CARD_BG;
        g.fill(x, y, x + w, y + CARD_H, cardBg);

        // Status indicator strip
        int statusColor = completed ? COLOR_ACCENT_GREEN : COLOR_UNAVAILABLE;
        g.fill(x, y + 6, x + 4, y + CARD_H - 6, statusColor);

        // Status icon
        String statusIcon = completed ? "\u2713" : "\u25cb";
        int iconColor = completed ? COLOR_ACCENT_GREEN : COLOR_TEXT_SECONDARY;
        g.drawString(font, statusIcon, x + 14, y + (CARD_H - 8) / 2, iconColor, false);

        // Name
        String name = adv.getName() != null ? adv.getName() : adv.getId();
        int maxNameW = w - 180;
        g.drawString(font, GuiUtils.truncate(font, name, maxNameW), x + 36,
                y + (CARD_H - 16) / 2, completed ? COLOR_TEXT_EMPHASIS : COLOR_TEXT_PRIMARY, false);

        // Tab + status metadata
        String tabLabel = adv.getTab() != null ? adv.getTab() : "\u2014";
        String statusLabel = Component.translatable(
                completed ? LangKeys.STAT_DONE : LangKeys.STATS_UNCOMPLETED).getString();
        String meta = tabLabel + "  \u00b7  " + (completed ? "\u2713 " : "\u25cb ") + statusLabel;
        int metaW = font.width(meta);
        g.drawString(font, meta, x + w - metaW - 16, y + (CARD_H - 16) / 2, COLOR_TEXT_SECONDARY, false);

        // Bottom divider
        g.fill(x + 36, y + CARD_H - 1, x + w - 16, y + CARD_H, COLOR_DIVIDER);
    }

    private List<CustomAdvancement> getAdvancementsForTab(String tab) {
        if (tab == null) {
            var all = new ArrayList<>(store.getAdvancements().values());
            all.sort(Comparator.comparing(a -> a.getName() != null ? a.getName() : a.getId()));
            return all;
        }
        return store.getAdvancementsByTab(tab);
    }

    // ═══════════════ Footer ═══════════════

    private void renderFooter(GuiGraphics g) {
        Font font = Minecraft.getInstance().font;
        int footerY = height - BOTTOM_H;
        g.fill(0, footerY - 1, width, footerY, COLOR_DIVIDER);

        String total = Component.translatable(LangKeys.STATS_CUSTOM).getString()
                + ": " + store.getTotalCount() + "    "
                + Component.translatable(LangKeys.STAT_DONE).getString()
                + ": " + store.getCompletedCount();
        g.drawString(font, total, PADDING, footerY + 4, COLOR_TEXT_SECONDARY, false);

        String hint = Component.translatable(LangKeys.STATS_CLICK_HINT).getString();
        int hintW = font.width(hint);
        g.drawString(font, hint, width - hintW - PADDING, footerY + 4, COLOR_TEXT_SECONDARY, false);
    }

    // ═══════════════ Scrollbars ═══════════════

    private void renderScrollbars(GuiGraphics g) {
        int contentTop = HEADER_H;
        int sidebarBottom = height - BOTTOM_H;

        // Sidebar scrollbar
        if (sidebarMaxScroll > 0) {
            int trackH = sidebarBottom - contentTop;
            int thumbH = Math.max(16, trackH * trackH / (trackH + sidebarMaxScroll));
            int thumbY = contentTop + sidebarScrollOff * (trackH - thumbH) / sidebarMaxScroll;
            g.fill(SIDEBAR_W - 3, contentTop, SIDEBAR_W - 1, sidebarBottom, COLOR_SCROLL_TRACK);
            g.fill(SIDEBAR_W - 3, thumbY,  SIDEBAR_W - 1, thumbY + thumbH, COLOR_SCROLL_THUMB);
        }

        // Cards scrollbar
        if (cardsMaxScroll > 0) {
            int contentX = SIDEBAR_W + PADDING;
            int contentW = width - contentX - PADDING;
            int cardsTop = HEADER_H + PADDING;
            int trackH = height - BOTTOM_H - PADDING - cardsTop;
            int thumbH = Math.max(16, trackH * trackH / (trackH + cardsMaxScroll));
            int thumbY = cardsTop + cardsScrollOff * (trackH - thumbH) / cardsMaxScroll;
            int barX = contentX + contentW + 2;
            g.fill(barX, cardsTop, barX + 2, cardsTop + trackH, COLOR_SCROLL_TRACK);
            g.fill(barX, thumbY, barX + 2, thumbY + thumbH, COLOR_SCROLL_THUMB);
        }
    }

    // ═══════════════ Input ═══════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int mx = (int) mouseX;
        int my = (int) mouseY;

        // Close button
        if (GuiUtils.inRect(mx, my, width - 32, 2, 20, 20)) {
            onClose();
            return true;
        }

        // Sidebar items
        int contentTop = HEADER_H;
        if (my >= contentTop && my < height - BOTTOM_H) {
            int y = contentTop + PADDING - sidebarScrollOff;
            if (GuiUtils.inRect(mx, my, 4, y, 112, 32)) {
                selectedTab = null;
                cardsScrollOff = 0;
                GuiUtils.playClickSound();
                return true;
            }
            y += 36;
            for (String tab : tabList) {
                if (GuiUtils.inRect(mx, my, 4, y, 112, 32)) {
                    if (!Objects.equals(selectedTab, tab)) {
                        selectedTab = tab;
                        cardsScrollOff = 0;
                        GuiUtils.playClickSound();
                    }
                    return true;
                }
                y += 36;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < SIDEBAR_W) {
            sidebarScrollOff -= (int) (scrollY * 20);
            sidebarScrollOff = Math.max(0, Math.min(sidebarScrollOff, sidebarMaxScroll));
        } else {
            cardsScrollOff -= (int) (scrollY * 20);
            cardsScrollOff = Math.max(0, Math.min(cardsScrollOff, cardsMaxScroll));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
