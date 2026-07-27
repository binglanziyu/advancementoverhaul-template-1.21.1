package com.example.advancementoverhaul.client.gui;

/**
 * GUI 渲染与交互工具方法集合。
 * <p>
 * 提供圆角矩形绘制、圆形绘制、文字截断、GUI 缩放、音效播放、
 * C2S 命令发送等杂项功能。大部分方法为静态方法，供渲染器和面板复用。
 */
import com.example.advancementoverhaul.client.gui.cache.CircleCache;
import com.example.advancementoverhaul.client.gui.cache.RoundedRectCache;

import net.minecraft.client.Minecraft;
import com.example.advancementoverhaul.network.C2SCommandPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import static com.example.advancementoverhaul.client.gui.Theme.*;

public final class GuiUtils {

    public static final int KEY_ESCAPE    = 256;
    public static final int KEY_ENTER     = 257;

    public static final int KEY_BACKSPACE = 259;
    public static final int KEY_DELETE    = 261;
    public static final int KEY_LEFT      = 263;
    public static final int KEY_RIGHT     = 262;
    public static final int KEY_HOME      = 268;
    public static final int KEY_END       = 269;

    private GuiUtils() {}

    // ═══════════════ Geometry ═══════════════

    public static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public static boolean inCircle(double mx, double my, int cx, int cy, int r) {
        double dx = mx - cx, dy = my - cy;
        return dx * dx + dy * dy <= (double) r * r;
    }

    // ═══════════════ Text ═══════════════

    public static String truncate(Font font, String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        int ellipsisW = font.width("\u2026");
        int lo = 0, hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (font.width(s.substring(0, mid)) + ellipsisW <= maxW) lo = mid;
            else hi = mid - 1;
        }
        return s.substring(0, lo) + "\u2026";
    }

    // ═══════════════ Drawing helpers ═══════════════

    public static void drawSmallBtn(GuiGraphics g, Font font, int x, int y, int w, String label, boolean hov) {
        g.fill(x, y, x + w, y + SMALL_BTN_H, hov ? BTN_HOV : BTN);
        g.renderOutline(x, y, w, SMALL_BTN_H, hov ? ACCENT : DIVIDER);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 4, hov ? TEXT_BR : TEXT, false);
    }

    public static void drawIconBtn(GuiGraphics g, Font font, int x, int y, int sz, String icon, boolean hov, boolean active) {
        g.fill(x, y, x + sz, y + sz, active ? BTN_HOV : (hov ? BTN_HOV : BTN));
        g.renderOutline(x, y, sz, sz, active ? ACCENT : DIVIDER);
        g.drawString(font, icon, x + (sz - font.width(icon)) / 2, y + (sz - 8) / 2,
                active ? ACCENT : (hov ? TEXT_BR : TEXT), false);
    }

    public static void drawPanelBg(GuiGraphics g, Font font, int px, int py, int pw, int ph,
                                   String title, int sw, int sh) {
        // 全屏暗色遮罩由 AdvancementScreen 统一管理（hasOv() 分支），
        // 此处仅绘制面板自身背景，避免 z=300 叠加造成双重变暗
        g.fill(px, py, px + pw, py + ph, PANEL);
        g.renderOutline(px, py, pw, ph, DIVIDER);
        g.fill(px, py, px + pw, py + 3, ACCENT);
        g.drawString(font, title, px + 14, py + 10, TEXT_BR, false);
        g.drawString(font, "\u2715", px + pw - 16, py + 10, TEXT_DIM, false);
    }

    public static void fillCircle(GuiGraphics g, int cx, int cy, int r, int color) {
        CircleCache.fillCircle(g, cx, cy, r, color);
    }

    public static void drawCircleOutline(GuiGraphics g, int cx, int cy, int r, int color) {
        CircleCache.drawCircleOutline(g, cx, cy, r, color);
    }

    // ═══════════════ P1: Rounded rectangle card drawing ═══════════════

    /** Draw a filled rounded rectangle card with optional drop shadow. */
    public static void fillRoundedCard(GuiGraphics g, int x, int y, int w, int h, int color) {
        RoundedRectCache.fillRoundedRect(g, x, y, w, h, color);
    }

    /** Draw a drop shadow under a card (offset by SHADOW_OFF pixels). */
    public static void drawCardShadow(GuiGraphics g, int x, int y, int w, int h) {
        RoundedRectCache.fillRoundedRect(g, x + SHADOW_OFF, y + SHADOW_OFF, w, h, SHADOW_COL);
    }

    /**
     * Draw a 1px rounded border for a card.
     * Uses an inset technique: draws the full area in border color,
     * then a 1px-smaller filled rect in background color on top.
     */
    public static void drawRoundedBorder(GuiGraphics g, int x, int y, int w, int h,
                                         int borderCol, int bgCol) {
        RoundedRectCache.fillRoundedRect(g, x, y, w, h, borderCol);
        RoundedRectCache.fillRoundedRect(g, x + 1, y + 1, w - 2, h - 2, bgCol);
    }

    // ═══════════════ Command sending ═══════════════


    public static void sendCommand(String cmd) {
        PacketDistributor.sendToServer(new C2SCommandPayload(cmd));
    }

    // ═══════════════ Sound ═══════════════

    public static void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    // ═══════════════ Panel hit-testing ═══════════════

    public static boolean closeHit(double mx, double my, int px, int py, int pw) {
        return inRect(mx, my, px + pw - 20, py + 6, 18, 18);
    }

    public static boolean outsidePanel(double mx, double my, int px, int py, int pw, int ph) {
        return mx < px || mx > px + pw || my < py || my > py + ph;
    }

    // ═══════════════ Tooltip ═══════════════

    public static void drawHoverTooltip(GuiGraphics g, Font font, int mx, int my,
                                        int btnX, int btnY, int btnW, int btnH,
                                        String text, int screenW, int screenH) {
        if (!inRect(mx, my, btnX, btnY, btnW, btnH)) return;
        int tw = font.width(text) + 8;
        int tx = btnX + btnW + 4;
        int ty = btnY;
        if (tx + tw > screenW) tx = btnX - tw - 4;
        if (ty + 16 > screenH) ty = screenH - 20;
        g.fill(tx, ty, tx + tw, ty + 16, TOOLTIP_BG);
        g.renderOutline(tx, ty, tw, 16, TOOLTIP_BORDER);
        g.drawString(font, text, tx + 4, ty + 4, TEXT, false);
    }
}