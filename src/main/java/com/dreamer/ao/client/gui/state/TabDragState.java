package com.dreamer.ao.client.gui.state;

/**
 * Encapsulates all state related to tab reordering (drag) and
 * the overflow dropdown menu interaction.*
 * Previously scattered as loose public fields in AdvancementScreen.
 */
public class TabDragState {

    // ── Tab reordering drag ──

    /** Index of the tab being dragged, or -1 if not dragging. */
    public int dragIdx = -1;

    /** Whether the drag has moved past the threshold (vs a plain click). */
    public boolean dragMoved = false;

    /** Mouse X position when the tab drag started. */
    public double dragStartX = 0;

    // ── Overflow dropdown ──

    /** Whether the overflow dropdown is currently open. */
    public boolean overDDOpen = false;

    /** X position of the overflow dropdown. */
    public int overflowDDX = -1;

    /** Scroll offset within the overflow dropdown list. */
    public int overflowScroll = 0;

    /** Reset all tab drag and dropdown state. Called on init and screen close. */
    public void reset() {
        dragIdx = -1;
        dragMoved = false;
        dragStartX = 0;
        overDDOpen = false;
        overflowDDX = -1;
        overflowScroll = 0;
    }
}