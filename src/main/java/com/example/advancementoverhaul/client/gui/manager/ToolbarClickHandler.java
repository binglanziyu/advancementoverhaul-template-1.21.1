package com.example.advancementoverhaul.client.gui.manager;

import com.example.advancementoverhaul.client.gui.AdvancementScreen;
import com.example.advancementoverhaul.client.gui.GuiUtils;
import com.example.advancementoverhaul.client.gui.NarrativeStatsScreen;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.example.advancementoverhaul.client.gui.Theme.*;

/**
 * Handles click detection for toolbar icon buttons.
 */
class ToolbarClickHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/Toolbar");

    static boolean handleButtons(AdvancementScreen screen, CanvasManager canvasMgr, double mx, double my) {
        int s = ICON_S, p = ICON_PAD, gap = ICON_GAP;

        int cx = screen.getScreenWidth() - p - s;
        int cy = p;

        // [1] Close
        if (GuiUtils.inRect(mx, my, cx, cy, s, s)) { screen.onClose(); return true; }
        cx -= s + gap;

        // [2] Stats — 打开叙事统计界面
        if (GuiUtils.inRect(mx, my, cx, cy, s, s)) {
            screen.overlay.current = com.example.advancementoverhaul.client.gui.state.OverlayState.Ov.NONE;
            screen.showDim = false;
            screen.journalScrollOff = 0;
            Minecraft.getInstance().setScreen(new NarrativeStatsScreen());
            return true;
        }
        cx -= s + gap;

        // [3] Journal
        if (GuiUtils.inRect(mx, my, cx, cy, s, s)) {
            screen.overlay.current = screen.overlay.current == com.example.advancementoverhaul.client.gui.state.OverlayState.Ov.JOURNAL
                    ? com.example.advancementoverhaul.client.gui.state.OverlayState.Ov.NONE
                    : com.example.advancementoverhaul.client.gui.state.OverlayState.Ov.JOURNAL;
            screen.showDim = false;
            screen.journalScrollOff = 0;
            return true;
        }
        cx -= s + gap;

        // [4] Tab management
        if (GuiUtils.inRect(mx, my, cx, cy, s, s)) { screen.openTabManage(); return true; }
        cx -= s + gap;

        // [5] Reset view
        if (GuiUtils.inRect(mx, my, cx, cy, s, s)) { canvasMgr.resetView(); return true; }

        // ── Bottom-right buttons ──
        boolean canEdit = Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasPermissions(2);
        int by = screen.getScreenHeight() - BOTTOM_H - p - s;
        cx = screen.getScreenWidth() - p - s;

        // [6] Export
        if (GuiUtils.inRect(mx, my, cx, by, s, s)) { GuiUtils.sendCommand("adv export"); return true; }
        by -= s + gap;

        // [6] Import — 服务端扫描 import/ 文件夹
        if (GuiUtils.inRect(mx, my, cx, by, s, s)) { GuiUtils.sendCommand("adv import"); return true; }
        by -= s + gap;

        // [7] Dimension panel
        if (GuiUtils.inRect(mx, my, cx, by, s, s)) {
            screen.showDim = !screen.showDim;
            if (screen.showDim) { screen.dimPanel.show(); screen.overlay.current = com.example.advancementoverhaul.client.gui.state.OverlayState.Ov.NONE; }
            return true;
        }
        by -= s + gap;

        // [8] Auto-layout (edit mode only)
        if (canEdit && screen.editMode) {
            if (GuiUtils.inRect(mx, my, cx, by, s, s)) { GuiUtils.sendCommand("adv autolayout"); return true; }
            by -= s + gap;
        }

        // [8.5] FTB notification mode toggle (edit mode only, FTB Quests loaded)
        if (canEdit && screen.editMode && com.example.advancementoverhaul.compat.ftb.FtbQuestsBridge.isLoaded()) {
            if (GuiUtils.inRect(mx, my, cx, by, s, s)) {
                AdvancementScreen.ftbNotifMode = (AdvancementScreen.ftbNotifMode + 1) % 3;
                return true;
            }
            by -= s + gap;
        }

        // [9] Edit mode toggle
        if (canEdit && GuiUtils.inRect(mx, my, cx, by, s, s)) {
            screen.editMode = !screen.editMode;
            AdvancementScreen.persistEdit = screen.editMode;
            return true;
        }

        return false;
    }
}
