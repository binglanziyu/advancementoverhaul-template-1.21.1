package com.example.advancementoverhaul.client.gui.state;

import java.util.*;

public class SelectionState {
    public String selectedId = null;
    public final Set<String> multiSel = new HashSet<>();
    public void select(String id) { selectedId = id; multiSel.clear(); if (id != null) multiSel.add(id); }
    public void toggle(String id) { if (!multiSel.add(id)) multiSel.remove(id); }
    public void clear() { selectedId = null; multiSel.clear(); }
}