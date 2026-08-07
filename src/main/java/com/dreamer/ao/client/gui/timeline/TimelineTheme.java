package com.dreamer.ao.client.gui.timeline;

/**
 * 时间线界面颜色主题常量。
 * <p>
 * 所有颜色均为 0xAARRGGBB 格式（由 build 工具从原始 int 值精确转换），
 * 确保值完全不变，仅提升可读性。
 */
public final class TimelineTheme {

    // ── Background colors ──
    public static final int BG_BASE             = 0x888A9EB0;
    public static final int BG                  = 0x888A9EB0;
    public static final int WARM_GLOW           = 0x14E8AEC8;
    public static final int DEEP_TINT           = 0x40606080;
    public static final int MIST                = 0x1C7DE948;
    public static final int GLASS_PANEL         = 0x60D3C2EA;
    public static final int GLASS_BORDER        = 0x20855FC8;
    public static final int GLASS_HEADER        = 0xFFFFFF;
    public static final int GLASS_INPUT_BG      = 0x6099B8D0;

    // ── Accent colors ──
    public static final int ACCENT_CYAN         = 0xFF7EC8CC;
    public static final int ACCENT_GLOW         = 0x2C7DB04C;
    public static final int ACCENT_SOFT         = 0xFF5AAEB4;
    public static final int ACCENT_MUTED        = 0xFF6AA8B4;
    public static final int EDIT_MODE_INDICATOR = 0xFF7EC8CC;

    // ── Text colors ──
    public static final int TEXT_PRIMARY        = 0xFFE4F0F6;
    public static final int TEXT_SECONDARY      = 0xAAAECCD8;
    public static final int TEXT_EMPHASIS       = 0xFFD0E4EE;
    public static final int TEXT_ACCENT         = 0xFF7EC8CC;
    public static final int TEXT_MUTED          = 0x8082A8B4;
    public static final int TEXT_WHITE          = 0xFFEAF4F8;
    public static final int TAB_TEXT_DIM        = 0xBB90B0C0;
    public static final int TAB_TEXT            = 0xFFD4E8F0;
    public static final int HINT_TEXT           = 0xAA98B8C8;

    // ── Axis and mark colors ──
    public static final int AXIS                = 0xD0BED0DA;
    public static final int AXIS_GLOW           = 0x1C9BB054;
    public static final int TICK_MARK           = 0x489820D0;
    public static final int DAY_LABEL           = 0xBB96B4C4;
    public static final int HOVER_RING          = 0x847EC8CC;
    public static final int HOVER_RING_INNER    = 0x447DB0CC;
    public static final int RING_RADIUS         = 14;
    public static final int RING_INNER_RADIUS   = 6;

    // ── Branch colors ──
    public static final int BRANCH_LINE         = 0x3098A0D0;
    public static final int BRANCH_NODE         = 0x50C0A0DC;
    public static final int BRANCH_NODE_UNLOCKED = 0xDD8BC4D0;
    public static final int BRANCH_LINE_LENGTH  = 28;

    // ── Node colors ──
    public static final int NODE_UNLOCKED       = 0xDD8BC4D0;
    public static final int NODE_LOCKED         = 0x28B098D8;
    public static final int NODE_HOVER          = 0xFFA0D8E0;
    public static final int NODE_WICK           = 0xFFE8F4F8;
    public static final int NODE_WICK_COLOR     = 0xFFE8F4F8;
    public static final int NODE_CORE           = 0xFF9AD0DC;
    public static final int NODE_LOCKED_GLOW    = 0x0CB498D8;
    public static final int NODE_HOVER_GLOW     = 0x30A0C8E0;
    public static final int NODE_GLOW_OUTER     = 0x108C70D0;
    public static final int NODE_GLOW_MID       = 0x209870D0;
    public static final int NODE_GLOW_INNER     = 0x38A870D0;
    public static final int NODE_GLOW_RADIUS    = 21;
    public static final int NODE_RADIUS         = 7;
    public static final int NODE_RADIUS_HOVER   = 10;

