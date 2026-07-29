package com.example.advancementoverhaul.client.gui.cache;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pre-rendered circle textures for GPU-accelerated circle drawing.
 * One-time GPU upload, then single blit() call per circle instead of ~40 fill() calls.
 *
 * <p>If initialization fails (e.g. GPU context unavailable), falls back to
 * pixel-by-pixel rendering automatically.
 */
public final class CircleCache {

    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/CircleCache");

    private static final int TEX_SIZE = 128; // power-of-2, radius=64
    private static final int HALF = TEX_SIZE / 2;

    private static ResourceLocation filledId;
    private static ResourceLocation outlineId;
    private static boolean ready = false;

    private CircleCache() {}

    public static void init() {
        if (ready) return;
        try {
            NativeImage filled = createImage(true);
            NativeImage outline = createImage(false);

            filledId = ResourceLocation.fromNamespaceAndPath("advancementoverhaul", "circle_filled");
            outlineId = ResourceLocation.fromNamespaceAndPath("advancementoverhaul", "circle_outline");

            Minecraft mc = Minecraft.getInstance();
            DynamicTexture filledTex = new DynamicTexture(filled);
            filledTex.upload();
            mc.getTextureManager().register(filledId, filledTex);

            DynamicTexture outlineTex = new DynamicTexture(outline);
            outlineTex.upload();
            mc.getTextureManager().register(outlineId, outlineTex);

            ready = true;
            LOGGER.info("Circle cache initialized ({}x{} textures)", TEX_SIZE, TEX_SIZE);
        } catch (Exception e) {
            ready = false;
            LOGGER.warn("Circle cache init failed, falling back to pixel-by-pixel rendering: {}", e.getMessage());
        }
    }

    private static NativeImage createImage(boolean filled) {
        NativeImage img = new NativeImage(TEX_SIZE, TEX_SIZE, true);
        int innerR = HALF - 2;

        for (int y = 0; y < TEX_SIZE; y++) {
            for (int x = 0; x < TEX_SIZE; x++) {
                double dx = x - HALF + 0.5;
                double dy = y - HALF + 0.5;
                double dist = Math.sqrt(dx * dx + dy * dy);

                int alpha;
                if (filled) {
                    alpha = (int) Math.clamp((HALF - dist + 0.5) * 255, 0, 255);
                } else {
                    double mid = (HALF + innerR) / 2.0;
                    double halfW = (HALF - innerR) / 2.0;
                    alpha = (int) Math.clamp((halfW - Math.abs(dist - mid) + 0.5) * 255, 0, 255);
                }
                // NativeImage.setPixelRGBA: packed ARGB format (A in highest byte)
                img.setPixelRGBA(x, y, (alpha << 24) | 0x00FFFFFF);
            }
        }
        return img;
    }

    public static void fillCircle(GuiGraphics g, int cx, int cy, int r, int color) {
        if (!ready || r <= 0) { fallbackFill(g, cx, cy, r, color); return; }

        int size = r * 2;
        float a = ((color >>> 24) & 0xFF) / 255f;
        float red = ((color >> 16) & 0xFF) / 255f;
        float green = ((color >> 8) & 0xFF) / 255f;
        float blue = (color & 0xFF) / 255f;

        RenderSystem.enableBlend();
        g.setColor(red, green, blue, a);
        g.blit(filledId, cx - r, cy - r, size, size,
                0f, 0f, TEX_SIZE, TEX_SIZE, TEX_SIZE, TEX_SIZE);
        g.setColor(1f, 1f, 1f, 1f);
    }

    public static void drawCircleOutline(GuiGraphics g, int cx, int cy, int r, int color) {
        if (!ready || r <= 0) { fallbackOutline(g, cx, cy, r, color); return; }

        int size = r * 2;
        float a = ((color >>> 24) & 0xFF) / 255f;
        float red = ((color >> 16) & 0xFF) / 255f;
        float green = ((color >> 8) & 0xFF) / 255f;
        float blue = (color & 0xFF) / 255f;

        RenderSystem.enableBlend();
        g.setColor(red, green, blue, a);
        g.blit(outlineId, cx - r, cy - r, size, size,
                0f, 0f, TEX_SIZE, TEX_SIZE, TEX_SIZE, TEX_SIZE);
        g.setColor(1f, 1f, 1f, 1f);
    }

    // ── Fallback (original pixel-by-pixel) ──

    static void fallbackFill(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt(Math.max(0, (double) r * r - (double) dy * dy));
            g.fill(cx - dx, cy + dy, cx + dx, cy + dy + 1, color);
        }
    }

    static void fallbackOutline(GuiGraphics g, int cx, int cy, int r, int color) {
        int inner = Math.max(0, r - 2);
        for (int dy = -r; dy <= r; dy++) {
            int outerDx = (int) Math.sqrt(Math.max(0, (double) r * r - (double) dy * dy));
            int innerDx = Math.abs(dy) <= inner
                    ? (int) Math.sqrt(Math.max(0, (double) inner * inner - (double) dy * dy))
                    : 0;
            g.fill(cx - outerDx, cy + dy, cx - innerDx, cy + dy + 1, color);
            g.fill(cx + innerDx, cy + dy, cx + outerDx, cy + dy + 1, color);
        }
    }
}