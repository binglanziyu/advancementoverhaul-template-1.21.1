package com.dreamer.ao.data;

/**
 * NBT/Component 匹配模式。
 */
public enum NbtMatchMode {
    IGNORE("ignore"),
    CONTAINS("contains"),
    EXACT("exact"),
    NONE_EMPTY("none_empty");

    private final String saveName;

    NbtMatchMode(String saveName) { this.saveName = saveName; }

    public String getSaveName() { return saveName; }

    public static NbtMatchMode fromSaveName(String name) {
        if (name == null) return IGNORE;
        for (NbtMatchMode m : values()) {
            if (m.saveName.equalsIgnoreCase(name)) return m;
        }
        return IGNORE;
    }
}
