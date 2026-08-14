package com.dreamer.ao.client.gui.timeline;

import com.dreamer.ao.LangKeys;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/**
 * 怪物装备规则的独立编辑屏。
 * <p>
 * 由 {@link PhaseEditScreen} 弹出，直接操作父屏持有的规则列表，
 * 关闭后回到父屏。因为装备配置层级较深（规则 → 部位 → 条目 → 附魔），
 * 放在主编辑器的窄列里会非常拥挤，故拆分为全屏独立编辑。
 */
public final class PhaseEquipRuleScreen extends Screen {

    private static final int MARGIN = 24;
    private static final int ROW_H = 18;

    private static final int C_PANEL = 0xF01B1B1B;
    private static final int C_FIELD = 0xFF2A2A2A;
    private static final int C_BTN = 0xFF3A3A3A;
    private static final int C_BTNH = 0xFF505050;
    private static final int C_PRIMARY = 0xFF2E5A88;
    private static final int C_TXT = 0xFFE0E0E0;
    private static final int C_LABEL = 0xFF9ACBE0;
    private static final int C_SCROLL = 0xFF5A5A5A;
    private static final int C_ERR = 0xFF8B3030;

    private final PhaseEditScreen parent;
    private final List<PhaseEditScreen.EquipRule> rules;

    private final List<Hotspot> hotspots = new ArrayList<>();
    private final List<EditBox> activeBoxes = new ArrayList<>();

    private int panelX, panelY, panelW, panelH;
    private int bodyTop, bodyBottom;
    private int scroll = 0;
    private int contentH = 0;

    private Object openTarget;
    private String openField;
    private PendingDropdown pendingDd;

    PhaseEquipRuleScreen(PhaseEditScreen parent, List<PhaseEditScreen.EquipRule> rules) {
        super(Component.translatable(LangKeys.PHASE_EQUIP_SCREEN_TITLE));
        this.parent = parent;
        this.rules = rules;
    }

    private void layout() {
        panelW = Math.max(400, Math.min(660, this.width - MARGIN * 2));
        panelH = Math.max(240, Math.min(460, this.height - MARGIN * 2));
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        bodyTop = panelY + 32;
        bodyBottom = panelY + panelH - 28;
    }

    // ────────────────────────── 渲染 ──────────────────────────

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        layout();
        hotspots.clear();
        activeBoxes.clear();

