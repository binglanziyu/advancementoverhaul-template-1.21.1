package com.example.advancementoverhaul.client.gui.panel;

import com.example.advancementoverhaul.LangKeys;
import com.example.advancementoverhaul.client.gui.AdvancementScreen;
import com.example.advancementoverhaul.client.gui.GuiUtils;
import com.example.advancementoverhaul.client.gui.TranslatedStrings;
import com.example.advancementoverhaul.client.gui.widget.ScrollBar;
import com.example.advancementoverhaul.data.ClientDataStore;
import com.example.advancementoverhaul.data.DimensionLock;
import com.example.advancementoverhaul.data.DisplayNameResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.Registries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.example.advancementoverhaul.client.gui.Theme.*;

public class DimensionPanel {

    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/DimensionPanel");

    private final AdvancementScreen parent;
    private boolean visible = false;
    private int px, py, pw, ph, scrollAreaY, scrollAreaH;
    private static final int ENTRY_H = 60, SBW = 6;
    private final ScrollBar scrollBar = new ScrollBar(SBW, 0xFF222238, 0xFF6666BB);
    private final List<DimEntry> entries = new ArrayList<>();

    // 缓存布局计算，避免每帧重复计算
    private int lastScreenW = -1, lastScreenH = -1;

    private static class DimEntry {
        final String id, displayName; boolean disabled; String requiredAdvancement;
        DimEntry(String id, String displayName, boolean disabled, String required) {
            this.id = id; this.displayName = displayName; this.disabled = disabled; this.requiredAdvancement = required;
        }
    }

    public DimensionPanel(AdvancementScreen parent) { this.parent = parent; }

    public void show() { visible = true; scrollBar.setScroll(0); load(); lastScreenW = -1; }
    public void hide() { visible = false; }

    private void load() {
        entries.clear();
        Map<String, DimensionLock> locks = ClientDataStore.getInstance().getDimensionLocks();
        Set<String> allDims = new LinkedHashSet<>();
        Minecraft mc = Minecraft.getInstance();
        // null 安全检查，避免连接断开瞬间 NPE
        if (mc.getConnection() != null) {
            try {
                var access = mc.getConnection().registryAccess();
                if (access != null) {
                    access.registryOrThrow(Registries.DIMENSION).keySet().forEach(rl -> allDims.add(rl.toString()));
                }
            } catch (Exception e) {
                LOGGER.debug("Dimension registry not available during panel load: {}", e.getMessage());
            }
        }
        allDims.add("minecraft:overworld");
        allDims.add("minecraft:the_nether");
        allDims.add("minecraft:the_end");
        if (locks != null) allDims.addAll(locks.keySet());
        for (String dim : allDims) {
            DimensionLock lock = locks != null ? locks.get(dim) : null;
            entries.add(new DimEntry(dim, DisplayNameResolver.friendlyDimension(dim), lock != null && lock.isDisabled(),
                    lock != null ? lock.getUnlockAdvancementId() : null));
        }
    }

    // 分离布局计算和渲染，仅在屏幕尺寸变化时重新计算
    private void computeBounds(int sw, int sh) {
        if (sw == lastScreenW && sh == lastScreenH) return;
        lastScreenW = sw;
        lastScreenH = sh;
        pw = 260; ph = Math.min(sh - 80, 420);
        px = sw - pw - 30; py = (sh - ph) / 2;
        scrollAreaY = py + 32; scrollAreaH = ph - 42;
    }

    public void render(GuiGraphics g, int mx, int my) {
        if (!visible) return;
        Font font = Minecraft.getInstance().font;
        int sw = parent.getScreenWidth(), sh = parent.getScreenHeight();
        computeBounds(sw, sh);
        int contentH = entries.size() * ENTRY_H;
        scrollBar.update(contentH, scrollAreaH);

        g.fill(0, 0, sw, sh, 0x60000000);
        g.fill(px, py, px + pw, py + ph, PANEL);
        g.renderOutline(px, py, pw, ph, DIVIDER);
        g.fill(px, py, px + pw, py + 3, ACCENT);
        g.drawString(font, TranslatedStrings.get(LangKeys.DIM_MGMT), px + 12, py + 9, ACCENT, false);
        boolean ch = GuiUtils.closeHit(mx, my, px, py, pw);
        g.drawString(font, "\u2715", px + pw - 18, py + 9, ch ? TEXT_BR : TEXT_DIM, false);

        g.enableScissor(px + 1, scrollAreaY, px + pw - 1, scrollAreaY + scrollAreaH);
        int y = scrollAreaY - scrollBar.getScroll();
        for (DimEntry e : entries) {
            if (y + ENTRY_H > scrollAreaY && y < scrollAreaY + scrollAreaH)
                renderEntry(g, font, e, px + 6, y, pw - 12, ENTRY_H - 4, mx, my);
            y += ENTRY_H;
        }
        g.disableScissor();
        scrollBar.render(g, px + pw - SBW - 2, scrollAreaY);
    }

