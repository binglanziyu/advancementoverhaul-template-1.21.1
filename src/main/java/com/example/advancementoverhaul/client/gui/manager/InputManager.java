package com.example.advancementoverhaul.client.gui.manager;

import com.example.advancementoverhaul.client.gui.AdvancementScreen;
import com.example.advancementoverhaul.client.gui.GuiUtils;
import com.example.advancementoverhaul.client.gui.ImageElement;
import com.example.advancementoverhaul.client.gui.ImageManager;
import com.example.advancementoverhaul.client.gui.state.OverlayLayout;
import com.example.advancementoverhaul.client.gui.state.OverlayState.Ov;
import com.example.advancementoverhaul.data.ClientDataStore;
import com.example.advancementoverhaul.data.DataStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.*;

import static com.example.advancementoverhaul.client.gui.Theme.*;

/**
 * Event dispatcher: routes all mouse/keyboard events to CanvasManager / TabManager / AdvancementScreen.
 * Also handles overlay click processing.
 */
public class InputManager {

    final AdvancementScreen screen;
    final CanvasManager canvasMgr;
    final TabManager tabMgr;

    public InputManager(AdvancementScreen screen, CanvasManager canvasMgr, TabManager tabMgr) {
        this.screen = screen;
        this.canvasMgr = canvasMgr;
        this.tabMgr = tabMgr;
    }

    // ═══════════════ MOUSE CLICK ═══════════════

    public boolean onMouseClicked(double mx, double my, int btn) {
        // Priority 1: List selector overlay
        if (screen.showSel) {
            if (screen.listSel.mouseClicked(mx, my, btn)) {
                if (!screen.listSel.isVisible()) screen.showSel = false;
                return true;
            }
            screen.showSel = false;
            return true;
        }

        // Priority 2: Dimension panel
        if (screen.showDim) {
            if (screen.dimPanel.mouseClicked(mx, my, btn)) return true;
            screen.showDim = false;
            return true;
        }

        // Priority 3: Modal overlays
        if (screen.overlay.current != Ov.NONE) {
            return handleOverlayClick(mx, my, btn);
        }

        // Priority 4: Toolbar buttons
        if (handleButtons(mx, my)) {
            GuiUtils.playClickSound();
            return true;
        }

        // Priority 5: Overflow dropdown
        if (screen.tabDrag.overDDOpen) {
            if (tabMgr.handleOverflowDDClick(mx, my)) {
                screen.tabDrag.overDDOpen = false;
                screen.tabDrag.overflowScroll = 0;
                GuiUtils.playClickSound();
                return true;
            }
            screen.tabDrag.overDDOpen = false;
            screen.tabDrag.overflowScroll = 0;
            if (my >= TAB_H) return true;
        }

        // Priority 6: Tab bar click
        if (my < TAB_H) {
            GuiUtils.playClickSound();
            tabMgr.tabClick(mx);
            return true;
        }

        // Bottom status bar
        if (my >= screen.getScreenHeight() - BOTTOM_H) return true;

        // Scrollbar click
        if (!screen.hasOv() && btn == 0) {
            if (canvasMgr.handleScrollbarClick(mx, my)) return true;
        }

        // Canvas interaction
        return handleCanvasClick(mx, my, btn);
    }

