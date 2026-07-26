package com.example.advancementoverhaul.client.gui.state;

import java.util.*;

public class OverlayState {
    public enum Ov { NONE, DETAIL, CREATE, EDIT, STATS, CTX, CONFIRM, TAB_INPUT, TAB_MANAGE }
    public String manageTabTarget = null;
    public record CtxAct(String label, Runnable action) {}

    public Ov current = Ov.NONE; public String detailId = null;
    public int ctxX, ctxY; public final List<CtxAct> ctxActions = new ArrayList<>();
    public String confirmText = ""; public Runnable confirmAction = null; public int statsScrollOff = 0;
    public boolean hasAny() { return current != Ov.NONE; }
    public void close() { current = Ov.NONE; confirmAction = null; manageTabTarget = null; }
}