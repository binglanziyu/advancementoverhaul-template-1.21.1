package com.example.advancementoverhaul.client.gui;

import com.example.advancementoverhaul.client.gui.cache.CircleCache;

import net.minecraft.client.Minecraft;
import com.example.advancementoverhaul.network.C2SCommandPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.example.advancementoverhaul.client.gui.Theme.*;

public final class GuiUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/GuiUtils");

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
        g.fill(0, 0, sw, sh, 0x80000000);
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

    // ═══════════════ Command sending ═══════════════


    public static void sendCommand(String cmd) {
        PacketDistributor.sendToServer(new C2SCommandPayload(cmd));
    }

    // ═══════════════ Sound ═══════════════

    public static void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    // ═══════════════ ItemStack serialization ═══════════════

    public static String serializeStack(ItemStack s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || s.isEmpty()) return "";
        try {
            return ((net.minecraft.nbt.CompoundTag) s.save(mc.level.registryAccess())).getAsString();
        } catch (Exception e) {
            LOGGER.warn("Failed to serialize ItemStack: {}", e.getMessage());
            return "";
        }
    }

    public static ItemStack deserializeStack(String nbt) {
        if (nbt == null || nbt.isEmpty()) return ItemStack.EMPTY;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return ItemStack.EMPTY;
        try {
            return ItemStack.parse(mc.level.registryAccess(),
                    net.minecraft.nbt.TagParser.parseTag(nbt)).orElse(ItemStack.EMPTY);
        } catch (Exception e) {
            LOGGER.warn("Failed to deserialize ItemStack from NBT: {}", e.getMessage());
            return ItemStack.EMPTY;
        }
    }

    public static String componentSummary(ItemStack s) {
        if (s.isEmpty()) return "";
        List<String> parts = new ArrayList<>();
        try {
            var enchants = s.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
            if (enchants != null)
                for (var e : enchants.entrySet())
                    parts.add(e.getKey().value().description().getString());
        } catch (Exception ignored) {}
        return String.join(", ", parts);
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