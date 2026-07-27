package com.example.advancementoverhaul.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * 成就完成牌匾 UI。
 * 从屏幕上方优雅滑入，展示成就名称，带小猫爪元素。
 * <p>
 * 通过 {@code RegisterGuiLayersEvent} 注册为 HUD 覆盖层，
 * 无论何种屏幕都会显示。
 * <p>
 * 若同时完成多个成就，排队依次展示，每段持续约 2.5 秒。
 */
public final class CompletionPlaque {

    // ═══════════════ State ═══════════════

    /** 待展示的牌匾队列 */
    private static final Queue<String> queue = new ArrayDeque<>();

    private static String advancementName = "";
    private static long spawnTime = 0;
    private static boolean active = false;

    // ═══════════════ Timing (ms) ═══════════════

    private static final long SLIDE_DURATION = 400;
    private static final long HOLD_DURATION = 2400;
    private static final long FADE_DURATION = 300;
    private static final long TOTAL_DURATION = SLIDE_DURATION + HOLD_DURATION + FADE_DURATION;

    // ═══════════════ Layout ═══════════════

    private static final int PLAQUE_W = 240;
    private static final int PLAQUE_H = 56;
    private static final int TARGET_Y = 22;

    // ═══════════════ Colors (base, alpha applied per frame) ═══════════════

    private static final int C_BG_OUTER = 0x24163E;     // deep purple outer
    private static final int C_BG_INNER = 0x362058;     // slightly lighter inner
    private static final int C_BORDER = 0xE8C0FF;        // lavender
    private static final int C_GOLD = 0xFFFFD700;         // gold
    private static final int C_TEXT = 0xFFFFF8E7;          // warm white
    private static final int C_PAW = 0xFFFFB6C1;           // light pink
    private static final int C_SPARKLE = 0xFFFFE0B0;       // pale sparkle
    private static final int C_ACCENT = 0xFFD4A0FF;        // accent lavender
    private static final int C_DIVIDER = 0x40D4A0FF;       // low-alpha divider

    private CompletionPlaque() {}

    // ═══════════════ Public API ═══════════════

    /**
     * 展示牌匾。
     * 若当前已有牌匾在展示中，则将新名称加入队列等待展示。
     */
    public static void show(String name) {
        if (active) {
            // 当前正在展示，加入队列
            queue.add(name);
        } else {
            advancementName = name;
            spawnTime = System.currentTimeMillis();
            active = true;
        }
    }

    /** 每帧由 GUI Layer 回调渲染。 */
    public static void render(GuiGraphics g, float partialTick) {
        if (!active) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || mc.font == null) return;

        long elapsed = System.currentTimeMillis() - spawnTime;

        // 当前展示完成，出队下一个（若有）
        if (elapsed > TOTAL_DURATION) {
            String next = queue.poll();
            if (next != null) {
                advancementName = next;
                spawnTime = System.currentTimeMillis();
                elapsed = 0;
            } else {
                active = false;
                return;
            }
        }

        int screenW = mc.getWindow().getGuiScaledWidth();
        Font font = mc.font;

        // --- Animation ---
        int alpha;
        int y;

        if (elapsed < SLIDE_DURATION) {
            float t = (float) elapsed / SLIDE_DURATION;
            float phase = easeOutBack(t);
            alpha = 255;
            y = TARGET_Y + (int) ((1f - phase) * -120);
        } else if (elapsed < SLIDE_DURATION + HOLD_DURATION) {
            alpha = 255;
            y = TARGET_Y;
        } else {
            float t = (float) (elapsed - SLIDE_DURATION - HOLD_DURATION) / (float) FADE_DURATION;
            alpha = (int) (255 * (1f - easeInQuad(t)));
            y = TARGET_Y;
        }

        if (alpha <= 0) return;
        int x = (screenW - PLAQUE_W) / 2;
        int a = alpha;

