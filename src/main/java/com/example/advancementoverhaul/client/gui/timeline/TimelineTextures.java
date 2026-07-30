/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.platform.NativeImage
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.renderer.texture.AbstractTexture
 *  net.minecraft.client.renderer.texture.DynamicTexture
 *  net.minecraft.client.renderer.texture.TextureManager
 *  net.minecraft.resources.ResourceLocation
 */
package com.example.advancementoverhaul.client.gui.timeline;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

public final class TimelineTextures {
    private static boolean initialized = false;
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
        TimelineTextures.ensure();
        return noiseFrost;
    }

    public static ResourceLocation vignette() {
        TimelineTextures.ensure();
        return vignetteTex;
    }

    public static ResourceLocation glow() {
        TimelineTextures.ensure();
        return glowCircle;
    }

    public static ResourceLocation droplet() {
        TimelineTextures.ensure();
        return droplet;
    }

    public static ResourceLocation streak() {
        TimelineTextures.ensure();
        return waterStreak;
    }

    public static void ensure() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            TimelineTextures.generateAll();
        }
        catch (Exception e) {
            initialized = false;
        }
    }

    public static void cleanup() {
        TextureManager tm = Minecraft.getInstance().getTextureManager();
        TimelineTextures.release(tm, noiseFrost);
        TimelineTextures.release(tm, vignetteTex);
        TimelineTextures.release(tm, glowCircle);
        TimelineTextures.release(tm, droplet);
        TimelineTextures.release(tm, waterStreak);
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
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
    }

    private static void generateAll() {
        noiseFrost = TimelineTextures.gen("noise_frost", 256, 256, TimelineTextures::pxNoiseFrost);
        vignetteTex = TimelineTextures.gen("vignette", 512, 512, TimelineTextures::pxVignette);
        glowCircle = TimelineTextures.gen("glow_circle", 64, 64, TimelineTextures::pxGlow);
        droplet = TimelineTextures.gen("droplet", 8, 8, TimelineTextures::pxDroplet);
        waterStreak = TimelineTextures.gen("water_streak", 4, 128, TimelineTextures::pxStreak);
    }

    private static ResourceLocation gen(String name, int w, int h, Px fn) {
        NativeImage img = new NativeImage(w, h, false);
        for (int y = 0; y < h; ++y) {
            for (int x = 0; x < w; ++x) {
                img.setPixelRGBA(x, y, fn.at(x, y, w, h));
            }
        }
        DynamicTexture dyn = new DynamicTexture(img);
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath((String)"advancementoverhaul", (String)("textures/gui/timeline/" + name));
        Minecraft.getInstance().getTextureManager().register(loc, (AbstractTexture)dyn);
        return loc;
    }

    private static int pxNoiseFrost(int x, int y, int w, int h) {
        if (noiseGrid == null) {
            Random rng = new Random(7777L);
            noiseGrid = new float[21][21];
            for (int j = 0; j <= 20; ++j) {
                for (int i = 0; i <= 20; ++i) {
                    TimelineTextures.noiseGrid[i][j] = rng.nextFloat();
                }
            }
        }
        float fx = (float)x / (float)w * 20.0f;
        float fy = (float)y / (float)h * 20.0f;
        int ix = (int)fx;
        int iy = (int)fy;
        float dx = fx - (float)ix;
        float dy = fy - (float)iy;
        float v = TimelineTextures.bilerp(ix, iy, dx, dy);
        int a = (int)(v * 22.0f);
        return TimelineTextures.argb(a, 208, 224, 232);
    }

    private static float bilerp(int ix, int iy, float dx, float dy) {
        ix = Math.min(ix, 19);
        iy = Math.min(iy, 19);
        float a = noiseGrid[ix][iy];
        float b = noiseGrid[ix + 1][iy];
        float c = noiseGrid[ix][iy + 1];
        float d = noiseGrid[ix + 1][iy + 1];
        return TimelineTextures.lerp(TimelineTextures.lerp(a, b, dx), TimelineTextures.lerp(c, d, dx), dy);
    }

    private static int pxVignette(int x, int y, int w, int h) {
        float dx = ((float)x - (float)w / 2.0f) / ((float)w / 2.0f);
        float dy = ((float)y - (float)h / 2.0f) / ((float)h / 2.0f);
        float dist = (float)Math.sqrt(dx * dx + dy * dy);
        float v = TimelineTextures.clamp01((dist - 0.55f) / 0.65f);
        v *= v;
        int a = (int)(v * 60.0f);
        return TimelineTextures.argb(a, 12, 20, 32);
    }

    private static int pxGlow(int x, int y, int w, int h) {
        float dx = ((float)x - (float)w / 2.0f) / ((float)w / 2.0f);
        float dy = ((float)y - (float)h / 2.0f) / ((float)h / 2.0f);
        float dist = (float)Math.sqrt(dx * dx + dy * dy);
        float brightness = TimelineTextures.clamp01(1.0f - dist);
        brightness *= brightness;
        int a = (int)(brightness * 255.0f);
        return TimelineTextures.argb(a, 255, 255, 255);
    }

    private static int pxDroplet(int x, int y, int w, int h) {
        float dx = ((float)x - (float)(w - 1) / 2.0f) / ((float)(w - 1) / 2.0f);
        float dy = ((float)y - (float)(h - 1) / 2.0f) / ((float)(h - 1) / 2.0f);
        float dist = (float)Math.sqrt(dx * dx + dy * dy);
        if (dist > 1.0f) {
            return 0;
        }
        float brightness = 1.0f - dist * dist;
        if (dy < 0.0f) {
            brightness *= 1.3f;
        }
        brightness = Math.min(1.0f, brightness);
        int a = (int)(brightness * 34.0f);
        return TimelineTextures.argb(a, 204, 222, 232);
    }

    private static int pxStreak(int x, int y, int w, int h) {
        float dx = ((float)x - (float)(w - 1) / 2.0f) / ((float)(w - 1) / 2.0f);
        float hFade = 1.0f - dx * dx;
        float vFade = 1.0f;
        if (y < 8) {
            vFade = (float)y / 8.0f;
        }
        if (y > h - 12) {
            vFade = (float)(h - y) / 12.0f;
        }
        float variation = 0.7f + 0.3f * (float)Math.sin((double)y * 0.3);
        int a = (int)(hFade * vFade * variation * 20.0f);
        return TimelineTextures.argb(a, 160, 200, 216);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    private static int argb(int a, int r, int g, int b) {
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | b & 0xFF;
    }

    @FunctionalInterface
    static interface Px {
        public int at(int var1, int var2, int var3, int var4);
    }
}

