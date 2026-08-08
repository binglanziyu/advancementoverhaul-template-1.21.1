package com.dreamer.ao.client.gui.layout;

/**
 * GUI 布局度量的<b>单一真源</b>。
 * <p>
 * 此前布局常量分散在两处且相互冲突——{@code Theme.EP_PW = 360} 与
 * {@code EditPanel.PANEL_W = 380} 同时存在，而 {@code EditPanel} 通过
 * {@code import static Theme.*} 使两者在同一作用域内可见。本类统一承接
 * 全部尺寸常量，{@code Theme} 收敛为纯颜色与动效参数。
 *
 * <h2>自适应策略</h2>
 * Minecraft 自身已有 {@code guiScale} 机制，因此这里<b>不重建一套响应式布局系统</b>，
 * 只解决真实痛点：面板在小分辨率下溢出屏幕。做法是按可用高度计算收缩因子
 * （{@link #panelScale}，上限 1.0——只收缩不放大），并把面板夹取到可用区内
 * （{@link #clampPanelX} / {@link #clampPanelY}）。
 *
 * <h2>正确性红线</h2>
 * 渲染与鼠标命中检测<b>必须读取同一组坐标</b>。为此面板尺寸统一通过
 * {@link #computePanel} 计算一次并写入字段，绝不允许渲染侧单独再乘一次缩放系数，
 * 否则会出现「看得见却点不中」的错位缺陷。
 * <p>
 * 不可实例化的纯常量/工具类。
 */
public final class LayoutMetrics {

    // ══════════════════════════════════════════════════════════
    // 卡片与图标
    // ══════════════════════════════════════════════════════════

    /** Card width (square icon card). */
    public static final int CARD_W      = 50;
    /** Card height. */
    public static final int CARD_H      = 50;
    /** Icon circle radius inside the card. */
    public static final int ICON_RADIUS = 20;
    /** Width of the colored strip on card edge. */
    public static final int STRIP_W     = 1;

    // ══════════════════════════════════════════════════════════
    // 界面框架（标签栏、底栏、工具栏图标）
    // ══════════════════════════════════════════════════════════

    /** Pixel distance before a mouse-down counts as a drag. */
    public static final int DRAG_THRESH = 5;
    /** Tab bar height at the top of the screen. */
    public static final int TAB_H       = 28;
    /** Bottom status bar height. */
    public static final int BOTTOM_H    = 24;
    /** Toolbar icon size (square). */
    public static final int ICON_S      = 18;
    /** Padding from screen edge to first toolbar icon. */
    public static final int ICON_PAD    = 3;
    /** Gap between adjacent toolbar icons. */
    public static final int ICON_GAP    = 3;
    /** Standard small button height (used by {@code GuiUtils.drawSmallBtn}). */
    public static final int SMALL_BTN_H = 20;

    // ══════════════════════════════════════════════════════════
    // 编辑面板 — 总体尺寸
    // ══════════════════════════════════════════════════════════

    /**
     * 编辑面板设计基准宽度。
     * <p>
     * 取原 {@code EditPanel.PANEL_W}（380）为唯一真源——它是实际参与布局计算的值；
     * 原 {@code Theme.EP_PW}（360）从未被面板本身使用，属于历史遗留的冲突定义。
     */
    public static final int PANEL_W     = 380;
    /** 编辑面板设计基准高度。 */
    public static final int PANEL_H     = 340;
    /** 面板与屏幕边缘的最小留白（左右各一半）。 */
    public static final int PANEL_MARGIN = 40;
    /** 面板收缩后的最小可用高度，低于此值将无法容纳必要控件。 */
    public static final int PANEL_MIN_H = 200;
    /** 面板顶部的最小 Y 偏移（避免遮挡标签栏）。 */
    public static final int PANEL_MIN_Y = 20;

    /** 条件列表行高。 */
    public static final int COND_ROW_H  = 22;

    // ══════════════════════════════════════════════════════════
    // 编辑面板 — 内部栅格
    // ══════════════════════════════════════════════════════════

