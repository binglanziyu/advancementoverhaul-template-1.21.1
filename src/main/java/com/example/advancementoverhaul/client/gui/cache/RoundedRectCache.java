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
 * Pre-rendered rounded rectangle texture for GPU-accelerated rounded rect drawing.
 * Follows the same pattern as {@link CircleCache} — one-time GPU upload,
 * single blit() call per rounded rect with RenderSystem color tinting.
 *
 * <p>Texture size: 128×128, corner radius: 16 (mapped from CARD_RADIUS=8 at 50px card size).
 *
 * <p>If initialization fails, falls back to plain g.fill() rectangle.
 */
public final class RoundedRectCache {

    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/RoundedRectCache");

    private static final int TEX_SIZE = 128;
    private static final int CORNER_R = 20; // corner radius in texture space (maps to ~8px at 50px card)

    private static ResourceLocation filledId;
    private static boolean ready = false;

    private RoundedRectCache() {}

    public static void init() {
        if (ready) return;
        try {
            NativeImage filled = createImage();

            filledId = ResourceLocation.fromNamespaceAndPath("advancementoverhaul", "rounded_rect_filled");

            Minecraft mc = Minecraft.getInstance();
            DynamicTexture filledTex = new DynamicTexture(filled);
            filledTex.upload();
            mc.getTextureManager().register(filledId, filledTex);

            ready = true;
            LOGGER.info("RoundedRect cache initialized ({}x{} texture, cornerR={})", TEX_SIZE, TEX_SIZE, CORNER_R);
        } catch (Exception e) {
            ready = false;
            LOGGER.warn("RoundedRect cache init failed, falling back to plain rectangle rendering: {}", e.getMessage());
        }
    }

    private static NativeImage createImage() {
        NativeImage img = new NativeImage(TEX_SIZE, TEX_SIZE, true);
        double cr = CORNER_R;

        for (int y = 0; y < TEX_SIZE; y++) {
            for (int x = 0; x < TEX_SIZE; x++) {
                double alpha = 1.0;

                // Top-left corner
                if (x < cr && y < cr) {
                    double dx = cr - x - 0.5;
                    double dy = cr - y - 0.5;
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    alpha = Math.clamp((cr - dist + 0.5), 0, 1);
                }
                // Top-right corner
                else if (x >= TEX_SIZE - cr && y < cr) {
                    double dx = x - (TEX_SIZE - cr) + 0.5;
                    double dy = cr - y - 0.5;
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    alpha = Math.clamp((cr - dist + 0.5), 0, 1);
                }
                // Bottom-left corner
                else if (x < cr && y >= TEX_SIZE - cr) {
                    double dx = cr - x - 0.5;
                    double dy = y - (TEX_SIZE - cr) + 0.5;
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    alpha = Math.clamp((cr - dist + 0.5), 0, 1);
                }
                // Bottom-right corner
                else if (x >= TEX_SIZE - cr && y >= TEX_SIZE - cr) {
                    double dx = x - (TEX_SIZE - cr) + 0.5;
                    double dy = y - (TEX_SIZE - cr) + 0.5;
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    alpha = Math.clamp((cr - dist + 0.5), 0, 1);
                }

                int a = (int) (alpha * 255);
                img.setPixelRGBA(x, y, (a << 24) | 0x00FFFFFF);
            }
        }
        return img;
    }

    public static void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (!ready || w <= 0 || h <= 0) {
            fallbackFill(g, x, y, w, h, color);
            return;
        }

        float a = ((color >>> 24) & 0xFF) / 255f;
        float red = ((color >> 16) & 0xFF) / 255f;
        float green = ((color >> 8) & 0xFF) / 255f;
        float blue = (color & 0xFF) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(red, green, blue, a);
        try {
            g.blit(filledId, x, y, w, h,
                    0f, 0f, TEX_SIZE, TEX_SIZE, TEX_SIZE, TEX_SIZE);
        } finally {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();
        }
    }

    static void fallbackFill(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color);
    }
}
