package com.dreamer.ao.client.gui.render;

/**
 * 覆盖层面板渲染器：绘制所有模态弹窗的视觉呈现。
 * <p>
 * 负责渲染详情面板、编辑面板、统计面板、上下文菜单、
 * 确认对话框、标签页管理面板等覆盖层 UI。
 * 每个覆盖层有独立的布局和渲染逻辑。
 */
import com.dreamer.ao.LangKeys;
import com.dreamer.ao.client.gui.AdvancementScreen;
import com.dreamer.ao.client.gui.GuiUtils;
import com.dreamer.ao.client.gui.TranslatedStrings;
import com.dreamer.ao.client.gui.state.OverlayLayout;
import com.dreamer.ao.data.ClientDataStore;
import com.dreamer.ao.data.model.CustomAdvancement;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.*;

import static com.dreamer.ao.client.gui.Theme.*;

public class OverlayRenderer {

    private final AdvancementScreen screen;
    private final HelpRenderer helpRenderer;
    private final DetailPanelRenderer detailPanelRenderer;
    private final JournalRenderer journalRenderer;

    public OverlayRenderer(AdvancementScreen screen) {
        this.screen = screen;
        this.helpRenderer = new HelpRenderer(screen);
        this.detailPanelRenderer = new DetailPanelRenderer(screen);
        this.journalRenderer = new JournalRenderer(screen);
    }

    // ═══════════════ 详情面板滚动上限（供 InputManager 读取） ═══════════════
    /** 详情面板当前帧的最大滚动值，在 renderDetail() 中更新 */
    public static int detailMaxScroll = 0;

    // ═══════════════ TOOLTIP ═══════════════

