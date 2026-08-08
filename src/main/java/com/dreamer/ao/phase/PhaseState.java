package com.dreamer.ao.phase;

import java.util.ArrayList;
import java.util.List;

/**
 * 阶段运行态。
 * <p>
 * 保存某个阶段定义当前的运行数据：是否已解锁、解锁时间、历史记录、临时状态（OP 施加、带过期时间）。
 */
public final class PhaseState {

    private final String phaseId;
    private boolean unlocked;
    private long unlockedAt;
    private final List<String> history = new ArrayList<>();

    /** 临时状态：OP 施加，带过期时间戳（ms）；0 表示不过期 */
    private TemporaryOverride tempOverride;

    public PhaseState(String phaseId) {
        this.phaseId = phaseId;
    }

    public String getPhaseId() {
        return phaseId;
    }

    public boolean isUnlocked() {
        return unlocked;
    }

    public void setUnlocked(boolean unlocked) {
        this.unlocked = unlocked;
        if (unlocked && unlockedAt == 0) {
            unlockedAt = System.currentTimeMillis();
        }
    }

    public long getUnlockedAt() {
        return unlockedAt;
    }

    public List<String> getHistory() {
        return history;
    }

    public void addHistory(String entry) {
        history.add(0, entry);
        if (history.size() > 20) {
            history.remove(history.size() - 1);
        }
    }

    public TemporaryOverride getTempOverride() {
        return tempOverride;
    }

    public void setTempOverride(TemporaryOverride tempOverride) {
        this.tempOverride = tempOverride;
    }

    public boolean hasActiveTempOverride(long now) {
        return tempOverride != null && (tempOverride.expireAt() == 0 || tempOverride.expireAt() > now);
    }

    /** 临时覆盖（OP 施加） */
    public record TemporaryOverride(PhaseEffectSet effects, long expireAt) {
    }
}
