package com.example.advancementoverhaul.data;

public class DimensionLock {
    private String unlockAdvancementId;
    private boolean disabled;

    public DimensionLock() {
        this.unlockAdvancementId = null;
        this.disabled = false;
    }

    @SuppressWarnings("unused") // Available for programmatic creation
    public DimensionLock(String unlockAdvancementId, boolean disabled) {
        this.unlockAdvancementId = unlockAdvancementId;
        this.disabled = disabled;
    }

    public String getUnlockAdvancementId() { return unlockAdvancementId; }
    public void setUnlockAdvancementId(String id) { this.unlockAdvancementId = id; }
    public boolean isDisabled() { return disabled; }
    public void setDisabled(boolean disabled) { this.disabled = disabled; }
}