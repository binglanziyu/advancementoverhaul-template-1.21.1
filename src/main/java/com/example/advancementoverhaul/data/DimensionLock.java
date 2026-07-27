package com.example.advancementoverhaul.data;

/**
 * 维度锁定配置。
 * <p>
 * 当 {@code disabled = true} 时，玩家进入该维度会被传回原维度，
 * 除非已完成了 {@code unlockAdvancementId} 指定的进度。
 * <p>
 * 通过 {@code /adv dimension lock <dim>} 设置，
 * 使用 {@code /adv dimension setcondition <dim> <advId>} 设置解锁条件。
 */
public class DimensionLock {

    /** 解锁所需的自定义进度 ID（null 表示无条件锁定，需管理员手动解锁） */
    private String unlockAdvancementId;

    /** true = 维度已锁定，玩家无法进入 */
    private boolean disabled;

    /** 无参构造器（Gson 反序列化需要） */
    public DimensionLock() {
        this.unlockAdvancementId = null;
        this.disabled = false;
    }

    /** 带参构造器，用于代码中创建 */
    public DimensionLock(String unlockAdvancementId, boolean disabled) {
        this.unlockAdvancementId = unlockAdvancementId;
        this.disabled = disabled;
    }

    // Getters & Setters
    public String getUnlockAdvancementId() { return unlockAdvancementId; }
    public void setUnlockAdvancementId(String id) { this.unlockAdvancementId = id; }
    public boolean isDisabled() { return disabled; }
    public void setDisabled(boolean disabled) { this.disabled = disabled; }
}
