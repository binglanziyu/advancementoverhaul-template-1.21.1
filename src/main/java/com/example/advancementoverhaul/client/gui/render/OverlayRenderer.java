package com.example.advancementoverhaul.client.gui.render;

import com.example.advancementoverhaul.LangKeys;
import com.example.advancementoverhaul.client.gui.AdvancementScreen;
import com.example.advancementoverhaul.client.gui.GuiUtils;
import com.example.advancementoverhaul.client.gui.TranslatedStrings;
import com.example.advancementoverhaul.client.gui.state.OverlayLayout;
import com.example.advancementoverhaul.data.ClientDataStore;
import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.DisplayNameResolver;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import com.example.advancementoverhaul.client.gui.ConditionTypeStyle;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.*;

import static com.example.advancementoverhaul.client.gui.Theme.*;

public class OverlayRenderer {

    private final AdvancementScreen screen;

    public OverlayRenderer(AdvancementScreen screen) { this.screen = screen; }

    // ═══════════════ TOOLTIP ═══════════════

    public void renderTooltip(GuiGraphics g, int mx, int my, String id) {
        Font font = screen.getFont();
        List<String> lines = new ArrayList<>();
        ClientDataStore cs = ClientDataStore.getInstance();
        boolean isVanilla = screen.isVanillaAdvId(id);
        DataStore.CustomAdvancement adv = cs.getAdvancement(id);
        AdvancementScreen.VanillaAdv va = screen.getVanillaAdv(id);
        String name = isVanilla ? (va != null ? va.getLocalizedName() : id) : (adv != null ? adv.getName() : id);
        lines.add(name);
        if (isVanilla) {
            lines.add(TranslatedStrings.get(LangKeys.TIP_VANILLA_RO));
            if (va != null && va.getLocalizedDesc() != null && !va.getLocalizedDesc().isEmpty()) lines.add(va.getLocalizedDesc());
            lines.add(TranslatedStrings.get(cs.isVanillaEnabled(id) ? LangKeys.ADV_TT_ENABLED : LangKeys.ADV_TT_DISABLED));
        } else {
            if (adv != null) {
                if (adv.getDescription() != null && !adv.getDescription().isEmpty()) lines.add(adv.getDescription());
                lines.add(TranslatedStrings.get(cs.isCompleted(id) ? LangKeys.DETAIL_COMPLETED : LangKeys.DETAIL_NOT_COMPLETED));
                if (adv.getPrerequisites() != null && !adv.getPrerequisites().isEmpty()) {
                    List<String> pNames = new ArrayList<>();
                    for (String pid : adv.getPrerequisites()) pNames.add(screen.prereqDisplayName(pid));
                    lines.add(Component.translatable(LangKeys.DETAIL_PREREQ_PREFIX, String.join(", ", pNames)).getString());
                }
                if (adv.getTab() != null && !adv.getTab().isEmpty())
                    lines.add(Component.translatable(LangKeys.DETAIL_TAB_PREFIX, adv.getTab()).getString());
            }
        }
        // 自动换行：当单行超出屏幕宽度 60% 时按单词拆分
        int maxLineW = (int) (screen.getScreenWidth() * 0.6);
        List<String> wrappedLines = new ArrayList<>();
        for (String l : lines) {
            if (font.width(l) <= maxLineW) {
                wrappedLines.add(l);
            } else {
                StringBuilder current = new StringBuilder();
                for (String word : l.split(" ")) {
                    if (current.length() > 0 && font.width(current + " " + word) > maxLineW) {
                        wrappedLines.add(current.toString());
                        current = new StringBuilder(word);
                    } else {
                        if (current.length() > 0) current.append(" ");
                        current.append(word);
                    }
                }
                if (current.length() > 0) wrappedLines.add(current.toString());
            }
        }
        lines = wrappedLines;

        int maxW = 0; for (String l : lines) maxW = Math.max(maxW, font.width(l));
        int tw = maxW + 12, th = lines.size() * 12 + 8;

        // 优先显示在鼠标上方
        int tx = mx + 12, ty = my - 4 - th;
        // 上方空间不够时放右边
        if (ty < 4) ty = my + 16;
        // 右边溢出时移到左边
        if (tx + tw > screen.getScreenWidth()) tx = mx - tw - 4;
        // 下方溢出
        if (ty + th > screen.getScreenHeight()) ty = screen.getScreenHeight() - th - 4;
        if (ty < 4) ty = 4;
        // 左边溢出
        if (tx < 4) tx = 4;

        g.fill(tx - 2, ty - 2, tx + tw + 2, ty + th + 2, TOOLTIP_BG);
        g.renderOutline(tx - 2, ty - 2, tw + 4, th + 4, TOOLTIP_BORDER);
        for (int i = 0; i < lines.size(); i++)
            g.drawString(font, lines.get(i), tx + 4, ty + 4 + i * 12, i == 0 ? TEXT_BR : TEXT_DIM, false);
    }

