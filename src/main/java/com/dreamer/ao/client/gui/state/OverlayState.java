package com.dreamer.ao.client.gui.state;

import java.util.*;

/** 覆盖层面板状态：控制模态弹窗的显示与关闭。 */
public class OverlayState {
    public String manageTabTarget = null;
    public record CtxAct(String label, Runnable action) {}

    public OverlayType current = OverlayType.NONE; public String detailId = null;
    public int ctxX, ctxY; public final List<CtxAct> ctxActions = new ArrayList<>();
    public String confirmText = ""; public Runnable confirmAction = null;
    /** P3: 详情面板打开时间戳（毫秒），用于滑入动画 */
    public long detailOpenTime = 0;
    /** 详情面板滚动偏移 */
    public int detailScrollOff = 0;
    /** 复制按钮提示文本显示时间戳（毫秒） */
    public long detailCopyTime = 0;
    /** ID 行内联复制按钮屏幕坐标（每帧渲染时更新） */
    public int detailInlineCopyX, detailInlineCopyY, detailInlineCopyW, detailInlineCopyH;
    public boolean hasAny() { return current != OverlayType.NONE; }
    public void close() {
        current = OverlayType.NONE; confirmAction = null; manageTabTarget = null;
        detailOpenTime = 0; detailScrollOff = 0;
    }
}