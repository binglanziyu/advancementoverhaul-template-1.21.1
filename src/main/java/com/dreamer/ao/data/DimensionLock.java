package com.dreamer.ao.data;

import com.google.gson.annotations.SerializedName;

/**
 * 维度锁定配置。
 * <p>
 * 当 {@code locked = true} 时，玩家进入该维度会被传回原维度，
 * 除非已完成了 {@code unlockAdvancementId} 指定的进度。
 * <p>
 * <b>设计决策：仅支持单条件解锁。</b>
 * 复杂的组合逻辑（如"完成 A 且 B 才能进""完成 A 或 B 就能进"）
 * 应在成就系统中通过父进度（AND）或多条触发器（OR）来表达，
 * 从而保持维度锁定逻辑简单、可预测。
 * <p>
 * 通过 {@code /adv dimension lock <dim>} 设置，
 * 使用 {@code /adv dimension setcondition <dim> <advId>} 设置解锁条件。
 * <p>
 * 注意：JSON 中键名保持 "disabled" 以兼容旧数据文件，
 * Java 字段名改为 "locked" 以消除语义歧义。
 */
public class DimensionLock {

    /** 解锁所需的自定义进度 ID（null 表示无条件锁定，需管理员手动解锁） */
    private String unlockAdvancementId;

    /** true = 维度已锁定，玩家无法进入。JSON 键名保持 "disabled" 兼容旧数据。 */
    @SerializedName("disabled")
    private boolean locked;

    /** 无参构造器（Gson 反序列化需要） */
    public DimensionLock() {
        this.unlockAdvancementId = null;
        this.locked = false;
    }

    /** 带参构造器，用于代码中创建 */
    public DimensionLock(String unlockAdvancementId, boolean locked) {
        this.unlockAdvancementId = unlockAdvancementId;
        this.locked = locked;
    }

    // Getters & Setters
    public String getUnlockAdvancementId() { return unlockAdvancementId; }
    public void setUnlockAdvancementId(String id) { this.unlockAdvancementId = id; }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    /**
     * @deprecated 请使用 {@link #isLocked()}。保留此方法用于平滑迁移。
     */
    @Deprecated
    public boolean isDisabled() { return locked; }

    /**
     * @deprecated 请使用 {@link #setLocked(boolean)}。保留此方法用于平滑迁移。
     */
    @Deprecated
    public void setDisabled(boolean disabled) { this.locked = disabled; }
}