    // ═══════════════ DETAIL ═══════════════

    private void renderDetail(GuiGraphics g, int mx, int my) {
        Font font = screen.getFont();
        int dw = OverlayLayout.DETAIL_W, dh = OverlayLayout.DETAIL_H;
        int dx = screen.mid(dw), dy = screen.midY(dh);
        GuiUtils.drawPanelBg(g, font, dx, dy, dw, dh,
                TranslatedStrings.get(LangKeys.DETAIL_TITLE),
                screen.getScreenWidth(), screen.getScreenHeight());
        ClientDataStore cs = ClientDataStore.getInstance();
        String id = screen.overlay.detailId;
        boolean isVanilla = screen.isVanillaAdvId(id);
        DataStore.CustomAdvancement adv = cs.getAdvancement(id);
        AdvancementScreen.VanillaAdv va = screen.getVanillaAdv(id);
        int ty = dy + 30;
        int textW = dw - 28;

        String name = isVanilla ? (va != null ? va.getLocalizedName() : id) : (adv != null ? adv.getName() : id);
        g.drawString(font, GuiUtils.truncate(font, name, textW - 44), dx + 14, ty, TEXT_BR, false); ty += 20;

        if (isVanilla) {
            if (va != null && va.getLocalizedDesc() != null)
                g.drawString(font, GuiUtils.truncate(font, va.getLocalizedDesc(), textW), dx + 14, ty, TEXT, false);
            ty += 16;
            boolean enabled = cs.isVanillaEnabled(id);
            g.drawString(font, TranslatedStrings.get(enabled ? LangKeys.ADV_TT_ENABLED : LangKeys.ADV_TT_DISABLED),
                    dx + 14, ty, enabled ? 0xFF55FF55 : 0xFFFF5555, false);
            ty += 16;
            g.drawString(font, TranslatedStrings.get(LangKeys.TIP_VANILLA_RO), dx + 14, ty, TEXT_DIM, false);
            ty += 24;
            if (va != null && va.icon() != null) {
                var rl = ResourceLocation.tryParse(va.icon());
                if (rl != null) {
                    var item = BuiltInRegistries.ITEM.get(rl);
                    if (item != null) g.renderItem(new ItemStack(item), dx + dw - 44, dy + 30);
                }
            }
        } else {
            if (adv != null) {
                if (adv.getDescription() != null)
                    g.drawString(font, GuiUtils.truncate(font, adv.getDescription(), textW), dx + 14, ty, TEXT, false);
                ty += 16;
                boolean done = cs.isCompleted(id);
                g.drawString(font, TranslatedStrings.get(done ? LangKeys.DETAIL_COMPLETED : LangKeys.DETAIL_NOT_COMPLETED),
                        dx + 14, ty, done ? 0xFF55FF55 : 0xFFFF5555, false);
                ty += 16;
                if (adv.getTab() != null)
                    g.drawString(font, GuiUtils.truncate(font, Component.translatable(LangKeys.DETAIL_TAB_PREFIX, adv.getTab()).getString(), textW),
                            dx + 14, ty, TEXT_DIM, false);
                ty += 16;
                if (adv.getPrerequisites() != null) {
                    for (String pid : adv.getPrerequisites()) {
                        g.drawString(font, GuiUtils.truncate(font, Component.translatable(LangKeys.DETAIL_PREREQ_PREFIX, screen.prereqDisplayName(pid)).getString(), textW),
                                dx + 14, ty, TEXT_DIM, false);
                        ty += 14;
                    }
                }
                ty += 8;
                if (adv.getConditions() != null && !adv.getConditions().isEmpty()) {
                    g.drawString(font, TranslatedStrings.get(LangKeys.CONDITIONS), dx + 14, ty, TEXT_BR, false);
                    ty += 16;
                    for (var c : adv.getConditions()) {
                        String line = c.getType() != null ? ConditionTypeStyle.of(c.getType()).displayName() : "???";
                        String tgtName = DisplayNameResolver.resolve(c.getType(), c.getTargetId());
                        if (!tgtName.isEmpty())
                            line += ": " + tgtName;
                        line += " x" + c.getCount();
                        g.drawString(font, GuiUtils.truncate(font, line, textW - 10), dx + 24, ty, TEXT_DIM, false);
                        ty += 14;
                    }
                }
                if (adv.getIcon() != null) {
                    var rl = ResourceLocation.tryParse(adv.getIcon());
                    if (rl != null) {
                        var item = BuiltInRegistries.ITEM.get(rl);
                        if (item != null) g.renderItem(new ItemStack(item), dx + dw - 44, dy + 30);
                    }
                }
            }
        }
    }

