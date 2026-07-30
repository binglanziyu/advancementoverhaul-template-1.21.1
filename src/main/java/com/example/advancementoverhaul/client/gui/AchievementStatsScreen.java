/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.Font
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.network.chat.Component
 */
package com.example.advancementoverhaul.client.gui;

import com.example.advancementoverhaul.client.gui.GuiUtils;
import com.example.advancementoverhaul.data.ClientDataStore;
import com.example.advancementoverhaul.data.model.CustomAdvancement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AchievementStatsScreen
extends Screen {
    private static final int BG = -252381447;
    private static final int OVERLAY = 1626403830;
    private static final int PANEL = -386336005;
    private static final int PANEL_BORDER = 1084274888;
    private static final int DIVIDER = 547403976;
    private static final int TEXT_PRIMARY = -11902344;
    private static final int TEXT_SECONDARY = -8349524;
    private static final int TEXT_EMPHASIS = -12956064;
    private static final int TEXT_WHITE = -985864;
    private static final int ACCENT_GREEN = -8672056;
    private static final int ACCENT_GREEN_GLOW = 1081846984;
    private static final int PROGRESS_BG = 547403976;
    private static final int PROGRESS_FILL = -8672056;
    private static final int SIDEBAR_W = 120;
    private static final int HEADER_H = 50;
    private static final int BOTTOM_H = 20;
    private static final int PADDING = 14;
    private static final int CARD_GAP = 6;
    private final ClientDataStore store = ClientDataStore.getInstance();
    private String selectedTab;
    private final List<String> tabList = new ArrayList<String>();
    private int sidebarScrollOff;
    private int cardsScrollOff;
    private int sidebarMaxScroll;
    private int cardsMaxScroll;

    public AchievementStatsScreen() {
        super((Component)Component.translatable((String)"advancementoverhaul.achievement_stats.title"));
    }

    protected void init() {
        super.init();
        this.tabList.clear();
        for (String tab : this.store.getTabs()) {
            if ("\u539f\u6709\u6210\u5c31".equals(tab) || "\u9ed8\u8ba4".equals(tab)) continue;
            this.tabList.add(tab);
        }
        if (!(this.tabList.isEmpty() || this.selectedTab != null && this.tabList.contains(this.selectedTab))) {
            this.selectedTab = this.tabList.get(0);
        }
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, -252381447);
        g.fillGradient(0, 0, this.width, 60, 1626403830, 0xFFFFFF);
        g.fillGradient(0, this.height - 20 - 10, this.width, this.height, 0xFFFFFF, 1626403830);
        this.renderHeader(g, mouseX, mouseY);
        this.renderSidebar(g, mouseX, mouseY);
        this.renderCards(g, mouseX, mouseY);
        this.renderFooter(g);
        this.renderScrollbars(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics g, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        String title = this.getTitle().getString();
        int titleW = font.width(title);
        g.pose().pushPose();
        g.pose().translate(0.0f, 0.0f, 0.0f);
        g.drawString(font, title, (this.width - titleW) / 2, 14, -12956064, false);
        int totalDone = this.store.getCompletedCount();
        int totalAll = this.store.getTotalCount();
        String summary = totalDone + " / " + totalAll;
        int summaryW = font.width(summary);
        g.drawString(font, summary, (this.width - summaryW) / 2, 28, -8349524, false);
        int closeX = this.width - 30;
        boolean hov = GuiUtils.inRect(mouseX, mouseY, closeX - 2, 2, 20, 20);
        g.fill(closeX, 4, closeX + 16, 20, hov ? 815839432 : 278968520);
        g.drawString(font, "\u2715", closeX + 4, 6, hov ? -12956064 : -8349524, false);
        g.fill(120, 49, this.width, 50, 547403976);
    }

    private void renderSidebar(GuiGraphics g, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        int contentTop = 50;
        int contentBottom = this.height - 20;
        g.fill(0, contentTop, 120, contentBottom, 278968520);
        g.fill(119, contentTop, 120, contentBottom, 547403976);
        g.enableScissor(0, contentTop, 120, contentBottom);
        int y = contentTop + 14 - this.sidebarScrollOff;
        this.renderSidebarItem(g, font, mouseX, mouseY, y, null, "\u2601", "\u5168\u90e8");
        y += 36;
        for (String tab : this.tabList) {
            this.renderSidebarItem(g, font, mouseX, mouseY, y, tab, "\u25cf", tab);
            y += 36;
        }
        int totalH = (this.tabList.size() + 1) * 36 + 14;
        this.sidebarMaxScroll = Math.max(0, totalH - (contentBottom - contentTop));
        if (this.sidebarScrollOff > this.sidebarMaxScroll) {
            this.sidebarScrollOff = this.sidebarMaxScroll;
        }
        g.disableScissor();
    }

    private void renderSidebarItem(GuiGraphics g, Font font, int mouseX, int mouseY, int y, String tab, String icon, String name) {
        boolean sel = Objects.equals(tab, this.selectedTab);
        boolean hov = GuiUtils.inRect(mouseX, mouseY, 4, y, 112, 32);
        if (sel) {
            g.fill(6, y + 2, 114, y + 30, 547403976);
            g.fill(6, y + 6, 10, y + 26, -8672056);
        } else if (hov) {
            g.fill(6, y + 2, 114, y + 30, 278968520);
        }
        int textColor = sel ? -12956064 : -11902344;
        g.drawString(font, icon, 16, y + 8, textColor, false);
        g.drawString(font, GuiUtils.truncate(font, name, 60), 32, y + 8, textColor, false);
        int done = tab == null ? this.store.getCompletedCount() : this.store.getTabCompletedCount(tab);
        int total = tab == null ? this.store.getTotalCount() : this.store.getTabTotalCount(tab);
        String prog = done + "/" + total;
        int progW = font.width(prog);
        g.drawString(font, prog, 104 - progW, y + 8, -8349524, false);
    }

    private void renderCards(GuiGraphics g, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        int contentX = 134;
        int contentW = this.width - contentX - 14;
        int contentTop = 64;
        int contentBottom = this.height - 20 - 14;
        int done = this.selectedTab == null ? this.store.getCompletedCount() : this.store.getTabCompletedCount(this.selectedTab);
        int total = this.selectedTab == null ? this.store.getTotalCount() : this.store.getTabTotalCount(this.selectedTab);
        float pct = total > 0 ? (float)done / (float)total : 0.0f;
        int barH = 10;
        int barY = contentTop;
        g.fill(contentX, barY, contentX + contentW, barY + barH, 547403976);
        if (pct > 0.0f) {
            int fillW = (int)((float)contentW * pct);
            g.fill(contentX, barY, contentX + fillW, barY + barH, -8672056);
        }
        String pctStr = String.format("%.0f%%", Float.valueOf(pct * 100.0f));
        int pctW = font.width(pctStr);
        g.drawString(font, pctStr, contentX + (contentW - pctW) / 2, barY + 1, -985864, false);
        int cardY = barY + barH + 14 - this.cardsScrollOff;
        List<CustomAdvancement> advs = this.getAdvancementsForTab(this.selectedTab);
        g.enableScissor(contentX, contentTop, contentX + contentW, contentBottom);
        if (advs.isEmpty()) {
            String empty = Component.translatable((String)"advancementoverhaul.achievement_stats.empty").getString();
            int ew = font.width(empty);
            g.drawString(font, empty, contentX + (contentW - ew) / 2, contentTop + 40, -8349524, false);
        } else {
            for (CustomAdvancement adv : advs) {
                boolean completed = this.store.isCompleted(adv.getId());
                int cardH = 36;
                if (cardY + cardH < contentTop || cardY > contentBottom) {
                    cardY += cardH + 6;
                    continue;
                }
                this.renderAchievementCard(g, font, mouseX, mouseY, contentX, cardY, contentW, cardH, adv, completed);
                cardY += cardH + 6;
            }
        }
        int totalH = advs.size() * 42 + barH + 14;
        this.cardsMaxScroll = Math.max(0, totalH - (contentBottom - contentTop));
        if (this.cardsScrollOff > this.cardsMaxScroll) {
            this.cardsScrollOff = this.cardsMaxScroll;
        }
        g.disableScissor();
    }

    private void renderAchievementCard(GuiGraphics g, Font font, int mouseX, int mouseY, int x, int y, int w, int h, CustomAdvancement adv, boolean completed) {
        boolean hov = GuiUtils.inRect(mouseX, mouseY, x, y, w, h);
        int cardBg = hov ? 547403976 : 211859656;
        g.fill(x, y, x + w, y + h, cardBg);
        int statusColor = completed ? -8672056 : 1623249120;
        g.fill(x, y + 6, x + 4, y + h - 6, statusColor);
        String statusIcon = completed ? "\u2713" : "\u25cb";
        int iconColor = completed ? -8672056 : -8349524;
        g.drawString(font, statusIcon, x + 14, y + (h - 8) / 2, iconColor, false);
        String name = adv.getName() != null ? adv.getName() : adv.getId();
        int maxNameW = w - 180;
        g.drawString(font, GuiUtils.truncate(font, name, maxNameW), x + 36, y + (h - 16) / 2, completed ? -12956064 : -11902344, false);
        String tabLabel = adv.getTab() != null ? adv.getTab() : "\u2014";
        String meta = tabLabel + "  \u00b7  " + (completed ? "\u2713 \u5df2\u5b8c\u6210" : "\u25cb \u672a\u5b8c\u6210");
        int metaW = font.width(meta);
        g.drawString(font, meta, x + w - metaW - 16, y + (h - 16) / 2, completed ? -8349524 : -8349524, false);
        g.fill(x + 36, y + h - 1, x + w - 16, y + h, 547403976);
    }

    private List<CustomAdvancement> getAdvancementsForTab(String tab) {
        if (tab == null) {
            ArrayList<CustomAdvancement> all = new ArrayList<CustomAdvancement>(this.store.getAdvancements().values());
            all.sort(Comparator.comparing(a -> a.getName() != null ? a.getName() : a.getId()));
            return all;
        }
        return this.store.getAdvancementsByTab(tab);
    }

    private void renderFooter(GuiGraphics g) {
        Font font = Minecraft.getInstance().font;
        int footerY = this.height - 20;
        g.fill(0, footerY - 1, this.width, footerY, 547403976);
        String total = "\u81ea\u5b9a\u4e49\u6210\u5c31: " + this.store.getTotalCount() + "    \u5df2\u5b8c\u6210: " + this.store.getCompletedCount();
        g.drawString(font, total, 14, footerY + 4, -8349524, false);
        String hint = "\u70b9\u51fb\u5206\u7c7b\u67e5\u770b\u5404\u6807\u7b7e\u8fdb\u5ea6";
        int hintW = font.width(hint);
        g.drawString(font, hint, this.width - hintW - 14, footerY + 4, -8349524, false);
    }

    private void renderScrollbars(GuiGraphics g) {
        int contentTop = 50;
        int sidebarBottom = this.height - 20;
        if (this.sidebarMaxScroll > 0) {
            int trackH = sidebarBottom - contentTop;
            int thumbH = Math.max(16, (int)((float)trackH / (float)(trackH + this.sidebarMaxScroll) * (float)trackH));
            int thumbY = contentTop + (int)((float)this.sidebarScrollOff / (float)this.sidebarMaxScroll * (float)(trackH - thumbH));
            g.fill(117, contentTop, 119, sidebarBottom, 144750792);
            g.fill(117, thumbY, 119, thumbY + thumbH, 547403976);
        }
        if (this.cardsMaxScroll > 0) {
            int contentX = 134;
            int contentW = this.width - contentX - 14;
            int trackH = this.height - 20 - 14 - 64;
            int thumbH = Math.max(16, (int)((float)trackH / (float)(trackH + this.cardsMaxScroll) * (float)trackH));
            int cardsTop = 64;
            int thumbY = cardsTop + (int)((float)this.cardsScrollOff / (float)this.cardsMaxScroll * (float)(trackH - thumbH));
            int barX = contentX + contentW + 2;
            g.fill(barX, cardsTop, barX + 2, cardsTop + trackH, 144750792);
            g.fill(barX, thumbY, barX + 2, thumbY + thumbH, 547403976);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int mx = (int)mouseX;
        int my = (int)mouseY;
        if (GuiUtils.inRect(mx, my, this.width - 32, 2, 20, 20)) {
            this.onClose();
            return true;
        }
        int contentTop = 50;
        int y = contentTop + 14 - this.sidebarScrollOff;
        if (GuiUtils.inRect(mx, my, 4, y, 112, 32)) {
            this.selectedTab = null;
            this.cardsScrollOff = 0;
            GuiUtils.playClickSound();
            return true;
        }
        y += 36;
        for (String tab : this.tabList) {
            if (GuiUtils.inRect(mx, my, 4, y, 112, 32)) {
                if (!Objects.equals(this.selectedTab, tab)) {
                    this.selectedTab = tab;
                    this.cardsScrollOff = 0;
                    GuiUtils.playClickSound();
                }
                return true;
            }
            y += 36;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < 120.0) {
            this.sidebarScrollOff -= (int)(scrollY * 20.0);
            this.sidebarScrollOff = Math.max(0, Math.min(this.sidebarScrollOff, this.sidebarMaxScroll));
        } else {
            this.cardsScrollOff -= (int)(scrollY * 20.0);
            this.cardsScrollOff = Math.max(0, Math.min(this.cardsScrollOff, this.cardsMaxScroll));
        }
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    public boolean isPauseScreen() {
        return true;
    }
}

