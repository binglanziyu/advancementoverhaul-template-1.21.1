package com.example.advancementoverhaul.client.gui.state;

import java.util.*;

/**
 * 覆盖层面板状态：控制模态弹窗的显示与关闭。
 * <p>
 * 支持的覆盖层类型（Ov 枚举）：
 * <ul>
 *   <li>DETAIL — 成就详情查看</li>
 *   <li>CREATE/EDIT — 创建/编辑成就表单</li>
 *   <li>STATS — 统计面板</li>
 *   <li>CTX — 右键上下文菜单</li>
 *   <li>CONFIRM — 确认对话框</li>
 *   <li>TAB_INPUT/TAB_MANAGE — 标签页管理面板</li>
 * </ul>
 */
public class OverlayState {
    public enum Ov { NONE, DETAIL, CREATE, EDIT, STATS, CTX, CONFIRM, TAB_INPUT, TAB_MANAGE }
    public String manageTabTarget = null;
    public record CtxAct(String label, Runnable action) {}


    public Ov current = Ov.NONE; public String detailId = null;
    public int ctxX, ctxY; public final List<CtxAct> ctxActions = new ArrayList<>();
    public String confirmText = ""; public Runnable confirmAction = null; public int statsScrollOff = 0;
    /** P3: 详情面板打开时间戳（毫秒），用于滑入动画 */
    public long detailOpenTime = 0;
    /** 详情面板滚动偏移 */
    public int detailScrollOff = 0;
    /** 复制按钮提示文本显示时间戳（毫秒） */
    public long detailCopyTime = 0;
    /** ID 行内联复制按钮屏幕坐标（每帧渲染时更新） */
    public int detailInlineCopyX, detailInlineCopyY, detailInlineCopyW, detailInlineCopyH;
    public boolean hasAny() { return current != Ov.NONE; }
    public void close() {
        current = Ov.NONE; confirmAction = null; manageTabTarget = null;
        detailOpenTime = 0; detailScrollOff = 0;
    }
}