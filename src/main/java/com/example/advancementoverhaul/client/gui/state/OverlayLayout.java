package com.example.advancementoverhaul.client.gui.state;

/**
 * Shared dimensions for overlay panels, used by both InputManager (hit-testing)
 * and AdvancementScreen/OverlayRenderer (positioning and drawing).
 * Eliminates the risk of mismatched hardcoded values between input handling
 * and rendering code.
 */
public final class OverlayLayout {

    // ── Detail panel ──
    public static final int DETAIL_W = 340;
    public static final int DETAIL_H = 360;
    /** 详情面板内容区顶部偏移（标题栏下方） */
    public static final int DETAIL_CONTENT_TOP = 30;
    /** 详情面板内容区底部偏移（按钮栏上方） */
    public static final int DETAIL_CONTENT_BOTTOM = 30;

    // ── Stats panel ──
    public static final int STATS_W = 300;
    public static final int STATS_H = 260;

    // ── Tab input dialog ──
    public static final int TAB_INPUT_W = 260;
    public static final int TAB_INPUT_H = 100;
    public static final int TAB_INPUT_INNER_PAD = 14;
    public static final int TAB_INPUT_BOX_Y = 34;
    public static final int TAB_INPUT_BTN_Y = 70;
    public static final int TAB_INPUT_BTN_W = 76;
    public static final int TAB_INPUT_BTN_H = 20;
    public static final int TAB_INPUT_OK_RIGHT = 90;       // ← 补这行
    public static final int TAB_INPUT_CANCEL_RIGHT = 180;

    // ── Confirm dialog ──
    public static final int CONFIRM_W = 300;
    public static final int CONFIRM_H = 100;
    public static final int CONFIRM_BTN_W = 72;
    public static final int CONFIRM_BTN_H = 20;
    public static final int CONFIRM_BTN_BOTTOM = 36;

    // ── Context menu ──
    public static final int CTX_ITEM_W = 120;
    public static final int CTX_ITEM_H = 22;
    public static final int CTX_PAD = 2;

    private OverlayLayout() {}
}