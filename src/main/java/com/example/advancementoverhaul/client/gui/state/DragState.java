package com.example.advancementoverhaul.client.gui.state;

public class DragState {
    public String dragCardId = null;
    public String dragImageId = null;
    public boolean dragMoved = false;
    public double dragStartMX, dragStartMY, lastDragMX, lastDragMY;
    public boolean boxSel = false; public double bsx, bsy, bex, bey;
    public void reset() { dragCardId = null; dragMoved = false; boxSel = false; }
}