    public void renderTooltip(GuiGraphics g, int mx, int my, String id) {
        Font font = screen.getFont();
        List<String> lines = new ArrayList<>();
        ClientDataStore cs = ClientDataStore.getInstance();
        boolean isVanilla = screen.isVanillaAdvId(id);
        CustomAdvancement adv = cs.getAdvancement(id);
        AdvancementScreen.VanillaAdv va = screen.getVanillaAdv(id);
        boolean hiddenUncompleted = !isVanilla && adv != null && adv.isHidden() && !cs.isCompleted(id);
        String name = isVanilla ? (va != null ? va.getLocalizedName() : id) : (hiddenUncompleted ? TranslatedStrings.get(LangKeys.HIDDEN_LOCKED) : (adv != null ? adv.getName() : id));
        lines.add(name);
        if (isVanilla) {
            lines.add(TranslatedStrings.get(LangKeys.TIP_VANILLA_RO));
            if (va != null && va.getLocalizedDesc() != null && !va.getLocalizedDesc().isEmpty()) lines.add(va.getLocalizedDesc());
            lines.add(TranslatedStrings.get(cs.isVanillaEnabled(id) ? LangKeys.ADV_TT_ENABLED : LangKeys.ADV_TT_DISABLED));
        } else {
            if (adv != null) {
                if (hiddenUncompleted) {
                    lines.add(TranslatedStrings.get(LangKeys.HIDDEN));
                } else {
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

    // ═══════════════ DETAIL（委托 DetailPanelRenderer） ═══════════════

    private void renderDetail(GuiGraphics g, int mx, int my) {
        detailMaxScroll = detailPanelRenderer.render(g, mx, my);
    }

    // ═══════════════ EDITOR ═══════════════

    private void renderEditor(GuiGraphics g, int mx, int my) {
        Font font = screen.getFont();
        screen.editPanel.render(g, mx, my, font, screen.getScreenWidth(), screen.getScreenHeight());
    }

    // ═══════════════ CTX ═══════════════

    private void renderCtx(GuiGraphics g, int mx, int my) {
        Font font = screen.getFont();
        int cx = screen.overlay.ctxX, cy = screen.overlay.ctxY;
        int mw = OverlayLayout.CTX_ITEM_W;
        int mh = screen.overlay.ctxActions.size() * OverlayLayout.CTX_ITEM_H + OverlayLayout.CTX_PAD * 2;
        if (cx + mw > screen.getScreenWidth()) cx = screen.getScreenWidth() - mw - 2;
        if (cy + mh > screen.getScreenHeight()) cy = screen.getScreenHeight() - mh - 2;
        // 完全不透明确保右键菜单不穿透看到画布内容
        g.fill(cx, cy, cx + mw, cy + mh, 0xFF2E2E42);
        g.renderOutline(cx, cy, mw, mh, ACCENT);
        for (int i = 0; i < screen.overlay.ctxActions.size(); i++) {
            int iy = cy + OverlayLayout.CTX_PAD + i * OverlayLayout.CTX_ITEM_H;
            boolean hov = GuiUtils.inRect(mx, my, cx + 2, iy, mw - 4, OverlayLayout.CTX_ITEM_H - 2);
            if (hov) {
                g.fill(cx + 2, iy, cx + mw - 2, iy + OverlayLayout.CTX_ITEM_H - 2, CTX_HOV);
                // P2: 左侧 2px accent 色竖条作为选中指示符
                g.fill(cx + 2, iy, cx + 2 + CTX_ACCENT_W, iy + OverlayLayout.CTX_ITEM_H - 2, ACCENT);
            }
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

        // 全屏暗色遮罩由 AdvancementScreen 统一管理，此处仅绘制面板自身
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

    // ═══════════════ HELP PANEL（委托给 HelpRenderer） ═══════════════

    public void renderHelp(GuiGraphics g, int mx, int my, Font font, int screenW, int screenH) {
        helpRenderer.renderHelp(g, mx, my, font, screenW, screenH);
    }

    public int getHelpScroll() { return helpRenderer.getHelpScroll(); }
    public void setHelpScroll(int s) { helpRenderer.setHelpScroll(s); }
    public void resetHelpScroll() { helpRenderer.resetHelpScroll(); }
    public int getHelpMaxScroll() { return helpRenderer.getHelpMaxScroll(); }
    public int getHelpPx() { return helpRenderer.getHelpPx(); }
    public int getHelpPy() { return helpRenderer.getHelpPy(); }
    public int getHelpPw() { return helpRenderer.getHelpPw(); }
    public int getHelpPh() { return helpRenderer.getHelpPh(); }

    // ═══════════════ JOURNAL（委托 JournalRenderer） ═══════════════

    public void renderJournal(GuiGraphics g, int mx, int my, Font font, int sw, int sh) {
        journalRenderer.render(g, mx, my, font, sw, sh);
    }

    // ═══════════════ RENDER DISPATCH ═══════════════

    public void renderOv(GuiGraphics g, int mx, int my) {
        switch (screen.overlay.current) {
            case DETAIL: renderDetail(g, mx, my); break;
            case CREATE: case EDIT: renderEditor(g, mx, my); break;
            case CTX: renderCtx(g, mx, my); break;
            case CONFIRM: renderConfirm(g, mx, my); break;
            case TAB_INPUT: renderTabInput(g, mx, my); break;
            case TAB_MANAGE: renderTabManage(g, mx, my); break;
            case JOURNAL: break; // Journal rendered separately
            default: break;
        }
    }

    // ═══════════════ TOASTS ═══════════════

    public void renderToasts(GuiGraphics g) {
        Font font = screen.getFont();
        var iter = screen.anim.toasts.iterator();
        // P3: 底部居中布局，自下而上堆叠
        int baseY = screen.getScreenHeight() - 40;
        int spacing = 26;
        int idx = 0;
        while (iter.hasNext()) {
            com.dreamer.ao.client.gui.state.AnimState.Toast t = iter.next();
            if (t.expired()) { iter.remove(); continue; }
            int a = t.alpha();
            if (a <= 0) continue;
            int col = (a << 24) | 0x00FFDD57;
            String text = "\u2713 " + t.name;
            int tw = font.width(text) + 16;
            int tx = (screen.getScreenWidth() - tw) / 2;
            int ty = baseY - idx * spacing;
            g.fill(tx, ty, tx + tw, ty + 22, (a << 24) | 0x001A1A2E);
            g.renderOutline(tx, ty, tw, 22, col);
            g.drawString(font, text, tx + 8, ty + 5, col, false);
            idx++;
        }
    }
}