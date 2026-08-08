package com.dreamer.ao.client.gui.render;

import com.dreamer.ao.client.gui.AdvancementScreen;
import com.dreamer.ao.client.gui.layout.LayoutMetrics;
import com.dreamer.ao.client.gui.state.OverlayType;
import com.dreamer.ao.client.gui.state.OverlayLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;

import static com.dreamer.ao.client.gui.Theme.*;

/**
 * 屏幕渲染调度协调器。
 * <p>
 * 接管 {@link AdvancementScreen} 中原本内联的五个渲染私有方法
 * （画布背景 / 画布内容 / 遮罩层 / Chrome / Overlay 层），
 * 使屏幕类回归为纯粹的状态持有与事件转发外壳。
 * 沿用项目既有约定：构造接收 {@code AdvancementScreen} 引用，
 * 通过它访问 public 字段与访问器方法（与 {@code CardRenderer(this)}、
 * {@code TabRenderer(this)} 模式一致）。
 */
public final class ScreenRenderCoordinator {

    private final AdvancementScreen screen;

    public ScreenRenderCoordinator(AdvancementScreen screen) {
        this.screen = screen;
    }

    /** 顶层渲染入口，语义与 {@code AdvancementScreen.render} 一致。 */
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderCanvasBackground(g);
        renderCanvasContent(g, mx, my);
        renderBackgroundMask(g);
        renderChrome(g, mx, my);
        renderOverlayLayer(g, mx, my, pt);
        screen.anim.tick();
    }

    /** Background fill for the entire screen. */
    private void renderCanvasBackground(GuiGraphics g) {
        g.fill(0, 0, screen.width, screen.height, BG);
        screen.cardRenderer.tickFrameTime();
    }

    /** Scrolled canvas region: grid, connections, cards, box selection, scroll indicators. */
    private void renderCanvasContent(GuiGraphics g, int mx, int my) {
        // Bug 4 修复：扩展 scissor 区域，防止 hover zoom 时边缘卡片被截断
        int hovPad = (int) (LayoutMetrics.CARD_W * screen.canvas.zoom * HOVER_ZOOM);
        g.enableScissor(-hovPad, LayoutMetrics.TAB_H - hovPad, screen.width + hovPad * 2, screen.height - LayoutMetrics.BOTTOM_H + hovPad);
        screen.cardRenderer.renderGrid(g);
        // CTX 浮动右键菜单不应遮挡底层画布：菜单设计为半透明浮层，
        // 仍需看到后面的卡片与连线，因此即使 blocksCanvas() 命中 CTX 也照常渲染。
        boolean canvasBlocked = screen.blocksCanvas()
                && screen.overlay.current != OverlayType.CTX;
        if (!canvasBlocked) {
            screen.cardRenderer.renderConnections(g);
            screen.cardRenderer.renderCards(g, mx, my);
            if (screen.drag.boxSel && screen.editMode) screen.cardRenderer.renderBoxSel(g);
        }
        screen.cardRenderer.renderScrollIndicators(g);
        g.disableScissor();
    }

    /**
     * Opaque mask to prevent {@code renderItem} 3D quads from penetrating
     * through popup overlays (ListSelector / detail/edit panels).
     */
    private void renderBackgroundMask(GuiGraphics g) {
        if (screen.showSel) {
            // 全屏完全不透明遮罩（选择器弹窗需要完全遮挡底层卡片）
            g.fill(0, 0, screen.width, screen.height, 0xFF1A1A2E);
        } else if (screen.hasOv()) {
            if (screen.overlay.current == OverlayType.CTX) {
                // CTX 浮动小菜单无需遮罩，让用户仍能看到底层画布
                return;
            } else if (screen.overlay.current == OverlayType.CREATE || screen.overlay.current == OverlayType.EDIT) {
                // 编辑/创建面板使用半透明遮罩，保持对底层画布的可见性
                g.fill(0, LayoutMetrics.TAB_H, screen.width, screen.height - LayoutMetrics.BOTTOM_H, 0xA01A1A2E);
            } else {
                // DETAIL/CONFIRM/TAB_INPUT 等保持完全不透明遮罩
                g.fill(0, LayoutMetrics.TAB_H, screen.width, screen.height - LayoutMetrics.BOTTOM_H, 0xFF1A1A2E);
            }
        }
    }

    /** Tab bar, bottom status bar, and toolbar buttons. */
    private void renderChrome(GuiGraphics g, int mx, int my) {
        screen.tabRenderer.renderTabs(g, mx, my);
        screen.tabRenderer.renderBottom(g, mx, my);
        screen.tabRenderer.renderButtons(g, mx, my);
    }

    /**
     * Overlay layer rendered at z=300: edit/detail/create panels,
     * dimension panel, list selector, help screen, managed EditBoxes,
     * tab-name input, card tooltips, and toast notifications.
     */
    private void renderOverlayLayer(GuiGraphics g, int mx, int my, float pt) {
        boolean ebVis = (screen.overlay.current == OverlayType.CREATE || screen.overlay.current == OverlayType.EDIT)
                && !screen.showSel && !screen.editPanel.isCondSelActive();
        screen.editPanel.updateVisibility(ebVis);

        g.pose().pushPose();
        g.pose().translate(0, 0, 300);

        screen.overlayRenderer.renderOv(g, mx, my);
        if (screen.overlay.current == OverlayType.JOURNAL) screen.overlayRenderer.renderJournal(g, mx, my, screen.getFont(), screen.width, screen.height);
        if (screen.showDim) screen.dimPanel.render(g, mx, my);
        if (screen.showSel) screen.listSel.render(g, mx, my);
        if (screen.showHelp) screen.overlayRenderer.renderHelp(g, mx, my, screen.getFont(), screen.width, screen.height);

        // Managed EditBoxes
        for (var w : screen.getRenderablesView()) {
            if (w instanceof EditBox eb && eb.isVisible()) {
                boolean isInlineCount = eb == screen.editPanel.getInlineCountBox();
                if (ebVis || isInlineCount) eb.render(g, mx, my, pt);
            }
        }

        // Tab name input popup
        if (screen.overlay.current == OverlayType.TAB_INPUT) {
            int px = screen.mid(OverlayLayout.TAB_INPUT_W);
            int py = screen.midY(OverlayLayout.TAB_INPUT_H);
            screen.tabNameBox.setX(px + OverlayLayout.TAB_INPUT_INNER_PAD);
            screen.tabNameBox.setY(py + OverlayLayout.TAB_INPUT_BOX_Y);
            screen.tabNameBox.setWidth(OverlayLayout.TAB_INPUT_W - 2 * OverlayLayout.TAB_INPUT_INNER_PAD);
            screen.tabNameBox.setVisible(true);
            screen.tabNameBox.render(g, mx, my, pt);
        } else {
            screen.tabNameBox.setVisible(false);
        }

        // Hover tooltip (only when no overlay is active)
        if (!screen.hasOv()) {
            // 图片元素覆盖时不显示卡片 tooltip（避免穿透显示）
            if (screen.imageAt(mx, my) == null) {
                String hoverCard = screen.getCanvasManager().cardAt(mx, my);
                if (hoverCard != null) screen.overlayRenderer.renderTooltip(g, mx, my, hoverCard);
            }
        }

        screen.overlayRenderer.renderToasts(g);
        g.pose().popPose();
    }
}
