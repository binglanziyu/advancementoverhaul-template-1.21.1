package com.dreamer.ao.client.gui.manager;

import com.dreamer.ao.LangKeys;
import com.dreamer.ao.client.gui.AdvancementScreen;
import com.dreamer.ao.client.gui.GuiUtils;
import com.dreamer.ao.client.gui.TranslatedStrings;
import com.dreamer.ao.client.gui.state.OverlayLayout;
import com.dreamer.ao.client.gui.state.OverlayState.Ov;
import com.dreamer.ao.data.ClientDataStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Handles click dispatch for modal overlay states.
 */
class OverlayClickHandler {

    static boolean handleOverlayClick(AdvancementScreen screen, double mx, double my, int btn) {
        switch (screen.overlay.current) {
            case DETAIL:
                if (btn == 0) clickDetail(screen, mx, my);
                else screen.overlay.close();
                return true;
            case CREATE: case EDIT:
                if (btn == 0) { clickEditor(screen, mx, my); return true; }
                return true;
            case CTX:
                if (btn == 0) clickCtx(screen, mx, my);
                else screen.overlay.close();
                return true;
            case TAB_INPUT:
                if (btn == 0) clickTabInput(screen, mx, my);
                return true;
            case TAB_MANAGE:
                if (btn == 0) clickTabManage(screen, mx, my);
                else screen.overlay.close();
                return true;
            case CONFIRM:
                if (btn == 0) clickConfirm(screen, mx, my);
                return true;
            case JOURNAL:
                if (btn == 0) clickJournal(screen, mx, my);
                else screen.overlay.close();
                return true;
            default:
                return false;
        }
    }

    private static void clickDetail(AdvancementScreen screen, double mx, double my) {
        int dw = OverlayLayout.DETAIL_W;
        int dh = OverlayLayout.DETAIL_H;
        int px = screen.mid(dw);
        int py = screen.midY(dh);

        // 关闭按钮
        if (GuiUtils.closeHit(mx, my, px, py, dw)) {
            screen.overlay.close();
            return;
        }
        // 面板外点击关闭
        if (GuiUtils.outsidePanel(mx, my, px, py, dw, dh)) {
            screen.overlay.close();
            return;
        }

        // ID 行内联复制按钮
        int ibx = screen.overlay.detailInlineCopyX;
        int iby = screen.overlay.detailInlineCopyY;
        int ibw = screen.overlay.detailInlineCopyW;
        int ibh = screen.overlay.detailInlineCopyH;
        if (GuiUtils.inRect(mx, my, ibx, iby, ibw, ibh) && screen.overlay.detailId != null) {
            Minecraft.getInstance().keyboardHandler.setClipboard(screen.overlay.detailId);
            screen.overlay.detailCopyTime = System.currentTimeMillis();
            return;
        }
    }

    private static void clickEditor(AdvancementScreen screen, double mx, double my) {
        Font font = screen.getFont();
        if (screen.editPanel.isCondSelActive()) {
            screen.editPanel.condSelClick(mx, my, font, screen.getScreenWidth(), screen.getScreenHeight());
            return;
        }
        boolean consumed = screen.editPanel.handleClick(mx, my, screen.getScreenWidth(), screen.getScreenHeight());
        if (consumed) {
            var focused = screen.editPanel.getLastFocusedWidget();
            if (focused != null) screen.setFocused(focused);
            if (!screen.editPanel.isVisible()) screen.overlay.close();
        } else {
            screen.editPanel.close();
            screen.overlay.close();
        }
    }

    private static void clickCtx(AdvancementScreen screen, double mx, double my) {
        int cx = screen.overlay.ctxX, cy = screen.overlay.ctxY;
        int mw = OverlayLayout.CTX_ITEM_W;
        int mh = screen.overlay.ctxActions.size() * OverlayLayout.CTX_ITEM_H + OverlayLayout.CTX_PAD * 2;
        if (cx + mw > screen.getScreenWidth()) cx = screen.getScreenWidth() - mw - 2;
        if (cy + mh > screen.getScreenHeight()) cy = screen.getScreenHeight() - mh - 2;
        if (mx >= cx && mx < cx + mw && my >= cy && my < cy + mh) {
            int idx = (int) ((my - cy - OverlayLayout.CTX_PAD) / OverlayLayout.CTX_ITEM_H);
            if (idx >= 0 && idx < screen.overlay.ctxActions.size()) {
                screen.overlay.ctxActions.get(idx).action().run();
                if (screen.overlay.current == Ov.CTX) {
                    screen.overlay.close();
                }
                return;
            }
        }
        screen.overlay.close();
    }