    // ═══════════════ EDITOR ═══════════════

    private void renderEditor(GuiGraphics g, int mx, int my) {
        Font font = screen.getFont();
        screen.editPanel.render(g, mx, my, font, screen.getScreenWidth(), screen.getScreenHeight());
    }

    // ═══════════════ STATS ═══════════════

    private void renderStats(GuiGraphics g, int mx, int my) {
        Font font = screen.getFont();
        int sw = OverlayLayout.STATS_W, sh = OverlayLayout.STATS_H;
        int sx = screen.mid(sw), sy = screen.midY(sh);
        GuiUtils.drawPanelBg(g, font, sx, sy, sw, sh,
                TranslatedStrings.get(LangKeys.STATISTICS),
                screen.getScreenWidth(), screen.getScreenHeight());
        ClientDataStore cs = ClientDataStore.getInstance();
        int total = cs.getTotalCount();
        int done = cs.getCompletedCount();
        double rate = total > 0 ? (done * 100.0 / total) : 0;
        List<String> lines = new ArrayList<>();
        lines.add(TranslatedStrings.get(LangKeys.STAT_CUSTOM) + ": " + total);
        lines.add(TranslatedStrings.get(LangKeys.STAT_DONE) + ": " + done);
        lines.add(TranslatedStrings.get(LangKeys.STAT_RATE) + ": " + String.format("%.1f%%", rate));
        int vanillaTotal = 0, vanillaDone = 0;
        for (var va : cs.getVanillaAdvancements()) {
            if (cs.isVanillaEnabled(va.id())) {
                vanillaTotal++;
                if (cs.isCompleted(va.id())) vanillaDone++;
            }
        }
        lines.add(TranslatedStrings.get(LangKeys.STAT_VANILLA) + ": " + vanillaDone + "/" + vanillaTotal);
        lines.add("");
        lines.add(TranslatedStrings.get(LangKeys.STAT_TAB_PROG));
        for (String tab : cs.getTabs()) {
            int tabT = cs.getTabTotalCount(tab);
            int tabD = cs.getTabCompletedCount(tab);
            if (tabT > 0) lines.add("  " + tab + ": " + tabD + "/" + tabT);
        }
        int lineY = sy + 30 + screen.overlay.statsScrollOff;
        g.enableScissor(sx + 2, sy + 28, sx + sw - 2, sy + sh - 2);
        for (String line : lines) {
            if (lineY + 14 >= sy + 28 && lineY < sy + sh - 4)
                g.drawString(font, line, sx + 14, lineY, TEXT, false);
            lineY += 16;
        }
        g.disableScissor();
    }

    public int calcStatsMaxScroll() {
        int lineCount = 5 + ClientDataStore.getInstance().getTabs().size();
        return Math.max(0, lineCount * 16 - 230);
    }

    // ═══════════════ CTX ═══════════════

    private void renderCtx(GuiGraphics g, int mx, int my) {
        Font font = screen.getFont();
        int cx = screen.overlay.ctxX, cy = screen.overlay.ctxY;
        int mw = OverlayLayout.CTX_ITEM_W;
        int mh = screen.overlay.ctxActions.size() * OverlayLayout.CTX_ITEM_H + OverlayLayout.CTX_PAD * 2;
        if (cx + mw > screen.getScreenWidth()) cx = screen.getScreenWidth() - mw - 2;
        if (cy + mh > screen.getScreenHeight()) cy = screen.getScreenHeight() - mh - 2;
        g.fill(cx, cy, cx + mw, cy + mh, CTX);
        g.renderOutline(cx, cy, mw, mh, ACCENT);
        for (int i = 0; i < screen.overlay.ctxActions.size(); i++) {
            int iy = cy + OverlayLayout.CTX_PAD + i * OverlayLayout.CTX_ITEM_H;
            boolean hov = GuiUtils.inRect(mx, my, cx + 2, iy, mw - 4, OverlayLayout.CTX_ITEM_H - 2);
            if (hov) g.fill(cx + 2, iy, cx + mw - 2, iy + OverlayLayout.CTX_ITEM_H - 2, BTN_HOV);
            g.drawString(font, screen.overlay.ctxActions.get(i).label(), cx + 8, iy + 4, TEXT, false);
        }
    }

