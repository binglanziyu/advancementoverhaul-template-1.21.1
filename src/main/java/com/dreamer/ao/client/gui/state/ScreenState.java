package com.dreamer.ao.client.gui.state;

/**
 * 统一容器，收拢 AdvancementScreen 中散落的 UI 状态字段并提供 dirty tracking。
 *
 * <h2>包含的状态</h2>
 * <ul>
 *   <li><b>面板可见性</b> — {@link #showDim}/{@link #showSel}/{@link #showHelp}</li>
 *   <li><b>模式标识</b> — {@link #editMode}/{@link #curTab}</li>
 *   <li><b>脏标记位图</b> — 供渲染层/管理器按需刷新，避免全量重绘</li>
 * </ul>
 *
 * <h2>Dirty Tracking 语义</h2>
 * <pre>
 * DIRTY_FILTERED     — 过滤列表缓存失效（tab 切换 / 数据变更）
 * DIRTY_VANILLA_POS  — 原版进度坐标需重新计算
 * DIRTY_TAB          — 标签栏布局（排序/增删）需刷新
 * </pre>
 * 调用 {@link #isDirty(int)} 检查对应位；{@link #clearDirty(int)} 清除单个位；
 * {@link #resetDirty()} 一次性清除所有脏标记。
 */
public class ScreenState {

    // ═══════════════ 面板可见性 ═══════════════

    /** 维度选择面板是否可见 */
    public boolean showDim;

    /** 列表选择器是否可见 */
    public boolean showSel;

    /** 帮助面板是否可见 */
    public boolean showHelp;

    // ═══════════════ 模式标识 ═══════════════

    /** 编辑模式开关（OP 权限 + Tab 键切换） */
    public boolean editMode;

    /** 当前选中的标签页名称（{@code null}=全部） */
    public String curTab;

    // ═══════════════ Dirty Tracking ═══════════════

    /** bitmask：当前等待处理的变化位集合 */
    private int dirtyMask;

    /** 过滤列表缓存已脏 */
    public static final int DIRTY_FILTERED    = 1;

    /** 原版进度坐标需重算 */
    public static final int DIRTY_VANILLA_POS = 1 << 1;

    /** 标签栏布局需刷新 */
    public static final int DIRTY_TAB         = 1 << 2;

    /**
     * 恢复所有字段到默认值（进入屏幕时调用）。
     * <p>
     * 保留 {@code curTab} 和 {@code editMode} 不重置，以支持跨屏幕打开持久化。
     */
    public void reset() {
        showDim = false;
        showSel = false;
        showHelp = false;
        dirtyMask = 0;
    }

    // ── 脏标记读写 ──

    /** 检查指定脏位是否已设置。 */
    public boolean isDirty(int flag) {
        return (dirtyMask & flag) != 0;
    }

    /** 标记一个脏位。 */
    public void markDirty(int flag) {
        dirtyMask |= flag;
    }

    /** 清除指定脏位。 */
    public void clearDirty(int flag) {
        dirtyMask &= ~flag;
    }

    /** 清除所有脏位。 */
    public void resetDirty() {
        dirtyMask = 0;
    }

    /** 返回当前脏位掩码（用于批量检查）。 */
    public int getDirtyMask() {
        return dirtyMask;
    }

    /** {@code true} 表示至少有一个脏位被设置。 */
    public boolean hasDirty() {
        return dirtyMask != 0;
    }
}
