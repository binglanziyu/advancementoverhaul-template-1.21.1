package com.dreamer.ao.client.gui.state;

/**
 * 覆盖层面板类型枚举。
 * <p>
 * 控制模态弹窗的显示与关闭：
 * <ul>
 *   <li>{@link #NONE} - 无面板</li>
 *   <li>{@link #DETAIL} - 详情面板</li>
 *   <li>{@link #CREATE} - 创建面板</li>
 *   <li>{@link #EDIT} - 编辑面板</li>
 *   <li>{@link #CTX} - 右键菜单</li>
 *   <li>{@link #CONFIRM} - 确认对话框</li>
 *   <li>{@link #TAB_INPUT} - 标签页输入</li>
 *   <li>{@link #TAB_MANAGE} - 标签页管理</li>
 *   <li>{@link #JOURNAL} - 冒险日志</li>
 * </ul>
 */
public enum OverlayType {
    NONE, DETAIL, CREATE, EDIT, CTX, CONFIRM, TAB_INPUT, TAB_MANAGE, JOURNAL
}