    // ═══════════════ CONFIRM ═══════════════

    private void renderConfirm(GuiGraphics g, int mx, int my) {
        Font font = screen.getFont();
        int cw = OverlayLayout.CONFIRM_W, ch = OverlayLayout.CONFIRM_H;
        int cx = screen.mid(cw), cy = screen.midY(ch);
        g.fill(0, 0, screen.getScreenWidth(), screen.getScreenHeight(), 0x80000000);
        g.fill(cx, cy, cx + cw, cy + ch, PANEL);
        g.renderOutline(cx, cy, cw, ch, PINK);
        g.drawString(font, screen.overlay.confirmText, cx + 14, cy + 14, TEXT_BR, false);
        int btnY = cy + ch - OverlayLayout.CONFIRM_BTN_BOTTOM;
        GuiUtils.drawSmallBtn(g, font, cx + cw - 170, btnY, OverlayLayout.CONFIRM_BTN_W,
                TranslatedStrings.get(LangKeys.CONFIRM),
                GuiUtils.inRect(mx, my, cx + cw - 170, btnY, OverlayLayout.CONFIRM_BTN_W, OverlayLayout.CONFIRM_BTN_H));
        GuiUtils.drawSmallBtn(g, font, cx + cw - 88, btnY, OverlayLayout.CONFIRM_BTN_W,
                TranslatedStrings.get(LangKeys.CANCEL),
                GuiUtils.inRect(mx, my, cx + cw - 88, btnY, OverlayLayout.CONFIRM_BTN_W, OverlayLayout.CONFIRM_BTN_H));
    }

    // ═══════════════ TAB INPUT ═══════════════

    private void renderTabInput(GuiGraphics g, int mx, int my) {
        Font font = screen.getFont();
        int pw = OverlayLayout.TAB_INPUT_W, ph = OverlayLayout.TAB_INPUT_H;
        int px = screen.mid(pw), py = screen.midY(ph);

        g.fill(0, TAB_H, screen.getScreenWidth(), screen.getScreenHeight() - BOTTOM_H, 0x80000000);
        g.fill(px, py, px + pw, py + ph, PANEL);
        g.renderOutline(px, py, pw, ph, DIVIDER);
        g.fill(px, py, px + pw, py + 3, ACCENT);
        g.drawString(font, TranslatedStrings.get(LangKeys.NEW_TAB), px + OverlayLayout.TAB_INPUT_INNER_PAD, py + 10, TEXT_BR, false);
        boolean ch = GuiUtils.closeHit(mx, my, px, py, pw);
        g.drawString(font, "\u2715", px + pw - 16, py + 10, ch ? TEXT_BR : TEXT_DIM, false);
        g.fill(px + 10, py + 60, px + pw - 10, py + 61, DIVIDER);
        boolean saveHov = GuiUtils.inRect(mx, my,
                px + pw - OverlayLayout.TAB_INPUT_OK_RIGHT, py + OverlayLayout.TAB_INPUT_BTN_Y,
                OverlayLayout.TAB_INPUT_BTN_W, OverlayLayout.TAB_INPUT_BTN_H);
        GuiUtils.drawSmallBtn(g, font,
                px + pw - OverlayLayout.TAB_INPUT_OK_RIGHT, py + OverlayLayout.TAB_INPUT_BTN_Y,
                OverlayLayout.TAB_INPUT_BTN_W, TranslatedStrings.get(LangKeys.SAVE), saveHov);
        boolean cancelHov = GuiUtils.inRect(mx, my,
                px + pw - OverlayLayout.TAB_INPUT_CANCEL_RIGHT, py + OverlayLayout.TAB_INPUT_BTN_Y,
                OverlayLayout.TAB_INPUT_BTN_W, OverlayLayout.TAB_INPUT_BTN_H);
        GuiUtils.drawSmallBtn(g, font,
                px + pw - OverlayLayout.TAB_INPUT_CANCEL_RIGHT, py + OverlayLayout.TAB_INPUT_BTN_Y,
                OverlayLayout.TAB_INPUT_BTN_W, TranslatedStrings.get(LangKeys.CANCEL), cancelHov);
    }

    // ═══════════════ TAB MANAGE ═══════════════

