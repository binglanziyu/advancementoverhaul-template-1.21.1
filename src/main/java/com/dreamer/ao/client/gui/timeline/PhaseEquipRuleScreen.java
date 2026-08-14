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
    private static final int C_SUB   = 0xFF8A8A8A;
    private static final int C_SCROLL = 0xFF5A5A5A;
    private static final int C_ERR = 0xFF8B3030;

    private final PhaseEditScreen parent;
    private final List<PhaseEditScreen.EquipRule> rules;

    private final List<Hotspot> hotspots = new ArrayList<>();
    private final List<EditBox> activeBoxes = new ArrayList<>();
    private final List<int[]> chanceBoxRects = new ArrayList<>();

    private int panelX, panelY, panelW, panelH;
    private int bodyTop, bodyBottom;
    private int scroll = 0;
    private int contentH = 0;

    private Object openTarget;
    private String openField;
    private PendingDropdown pendingDd;

    /** 当前展开“目标怪物”多选面板的规则（null 表示未展开） */
    private PhaseEditScreen.EquipRule entityTargetOpen;

    PhaseEquipRuleScreen(PhaseEditScreen parent, List<PhaseEditScreen.EquipRule> rules) {
        super(Component.translatable(LangKeys.PHASE_EQUIP_SCREEN_TITLE));
        this.parent = parent;
        // 深拷贝：子屏编辑副本，取消时不写回父屏
        this.rules = new ArrayList<>();
        for (PhaseEditScreen.EquipRule src : rules) {
            PhaseEditScreen.EquipRule r = new PhaseEditScreen.EquipRule(src.entities);
            for (var es : src.slots.entrySet()) {
                List<PhaseEditScreen.EquipEntry> dst = r.slots.get(es.getKey());
                for (PhaseEditScreen.EquipEntry e : es.getValue()) {
                    PhaseEditScreen.EquipEntry ne =
                            new PhaseEditScreen.EquipEntry(e.chanceBox.getValue(), e.itemId);
                    ne.enchants.putAll(e.enchants);
                    ne.showEnch = e.showEnch;
                    dst.add(ne);
                }
                r.slotExpanded.put(es.getKey(), src.slotExpanded.getOrDefault(es.getKey(), false));
            }
            this.rules.add(r);
        }
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
        chanceBoxRects.clear();

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
        int bw = Math.min(100, (panelW - 36) / 3);
        drawFooterBtn(g, x, by, bw, LangKeys.PHASE_EQUIP_APPLY, mouseX, mouseY, true, this::onApply);
        drawFooterBtn(g, x + bw + 6, by, bw, LangKeys.PHASE_EDIT_CANCEL, mouseX, mouseY, false, this::onClose);

        for (EditBox b : activeBoxes) {
            b.render(g, mouseX, mouseY, partialTick);
        }
        renderDropdown(g);
        drawChanceTooltip(g, mouseX, mouseY);
    }

    private void drawChanceTooltip(GuiGraphics g, int mx, int my) {
        for (int[] r : chanceBoxRects) {
            if (inRect(mx, my, r[0], r[1], r[2], r[3])) {
                String text = Component.translatable(LangKeys.PHASE_EQUIP_CHANCE_TIP).getString();
                int maxW = Math.min(260, this.width - 16);
                List<String> lines = wrap(text, maxW - 12);
                int tw = Math.min(maxW, lines.stream().mapToInt(font::width).max().orElse(0) + 12);
                int th = lines.size() * 12 + 10;
                int tx = Math.min(mx + 12, this.width - tw - 4);
                int ty = Math.min(my + 12, this.height - th - 4);
                g.fill(tx, ty, tx + tw, ty + th, 0xF01A1A1A);
                g.fill(tx, ty, tx + tw, ty + 1, C_LABEL);
                for (int i = 0; i < lines.size(); i++) {
                    g.drawString(font, Component.literal(lines.get(i)), tx + 6, ty + 6 + i * 12, C_TXT, false);
                }
                return;
            }
        }
    }

    private List<String> wrap(String s, int maxPx) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (char c : s.toCharArray()) {
            cur.append(c);
            if (font.width(cur.toString()) > maxPx) {
                if (cur.length() > 1) {
                    out.add(cur.substring(0, cur.length() - 1));
                    cur = new StringBuilder(String.valueOf(c));
                } else {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        if (out.isEmpty()) out.add(s);
        return out;
    }

    private int renderRules(GuiGraphics g, int x, int y, int w, int mx, int my) {
        y = addBtn(g, x, y, w, Component.translatable(LangKeys.PHASE_ROW_NEW).getString(), mx, my,
                () -> rules.add(parent.newEquipRule()));
        for (PhaseEditScreen.EquipRule rule : new ArrayList<>(rules)) {
            if (visible(y)) {
                // 目标怪物（多选）：点击展开下拉，可勾选多种怪物类型
                String targetLabel = rule.entities.isEmpty()
                        ? Component.translatable(LangKeys.PHASE_EQUIP_TARGET_ALL).getString()
                        : formatEntityList(rule.entities);
                int targetW = Math.max(140, w * 50 / 100);
                int targetX = x;
                int targetY = y;
                boolean targetHover = mx >= targetX && mx <= targetX + targetW
                        && my >= targetY && my <= targetY + ROW_H;
                if (targetHover) hotspots.add(new Hotspot(targetX, targetY, targetW, ROW_H, () -> {
                    entityTargetOpen = (entityTargetOpen == rule) ? null : rule;
                }));
                int tbg = (entityTargetOpen == rule) ? 0x663344 : (targetHover ? 0x444444 : 0x2A2A2A);
                g.fill(targetX, targetY, targetX + targetW, targetY + ROW_H, tbg);
                // 左侧可点击指示框（▼/▶），明确本行可点击展开多选面板
                int caretX = targetX + 5, caretY = targetY + (ROW_H - 8) / 2;
                g.fill(caretX, caretY, caretX + 8, caretY + 8, 0xFF1F1F1F);
                g.drawString(font, Component.literal(entityTargetOpen == rule ? "▼" : "▶"),
                        caretX + 1, caretY, 0xFF9CDCFE, false);
                g.drawString(font,
                        Component.literal(Component.translatable(LangKeys.PHASE_EQUIP_TARGET).getString()
                                + "：" + targetLabel),
                        caretX + 12, targetY + (ROW_H - 8) / 2, 0xE0E0E0, false);
                drawBtn(g, x + w - 76, y, 56, mx, my,
                        Component.translatable(rule.expanded ? LangKeys.PHASE_HIDE : LangKeys.PHASE_SHOW).getString(),
                        () -> rule.expanded = !rule.expanded);
                drawRemove(g, x + w - 16, y, mx, my, () -> rules.remove(rule));
            }
            y += ROW_H;
            // “目标怪物”多选面板：与目标怪物行强绑定，独立于规则是否整体展开。
            // 之前错误地嵌在 rule.expanded 分支内，导致仅点击目标怪物行
            // （未展开规则）时面板永不渲染，表现即为“点击后无可见勾选位置”。
            if (entityTargetOpen == rule) {
                g.disableScissor();
                y = renderEntityMultiSelect(g, x, y, w, mx, my, rule);
                g.enableScissor(x, bodyTop, x + w, bodyBottom);
            }
            if (rule.expanded) {
                y = renderSlots(g, rule, x, y, w, mx, my);
            }
        }
        return y + 6;
    }

    private int renderSlots(GuiGraphics g, PhaseEditScreen.EquipRule rule, int x, int y, int w, int mx, int my) {
        List<String> slots = PhaseEditScreen.equipSlots();
        int maxY = y;
        for (String slot : slots) {
            List<PhaseEditScreen.EquipEntry> entries =
                    rule.slots.getOrDefault(slot, new ArrayList<>());
            boolean selected = rule.slotExpanded.getOrDefault(slot, false);
            int cy = y;
            if (visible(cy)) {
                // 部位行：中文名 + 条目数 + 勾选选择按钮 + 删除该部位(×)
                g.drawString(font, Component.literal(PhaseEditScreen.slotDisplay(slot)), x, cy + 3, C_LABEL, false);
                String cnt = "(" + entries.size() + ")";
                g.drawString(font, Component.literal(cnt), x + 70, cy + 3, C_SUB, false);
                drawBtn(g, x + w - 170, cy, 80, mx, my,
                        (selected ? "[x] " : "[ ] ")
                                + Component.translatable(LangKeys.PHASE_SELECT_SLOT).getString(),
                        () -> rule.slotExpanded.put(slot, !selected));
                drawRemove(g, x + w - 16, cy, mx, my, () -> entries.clear());
            }
            y += ROW_H;
            if (selected) {
                if (entries.isEmpty()) {
                    if (visible(y)) {
                        g.drawString(font, Component.literal(Component.translatable(LangKeys.PHASE_NONE).getString()),
                                x + 8, y + 3, C_SUB, false);
                    }
                    y += ROW_H;
                }
                for (PhaseEditScreen.EquipEntry en : new ArrayList<>(entries)) {
                    if (visible(y)) {
                        renderEquipEntry(g, en, x + 8, y, w - 16, mx, my);
                    }
                    y += ROW_H;
                    if (en.showEnch) {
                        for (String encId : PhaseEditScreen.enchantOptions()) {
                            if (visible(y)) {
                                boolean on = en.enchants.containsKey(encId);
                                drawBtn(g, x + 16, y, w - 32, mx, my,
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
                y = addBtn(g, x + 8, y, w - 16, Component.translatable(LangKeys.PHASE_ROW_NEW).getString(), mx, my,
                        () -> entries.add(parent.newEquipEntry()));
            }
            maxY = Math.max(maxY, y);
        }
        return maxY;
    }

    private void renderEquipEntry(GuiGraphics g, PhaseEditScreen.EquipEntry en, int cx, int cy, int rowW, int mx, int my) {
        // 概率输入框（默认 1.0）：非法或越界时整格标红
        boolean bad = !PhaseEditScreen.isChanceValid(en.chanceBox.getValue());
        int boxX = cx + 2, boxW = 36;
        if (bad) {
            g.fill(boxX - 1, cy - 1, boxX + boxW + 1, cy + 15, C_ERR);
        }
        en.chanceBox.setPosition(boxX, cy);
        en.chanceBox.setWidth(boxW);
        activeBoxes.add(en.chanceBox);
        chanceBoxRects.add(new int[]{boxX, cy, boxW, 14});

        // 空装备（未选物品）：不显示附魔
        boolean emptyItem = en.itemId == null || en.itemId.isEmpty();
        if (emptyItem) en.showEnch = false;

        int iy = cy;
        // 整行布局：概率框 | 物品下拉 | 附魔按钮 | 删除
        int remX = boxX + boxW + 4;
        int enchW = 70;
        int delW = 14;
        int iw = Math.max(60, rowW - (remX - cx) - (emptyItem ? 0 : enchW) - delW - 12);
        // 物品下拉：含“空装备”选项，显示名走汉化（mod 装备读取语言文件）
        List<String> opts = new ArrayList<>(PhaseEditScreen.itemOptions());
        opts.add(0, "");
        drawSelection(g, remX, iy, iw, en.itemId, mx, my,
                en, "item", opts, PhaseEditScreen::itemDisplay);
        int ex = remX + iw + 4;
        if (!emptyItem) {
            int ew = Math.max(20, (cx + rowW - delW - 4) - ex);
            drawBtn(g, ex, iy, ew, mx, my,
                    Component.translatable(LangKeys.PHASE_EDIT_EQUIP_ENCHANT).getString()
                            + (en.enchants.isEmpty() ? "" : "(" + en.enchants.size() + ")"),
                    () -> en.showEnch = !en.showEnch);
        }
        drawRemove(g, cx + rowW - delW, iy, mx, my, () -> {
            PhaseEditScreen.EquipRule r = currentRuleOf(en);
            if (r != null) {
                for (List<PhaseEditScreen.EquipEntry> es : r.slots.values()) {
                    if (es.remove(en)) break;
                }
            }
        });
    }

    private PhaseEditScreen.EquipRule currentRuleOf(PhaseEditScreen.EquipEntry target) {
        for (PhaseEditScreen.EquipRule r : rules) {
            for (List<PhaseEditScreen.EquipEntry> es : r.slots.values()) {
                if (es.contains(target)) return r;
            }
        }
        return null;
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

    private static String formatEntityList(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String id : list) {
            if (sb.length() > 0) sb.append(",");
            sb.append(PhaseEditScreen.entityDisplay(id));
        }
        return sb.toString();
    }

    /** 渲染“目标怪物”多选面板：列出全部可穿装备的怪物类型，勾选即加入/移出当前规则 */
    private int renderEntityMultiSelect(GuiGraphics g, int x, int y0, int w, int mx, int my,
                                        PhaseEditScreen.EquipRule rule) {
        List<String> opts = new ArrayList<>(PhaseEditScreen.entityOptions());
        int rowH = 13;
        int panelW = Math.max(160, w);
        int x0 = x;
        int y = y0;
        // 空列表兜底：避免无任何可穿装备的敌对生物时报“无可见勾选位置”且面板空白
        if (opts.isEmpty()) {
            int tipH = rowH + 4;
            g.fill(x0, y, x0 + panelW, y + tipH, 0xFF1A1A1A);
            g.drawString(font, Component.literal(Component.translatable(LangKeys.PHASE_EQUIP_TARGET_EMPTY).getString()),
                    x0 + 4, y + 3, C_SUB, false);
            return y + tipH + 2;
        }
        int totalH = opts.size() * rowH + 4;
        g.fill(x0, y, x0 + panelW, y + totalH, 0xFF1A1A1A);
        for (int i = 0; i < opts.size(); i++) {
            String id = opts.get(i);
            int iy = y + 2 + i * rowH;
            boolean on = rule.entities.contains(id);
            boolean hov = inRect(mx, my, x0, iy, panelW, rowH);
            if (hov) g.fill(x0, iy, x0 + panelW, iy + rowH, 0x333333);
            // 可点击勾选框：未选为空心方框 □，选中为实心填色 + √，避免与“X 取消”歧义
            int boxX = x0 + 4, boxY = iy + 2, boxS = 9;
            g.fill(boxX, boxY, boxX + boxS, boxY + boxS, on ? 0xFF3C8D40 : 0xFF2A2A2A);
            g.fill(boxX + 1, boxY + 1, boxX + boxS - 1, boxY + boxS - 1, on ? 0xFF4CAF50 : 0xFF3A3A3A);
            g.drawString(font, Component.literal(on ? "√" : ""),
                    boxX + 1, boxY, 0xFFFFFFFF, false);
            g.drawString(font, Component.literal(PhaseEditScreen.entityDisplay(id)),
                    boxX + boxS + 4, iy + 2, C_TXT, false);
            final String fid = id;
            int fy = iy;
            // 注意：多选面板为浮层，点击命中不应受 body 裁剪（visible）限制，
            // 否则面板项超出可视区时无法点选。
            if (inRect(mx, my, x0, fy, panelW, rowH)) {
                hotspots.add(new Hotspot(x0, fy, panelW, rowH, () -> {
                    if (rule.entities.contains(fid)) rule.entities.remove(fid);
                    else rule.entities.add(fid);
                }));
            }
        }
        if (visible(y + totalH)) {
            g.drawString(font,
                    Component.literal(Component.translatable(LangKeys.PHASE_EQUIP_TARGET_TIP).getString()),
                    x0 + 4, y + 2 + opts.size() * rowH, C_SUB, false);
        }
        return y + totalH + 2;
    }

    private void applyOption(Object target, String field, String opt) {
        if (target instanceof PhaseEditScreen.EquipEntry e && "item".equals(field)) {
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
        entityTargetOpen = null;
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
            if (entityTargetOpen != null) {
                entityTargetOpen = null;
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

    /** 应用：将子屏深拷贝的改动写回父屏后关闭 */
    private void onApply() {
        parent.commitEquipRules(rules);
        onClose();
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
