package com.example.advancementoverhaul.client.gui;

/**
 * 画布上的图片元素，可拖动、缩放、锁定。
 */
public class ImageElement {
    private String id;
    private String path;
    private int x, y;
    private float scale;
    private boolean locked;

    private transient net.minecraft.resources.ResourceLocation textureId;
    private transient int originalWidth;
    private transient int originalHeight;

    public ImageElement() { this.scale = 1.0f; this.locked = false; }

    public ImageElement(String id, String path, int x, int y) {
        this.id = id;
        this.path = path;
        this.x = x;
        this.y = y;
        this.scale = 1.0f;
        this.locked = false;
    }

    public String getId() { return id; }
    public String getPath() { return path; }
    public int getX() { return x; }
    public int getY() { return y; }
    public float getScale() { return scale; }
    public boolean isLocked() { return locked; }
    public net.minecraft.resources.ResourceLocation getTextureId() { return textureId; }
    public int getOriginalWidth() { return originalWidth; }
    public int getOriginalHeight() { return originalHeight; }

    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public void setScale(float scale) { this.scale = Math.max(0.1f, Math.min(5.0f, scale)); }
    public void setLocked(boolean locked) { this.locked = locked; }
    public void setTextureId(net.minecraft.resources.ResourceLocation id) { this.textureId = id; }
    public void setOriginalSize(int w, int h) { this.originalWidth = w; this.originalHeight = h; }

    public int getRenderWidth() { return (int) (originalWidth * scale); }
    public int getRenderHeight() { return (int) (originalHeight * scale); }
}