    private static final int TAB_MANAGE_W = 320;
    private static final int TAB_MANAGE_ROW_H = 26;

    private void renderTabManage(GuiGraphics g, int mx, int my) {
        Font font = screen.getFont();
        ClientDataStore cs = ClientDataStore.getInstance();
        List<String> customTabs = cs.getCustomTabs();

        int maxH = screen.getScreenHeight() - 80;
        int contentH = customTabs.size() * TAB_MANAGE_ROW_H + 50;
        int ph = Math.min(maxH, Math.max(120, contentH));
        int pw = TAB_MANAGE_W;
        int px = screen.mid(pw), py = screen.midY(ph);

        g.fill(0, 0, screen.getScreenWidth(), screen.getScreenHeight(), 0x80000000);

        GuiUtils.drawPanelBg(g, font, px, py, pw, ph,
                Component.translatable(LangKeys.TAB_MANAGE_TITLE).getString(),
                screen.getScreenWidth(), screen.getScreenHeight());

        int ty = py + 30;
        g.enableScissor(px + 1, py + 28, px + pw - 1, py + ph - 2);

        if (customTabs.isEmpty()) {
            g.drawString(font, Component.translatable(LangKeys.TAB_MANAGE_EMPTY).getString(),
                    px + 14, ty + 8, TEXT_DIM, false);
        } else {
            for (int i = 0; i < customTabs.size(); i++) {
                String tab = customTabs.get(i);
                if (ty + TAB_MANAGE_ROW_H > py + ph) break;

                int advCount = 0;
                int vanillaCount = 0;
                for (var adv : cs.getAdvancements().values())
                    if (tab.equals(adv.getTab())) advCount++;
                for (var meta : cs.getVanillaMeta().values())
                    if (tab.equals(meta.getTab())) vanillaCount++;

                String countStr = advCount + (vanillaCount > 0 ? "+" + vanillaCount : "");
                String label = tab + " (" + countStr + ")";

                boolean rowHov = GuiUtils.inRect(mx, my, px + 10, ty, pw - 40, TAB_MANAGE_ROW_H - 2);
                if (rowHov) g.fill(px + 10, ty, px + pw - 10, ty + TAB_MANAGE_ROW_H - 2, BTN_HOV);

                g.drawString(font, GuiUtils.truncate(font, label, pw - 56),
                        px + 14, ty + 7, TEXT, false);

                boolean delHov = GuiUtils.inRect(mx, my, px + pw - 30, ty, 20, TAB_MANAGE_ROW_H - 2);
                g.drawString(font, "\u2715", px + pw - 24, ty + 7, delHov ? PINK : TEXT_DIM, false);

                ty += TAB_MANAGE_ROW_H;
            }
        }

        g.disableScissor();

        boolean closeHov = GuiUtils.closeHit(mx, my, px, py, pw);
        g.drawString(font, "\u2715", px + pw - 16, py + 10, closeHov ? TEXT_BR : TEXT_DIM, false);
    }

    // ═══════════════ RENDER DISPATCH ═══════════════

    public void renderOv(GuiGraphics g, int mx, int my) {
        switch (screen.overlay.current) {
            case DETAIL: renderDetail(g, mx, my); break;
            case CREATE: case EDIT: renderEditor(g, mx, my); break;
            case STATS: renderStats(g, mx, my); break;
            case CTX: renderCtx(g, mx, my); break;
            case CONFIRM: renderConfirm(g, mx, my); break;
            case TAB_INPUT: renderTabInput(g, mx, my); break;
            case TAB_MANAGE: renderTabManage(g, mx, my); break;
            default: break;
        }
    }

    // ═══════════════ TOASTS ═══════════════

    public void renderToasts(GuiGraphics g) {
        Font font = screen.getFont();
        var iter = screen.anim.toasts.iterator();
        int y = screen.getScreenHeight() - 80;
        while (iter.hasNext()) {
            com.example.advancementoverhaul.client.gui.state.AnimState.Toast t = iter.next();
            if (t.expired()) { iter.remove(); continue; }
            int a = t.alpha();
            if (a <= 0) continue;
            int col = (a << 24) | 0x00FFDD57;
            String text = "\u2713 " + t.name;
            int tw = font.width(text) + 16;
            int tx = screen.getScreenWidth() - tw - 20;
            g.fill(tx, y, tx + tw, y + 22, (a << 24) | 0x001A1A2E);
            g.renderOutline(tx, y, tw, 22, col);
            g.drawString(font, text, tx + 8, y + 5, col, false);
            y -= 26;
        }
    }
}