    private static void clickTabInput(AdvancementScreen screen, double mx, double my) {
        int px = screen.mid(OverlayLayout.TAB_INPUT_W);
        int py = screen.midY(OverlayLayout.TAB_INPUT_H);

        if (GuiUtils.closeHit(mx, my, px, py, OverlayLayout.TAB_INPUT_W)
                || GuiUtils.outsidePanel(mx, my, px, py, OverlayLayout.TAB_INPUT_W, OverlayLayout.TAB_INPUT_H)) {
            screen.closeTabInput();
            return;
        }

        if (GuiUtils.inRect(mx, my,
                px + OverlayLayout.TAB_INPUT_W - OverlayLayout.TAB_INPUT_OK_RIGHT,
                py + OverlayLayout.TAB_INPUT_BTN_Y,
                OverlayLayout.TAB_INPUT_BTN_W, OverlayLayout.TAB_INPUT_BTN_H)) {
            String tn = screen.tabNameBox.getValue().trim();
            if (!tn.isEmpty()) GuiUtils.sendCommand("adv tab add " + tn);
            screen.closeTabInput();
            return;
        }

        if (GuiUtils.inRect(mx, my,
                px + OverlayLayout.TAB_INPUT_W - OverlayLayout.TAB_INPUT_CANCEL_RIGHT,
                py + OverlayLayout.TAB_INPUT_BTN_Y,
                OverlayLayout.TAB_INPUT_BTN_W, OverlayLayout.TAB_INPUT_BTN_H)) {
            screen.closeTabInput();
        }
    }

    private static void clickTabManage(AdvancementScreen screen, double mx, double my) {
        ClientDataStore cs = ClientDataStore.getInstance();
        List<String> customTabs = cs.getCustomTabs();

        int pw = 320;
        int maxH = screen.getScreenHeight() - 80;
        int contentH = customTabs.size() * 26 + 50;
        int ph = Math.clamp(contentH, 120, maxH);
        int px = screen.mid(pw), py = screen.midY(ph);

        if (GuiUtils.closeHit(mx, my, px, py, pw)) { screen.overlay.close(); return; }
        if (GuiUtils.outsidePanel(mx, my, px, py, pw, ph)) { screen.overlay.close(); return; }

        int ty = py + 30;
        for (String tab : customTabs) {
            if (ty + 26 > py + ph) break;

            if (GuiUtils.inRect(mx, my, px + pw - 30, ty, 20, 24)) {
                int advCount = 0;
                for (var adv : cs.getAdvancements().values())
                    if (tab.equals(adv.getTab())) advCount++;

                if (advCount > 0) {
                    screen.overlay.manageTabTarget = tab;
                    screen.overlay.confirmText = Component.translatable(
                            LangKeys.TAB_CONFIRM_DELETE_MSG, tab, advCount).getString();
                    screen.overlay.confirmAction = () -> {
                        screen.cascadeDeleteTab(screen.overlay.manageTabTarget);
                        screen.overlay.manageTabTarget = null;
                    };
                    screen.overlay.current = Ov.CONFIRM;
                } else {
                    GuiUtils.sendCommand("adv tab delete " + tab);
                }
                return;
            }

            if (GuiUtils.inRect(mx, my, px + 10, ty, pw - 40, 24)) return;
            ty += 26;
        }
    }

    private static void clickConfirm(AdvancementScreen screen, double mx, double my) {
        int px = screen.mid(OverlayLayout.CONFIRM_W);
        int py = screen.midY(OverlayLayout.CONFIRM_H);
        int btnY = py + OverlayLayout.CONFIRM_H - OverlayLayout.CONFIRM_BTN_BOTTOM;

        if (GuiUtils.inRect(mx, my,
                px + OverlayLayout.CONFIRM_W - 170, btnY,
                OverlayLayout.CONFIRM_BTN_W, OverlayLayout.CONFIRM_BTN_H)) {
            if (screen.overlay.confirmAction != null) screen.overlay.confirmAction.run();
            screen.overlay.close();
            return;
        }

        if (GuiUtils.inRect(mx, my,
                px + OverlayLayout.CONFIRM_W - 88, btnY,
                OverlayLayout.CONFIRM_BTN_W, OverlayLayout.CONFIRM_BTN_H)) {
            screen.overlay.close();
            return;
        }

        if (GuiUtils.outsidePanel(mx, my, px, py, OverlayLayout.CONFIRM_W, OverlayLayout.CONFIRM_H))
            screen.overlay.close();
    }

    private static void clickJournal(AdvancementScreen screen, double mx, double my) {
        int sw = screen.getScreenWidth(), sh = screen.getScreenHeight();
        int jw = Math.min(sw - 60, 520);
        int jh = Math.min(sh - 40, 420);
        int jx = (sw - jw) / 2;
        int jy = Math.max(20, (sh - jh) / 2);

        if (GuiUtils.closeHit(mx, my, jx, jy, jw)) {
            screen.overlay.close();
            return;
        }
        if (GuiUtils.outsidePanel(mx, my, jx, jy, jw, jh)) {
            screen.overlay.close();
            return;
        }
    }
}