    private boolean handleCanvasClick(double mx, double my, int btn) {
        String card = canvasMgr.cardAt((int) mx, (int) my);

        if (btn == 0) {
            // 图片在卡片之上渲染，应优先检测
            ImageElement hitImg = screen.imageAt(mx, my);
            if (hitImg != null) {
                screen.selectedImageId = hitImg.getId();
                if (!hitImg.isLocked() && screen.editMode) {
                    screen.drag.dragImageId = hitImg.getId();
                    screen.drag.lastDragMX = mx;
                    screen.drag.lastDragMY = my;
                    screen.drag.dragMoved = false;
                }
                return true;
            }
            screen.selectedImageId = null;

            // 卡片检测（图片未命中后）
            if (card != null) {
                return handleCardLeftClick(mx, my, card);
            }

            // Clicked empty canvas
            screen.selection.clear();
            if (screen.editMode && !screen.blocksCanvas()) {
                screen.drag.boxSel = true;
                screen.drag.bsx = mx; screen.drag.bsy = my;
                screen.drag.bex = mx; screen.drag.bey = my;
            } else if (!screen.blocksCanvas()) {
                screen.canvas.panning = true;
            }
            return true;
        }

            if (btn == 1) {
            // 右键图片：缩放/锁定/删除
            ImageElement rClickImg = screen.imageAt(mx, my);
            if (rClickImg != null && screen.editMode) {
                screen.showImageCtx(mx, my, rClickImg.getId());
                return true;
            }
            if (screen.editMode && card != null) {
                if (screen.selection.multiSel.size() > 1 && screen.selection.multiSel.contains(card)) {
                    screen.showBatchCtx(mx, my);
                } else {
                    if (screen.isVanillaAdvId(card)) screen.showVanillaCtx(mx, my, card);
                    else screen.showCtx(mx, my, card);
                }
            } else if (screen.editMode) {
                screen.showCanvasCtx(mx, my);
            } else if (!screen.blocksCanvas()) {
                screen.canvas.panning = true;
            }
            return true;
        }

        // Middle button: always pan
        if (!screen.blocksCanvas()) {
            screen.canvas.panning = true;
        }
        return true;
    }

    private boolean handleCardLeftClick(double mx, double my, String card) {
        if (screen.editMode && Screen.hasShiftDown()) {
            screen.selection.toggle(card);
        } else if (screen.editMode) {
            if (screen.isVanillaAdvId(card)) {
                var cs = ClientDataStore.getInstance();
                DataStore.VanillaAdvMeta meta = cs.getVanillaMeta(card);
                if (meta != null && meta.getTab() != null && !meta.getTab().isEmpty()) {
                    screen.drag.dragCardId = card;
                    screen.drag.dragStartMX = mx;
                    screen.drag.dragStartMY = my;
                    screen.drag.lastDragMX = mx;
                    screen.drag.lastDragMY = my;
                    screen.drag.dragMoved = false;
                } else {
                    // 未分配标签的原版成就，打开标签分配选择器
                    tabMgr.openVanillaTabSel(card);
                }
            } else {
                screen.drag.dragCardId = card;
                screen.drag.dragStartMX = mx;
                screen.drag.dragStartMY = my;
                screen.drag.lastDragMX = mx;
                screen.drag.lastDragMY = my;
                screen.drag.dragMoved = false;
            }
        } else {
            screen.selection.select(card);
            screen.overlay.detailId = card;
            screen.overlay.current = Ov.DETAIL;
        }
        return true;
    }
    // ═══════════════ MOUSE RELEASE ═══════════════

    public boolean onMouseReleased(double mx, double my, int btn) {
        if (screen.showDim) { screen.dimPanel.mouseReleased(mx, my, btn); }
        canvasMgr.resetScrollDrag();

        if (screen.tabDrag.dragIdx >= 0) {
            screen.tabDrag.dragIdx = -1;
            screen.tabDrag.dragMoved = false;
            return true;
        }

        // Image drag release
        if (screen.drag.dragImageId != null) {
            if (screen.drag.dragMoved) ImageManager.save(screen.imageElements);
            screen.drag.dragImageId = null;
            return false;
        }

        if (screen.drag.dragCardId != null) {
            if (screen.drag.dragMoved) {
                if (screen.isVanillaAdvId(screen.drag.dragCardId)) {
                    int[] pos = screen.vanillaPos.get(screen.drag.dragCardId);
                    if (pos != null) GuiUtils.sendCommand("adv vanilla setpos " + screen.drag.dragCardId + " " + pos[0] + " " + pos[1]);
                } else {
                    var a = screen.adv(screen.drag.dragCardId);
                    if (a != null) GuiUtils.sendCommand("adv updatejson " + screen.drag.dragCardId + " {\"x\":" + a.getX() + ",\"y\":" + a.getY() + "}");
                }
            } else {
                screen.selection.select(screen.drag.dragCardId);
                screen.overlay.detailId = screen.drag.dragCardId;
                screen.overlay.current = Ov.DETAIL;
            }
            screen.drag.dragCardId = null;
            return false;
        }

        if (screen.drag.boxSel) {
            screen.drag.boxSel = false;
            canvasMgr.applyBoxSel();
        }
        screen.canvas.panning = false;
        return false;
    }

