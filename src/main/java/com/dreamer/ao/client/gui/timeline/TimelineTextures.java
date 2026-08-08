package com.dreamer.ao.client.gui.timeline;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import com.dreamer.ao.ModInfo;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public final class TimelineTextures {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean initialized;
    private static ResourceLocation noiseFrost;
    private static ResourceLocation vignetteTex;
    private static ResourceLocation glowCircle;
    private static ResourceLocation droplet;
    private static ResourceLocation waterStreak;
    private static float[][] noiseGrid;
    private static final int NG = 20;

    private TimelineTextures() {
    }

    public static ResourceLocation noiseFrost() {
        ensure();
        return noiseFrost;
    }

    public static ResourceLocation vignette() {
        ensure();
        return vignetteTex;
    }

    public static ResourceLocation glow() {
        ensure();
        return glowCircle;
    }

    public static ResourceLocation droplet() {
        ensure();
        return droplet;
    }

    public static ResourceLocation streak() {
        ensure();
        return waterStreak;
    }

    public static void ensure() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            generateAll();
        } catch (Exception e) {
            initialized = false;
        }
    }

    public static void cleanup() {
        TextureManager tm = Minecraft.getInstance().getTextureManager();
        release(tm, noiseFrost);
        release(tm, vignetteTex);
        release(tm, glowCircle);
        release(tm, droplet);
        release(tm, waterStreak);
        waterStreak = null;
        droplet = null;
        glowCircle = null;
        vignetteTex = null;
        noiseFrost = null;
        initialized = false;
    }

    private static void release(TextureManager tm, ResourceLocation loc) {
        if (loc != null) {
            try {
                tm.release(loc);
            } catch (Exception ignored) {
                LOGGER.debug("Failed to release texture {}", loc, ignored);
            }
        }
    }

    private static void generateAll() {
        noiseFrost = gen("noise_frost", 256, 256, TimelineTextures::pxNoiseFrost);
        vignetteTex = gen("vignette", 512, 512, TimelineTextures::pxVignette);
        glowCircle = gen("glow_circle", 64, 64, TimelineTextures::pxGlow);
        droplet = gen("droplet", 8, 8, TimelineTextures::pxDroplet);
        waterStreak = gen("water_streak", 4, 128, TimelineTextures::pxStreak);
    }

    private static ResourceLocation gen(String name, int w, int h, Px fn) {
        NativeImage img = new NativeImage(w, h, false);
        for (int y = 0; y < h; ++y) {
            for (int x = 0; x < w; ++x) {
                img.setPixelRGBA(x, y, fn.at(x, y, w, h));
            }
        }
        DynamicTexture dyn = new DynamicTexture(img);
        ResourceLocation loc = ModInfo.rl("textures/gui/timeline/" + name);
        Minecraft.getInstance().getTextureManager().register(loc, dyn);
        return loc;
    }

    private static int pxNoiseFrost(int x, int y, int w, int h) {
        if (noiseGrid == null) {
            Random rng = new Random(7777L);
            noiseGrid = new float[21][21];
            for (int j = 0; j <= 20; ++j) {
                for (int i = 0; i <= 20; ++i) {
                    noiseGrid[i][j] = rng.nextFloat();
                }
            }
        }
        float fx = (float) x / (float) w * 20.0f;
        float fy = (float) y / (float) h * 20.0f;
        int ix = (int) fx;
        int iy = (int) fy;
        float dx = fx - (float) ix;
        float dy = fy - (float) iy;
        float v = bilerp(ix, iy, dx, dy);
        int a = (int) (v * 22.0f);
        return argb(a, 208, 224, 232);
    }

    private static float bilerp(int ix, int iy, float dx, float dy) {
        ix = Math.min(ix, 19);
        iy = Math.min(iy, 19);
        float a = noiseGrid[ix][iy];
        float b = noiseGrid[ix + 1][iy];
        float c = noiseGrid[ix][iy + 1];
        float d = noiseGrid[ix + 1][iy + 1];
        return lerp(lerp(a, b, dx), lerp(c, d, dx), dy);
    }

    private static int pxVignette(int x, int y, int w, int h) {
        float dx = ((float) x - (float) w / 2.0f) / ((float) w / 2.0f);
        float dy = ((float) y - (float) h / 2.0f) / ((float) h / 2.0f);
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float v = clamp01((dist - 0.55f) / 0.65f);
        v *= v;
        int a = (int) (v * 60.0f);
        return argb(a, 12, 20, 32);
    }

    private static int pxGlow(int x, int y, int w, int h) {
        float dx = ((float) x - (float) w / 2.0f) / ((float) w / 2.0f);
        float dy = ((float) y - (float) h / 2.0f) / ((float) h / 2.0f);
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float brightness = clamp01(1.0f - dist);
        brightness *= brightness;
        int a = (int) (brightness * 255.0f);
        return argb(a, 255, 255, 255);
    }

    private static int pxDroplet(int x, int y, int w, int h) {
        float dx = ((float) x - (float) (w - 1) / 2.0f) / ((float) (w - 1) / 2.0f);
        float dy = ((float) y - (float) (h - 1) / 2.0f) / ((float) (h - 1) / 2.0f);
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist > 1.0f) {
            return 0;
        }
        float brightness = 1.0f - dist * dist;
        if (dy < 0.0f) {
            brightness *= 1.3f;
        }
        brightness = Math.min(1.0f, brightness);
        int a = (int) (brightness * 34.0f);
        return argb(a, 204, 222, 232);
    }

    private static int pxStreak(int x, int y, int w, int h) {
        float dx = ((float) x - (float) (w - 1) / 2.0f) / ((float) (w - 1) / 2.0f);
        float hFade = 1.0f - dx * dx;
        float vFade = 1.0f;
        if (y < 8) {
            vFade = (float) y / 8.0f;
        }
        if (y > h - 12) {
            vFade = (float) (h - y) / 12.0f;
        }
        float variation = 0.7f + 0.3f * (float) Math.sin((double) y * 0.3);
        int a = (int) (hFade * vFade * variation * 20.0f);
        return argb(a, 160, 200, 216);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    private static int argb(int a, int r, int g, int b) {
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }

    @FunctionalInterface
    interface Px {
        int at(int x, int y, int w, int h);
    }
}
