package com.dreamer.ao.client.gui.manager;

import com.dreamer.ao.client.gui.AdvancementScreen;
import com.dreamer.ao.client.gui.GuiUtils;
import com.dreamer.ao.client.gui.render.TabRenderer;
import com.dreamer.ao.client.gui.NarrativeStatsScreen;
import com.dreamer.ao.client.gui.state.OverlayType;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles click detection for toolbar icon buttons.
 */
class ToolbarClickHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolbarClickHandler.class);

    static boolean handleButtons(AdvancementScreen screen, CanvasManager canvasMgr, double mx, double my) {
        for (TabRenderer.TBtn b : screen.tabRenderer.buildToolbar()) {
            if (!GuiUtils.inRect(mx, my, b.x, b.y, b.s, b.s)) continue;
            switch (b.clickId) {
                case TabRenderer.C_CLOSE -> screen.onClose();
                case TabRenderer.C_STATS -> {
                    screen.overlay.current = OverlayType.NONE;
                    screen.showDim = false;
                    screen.journalScrollOff = 0;
                    Minecraft.getInstance().setScreen(new NarrativeStatsScreen());
                }
                case TabRenderer.C_JOURNAL -> {
                    screen.overlay.current = screen.overlay.current == OverlayType.JOURNAL
                            ? OverlayType.NONE : OverlayType.JOURNAL;
                    screen.showDim = false;
                    screen.journalScrollOff = 0;
                }
                case TabRenderer.C_TABS -> screen.openTabManage();
                case TabRenderer.C_RESET -> canvasMgr.resetView();
                case TabRenderer.C_EXPORT -> GuiUtils.sendCommand("adv export");
                case TabRenderer.C_HELP -> screen.showHelp = !screen.showHelp;
                case TabRenderer.C_IMPORT -> GuiUtils.sendCommand("adv import");
                case TabRenderer.C_DIM -> {
                    screen.showDim = !screen.showDim;
                    if (screen.showDim) { screen.dimPanel.show(); screen.overlay.current = OverlayType.NONE; }
                }
                case TabRenderer.C_AUTOLAYOUT -> GuiUtils.sendCommand("adv autolayout");
                case TabRenderer.C_FTB -> AdvancementScreen.ftbNotifMode = (AdvancementScreen.ftbNotifMode + 1) % 3;
                case TabRenderer.C_EDIT -> {
                    screen.editMode = !screen.editMode;
                    AdvancementScreen.persistEdit = screen.editMode;
                }
                default -> { /* 未知按钮，忽略 */ }
            }
            return true;
        }
        return false;
    }
}
