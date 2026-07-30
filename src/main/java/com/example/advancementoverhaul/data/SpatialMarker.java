/*
 * Decompiled with CFR 0.152.
 */
package com.example.advancementoverhaul.data;

public class SpatialMarker {
    private String id;
    private String name;
    private int x;
    private int y;
    private int z;
    private String dimension;
    private String icon;
    private int color;
    private long createdAt;

    public SpatialMarker() {
    }

    public SpatialMarker(String id, String name, int x, int y, int z, String dimension, String icon, int color) {
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
        this.icon = icon;
        this.color = color;
        this.createdAt = System.currentTimeMillis();
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getX() {
        return this.x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return this.y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getZ() {
        return this.z;
    }

    public void setZ(int z) {
        this.z = z;
    }

    public String getDimension() {
        return this.dimension;
    }

    public void setDimension(String dimension) {
        this.dimension = dimension;
    }

    public String getIcon() {
        return this.icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public int getColor() {
        return this.color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public long getCreatedAt() {
        return this.createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public double distance2D(int px, int pz) {
        double dx = this.x - px;
        double dz = this.z - pz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public double distance3D(int px, int py, int pz) {
        double dx = this.x - px;
        double dy = this.y - py;
        double dz = this.z - pz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}