    private void renderEntry(GuiGraphics g, Font font, DimEntry e, int x, int y, int w, int h, int mx, int my) {
        boolean hov = GuiUtils.inRect(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, hov ? BTN_HOV : BTN);
        g.renderOutline(x, y, w, h, hov ? 0xFF6A6A90 : DIVIDER);
        g.drawString(font, e.disabled ? "\u2716" : "\u2714", x + 6, y + 8, TEXT_BR, false);
        g.drawString(font, e.displayName, x + 26, y + 8, TEXT_BR, false);
        String st = e.disabled
                ? TranslatedStrings.get(LangKeys.LOCKED)
                : TranslatedStrings.get(LangKeys.UNLOCKED);
        g.drawString(font, st, x + w - font.width(st) - 6, y + 8, e.disabled ? PINK : ACCENT, false);
        g.drawString(font, e.id, x + 26, y + 22, TEXT_DIM, false);
        int condY = y + 38;
        String condLbl = TranslatedStrings.get(LangKeys.COND_LABEL);
        String condText = e.requiredAdvancement != null
                ? condLbl + " " + GuiUtils.truncate(font, e.requiredAdvancement, 80)
                : condLbl + " " + TranslatedStrings.get(LangKeys.NONE);
        g.drawString(font, condText, x + 26, condY, e.requiredAdvancement != null ? ORANGE : TEXT_DIM, false);
        int btnX = x + w - 46;
        boolean setHov = GuiUtils.inRect(mx, my, btnX, condY - 2, 18, 14);
        g.fill(btnX, condY - 2, btnX + 18, condY + 12, setHov ? BTN_HOV : BTN);
        g.drawString(font, TranslatedStrings.get(LangKeys.SET_CONDITION), btnX + 3, condY, setHov ? TEXT_BR : TEXT, false);
        if (e.requiredAdvancement != null) {
            int clrX = x + w - 22;
            g.drawString(font, "\u2715", clrX, condY,
                    GuiUtils.inRect(mx, my, clrX, condY - 2, 16, 14) ? PINK : TEXT_DIM, false);
        }
    }

    public boolean mouseClicked(double mx, double my, int btn) {
        if (!visible) return false;
        int sw = parent.getScreenWidth(), sh = parent.getScreenHeight();
        computeBounds(sw, sh);
        if (GuiUtils.closeHit(mx, my, px, py, pw)) { hide(); return true; }
        if (GuiUtils.inRect(mx, my, px, py, pw, ph)) {
            if (scrollBar.needsScrollbar()) {
                if (scrollBar.handleClick(mx, my, px + pw - SBW - 2, scrollAreaY)) return true;
            }
            if (my >= scrollAreaY && my < scrollAreaY + scrollAreaH) {
                int idx = (int) ((my - scrollAreaY + scrollBar.getScroll()) / ENTRY_H);
                if (idx >= 0 && idx < entries.size()) {
                    DimEntry e = entries.get(idx);
                    int condScreenY = scrollAreaY - scrollBar.getScroll() + idx * ENTRY_H + 38;
                    int btnX = px + pw - 46 - 6;
                    if (GuiUtils.inRect(mx, my, btnX, condScreenY - 2, 18, 14)) { openConditionSelector(e); return true; }
                    if (e.requiredAdvancement != null) {
                        int clrX = px + pw - 22 - 6;
                        if (GuiUtils.inRect(mx, my, clrX, condScreenY - 2, 16, 14)) { setCondition(e, null); return true; }
                    }
                    if (my < condScreenY - 6) { toggleDisable(e); return true; }
                }
            }
            return true;
        }
        return false;
    }

    private void toggleDisable(DimEntry e) {
        GuiUtils.sendCommand("adv dimension " + (e.disabled ? "unlock " : "lock ") + e.id);
        e.disabled = !e.disabled;
    }

    private void openConditionSelector(DimEntry e) {
        List<ListSelector.Entry> selEntries = new ArrayList<>();
        selEntries.add(new ListSelector.Entry("", TranslatedStrings.get(LangKeys.NONE)));
        for (var a : ClientDataStore.getInstance().getAdvancements().values())
            selEntries.add(new ListSelector.Entry(a.getId(), a.getName()));
        selEntries.sort(Comparator.comparing(ListSelector.Entry::display, String.CASE_INSENSITIVE_ORDER));
        parent.showSelector(selEntries, entry -> setCondition(e, entry.id().isEmpty() ? null : entry.id()));
    }

    private void setCondition(DimEntry e, String advId) {
        if (advId != null) GuiUtils.sendCommand("adv dimension setcondition " + e.id + " " + advId);
        else GuiUtils.sendCommand("adv dimension removecondition " + e.id);
        e.requiredAdvancement = advId;
    }

    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (!visible) return false;
        if (GuiUtils.inRect(mx, my, px, py, pw, ph)) return scrollBar.handleScroll(sy);
        return false;
    }

    public void mouseReleased(double mx, double my, int btn) {
        scrollBar.handleRelease();
    }

    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        return scrollBar.handleDrag(my);
    }
}