        this.renderTransparentBackground(g);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, C_PANEL);
        g.drawCenteredString(font, this.title, panelX + panelW / 2, panelY + 8, C_LABEL);
        g.drawString(font, Component.translatable(LangKeys.PHASE_EQUIP_COUNT, String.valueOf(rules.size())),
                panelX + 12, panelY + 21, 0xFF909090, false);

        int x = panelX + 12;
        int w = panelW - 24;
        g.enableScissor(x, bodyTop, x + w, bodyBottom);
        int y = renderRules(g, x, bodyTop - scroll, w, mouseX, mouseY);
        g.disableScissor();
        contentH = (y + scroll) - bodyTop;
        drawScrollbar(g, x + w - 3, bodyTop, bodyBottom - bodyTop, contentH, scroll);

        int by = panelY + panelH - 22;
        int bw = Math.min(100, (panelW - 36) / 2);
        drawFooterBtn(g, x, by, bw, LangKeys.PHASE_EQUIP_APPLY, mouseX, mouseY, true, this::onClose);

        for (EditBox b : activeBoxes) {
            b.render(g, mouseX, mouseY, partialTick);
        }
        renderDropdown(g);
    }

    private int renderRules(GuiGraphics g, int x, int y, int w, int mx, int my) {
        y = addBtn(g, x, y, w, Component.translatable(LangKeys.PHASE_ROW_NEW).getString(), mx, my,
                () -> rules.add(parent.newEquipRule()));
        for (PhaseEditScreen.EquipRule rule : new ArrayList<>(rules)) {
            if (visible(y)) {
                drawSelection(g, x, y, Math.max(120, w * 45 / 100), rule.entity, mx, my,
                        rule, "entity", PhaseEditScreen.entityOptions(), PhaseEditScreen::entityDisplay);
                drawBtn(g, x + w - 76, y, 56, mx, my,
                        Component.translatable(rule.expanded ? LangKeys.PHASE_HIDE : LangKeys.PHASE_SHOW).getString(),
                        () -> rule.expanded = !rule.expanded);
                drawRemove(g, x + w - 16, y, mx, my, () -> rules.remove(rule));
            }
            y += ROW_H;
            if (rule.expanded) {
                y = renderSlots(g, rule, x, y, w, mx, my);
            }
        }
        return y + 6;
    }

    private int renderSlots(GuiGraphics g, PhaseEditScreen.EquipRule rule, int x, int y, int w, int mx, int my) {
        for (String slot : PhaseEditScreen.equipSlots()) {
            List<PhaseEditScreen.EquipEntry> entries = rule.slots.get(slot);
            if (entries == null) continue;
            if (visible(y)) {
                g.drawString(font, Component.translatable(LangKeys.PHASE_EDIT_EQUIP_SLOT).getString() + ": " + slot,
                        x + 10, y + 3, C_LABEL, false);
                drawRemove(g, x + w - 16, y, mx, my, entries::clear);
            }
            y += ROW_H;
            for (PhaseEditScreen.EquipEntry en : new ArrayList<>(entries)) {
                if (visible(y)) {
                    // 概率输入框：非法或越界时整格标红
                    boolean bad = !PhaseEditScreen.isChanceValid(en.chanceBox.getValue());
                    if (bad) {
                        g.fill(x + 15, y - 1, x + 16 + 34, y + 15, C_ERR);
                    }
                    en.chanceBox.setPosition(x + 16, y);
                    en.chanceBox.setWidth(34);
                    activeBoxes.add(en.chanceBox);

                    int iw = Math.max(110, w * 40 / 100);
                    drawSelection(g, x + 54, y, iw, en.itemId, mx, my,
                            en, "item", PhaseEditScreen.itemOptions(), PhaseEditScreen::shortDisplay);
                    int ex = x + 54 + iw + 4;
                    int ew = Math.max(50, (x + w - 20) - ex);
                    drawBtn(g, ex, y, ew, mx, my,
                            Component.translatable(LangKeys.PHASE_EDIT_EQUIP_ENCHANT).getString()
                                    + (en.enchants.isEmpty() ? "" : "(" + en.enchants.size() + ")"),
                            () -> en.showEnch = !en.showEnch);
                    drawRemove(g, x + w - 16, y, mx, my, () -> entries.remove(en));
                }
                y += ROW_H;
                if (en.showEnch) {
                    for (String encId : PhaseEditScreen.enchantOptions()) {
                        if (visible(y)) {
                            boolean on = en.enchants.containsKey(encId);
                            drawBtn(g, x + 24, y, w - 44, mx, my,
                                    (on ? "[x] " : "[ ] ") + PhaseEditScreen.enchantDisplay(encId),
                                    () -> {
                                        if (on) en.enchants.remove(encId);
                                        else en.enchants.put(encId, 1);
                                    });
                        }
                        y += ROW_H;
                    }
                }
            }
            y = addBtn(g, x + 16, y, w - 32, Component.translatable(LangKeys.PHASE_ROW_NEW).getString(), mx, my,
                    () -> entries.add(parent.newEquipEntry()));
        }
        return y;
    }

    // ────────────────────────── 控件 ──────────────────────────

    private int addBtn(GuiGraphics g, int x, int y, int w, String text, int mx, int my, Runnable onClick) {
        drawBtn(g, x, y, w, mx, my, text, onClick);
        return y + 16;
    }

    private void drawBtn(GuiGraphics g, int x, int y, int w, int mx, int my, String text, Runnable onClick) {
        if (!visible(y)) return;
        boolean hov = inRect(mx, my, x, y, w, 14);
        g.fill(x, y, x + w, y + 14, hov ? C_BTNH : C_BTN);
        g.drawString(font, Component.literal(trunc(text, w - 6)), x + 3, y + 3, C_TXT, false);
        hotspots.add(new Hotspot(x, y, w, 14, onClick));
    }

    private void drawFooterBtn(GuiGraphics g, int x, int y, int w, String key,
                               int mx, int my, boolean primary, Runnable action) {
        boolean hov = inRect(mx, my, x, y, w, 16);
        g.fill(x, y, x + w, y + 16, hov ? C_BTNH : (primary ? C_PRIMARY : C_BTN));
        Component lbl = Component.translatable(key);
        g.drawString(font, lbl, x + Math.max(2, (w - font.width(lbl)) / 2), y + 4, C_TXT, false);
        hotspots.add(new Hotspot(x, y, w, 16, action));
    }

    private void drawRemove(GuiGraphics g, int x, int y, int mx, int my, Runnable onRemove) {
        boolean hov = inRect(mx, my, x, y, 12, 14);
        g.fill(x, y, x + 12, y + 14, hov ? C_ERR : C_BTN);
        g.drawString(font, Component.literal("x"), x + 4, y + 3, C_TXT, false);
        hotspots.add(new Hotspot(x, y, 12, 14, onRemove));
    }

    private void drawSelection(GuiGraphics g, int x, int y, int w, String current, int mx, int my,
                               Object target, String field, List<String> options,
                               java.util.function.Function<String, String> display) {
        boolean hov = inRect(mx, my, x, y, w, 14);
        boolean empty = current == null || current.isEmpty();
        if (empty) {
            g.fill(x - 1, y - 1, x + w + 1, y + 15, C_ERR);
        }
        g.fill(x, y, x + w, y + 14, hov ? C_BTNH : C_FIELD);
        String label = empty ? Component.translatable(LangKeys.PHASE_NONE).getString() : display.apply(current);
        g.drawString(font, Component.literal(trunc(label, w - 14)), x + 3, y + 3, C_TXT, false);
        g.drawString(font, Component.literal("v"), x + w - 9, y + 3, C_TXT, false);
        hotspots.add(new Hotspot(x, y, w, 14, () -> {
            if (target == openTarget && field.equals(openField)) {
                openTarget = null;
                openField = null;
            } else {
                openTarget = target;
                openField = field;
            }
        }));
        if (target == openTarget && field.equals(openField)) {
            pendingDd = new PendingDropdown(x, y + 14, w, options, target, field, display);
        }
    }

    private void renderDropdown(GuiGraphics g) {
        PendingDropdown dd = pendingDd;
        pendingDd = null;
        if (dd == null) return;
        int n = dd.options().size();
        if (n == 0) return;
        int h = n * 13;
        int y = dd.y();
        if (y + h > panelY + panelH) y = Math.max(panelY + 20, dd.y() - 14 - h);
        int top = Math.max(panelY + 20, y);
        int bottom = Math.min(panelY + panelH - 4, y + h);
        g.enableScissor(dd.x(), top, dd.x() + dd.w(), bottom);
        g.fill(dd.x(), y, dd.x() + dd.w(), y + h, 0xFF1A1A1A);
        for (int i = 0; i < n; i++) {
            String opt = dd.options().get(i);
            int iy = y + i * 13;
            if (iy + 13 < top || iy > bottom) continue;
            g.fill(dd.x(), iy, dd.x() + dd.w(), iy + 13, C_FIELD);
            g.drawString(font, Component.literal(trunc(dd.display().apply(opt), dd.w() - 6)),
                    dd.x() + 3, iy + 3, C_TXT, false);
            hotspots.add(new Hotspot(dd.x(), iy, dd.w(), 13, () -> {
                applyOption(dd.target(), dd.field(), opt);
                openTarget = null;
                openField = null;
            }));
        }
        g.disableScissor();
    }

    private void applyOption(Object target, String field, String opt) {
        if (target instanceof PhaseEditScreen.EquipRule r && "entity".equals(field)) {
            r.entity = opt;
        } else if (target instanceof PhaseEditScreen.EquipEntry e && "item".equals(field)) {
            e.itemId = opt;
        }
    }

    private void drawScrollbar(GuiGraphics g, int x, int y, int viewH, int total, int cur) {
        if (total <= viewH || viewH <= 0) return;
        int barH = Math.max(12, viewH * viewH / total);
        int maxScroll = total - viewH;
        int barY = y + (maxScroll <= 0 ? 0 : (viewH - barH) * cur / maxScroll);
        g.fill(x, y, x + 3, y + viewH, 0xFF202020);
        g.fill(x, barY, x + 3, barY + barH, C_SCROLL);
    }

    private boolean visible(int y) {
        return y + ROW_H >= bodyTop && y <= bodyBottom;
    }

    private String trunc(String s, int maxPx) {
        if (font.width(s) <= maxPx) return s;
        while (s.length() > 1 && font.width(s + "..") > maxPx) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "..";
    }

    private static boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ────────────────────────── 交互 ──────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX, my = (int) mouseY;
        for (EditBox b : activeBoxes) {
            if (b.mouseClicked(mouseX, mouseY, button)) {
                b.setFocused(true);
                for (EditBox other : activeBoxes) {
                    if (other != b) other.setFocused(false);
                }
                return true;
            }
        }
        for (int i = hotspots.size() - 1; i >= 0; i--) {
            Hotspot h = hotspots.get(i);
            if (inRect(mx, my, h.x(), h.y(), h.w(), h.h())) {
                h.action().run();
                return true;
            }
        }
        openTarget = null;
        openField = null;
        for (EditBox b : activeBoxes) {
            b.setFocused(false);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        int viewH = bodyBottom - bodyTop;
        int max = Math.max(0, contentH - viewH);
        scroll = Math.max(0, Math.min(max, scroll - (int) (dy * 14)));
        return true;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        for (EditBox b : activeBoxes) {
            if (b.isFocused() && b.charTyped(c, mods)) return true;
        }
        return super.charTyped(c, mods);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (openTarget != null) {
                openTarget = null;
                openField = null;
                return true;
            }
            onClose();
            return true;
        }
        for (EditBox b : activeBoxes) {
            if (b.isFocused() && b.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            parent.onEquipScreenClosed();
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record PendingDropdown(int x, int y, int w, List<String> options, Object target, String field,
                                   java.util.function.Function<String, String> display) {
    }

    private record Hotspot(int x, int y, int w, int h, Runnable action) {
    }
}