    // ═══════════════ MOUSE DRAG ═══════════════

    public boolean onMouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (screen.showDim && screen.dimPanel.mouseDragged(mx, my, btn, dx, dy)) return true;

        // Tab reordering drag
        if (screen.tabDrag.dragIdx >= 0) {
            if (!screen.tabDrag.dragMoved && Math.abs(mx - screen.tabDrag.dragStartX) > DRAG_THRESH)
                screen.tabDrag.dragMoved = true;
            if (screen.tabDrag.dragMoved) screen.tabRenderer.setDragVisualX((int) mx);
            return true;
        }

        // Scrollbar drag
        if (canvasMgr.scrollDragH) { canvasMgr.updateHScrollFromMouse(mx); return true; }
        if (canvasMgr.scrollDragV) { canvasMgr.updateVScrollFromMouse(my); return true; }

        // Image drag — 无阈值，按住即拖
        if (screen.drag.dragImageId != null) {
            screen.drag.dragMoved = true;
            ImageElement img = screen.findImageById(screen.drag.dragImageId);
            if (img != null && !img.isLocked()) {
                double deltaX = mx - screen.drag.lastDragMX, deltaY = my - screen.drag.lastDragMY;
                img.setX((int) Math.round(img.getX() + deltaX / screen.canvas.zoom));
                img.setY((int) Math.round(img.getY() + deltaY / screen.canvas.zoom));
            }
            screen.drag.lastDragMX = mx; screen.drag.lastDragMY = my;
            return true;
        }

        // Card drag
        if (screen.drag.dragCardId != null) {
            if (!screen.drag.dragMoved && Math.hypot(mx - screen.drag.dragStartMX, my - screen.drag.dragStartMY) > DRAG_THRESH)
                screen.drag.dragMoved = true;
            if (screen.drag.dragMoved) {
                double deltaX = mx - screen.drag.lastDragMX, deltaY = my - screen.drag.lastDragMY;
                if (screen.isVanillaAdvId(screen.drag.dragCardId)) {
                    int[] pos = screen.vanillaPos.get(screen.drag.dragCardId);
                    if (pos != null) {
                        pos[0] += (int) Math.round(deltaX / screen.canvas.zoom);
                        pos[1] += (int) Math.round(deltaY / screen.canvas.zoom);
                    }
                } else {
                    var a = ClientDataStore.getInstance().getAdvancement(screen.drag.dragCardId);
                    if (a != null) {
                        a.setX((int) Math.round(a.getX() + deltaX / screen.canvas.zoom));
                        a.setY((int) Math.round(a.getY() + deltaY / screen.canvas.zoom));
                    }
                }
            }
            screen.drag.lastDragMX = mx; screen.drag.lastDragMY = my;
            return true;
        }

        // Box selection drag
        if (screen.drag.boxSel) { screen.drag.bex = mx; screen.drag.bey = my; return true; }

        // Canvas panning
        if (screen.canvas.panning && !screen.blocksCanvas()) {
            screen.canvas.scrollX += dx;
            screen.canvas.scrollY += dy;
            return true;
        }