        // --- Render ---
        renderPlaque(g, font, x, y, a);
    }

    // ═══════════════ Main render ═══════════════

    private static void renderPlaque(GuiGraphics g, Font font, int x, int y, int a) {
        // Shadow
        GuiUtils.drawCardShadow(g, x, y, PLAQUE_W, PLAQUE_H);

        // Outer background
        g.fill(x, y, x + PLAQUE_W, y + PLAQUE_H, (a << 24) | C_BG_OUTER);

        // Inner glow region (lighter center)
        int innerMargin = 4;
        g.fill(x + innerMargin, y + innerMargin,
                x + PLAQUE_W - innerMargin, y + PLAQUE_H - innerMargin,
                ((int)(a * 0.45f) << 24) | C_BG_INNER);

        // Border
        int borderA = (int)(a * 0.75f);
        g.renderOutline(x, y, PLAQUE_W, PLAQUE_H,
                (borderA << 24) | C_BORDER);

        // Top accent bar
        int accentA = (int)(a * 0.9f);
        g.fill(x + 8, y + 2, x + PLAQUE_W - 8, y + 4,
                (accentA << 24) | C_ACCENT);

        // ── Top sparkles ──
        drawSparkle(g, x + 11, y + 7, 1.5f, (a << 24) | C_SPARKLE);
        drawSparkle(g, x + PLAQUE_W - 11, y + 7, 1.5f, (a << 24) | C_SPARKLE);

        // ── Paw + Title row ──
        int pawA = a;
        drawSmallCatPaw(g, x + 22, y + 6, 5, (pawA << 24) | C_PAW);
        drawSmallCatPaw(g, x + PLAQUE_W - 22, y + 6, 5, (pawA << 24) | C_PAW, true);

        String title = "\u2726 \u6210\u5C31\u8FBE\u6210 \u2726"; // "✦ 成就达成 ✦"
        int titleW = font.width(title);
        g.drawString(font, title, x + (PLAQUE_W - titleW) / 2, y + 10,
                (a << 24) | C_GOLD, false);

        // ── Divider ──
        int divX1 = x + 30;
        int divX2 = x + PLAQUE_W - 30;
        int divY = y + 22;
        g.fill(divX1, divY, divX2, divY + 1, (a << 24) | C_DIVIDER);

        // ── Center paw decorations ──
        drawTinyPaw(g, x + 38, divY - 2, 3, ((int)(a * 0.7f) << 24) | C_PAW);
        drawTinyPaw(g, x + PLAQUE_W - 41, divY - 2, 3, ((int)(a * 0.7f) << 24) | C_PAW);

        // ── Advancement name ──
        String displayName = GuiUtils.truncate(font, advancementName, PLAQUE_W - 40);
        int nameW = font.width(displayName);
        g.drawString(font, displayName, x + (PLAQUE_W - nameW) / 2, y + 29,
                (a << 24) | C_TEXT, false);

        // ── Bottom paw trail ──
        drawPawTrail(g, x + 16, y + PLAQUE_H - 12, 3, (a << 24) | C_PAW, 1);
        drawPawTrail(g, x + PLAQUE_W - 16, y + PLAQUE_H - 12, 3, (a << 24) | C_PAW, -1);

        // ── Bottom corner sparkles ──
        drawSparkle(g, x + 9, y + PLAQUE_H - 7, 1.0f, ((int)(a * 0.6f) << 24) | C_SPARKLE);
        drawSparkle(g, x + PLAQUE_W - 9, y + PLAQUE_H - 7, 1.0f, ((int)(a * 0.6f) << 24) | C_SPARKLE);
    }

    // ═══════════════ Cat paw drawing ═══════════════

    /**
     * 绘制完整小猫爪印（主肉垫 + 4 个趾垫扇形排列）。
     *
     * @param mirror true 时镜像翻转
     */
    private static void drawSmallCatPaw(GuiGraphics g, int cx, int cy, int size,
                                         int color, boolean mirror) {
        int d = mirror ? -1 : 1;

        // 主肉垫：圆角矩形
        int pw = (int) (size * 1.6f);
        int ph = (int) (size * 1.0f);
        GuiUtils.fillRoundedCard(g, cx - pw / 2, cy + 2, pw, ph, color);

        // 4 个趾垫：弧形排列
        int tr = Math.max(1, (int) (size * 0.3f));
        int spread = (int) (size * 1.15f);
        int outerY = cy - 2;
        int innerY = cy - size + 1;

        GuiUtils.fillCircle(g, cx - spread * d, outerY, tr, color);
        GuiUtils.fillCircle(g, cx - spread / 3 * d, innerY, tr, color);
        GuiUtils.fillCircle(g, cx + spread / 3 * d, innerY, tr, color);
        GuiUtils.fillCircle(g, cx + spread * d, outerY, tr, color);
    }

    private static void drawSmallCatPaw(GuiGraphics g, int cx, int cy,
                                         int size, int color) {
        drawSmallCatPaw(g, cx, cy, size, color, false);
    }

    /** 绘制迷你猫爪（仅主垫+2 趾）。 */
    private static void drawTinyPaw(GuiGraphics g, int cx, int cy, int size, int color) {
        int pw = (int) (size * 1.4f);
        int ph = (int) (size * 0.8f);
        GuiUtils.fillRoundedCard(g, cx - pw / 2, cy + 1, pw, ph, color);
        int tr = Math.max(1, size / 3);
        GuiUtils.fillCircle(g, cx - size / 2, cy - size / 3, tr, color);
        GuiUtils.fillCircle(g, cx + size / 2, cy - size / 3, tr, color);
    }

    /** 绘制一对迷你爪印组成的脚印轨迹。 */
    private static void drawPawTrail(GuiGraphics g, int startX, int cy, int size,
                                      int color, int dir) {
        for (int i = 0; i < 2; i++) {
            int cx = startX + i * size * 3 * dir;
            int tr = Math.max(1, size / 3);
            // 主肉垫
            GuiUtils.fillCircle(g, cx, cy + size / 2, size / 2, color);
            // 趾垫
            GuiUtils.fillCircle(g, cx - size * dir, cy - size / 3, tr, color);
            GuiUtils.fillCircle(g, cx + size * dir, cy - size / 3, tr, color);
        }
    }

    // ═══════════════ Sparkle drawing ═══════════════

    /** 绘制四角星闪烁。 */
    private static void drawSparkle(GuiGraphics g, int cx, int cy, float size, int color) {
        int s = (int) size;
        // 水平线
        g.fill(cx - s, cy, cx + s + 1, cy + 1, color);
        // 垂直线
        g.fill(cx, cy - s, cx + 1, cy + s + 1, color);
    }

    // ═══════════════ Easing functions ═══════════════

    /** 回弹缓出：c ≈ 1.70158 */
    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    private static float easeInQuad(float t) {
        return t * t;
    }
}
