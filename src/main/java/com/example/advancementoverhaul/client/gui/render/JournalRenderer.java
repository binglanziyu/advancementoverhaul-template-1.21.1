package com.example.advancementoverhaul.client.gui.render;

import com.example.advancementoverhaul.LangKeys;
import com.example.advancementoverhaul.client.gui.AdvancementScreen;
import com.example.advancementoverhaul.client.gui.GuiUtils;
import com.example.advancementoverhaul.client.gui.TranslatedStrings;
import com.example.advancementoverhaul.data.ClientDataStore;
import com.example.advancementoverhaul.data.model.CustomAdvancement;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

import static com.example.advancementoverhaul.client.gui.Theme.*;

/**
 * 冒险日志渲染器。
 * <p>
 * 按完成时间倒序展示已完成成就，配以序号、名称和风味文本，带滚动条支持。
 * <p>
 * 从 {@link OverlayRenderer} 拆分而来。
 */
final class JournalRenderer {

    private final AdvancementScreen screen;

    JournalRenderer(AdvancementScreen screen) {
        this.screen = screen;
    }

    /**
     * 渲染冒险日志面板。
     */
    void render(GuiGraphics g, int mx, int my, Font font, int sw, int sh) {
        int jw = Math.min(sw - 60, 520);
        int jh = Math.min(sh - 40, 420);
        int jx = (sw - jw) / 2;
        int jy = Math.max(20, (sh - jh) / 2);

        GuiUtils.drawPanelBg(g, font, jx, jy, jw, jh,
                TranslatedStrings.get(LangKeys.JOURNAL_TITLE), sw, sh);

        ClientDataStore cs = ClientDataStore.getInstance();

        // 收集已完成的成就
        List<java.util.AbstractMap.SimpleEntry<String, String>> completed = new ArrayList<>();
        for (var entry : cs.getAdvancements().entrySet()) {
            if (cs.isCompleted(entry.getKey())) {
                completed.add(new java.util.AbstractMap.SimpleEntry<>(
                        entry.getKey(), entry.getValue().getName()));
            }
        }
        for (var entry : cs.getVanillaAdvancements()) {
            if (cs.isCompleted(entry.id())) {
                completed.add(new java.util.AbstractMap.SimpleEntry<>(
                        entry.id(), entry.name()));
            }
        }

        if (completed.isEmpty()) {
            String empty = TranslatedStrings.get(LangKeys.JOURNAL_EMPTY);
            g.drawString(font, empty, jx + (jw - font.width(empty)) / 2,
                    jy + jh / 2 - 10, TEXT_DIM, false);
            return;
        }

        int contentTop = jy + 28;
        int contentBottom = jy + jh - 14;
        int contentH = contentBottom - contentTop;
        int rowH = 28;
        int totalH = completed.size() * rowH;
        int maxScroll = Math.max(0, totalH - contentH);
        if (screen.journalScrollOff > maxScroll) screen.journalScrollOff = maxScroll;
        if (screen.journalScrollOff < 0) screen.journalScrollOff = 0;

        boolean needsScroll = maxScroll > 0;
        if (needsScroll) {
            int sbX = jx + jw - 6;
            g.fill(sbX, contentTop, sbX + 4, contentBottom, 0xFF222238);
            double vRatio = maxScroll > 0 ? (double) screen.journalScrollOff / maxScroll : 0;
            double vSize = Math.max(0.08, (double) contentH / totalH);
            int thumbH = Math.max(16, (int) (vSize * contentH));
            int thumbY = contentTop + (int) (vRatio * (contentH - thumbH));
            g.fill(sbX, thumbY, sbX + 4, thumbY + thumbH, 0xFF6666BB);
        }

        g.enableScissor(jx + 1, contentTop, jx + jw - 1, contentBottom);
        int ty = contentTop - screen.journalScrollOff;
        int idx = 0;
        for (var entry : completed) {
            if (ty + rowH > contentTop && ty < contentBottom) {
                String advId = entry.getKey();
                String advName = entry.getValue();
                CustomAdvancement adv = cs.getAdvancement(advId);

                String num = String.format("%2d.", idx + 1);
                g.drawString(font, num, jx + 14, ty + 7, TEXT_DIM, false);

                String displayName = GuiUtils.truncate(font, advName, jw - 80);
                g.drawString(font, displayName, jx + 44, ty + 7, TEXT_BR, false);

                if (adv != null && adv.getLore() != null && !adv.getLore().isEmpty()) {
                    String loreTrunc = GuiUtils.truncate(font, adv.getLore(), jw - 90);
                    g.drawString(font, "\u2728 " + loreTrunc, jx + 54, ty + 19, 0xFFFFD700, false);
                }

                g.fill(jx + 14, ty + rowH - 1, jx + jw - 20, ty + rowH, 0x10FFFFFF);
            }
            ty += rowH;
            idx++;
        }
        g.disableScissor();
    }
}