        return false;
    }

    // ═══════════════ MOUSE SCROLL ═══════════════

    public boolean onMouseScrolled(double mx, double my, double sx, double sy) {
        if (screen.showDim && screen.dimPanel.mouseScrolled(mx, my, sx, sy)) return true;
        if (screen.showSel && screen.listSel.mouseScrolled(mx, my, sx, sy)) return true;

        // Overflow dropdown scroll
        if (screen.tabDrag.overDDOpen && screen.tabDrag.overflowDDX >= 0) {
            List<String> over = screen.tabRenderer.getOverflowTabs();
            int mw = OverlayLayout.CTX_ITEM_W;
            int availH = screen.getScreenHeight() - TAB_H - 4;
            int maxVisible = Math.min(over.size(), availH / 22);
            int showH = maxVisible * 22 + 4;
            if (mx >= screen.tabDrag.overflowDDX && mx < screen.tabDrag.overflowDDX + mw
                    && my >= TAB_H && my < TAB_H + showH) {
                int maxScroll = Math.max(0, over.size() * 22 - showH + 4);
                screen.tabDrag.overflowScroll = (int) Math.clamp(screen.tabDrag.overflowScroll - sy * 30, 0, maxScroll);
                return true;
            }
        }

        // Stats overlay scroll
        if (screen.overlay.current == Ov.STATS) {
            int lineCount = 5 + ClientDataStore.getInstance().getTabs().size();
            int ms = Math.max(0, lineCount * 16 - 230);
            if (ms > 0) {
                screen.overlay.statsScrollOff = (int) Math.clamp(screen.overlay.statsScrollOff - sy * 30, 0, ms);
                return true;
            }
        }

        // Edit panel scroll
        if ((screen.overlay.current == Ov.CREATE || screen.overlay.current == Ov.EDIT)
                && screen.editPanel.handleScroll(mx, my, sy, screen.getScreenWidth(), screen.getScreenHeight()))
            return true;

        // 图片缩放（鼠标悬停在图片上时）
        if (!screen.blocksCanvas()) {
            ImageElement scrollImg = screen.imageAt(mx, my);
            if (scrollImg != null && !scrollImg.isLocked() && screen.editMode) {
                scrollImg.setScale(scrollImg.getScale() * (float) (1.0 + sy * 0.1));
                ImageManager.save(screen.imageElements);
                return true;
            }
            screen.canvas.zoomAt(mx, my, sy);
        }
        return true;
    }

    // ═══════════════ KEYBOARD ═══════════════

    public boolean onCharTyped(char chr, int mod) {
        if (screen.showSel) { screen.listSel.charTyped(chr); return true; }
        if (screen.editPanel.isCondSelActive()) { screen.editPanel.condSelCharTyped(chr); return true; }
        return false;
    }

    public boolean onKeyPressed(int kc, int sc, int mod) {
        if (screen.editPanel.handleInlineCountKey(kc)) return true;

        if (screen.showSel) {
            if (kc == GLFW.GLFW_KEY_ESCAPE) { screen.showSel = false; return true; }
            screen.listSel.keyPressed(kc);
            return true;
        }
        if (screen.showDim && kc == GLFW.GLFW_KEY_ESCAPE) { screen.showDim = false; return true; }

        if (screen.editPanel.isCondSelActive()) {
            if (kc == GLFW.GLFW_KEY_ESCAPE) { screen.editPanel.closeCondSel(); return true; }
            screen.editPanel.condSelKeyPressed(kc);
            return true;
        }

        if (screen.tabDrag.overDDOpen && kc == GLFW.GLFW_KEY_ESCAPE) {
            screen.tabDrag.overDDOpen = false;
            screen.tabDrag.overflowScroll = 0;
            return true;
        }

        if (screen.overlay.current == Ov.TAB_INPUT) {
            if (kc == GLFW.GLFW_KEY_ENTER) {
                String tn = screen.tabNameBox.getValue().trim();
                if (!tn.isEmpty()) GuiUtils.sendCommand("adv tab add " + tn);
                screen.closeTabInput();
                return true;
            }
            if (kc == GLFW.GLFW_KEY_ESCAPE) { screen.closeTabInput(); return true; }
            return false;
        }

        if (screen.overlay.current != Ov.NONE) {
            if (kc == GLFW.GLFW_KEY_ESCAPE) { screen.overlay.close(); screen.editPanel.close(); return true; }
        }

        if (kc == GLFW.GLFW_KEY_DELETE && screen.editMode && !screen.selection.multiSel.isEmpty()) {
            screen.requestBatchDelete();
            return true;
        }

        if (kc == GLFW.GLFW_KEY_ESCAPE) { screen.onClose(); return true; }

        return false;
    }

    // ═══════════════ Overlay click handling ═══════════════

    private boolean handleOverlayClick(double mx, double my, int btn) {
        switch (screen.overlay.current) {
            case DETAIL:
                if (btn == 0) clickDetail(mx, my);
                else screen.overlay.close();
                return true;
            case CREATE: case EDIT:
                if (btn == 0) { clickEditor(mx, my); return true; }
                return true;
            case STATS:
                if (btn == 0) clickStats(mx, my);
                else screen.overlay.close();
                return true;
            case CTX:
                if (btn == 0) clickCtx(mx, my);
                else screen.overlay.close();
                return true;
            case TAB_INPUT:
                if (btn == 0) clickTabInput(mx, my);
                return true;
            case TAB_MANAGE:
                if (btn == 0) clickTabManage(mx, my);
                else screen.overlay.close();
                return true;
            case CONFIRM:
                if (btn == 0) clickConfirm(mx, my);
                return true;
            default:
                return false;
        }
    }

    private void clickDetail(double mx, double my) {
        int px = screen.mid(OverlayLayout.DETAIL_W);
        int py = screen.midY(OverlayLayout.DETAIL_H);
        if (GuiUtils.closeHit(mx, my, px, py, OverlayLayout.DETAIL_W)
                || GuiUtils.outsidePanel(mx, my, px, py, OverlayLayout.DETAIL_W, OverlayLayout.DETAIL_H))
            screen.overlay.close();
    }

    private void clickEditor(double mx, double my) {
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

    private void clickStats(double mx, double my) {
        int px = screen.mid(OverlayLayout.STATS_W);
        int py = screen.midY(OverlayLayout.STATS_H);
        if (GuiUtils.closeHit(mx, my, px, py, OverlayLayout.STATS_W)
                || GuiUtils.outsidePanel(mx, my, px, py, OverlayLayout.STATS_W, OverlayLayout.STATS_H))
            screen.overlay.close();
    }

    private void clickCtx(double mx, double my) {
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

    private void clickTabInput(double mx, double my) {
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

    private void clickTabManage(double mx, double my) {
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
                            com.example.advancementoverhaul.LangKeys.TAB_CONFIRM_DELETE_MSG, tab, advCount).getString();
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

    private void clickConfirm(double mx, double my) {
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

    // ═══════════════ Toolbar button handling ═══════════════

    boolean handleButtons(double mx, double my) {
        int s = ICON_S, p = ICON_PAD, gap = ICON_GAP;

        int cx = screen.getScreenWidth() - p - s;
        int cy = p;

        // [1] Close
        if (GuiUtils.inRect(mx, my, cx, cy, s, s)) { screen.onClose(); return true; }
        cx -= s + gap;

        // [2] Stats
        if (GuiUtils.inRect(mx, my, cx, cy, s, s)) {
            screen.overlay.current = screen.overlay.current == Ov.STATS ? Ov.NONE : Ov.STATS;
            screen.showDim = false;
            screen.overlay.statsScrollOff = 0;
            return true;
        }
        cx -= s + gap;

        // [3] Tab management
        if (GuiUtils.inRect(mx, my, cx, cy, s, s)) { screen.openTabManage(); return true; }
        cx -= s + gap;

        // [4] Reset view
        if (GuiUtils.inRect(mx, my, cx, cy, s, s)) { canvasMgr.resetView(); return true; }

        // ── Bottom-right buttons ──
        boolean canEdit = Minecraft.getInstance().player != null && !Minecraft.getInstance().player.isSpectator();
        int by = screen.getScreenHeight() - BOTTOM_H - p - s;
        cx = screen.getScreenWidth() - p - s;

        // [5] Export
        if (GuiUtils.inRect(mx, my, cx, by, s, s)) { GuiUtils.sendCommand("adv export"); return true; }
        by -= s + gap;

        // [6] Import
        if (GuiUtils.inRect(mx, my, cx, by, s, s)) { GuiUtils.sendCommand("adv import"); return true; }
        by -= s + gap;

        // [7] Dimension panel
        if (GuiUtils.inRect(mx, my, cx, by, s, s)) {
            screen.showDim = !screen.showDim;
            if (screen.showDim) { screen.dimPanel.show(); screen.overlay.current = Ov.NONE; }
            return true;
        }
        by -= s + gap;

        // [8] Auto-layout (edit mode only)
        if (canEdit && screen.editMode) {
            if (GuiUtils.inRect(mx, my, cx, by, s, s)) { GuiUtils.sendCommand("adv autolayout"); return true; }
            by -= s + gap;
        }

        // [9] Edit mode toggle
        if (canEdit && GuiUtils.inRect(mx, my, cx, by, s, s)) {
            screen.editMode = !screen.editMode;
            AdvancementScreen.persistEdit = screen.editMode;
            return true;
        }

        return false;
    }
}