    /** Edit panel inner padding. */
    public static final int EP_PAD      = 10;
    /** Edit panel label width. */
    public static final int EP_LW       = 24;
    /** Edit panel field gap. */
    public static final int EP_FG       = 4;
    /** Edit panel margin gap. */
    public static final int EP_MG       = 8;
    /** Edit panel row height. */
    public static final int EP_ROW      = 24;
    /** Max conditions per advancement. */
    public static final int EP_COND_MAX = 3;
    /** Condition selector row height. */
    public static final int EP_COND_ROW = 14;
    /** Total condition selector area height. */
    public static final int EP_COND_AREA = EP_COND_MAX * EP_COND_ROW + 4;

    // ── Row 1：名称 / 描述 ──
    public static final int NAME_AREA_X   = 40;
    public static final int NAME_FIELD_W  = 120;
    public static final int DESC_LABEL_X  = 168;
    public static final int DESC_AREA_X   = 200;

    // ── Row 2：分类 / 隐藏 / 图标 / 前置 ──
    public static final int BTN_GAP       = 6;
    public static final int CAT_BTN_W     = 80;
    public static final int HIDDEN_BTN_X  = 14 + CAT_BTN_W + BTN_GAP;
    public static final int HIDDEN_BTN_W  = 22;
    public static final int ICON_BTN_X    = HIDDEN_BTN_X + HIDDEN_BTN_W + BTN_GAP;
    public static final int ICON_BTN_W    = 62;
    public static final int PREREQ_BTN_W  = 110;
    /** 普通模式下前置按钮的起始 X。 */
    public static final int PREREQ_BTN_X_NORMAL = ICON_BTN_X + ICON_BTN_W + BTN_GAP;

    // ── 条件行 ──
    public static final int COND_TYPE_X   = 14;
    public static final int COND_TYPE_W   = 72;
    public static final int COND_TGT_X    = 90;
    public static final int COND_CNT_W    = 36;
    public static final int COND_DEL_W    = 24;

    // ══════════════════════════════════════════════════════════
    // 自适应计算
    // ══════════════════════════════════════════════════════════

    /**
     * 面板的实际尺寸与位置。
     * <p>
     * 由 {@link #computePanel} 一次性算出，渲染与命中检测共用，
     * 从根本上杜绝两侧公式漂移导致的点击错位。
     *
     * @param x 面板左上角 X
     * @param y 面板左上角 Y
     * @param w 面板实际宽度
     * @param h 面板实际高度
     */
    public record PanelBounds(int x, int y, int w, int h) {}

    /**
     * 按屏幕尺寸计算居中且完整可见的面板边界。
     * <p>
     * 宽度收缩到 {@code screenW - PANEL_MARGIN} 以内；
     * 高度在 {@code [PANEL_MIN_H, PANEL_H]} 区间内按可用高度夹取。
     */
    public static PanelBounds computePanel(int screenW, int screenH) {
        int w = Math.min(PANEL_W, screenW - PANEL_MARGIN);
        int h = Math.clamp(screenH - PANEL_MARGIN, PANEL_MIN_H, PANEL_H);
        return new PanelBounds(clampPanelX(w, screenW), clampPanelY(h, screenH), w, h);
    }

    /** 可用画布高度：扣除顶部标签栏与底部状态栏。 */
    public static int availableHeight(int screenH) {
        return Math.max(0, screenH - TAB_H - BOTTOM_H);
    }

    /**
     * 面板收缩因子，上限 1.0（空间充足时不放大，避免破坏像素对齐）。
     * <p>
     * 供需要按比例缩放内部元素的场景使用；面板本身的尺寸夹取由
     * {@link #computePanel} 完成。
     */
    public static float panelScale(int screenH) {
        int available = availableHeight(screenH);
        if (available >= PANEL_H) return 1.0f;
        return Math.max(0f, (float) available / PANEL_H);
    }

    /** 水平居中并保证面板完整落在屏幕内。 */
    public static int clampPanelX(int panelW, int screenW) {
        return Math.max(0, (screenW - panelW) / 2);
    }

    /** 垂直居中并保证面板不上溢标签栏。 */
    public static int clampPanelY(int panelH, int screenH) {
        return Math.max(PANEL_MIN_Y, (screenH - panelH) / 2);
    }

    private LayoutMetrics() {}
}