    // ── Glow colors (RGB components) ──
    public static final int GLOW_OUTER_RGB      = 0x70ADB8;
    public static final int GLOW_MID_RGB        = 0x78B6C4;
    public static final int GLOW_INNER_RGB      = 0x89C4CC;
    public static final int GLOW_CORE_RGB       = 0x99D0DC;
    public static final int GLOW_OUTER_A        = 60;
    public static final int GLOW_MID_A          = 100;
    public static final int GLOW_INNER_A        = 140;
    public static final int GLOW_CORE_A         = 200;

    // ── Tab colors ──
    public static final int TAB_SELECTED        = 0x5C934DD4;
    public static final int TAB_HOVER           = 0x3C9BB098;
    public static final int TAB_INDICATOR       = 0xCC7EC8CC;
    public static final int TAB_RADIUS          = 6;
    public static final int TAB_PAD_X           = 12;
    public static final int TAB_BAR_BG          = 0xFFFFFF;
    public static final int TAB_BG              = 0xFFFFFF;
    public static final int TAB_SHADOW          = 0;

    // ── Editor colors ──
    public static final int EDITOR_PANEL        = 0xE0D6E0EA;
    public static final int EDITOR_INPUT_BG     = 0x6099B8D0;
    public static final int EDITOR_BORDER       = 0x3894A8D0;
    public static final int EDITOR_OVERLAY      = 0x800C1828;
    public static final int EDITOR_SELECT_BG    = 0x549094C8;
    public static final int EDITOR_HOVER        = 0x408090CC;
    public static final int EDIT_MODE_BG        = 0x1C7EB04C;

    // ── Button colors ──
    public static final int BTN_SHADOW          = 0x38000000;
    public static final int BTN_RADIUS          = 5;
    public static final int DEL_BTN             = 0xDDCC6A6A;
    public static final int DEL_BTN_HOVER       = 0xEECC6A6A;
    public static final int SAVE_BTN            = 0xDD6AB4BC;
    public static final int SAVE_BTN_HOVER      = 0xEE6AB4BC;
    public static final int CANCEL_BTN          = 0x6C90A0C8;
    public static final int CANCEL_BTN_HOVER    = 0x8C90BCC8;

    // ── Rain / visual effect colors ──
    public static final int RAIN_DROP           = 0x1490ACD8;
    public static final int RAIN_DROP_FAINT     = 0x0A9080D8;
    public static final int NOISE_ALPHA         = 10;
    public static final int STREAK_ALPHA        = 8;
    public static final int NOISE_COLUMNS       = 24;

    // ── Link colors ──
    public static final int LINK_ADV            = 0xFFD0B878;

    // ── Panel aliases ──
    public static final int PANEL               = 0x60D3C2EA;
    public static final int DIVIDER             = 0x20855FC8;
    public static final int OVERLAY             = 0x800C1828;

    // ── Layout dimensions ──
    public static final int HEADER_H            = 34;
    public static final int TAB_H               = 30;
    public static final int BOTTOM_H            = 28;
    public static final int AXIS_Y_FALLBACK     = 130;
    public static final int BRANCH_Y_OFFSET     = 32;
    public static final int DAY_PITCH           = 80;
    public static final int PADDING             = 16;
    public static final int MIN_TIMELINE_DAYS   = 10;
    public static final int AXIS_HOVER_ZONE     = 18;
    public static final int LABEL_ABOVE_OFFSET  = 22;
    public static final int ICON_ABOVE_OFFSET   = 12;

    // ── Particle / effect counts ──
    public static final int RAIN_COUNT          = 40;
    public static final int DROPLET_COUNT       = 15;
    public static final int STREAK_COUNT        = 6;

    // ── Zoom and scroll ──
    public static final double ZOOM_MIN         = 0.25;
    public static final double ZOOM_MAX         = 2.5;
    public static final double ZOOM_STEP        = 0.1;
    public static final int SCROLL_SPEED        = 20;

    // ── Phase panel colors ──
    public static final int PREVIEW_TAG_BG       = 0x30F59E0B;
    public static final int PREVIEW_TAG_FG       = 0xFFF59E0B;

    // ── Animation timing ──
    public static final long ENTER_ANIM_DURATION = 1200L;
    public static final long NODE_STAGGER_DELAY  = 80L;
    public static final long BREATHE_PERIOD      = 3000L;

    private TimelineTheme() {
    }
}
