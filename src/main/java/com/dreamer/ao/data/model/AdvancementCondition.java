package com.dreamer.ao.data.model;

import com.dreamer.ao.data.ConditionType;
import com.dreamer.ao.data.NbtMatchMode;

/**
 * 单个进度条件：类型、目标、数量、NBT/Component 匹配配置。
 * <p>
 * NbtMatchMode 使用 transient 缓存枚举值，避免每次 getter 做字符串遍历。
 */
public class AdvancementCondition {
    private ConditionType type;
    private String targetId;
    private int count;
    private String nbtMatchMode;
    private String targetNbt;

    /** transient 枚举缓存，避免每次 getter 做字符串遍历 */
    private transient NbtMatchMode nbtMatchModeParsed;

    public AdvancementCondition() {
        this.type = ConditionType.KILL_ENTITY;
        this.count = 1;
        this.nbtMatchMode = "ignore";
        this.nbtMatchModeParsed = NbtMatchMode.IGNORE;
    }

    public AdvancementCondition(ConditionType type, String targetId, int count) {
        this.type = type;
        this.targetId = targetId;
        this.count = count;
        this.nbtMatchMode = "ignore";
        this.nbtMatchModeParsed = NbtMatchMode.IGNORE;
    }

    /** 深拷贝 */
    public AdvancementCondition deepCopy() {
        AdvancementCondition c = new AdvancementCondition(type, targetId, count);
        c.nbtMatchMode = nbtMatchMode;
        c.nbtMatchModeParsed = nbtMatchModeParsed;
        c.targetNbt = targetNbt;
        return c;
    }

    // Getters
    public ConditionType getType() { return type; }
    public String getTargetId() { return targetId; }
    public int getCount() { return count; }

    /** 返回缓存的 NbtMatchMode 枚举值，首次调用时解析 */
    public NbtMatchMode getNbtMatchMode() {
        if (nbtMatchModeParsed == null) {
            nbtMatchModeParsed = NbtMatchMode.fromSaveName(nbtMatchMode);
        }
        return nbtMatchModeParsed;
    }

    public String getTargetNbt() { return targetNbt; }

    // Setters
    public void setType(ConditionType type) { this.type = type; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public void setCount(int count) { this.count = count; }

    /** 通过字符串设置匹配模式（清除缓存，重新解析并标准化） */
    public void setNbtMatchMode(String mode) {
        this.nbtMatchModeParsed = null;
        this.nbtMatchMode = mode != null ? NbtMatchMode.fromSaveName(mode).getSaveName() : "ignore";
    }

    /** 通过枚举直接设置匹配模式 */
    public void setNbtMatchMode(NbtMatchMode mode) {
        this.nbtMatchModeParsed = mode;
        this.nbtMatchMode = mode != null ? mode.getSaveName() : "ignore";
    }

    public void setTargetNbt(String nbt) { this.targetNbt = nbt; }
}
