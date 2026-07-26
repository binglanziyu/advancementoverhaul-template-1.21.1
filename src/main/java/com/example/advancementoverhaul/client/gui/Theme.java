package com.example.advancementoverhaul.client.gui;

import java.util.List;

public final class Theme {

    // ══════════════════════════════════════════════════════════
    // Colors — Base surfaces
    // ══════════════════════════════════════════════════════════

    /** Main canvas background fill. */
    public static final int BG          = 0xFF383848;
    /** Floating panel/dialog surface — semi-transparent for overlay effect. */
    public static final int PANEL       = 0xF02A2A3C;
    /** Opaque bar surface for the tab strip and bottom status bar. */
    public static final int BAR         = 0xFF2A2A3A;
    /** Thin divider lines between UI sections. */
    public static final int DIVIDER     = 0xFF444460;
    /** Subtle canvas grid lines (very low alpha). */
    public static final int GRID        = 0x10FFFFFF;

    // ══════════════════════════════════════════════════════════
    // Colors — Card states
    // ══════════════════════════════════════════════════════════

    /** Default card background. */
    public static final int CARD        = 0xFF444460;
    /** Card background when the advancement is completed. */
    public static final int CARD_DONE   = 0xFF3A5248;
    /** Card background on mouse hover. */
    public static final int CARD_HOV    = 0xFF50506C;
    /** Card background when selected. */
    public static final int CARD_SEL    = 0xFF58587A;
    /** Card background for vanilla (built-in) advancements. */
    public static final int CARD_VANILLA= 0xFF555570;

    // ══════════════════════════════════════════════════════════
    // Colors — Accents
    // ══════════════════════════════════════════════════════════

    /** Primary accent (green) — completed states, active borders, panel header stripe. */
    public static final int ACCENT      = 0xFF4CAF50;
    /** Secondary accent (pink) — destructive actions (delete). */
    public static final int PINK        = 0xFFE91E63;
    /** Tertiary accent (blue) — informational highlights, focused input borders. */
    public static final int BLUE        = 0xFF42A5F5;
    /** Quaternary accent (orange) — warnings, special indicators. */
    public static final int ORANGE      = 0xFFF0A040;

    // ══════════════════════════════════════════════════════════
    // Colors — Text
    // ══════════════════════════════════════════════════════════

    /** Standard readable text. */
    public static final int TEXT        = 0xFFD8D8E8;
    /** Bright/emphasized text (headings, hover states). */
    public static final int TEXT_BR     = 0xFFFFFFFF;
    /** Dimmed/secondary text (placeholders, close icons). */
    public static final int TEXT_DIM    = 0xFF9090A8;

    // ══════════════════════════════════════════════════════════
    // Colors — Lines and connections
    // ══════════════════════════════════════════════════════════

    /** Default prerequisite connection line. */
    public static final int LINE        = 0xFF555570;
    /** Connection line when prerequisite is satisfied (completed). */
    public static final int LINE_DONE   = 0xFF4CAF50;

    // ══════════════════════════════════════════════════════════
    // Colors — Progress bar
    // ══════════════════════════════════════════════════════════

    /** Progress bar background track. */
    public static final int BAR_BG      = 0xFF2E2E42;

    // ══════════════════════════════════════════════════════════
    // Colors — Buttons
    // ══════════════════════════════════════════════════════════

    /** Default button surface. */
    public static final int BTN         = 0xFF3A3A52;
    /** Button surface on hover. */
    public static final int BTN_HOV     = 0xFF4A4A68;

    // ══════════════════════════════════════════════════════════
    // Colors — Context menu
    // ══════════════════════════════════════════════════════════

    /** Context menu surface. */
    public static final int CTX         = 0xF02E2E42;
    /** Context menu item on hover. */
    public static final int CTX_HOV     = 0xFF444460;

    // ══════════════════════════════════════════════════════════
    // Colors — Input fields
    // ══════════════════════════════════════════════════════════

    /** Input field background. */
    public static final int INPUT_BG    = 0xFF333350;
    /** Input field border (unfocused). */
    public static final int INPUT_BORDER= 0xFF555570;
    /** Input field border (focused). */
    public static final int INPUT_BORDER_FOCUSED = 0xFF42A5F5;

    // ══════════════════════════════════════════════════════════
    // Colors — Tooltips
    // ══════════════════════════════════════════════════════════

    /** Tooltip background (used by {@code GuiUtils.drawHoverTooltip}). */
    public static final int TOOLTIP_BG     = 0xF01A1A2E;
    /** Tooltip border. */
    public static final int TOOLTIP_BORDER = 0xFF555570;

    // ══════════════════════════════════════════════════════════
    // Layout — Card and icon dimensions
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
    // Layout — Chrome (tab bar, bottom bar, toolbar icons)
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
    // Layout — Edit panel dimensions
    // ══════════════════════════════════════════════════════════

    /** Edit panel total width. */
    public static final int EP_PW       = 360;
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
    public static final int EP_COND_AREA= EP_COND_MAX * EP_COND_ROW + 4;

    // ══════════════════════════════════════════════════════════
    // Interaction — Zoom and animation
    // ══════════════════════════════════════════════════════════

    /** Minimum canvas zoom level. */
    public static final double ZOOM_MIN = 0.3;
    /** Maximum canvas zoom level. */
    public static final double ZOOM_MAX = 1.5;
    /** Default animation duration in milliseconds (toasts, transitions). */
    public static final long ANIM_MS    = 400;

    // ══════════════════════════════════════════════════════════
    // Presets
    // ══════════════════════════════════════════════════════════

    /**
     * Preset count values for the edit panel count selector.
     * Immutable list — safe to share without defensive copying.
     * Previously an {@code int[]} which was externally mutable.
     */
    public static final List<Integer> COUNT_PRESETS = List.of(1, 2, 5, 10, 16, 25, 50, 64);

    private Theme() {}
}