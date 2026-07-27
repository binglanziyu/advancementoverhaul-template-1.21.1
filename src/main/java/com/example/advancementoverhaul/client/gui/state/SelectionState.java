package com.example.advancementoverhaul.client.gui.state;

import java.util.*;

/**
 * 卡片选中状态：单选与多选管理。
 * <p>
 * 支持单击选中（select）、Ctrl+点击多选切换（toggle）、以及清空选择（clear）。
 * 多选用于批量操作（批量删除/隐藏/显示/移动）。
 */
public class SelectionState {
    public String selectedId = null;
    public final Set<String> multiSel = new HashSet<>();
    public void select(String id) { selectedId = id; multiSel.clear(); if (id != null) multiSel.add(id); }
    public void toggle(String id) { if (!multiSel.add(id)) multiSel.remove(id); }
    public void clear() { selectedId = null; multiSel.clear(); }
}