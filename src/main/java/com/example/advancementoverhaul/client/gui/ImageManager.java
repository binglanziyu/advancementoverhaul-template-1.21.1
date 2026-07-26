package com.example.advancementoverhaul.client.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * 管理画布图片元素的加载、缓存和持久化。
 */
public final class ImageManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/ImageManager");
    private static final String IMAGE_DIR = "images";
    private static final String SAVE_FILE = "image_elements.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path configDir;
    private static final Map<String, DynamicTexture> textureCache = new HashMap<>();

    private ImageManager() {}

    public static void init(Path dir) {
        configDir = dir.resolve("advancement_overhaul");
        try { Files.createDirectories(configDir.resolve(IMAGE_DIR)); }
        catch (IOException e) { LOGGER.error("Failed to create images directory", e); }
    }

    public static Path getImagesDir() {
        return configDir != null ? configDir.resolve(IMAGE_DIR) : null;
    }

    public static List<String> listImageFiles() {
        List<String> files = new ArrayList<>();
        Path dir = getImagesDir();
        if (dir == null || !Files.exists(dir)) return files;
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.toString().toLowerCase().endsWith(".png"))
                    .sorted()
                    .forEach(p -> files.add(p.getFileName().toString()));
        } catch (IOException e) { LOGGER.error("Failed to list images", e); }
        return files;
    }

    /**
     * 加载图片纹理，返回 ResourceLocation 或 null。
     * 同时将错误信息存入 lastError，供调用方读取。
     */
    private static String lastError = null;

    public static String getLastError() { return lastError; }

    public static ResourceLocation loadTexture(String elementId, String filename) {
        lastError = null;
        String cacheKey = elementId;
        if (textureCache.containsKey(cacheKey) && textureCache.get(cacheKey) != null) {
            return ResourceLocation.fromNamespaceAndPath("advancementoverhaul",
                    "img_" + elementId.replace(":", "_").replace("/", "_"));
        }
        Path dir = getImagesDir();
        if (dir == null) { lastError = "图片目录不存在"; return null; }
        Path filePath = dir.resolve(filename);
        if (!Files.exists(filePath)) { lastError = "文件不存在: " + filename; return null; }

        // 文件大小检查（16MB）
        try {
            if (Files.size(filePath) > 16 * 1024 * 1024) {
                lastError = "文件过大（超过16MB）: " + filename;
                return null;
            }
        } catch (IOException ignored) {}

        try (InputStream is = Files.newInputStream(filePath)) {
            NativeImage nativeImage = NativeImage.read(is);
            DynamicTexture texture = new DynamicTexture(nativeImage);
            ResourceLocation texId = ResourceLocation.fromNamespaceAndPath("advancementoverhaul",
                    "img_" + elementId.replace(":", "_").replace("/", "_"));
            Minecraft.getInstance().getTextureManager().register(texId, texture);
            textureCache.put(cacheKey, texture);
            return texId;
        } catch (Exception e) {
            lastError = "无法解析图片: " + filename + "（" + e.getMessage() + "）";
            LOGGER.warn("Failed to load image {}: {}", filename, e.getMessage());
            return null;
        }
    }

    public static int[] getTextureSize(String elementId) {
        DynamicTexture tex = textureCache.get(elementId);
        if (tex != null && tex.getPixels() != null) {
            return new int[]{ tex.getPixels().getWidth(), tex.getPixels().getHeight() };
        }
        return new int[]{ 64, 64 };
    }

    public static void save(List<ImageElement> elements) {
        if (configDir == null) return;
        try {
            Files.writeString(configDir.resolve(SAVE_FILE), GSON.toJson(elements));
        } catch (IOException e) { LOGGER.error("Failed to save image elements", e); }
    }

    public static List<ImageElement> load() {
        if (configDir == null) return new ArrayList<>();
        Path f = configDir.resolve(SAVE_FILE);
        if (!Files.exists(f)) return new ArrayList<>();
        try {
            Type t = new TypeToken<List<ImageElement>>(){}.getType();
            List<ImageElement> list = GSON.fromJson(Files.readString(f), t);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            LOGGER.error("Failed to load image elements", e);
            return new ArrayList<>();
        }
    }

    public static void clearCache() {
        for (DynamicTexture tex : textureCache.values()) {
            if (tex != null) tex.close();
        }
        textureCache.clear();
    }
}