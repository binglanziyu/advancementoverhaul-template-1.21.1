package com.dreamer.ao.client.gui.manager;

import com.dreamer.ao.client.gui.AdvancementScreen;
import com.dreamer.ao.client.gui.GuiUtils;
import com.dreamer.ao.client.gui.ImageElement;
import com.dreamer.ao.client.gui.ImageManager;
import com.dreamer.ao.client.gui.state.OverlayLayout;
import com.dreamer.ao.client.gui.state.OverlayType;
import com.dreamer.ao.data.ClientDataStore;
import com.dreamer.ao.data.model.VanillaAdvMeta;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.*;

import static com.dreamer.ao.client.gui.Theme.*;

/**
 * Event dispatcher: routes all mouse/keyboard events to CanvasManager / TabManager / AdvancementScreen.
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
            // 右键点击任意位置直接关闭选择器
            if (btn == 1) {
                screen.showSel = false;
                return true;
            }
            if (screen.listSel.mouseClicked(mx, my, btn)) {
                if (!screen.listSel.isVisible()) screen.showSel = false;
                return true;
            }
            screen.showSel = false;
            return true;
        }

        // Priority 2: Help panel
        if (screen.showHelp) {
            if (btn == 0) {
                int px = screen.overlayRenderer.getHelpPx();
                int py = screen.overlayRenderer.getHelpPy();
                int pw = screen.overlayRenderer.getHelpPw();
                int ph = screen.overlayRenderer.getHelpPh();
                // Close button hit
                if (GuiUtils.inRect(mx, my, px + pw - 18, py + 3, 15, 22 - 3)) {
                    screen.showHelp = false;
                    screen.overlayRenderer.resetHelpScroll();
                    return true;
                }
                // Click outside panel → close
                if (GuiUtils.outsidePanel(mx, my, px, py, pw, ph)) {
                    screen.showHelp = false;
                    screen.overlayRenderer.resetHelpScroll();
                    return true;
                }
            }
            return true;
        }

        // Priority 3: Dimension panel
        if (screen.showDim) {
            if (screen.dimPanel.mouseClicked(mx, my, btn)) {
                screen.showDim = screen.dimPanel.isVisible();
                return true;
            }
            screen.showDim = false;
            return true;
        }

        // Priority 3: Modal overlays
        if (screen.overlay.current != OverlayType.NONE) {
            return OverlayClickHandler.handleOverlayClick(screen, mx, my, btn);
        }

        // Priority 4: Toolbar buttons
        if (ToolbarClickHandler.handleButtons(screen, canvasMgr, mx, my)) {
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

        // Bottom status bar (计数显示区，无交互，拦截避免穿透到画布)
        if (my >= screen.getScreenHeight() - BOTTOM_H) {
            return true;
        }

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

            if (card != null) {
                return handleCardLeftClick(mx, my, card);
            }

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
            ImageElement rClickImg = screen.imageAt(mx, my);
            if (rClickImg != null && screen.editMode) {
                screen.showImageCtx(mx, my, rClickImg.getId());
                return true;
            }
            if (card != null) {
                if (screen.editMode) {
                    if (screen.selection.multiSel.size() > 1 && screen.selection.multiSel.contains(card)) {
                        screen.showBatchCtx(mx, my);
                    } else {
                        if (screen.isVanillaAdvId(card)) screen.showVanillaCtx(mx, my, card);
                        else screen.showCtx(mx, my, card);
                    }
                } else {
                    screen.showViewCtx(mx, my, card);
                }
            } else if (screen.editMode) {
                screen.showCanvasCtx(mx, my);
            } else if (!screen.blocksCanvas()) {
                screen.canvas.panning = true;
            }
            return true;
        }

        if (!screen.blocksCanvas()) {
            screen.canvas.panning = true;
        }
        return true;
    }

    private boolean handleCardLeftClick(double mx, double my, String card) {
        if (screen.editMode && Screen.hasShiftDown()) {
            // Shift + 点击：切换多选
            screen.selection.toggle(card);
            if (!screen.selection.multiSel.contains(card)) {
                screen.selection.selectedId = screen.selection.multiSel.isEmpty() ? null
                        : screen.selection.multiSel.iterator().next();
            } else {
                screen.selection.selectedId = card;
            }
        } else if (screen.editMode) {
            // 编辑模式 — 如果点的是多选外的卡片，先清除旧选中状态
            if (!screen.selection.multiSel.contains(card)) {
                screen.selection.multiSel.clear();
            }
            screen.selection.selectedId = card;
            screen.selection.multiSel.add(card);

            if (screen.isVanillaAdvId(card)) {
                var cs = ClientDataStore.getInstance();
                VanillaAdvMeta meta = cs.getVanillaMeta(card);
                if (meta != null && meta.getTab() != null && !meta.getTab().isEmpty()) {
                    screen.drag.dragCardId = card;
                    screen.drag.dragStartMX = mx;
                    screen.drag.dragStartMY = my;
                    screen.drag.lastDragMX = mx;
                    screen.drag.lastDragMY = my;
                    screen.drag.dragMoved = false;
                } else {
                    tabMgr.openVanillaTabSel(card);
                }
            } else {
                screen.drag.dragCardId = card;
                screen.drag.dragStartMX = mx;
                screen.drag.dragStartMY = my;
                screen.drag.lastDragMX = mx;
                screen.drag.lastDragMY = my;
                screen.drag.dragMoved = false;
                // 标记本地拖动未同步，避免后续 syncAll 把该卡片坐标覆盖回服务端旧值
                ClientDataStore.getInstance().markLocalAdvDirty(card);
            }
        } else {
            screen.selection.select(card);
        }
        return true;
    }

    // ═══════════════ MOUSE RELEASE ═══════════════

    public boolean onMouseReleased(double mx, double my, int btn) {
        if (screen.showDim) { screen.dimPanel.mouseReleased(mx, my, btn); }
        if (screen.showSel) { screen.listSel.mouseReleased(mx, my, btn); }
        if (screen.editPanel.isVisible()) {
            screen.editPanel.mouseReleased(screen.getScreenWidth(), screen.getScreenHeight());
        }
        canvasMgr.resetScrollDrag();

        if (screen.tabManageDrag.dragFrom >= 0) {
            if (screen.tabManageDrag.dragging) {
                OverlayClickHandler.commitTabManageReorder(screen, screen.tabManageDrag.dragVisualY);
            }
            screen.tabManageDrag.reset();
            return true;
        }

        if (screen.drag.dragImageId != null) {
            if (screen.drag.dragMoved) ImageManager.save(screen.imageElements);
            screen.drag.dragImageId = null;
            return false;
        }

        if (screen.drag.dragCardId != null) {
            if (screen.drag.dragMoved) {
                // 保存被拖拽卡片的位置
                saveCardPosition(screen.drag.dragCardId);
                // 多选拖动：同时保存所有其他选中卡片的位置
                for (String selId : screen.selection.multiSel) {
                    if (!selId.equals(screen.drag.dragCardId)) {
                        saveCardPosition(selId);
                    }
                }
            } else {
                screen.selection.select(screen.drag.dragCardId);
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
        if (screen.showSel && screen.listSel.mouseDragged(mx, my, btn, dx, dy)) return true;
        if (screen.editPanel.isVisible()) {
            screen.editPanel.mouseDragged(my, screen.getScreenWidth(), screen.getScreenHeight());
        }

        if (screen.tabManageDrag.dragFrom >= 0) {
            if (!screen.tabManageDrag.dragging
                    && Math.abs(my - screen.tabManageDrag.dragStartY) > DRAG_THRESH)
                screen.tabManageDrag.dragging = true;
            if (screen.tabManageDrag.dragging) screen.tabManageDrag.dragVisualY = my;
            return true;
        }

        if (canvasMgr.scrollDragH) { canvasMgr.updateHScrollFromMouse(mx); return true; }
        if (canvasMgr.scrollDragV) { canvasMgr.updateVScrollFromMouse(my); return true; }

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

        if (screen.drag.dragCardId != null) {
            if (!screen.drag.dragMoved && Math.hypot(mx - screen.drag.dragStartMX, my - screen.drag.dragStartMY) > DRAG_THRESH)
                screen.drag.dragMoved = true;
            if (screen.drag.dragMoved) {
                double deltaX = mx - screen.drag.lastDragMX, deltaY = my - screen.drag.lastDragMY;
                int dxWorld = (int) Math.round(deltaX / screen.canvas.zoom);
                int dyWorld = (int) Math.round(deltaY / screen.canvas.zoom);
                // 移动被拖拽的卡片
                moveCard(screen.drag.dragCardId, dxWorld, dyWorld);
                // 多选拖动：同时移动所有其他选中的卡片
                for (String selId : screen.selection.multiSel) {
                    if (!selId.equals(screen.drag.dragCardId)) {
                        moveCard(selId, dxWorld, dyWorld);
                    }
                }
            }
            screen.drag.lastDragMX = mx; screen.drag.lastDragMY = my;
            return true;
        }

        if (screen.drag.boxSel) { screen.drag.bex = mx; screen.drag.bey = my; return true; }

        if (screen.canvas.panning && !screen.blocksCanvas()) {
            screen.canvas.scrollX += dx;
            screen.canvas.scrollY += dy;
            return true;
        }

        return false;
    }

    // ═══════════════ MOUSE SCROLL ═══════════════

    public boolean onMouseScrolled(double mx, double my, double sx, double sy) {
        if (screen.showHelp) {
            int s = screen.overlayRenderer.getHelpScroll();
            s = (int) Math.clamp(s - sy * 20, 0, screen.overlayRenderer.getHelpMaxScroll());
            screen.overlayRenderer.setHelpScroll(s);
            return true;
        }
        if (screen.showDim && screen.dimPanel.mouseScrolled(mx, my, sx, sy)) return true;
        if (screen.showSel && screen.listSel.mouseScrolled(mx, my, sx, sy)) return true;

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

        if (screen.overlay.current == OverlayType.DETAIL) {
            // Bug 2 修复：使用 OverlayRenderer 中计算的真实 maxScroll
            int ms = com.dreamer.ao.client.gui.render.OverlayRenderer.detailMaxScroll;
            screen.overlay.detailScrollOff = (int) Math.clamp(
                    screen.overlay.detailScrollOff - sy * 30, 0, ms);
            return true;
        }

        if (screen.overlay.current == OverlayType.JOURNAL) {
            ClientDataStore cs = ClientDataStore.getInstance();
            int count = 0;
            for (var entry : cs.getAdvancements().entrySet())
                if (cs.isCompleted(entry.getKey())) count++;
            for (var entry : cs.getVanillaAdvancements())
                if (cs.isCompleted(entry.id())) count++;
            int totalH = count * 28;
            int availH = Math.min(screen.getScreenHeight() - 40, 420) - 42;
            int ms = Math.max(0, totalH - availH);
            screen.journalScrollOff = (int) Math.clamp(screen.journalScrollOff - sy * 30, 0, ms);
            return true;
        }

        if ((screen.overlay.current == OverlayType.CREATE || screen.overlay.current == OverlayType.EDIT)
                && screen.editPanel.handleScroll(mx, my, sy, screen.getScreenWidth(), screen.getScreenHeight()))
            return true;

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
        if (screen.showHelp && kc == GLFW.GLFW_KEY_ESCAPE) { screen.showHelp = false; return true; }

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

        if (screen.overlay.current == OverlayType.TAB_INPUT) {
            if (kc == GLFW.GLFW_KEY_ENTER) {
                String tn = screen.tabNameBox.getValue().trim();
                if (!tn.isEmpty()) GuiUtils.sendCommand("adv tab add " + tn);
                screen.closeTabInput();
                return true;
            }
            if (kc == GLFW.GLFW_KEY_ESCAPE) { screen.closeTabInput(); return true; }
            return false;
        }

        if (screen.overlay.current != OverlayType.NONE) {
            if (kc == GLFW.GLFW_KEY_ESCAPE) { screen.overlay.close(); screen.editPanel.close(); return true; }
        }

        if (kc == GLFW.GLFW_KEY_DELETE && screen.editMode && !screen.selection.multiSel.isEmpty()) {
            screen.requestBatchDelete();
            return true;
        }

        if (kc == GLFW.GLFW_KEY_ESCAPE) { screen.onClose(); return true; }

        return false;
    }

    // ═══════════════ 卡片拖拽辅助方法 ═══════════════

    /**
     * 按世界坐标增量移动单张卡片（自定义或原版）。
     * 被 {@link #onMouseDragged} 调用，支持多选批量移动。
     * <p>
     * 移动后会标记空间网格脏，确保下一帧 {@link CardRenderer#ensureGrid()}
     * 用更新后的坐标重建索引，避免卡片视觉位置与数据位置不同步。
     *
     * @param cardId 卡片 ID（自定义 ID 或 vanilla ResourceLocation 字符串）
     * @param dxWorld 世界坐标 X 增量
     * @param dyWorld 世界坐标 Y 增量
     */
    private void moveCard(String cardId, int dxWorld, int dyWorld) {
        if (screen.isVanillaAdvId(cardId)) {
            int[] pos = screen.vanillaPos.get(cardId);
            if (pos != null) {
                pos[0] += dxWorld;
                pos[1] += dyWorld;
            }
        } else {
            var a = ClientDataStore.getInstance().getAdvancement(cardId);
            if (a != null) {
                a.setX(a.getX() + dxWorld);
                a.setY(a.getY() + dyWorld);
            }
        }
        // 标记空间网格脏，确保拖拽时卡片渲染位置实时更新
        screen.cardRenderer.markBoundsDirty();
    }

    /**
     * 发送命令保存单张卡片位置。
     * 被 {@link #onMouseReleased} 调用，支持多选批量保存。
     *
     * @param cardId 卡片 ID
     */
    private void saveCardPosition(String cardId) {
        if (screen.isVanillaAdvId(cardId)) {
            int[] pos = screen.vanillaPos.get(cardId);
            if (pos != null) {
                GuiUtils.sendCommand("adv vanilla setpos " + cardId + " " + pos[0] + " " + pos[1]);
            }
        } else {
            var a = screen.adv(cardId);
            if (a != null) {
                GuiUtils.sendCommand("adv updatejson " + cardId + " {\"x\":" + a.getX() + ",\"y\":" + a.getY() + "}");
            }
        }
    }
}
