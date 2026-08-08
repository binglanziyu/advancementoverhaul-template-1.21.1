package com.dreamer.ao.client.gui.timeline;

import com.dreamer.ao.LangKeys;
import com.dreamer.ao.client.gui.GuiUtils;
import com.dreamer.ao.data.ClientDataStore;
import com.dreamer.ao.data.DisplayNameResolver;
import com.dreamer.ao.network.NetworkHandler;
import com.dreamer.ao.network.payload.PhaseDefEditPayload;
import com.dreamer.ao.network.payload.PhaseSyncPayload;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 故事阶段管理面板 —— 三列（全局/维度/玩家）状态可视化界面。
 * <p>
 * 浏览模式：只读查看三个作用域的阶段与效果。
 * 编辑模式（仅 OP）：列标题可点击展开阶段下拉，下拉项左侧箭头可强制切换；
 * 效果列表首行为“新建”，每行末尾可删除。
 * <p>
 * 底部固定区域展示当前实际生效效果（全局 + 维度 + 玩家 三层叠加，可滚动）。
 */
public class PhasePanelScreen extends Screen {

    // ── 布局常量 ──
    private static final int PANEL_W      = 520;
    private static final int PANEL_H      = 380;
    private static final int MIN_PANEL_W  = 360;
    private static final int MIN_PANEL_H  = 260;
    private static final int HEADER_H     = 26;
    private static final int COL_GAP      = 5;
    private static final int PADDING_X    = 8;
    private static final int COL_Y_START  = 34;
    private static final int ROW_H        = 12;
    private static final int TITLE_H      = 15;
    private static final int BOTTOM_H     = 78;

    // ── 色彩 ──
    private static final int BG_DARK        = 0xE62A2A36;
    private static final int BG_HEADER      = 0x50333344;
    private static final int BG_COL_TITLE   = 0x40FFFFFF;
    private static final int BG_COL_TITLE_A = 0x60F59E0B;
    private static final int BG_DROPDOWN    = 0xF01E1E2A;
    private static final int DIVIDER        = 0x40FFFFFF;
    private static final int TEXT_PRIMARY   = 0xFFFFFFFF;
    private static final int TEXT_SECONDARY = 0xFFAAAAAA;
    private static final int TEXT_DIM       = 0xFF777777;
    private static final int TEXT_ACTIVE    = 0xFF6AB4BC;
    private static final int TEXT_POS       = 0xFF4ADE80;
    private static final int TEXT_NEG       = 0xFFF87171;
    private static final int SCROLL_BAR     = 0xFF5A5A5A;
    private static final int SCROLL_BG      = 0x40000000;

    private static final int SCOPE_GLOBAL = 0;
    private static final int SCOPE_DIM    = 1;
    private static final int SCOPE_PLAYER = 2;

    // ── 状态 ──
    private final Screen parent;
    private final boolean isOp;
    private boolean editMode = false;

    private int panelLeft, panelTop, effectiveW, effectiveH, colW;
    private int colTop, colBottom;

    private String selectedDimKey = "minecraft:overworld";
    private final List<String> availableDims = new ArrayList<>();
    private UUID selectedPlayer;
    private final List<UUID> availablePlayers = new ArrayList<>();

    /** 展开阶段下拉的列，-1 表示未展开 */
    private int openTitleDropdown = -1;
    private int titleDropdownScroll = 0;

    private final int[] effectScroll = new int[3];
    private final int[] effectContentH = new int[3];

    private int bottomScroll = 0;
    private final List<EffectRow> currentEffects = new ArrayList<>();

    private final List<Hotspot> hotspots = new ArrayList<>();

    private record Hotspot(int x, int y, int w, int h, Runnable action) {
        boolean hit(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private record EffectRow(String label, double value, boolean percent, boolean plain, String tag) {
        static EffectRow num(String label, double v, boolean pct) {
            return new EffectRow(label, v, pct, false, null);
        }

        static EffectRow text(String label) {
            return new EffectRow(label, 0, false, true, null);
        }

        /** 带删除标签的数值/文本行（tag 形如 attributes:max_health） */
        static EffectRow tagged(String label, String tag) {
            return new EffectRow(label, 0, false, true, tag);
        }

        EffectRow withTag(String tag) {
            return new EffectRow(this.label, this.value, this.percent, this.plain, tag);
        }
    }

    private static final class Column {
        String title;
        String phaseId;
        String stateName;
        List<EffectRow> effects = List.of();
    }

    private final Column colWorld = new Column();
    private final Column colDim = new Column();
    private final Column colPlayer = new Column();

    public PhasePanelScreen(Screen parent) {
        super(Component.translatable(LangKeys.PHASE_PANEL_TITLE));
        this.parent = parent;
        var player = Minecraft.getInstance().player;
        this.isOp = player != null && player.hasPermissions(2);
        if (player != null) this.selectedPlayer = player.getUUID();
    }

    @Override
    protected void init() {
        computeLayout();
        refreshDimensionList();
        refreshPlayerList();
        refreshAllColumns();
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new com.dreamer.ao.network.payload.PhaseRequestPayload());
    }

    private void computeLayout() {
        effectiveW = Math.max(MIN_PANEL_W, Math.min(PANEL_W, this.width - 20));
        effectiveH = Math.max(MIN_PANEL_H, Math.min(PANEL_H, this.height - 20));
        colW = (effectiveW - 2 * PADDING_X - 2 * COL_GAP) / 3;
        panelLeft = (this.width - effectiveW) / 2;
        panelTop = (this.height - effectiveH) / 2;
        colTop = panelTop + COL_Y_START;
        colBottom = panelTop + effectiveH - BOTTOM_H - 6;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ────────────────────────── 渲染 ──────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        computeLayout();
        hotspots.clear();

        GuiUtils.fillRoundedCard(g, panelLeft, panelTop, effectiveW, effectiveH, BG_DARK);
        renderHeader(g, mouseX, mouseY);

        for (int ci = 0; ci < 3; ci++) {
            renderColumn(g, ci, mouseX, mouseY);
        }

        renderBottomCurrentEffects(g);

        // 下拉浮层最后绘制
        if (openTitleDropdown >= 0) {
            renderTitleDropdown(g, openTitleDropdown, mouseX, mouseY);
        }
    }

    private void renderHeader(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(panelLeft, panelTop, panelLeft + effectiveW, panelTop + HEADER_H, BG_HEADER);

        Component title = Component.translatable(LangKeys.PHASE_PANEL_TITLE);
        g.drawString(font, title, panelLeft + (effectiveW - font.width(title)) / 2,
                panelTop + 6, TEXT_PRIMARY, false);

        int right = panelLeft + effectiveW;
        Component close = Component.literal("\u2715");
        int closeX = right - font.width(close) - 10;
        g.drawString(font, close, closeX, panelTop + 6, TEXT_SECONDARY, false);
        hotspots.add(new Hotspot(closeX - 3, panelTop + 4, font.width(close) + 6, 14, this::onClose));

        // 右侧“编辑”（仅 OP 可见可点），左侧“浏览”
        int cursor = closeX - 8;
        if (isOp) {
            Component edit = Component.translatable(LangKeys.PHASE_MODE_EDIT);
            int w = font.width(edit) + 10;
            int x = cursor - w;
            boolean hov = inRect(mouseX, mouseY, x, panelTop + 4, w, 15);
            g.fill(x, panelTop + 5, x + w, panelTop + 20, editMode ? BG_COL_TITLE_A : (hov ? 0x40FFFFFF : 0x25FFFFFF));
            g.drawString(font, edit, x + 5, panelTop + 9, editMode ? TEXT_PRIMARY : TEXT_SECONDARY, false);
            hotspots.add(new Hotspot(x, panelTop + 5, w, 15, () -> {
                editMode = true;
                openTitleDropdown = -1;
            }));
            cursor = x - 4;
        }
        Component browse = Component.translatable(LangKeys.PHASE_MODE_BROWSE);
        int bw = font.width(browse) + 10;
        int bx = cursor - bw;
        boolean bhov = inRect(mouseX, mouseY, bx, panelTop + 4, bw, 15);
        g.fill(bx, panelTop + 5, bx + bw, panelTop + 20, !editMode ? BG_COL_TITLE_A : (bhov ? 0x40FFFFFF : 0x25FFFFFF));
        g.drawString(font, browse, bx + 5, panelTop + 9, !editMode ? TEXT_PRIMARY : TEXT_SECONDARY, false);
        hotspots.add(new Hotspot(bx, panelTop + 5, bw, 15, () -> {
            editMode = false;
            openTitleDropdown = -1;
        }));
    }

    private void renderColumn(GuiGraphics g, int ci, int mouseX, int mouseY) {
        int x = colX(ci);
        Column col = getColumn(ci);

        // ── 列标题（模块内居中，可点击切换） ──
        boolean active = openTitleDropdown == ci;
        boolean hov = inRect(mouseX, mouseY, x, colTop, colW, TITLE_H);
        g.fill(x, colTop, x + colW, colTop + TITLE_H,
                active ? BG_COL_TITLE_A : (hov && editMode ? 0x55FFFFFF : BG_COL_TITLE));
        Component t = Component.translatable(col.title);
        String suffix = editMode ? " \u25BE" : "";
        int tw = font.width(t) + font.width(suffix);
        g.drawString(font, Component.literal(t.getString() + suffix),
                x + (colW - tw) / 2, colTop + 4, TEXT_PRIMARY, false);
        if (editMode) {
            final int idx = ci;
            // 维度/玩家列标题点击 = 切换维度/玩家（不再展开阶段下拉）
            if (ci == SCOPE_DIM) {
                hotspots.add(new Hotspot(x, colTop, colW, TITLE_H, () -> cycleDimension()));
            } else if (ci == SCOPE_PLAYER) {
                hotspots.add(new Hotspot(x, colTop, colW, TITLE_H, () -> cyclePlayer()));
            }
            // 全局列标题不再承担切换，阶段切换移至"当前阶段"行内
        }

        int cy = colTop + TITLE_H + 3;

        // ── 维度 / 玩家 选择器 ──
        if (ci == SCOPE_DIM) {
            cy = renderPicker(g, x, cy, mouseX, mouseY,
                    DisplayNameResolver.friendlyDimension(selectedDimKey), () -> cycleDimension());
        } else if (ci == SCOPE_PLAYER) {
            cy = renderPicker(g, x, cy, mouseX, mouseY,
                    getPlayerDisplayName(selectedPlayer), () -> cyclePlayer());
        }

        // ── 当前阶段名（点击展开阶段切换下拉） ──
        boolean phaseActive = openTitleDropdown == ci;
        boolean phov = editMode && inRect(mouseX, mouseY, x, cy, colW, 16);
        g.fill(x, cy, x + colW, cy + 16, phaseActive ? 0x55FFFFFF : (phov ? 0x40FFFFFF : 0x30FFFFFF));
        String arrow = editMode ? " \u25BE" : "";
        g.drawString(font, Component.literal(trunc(col.stateName, colW - 8) + arrow),
                x + 4, cy + 4, TEXT_ACTIVE, false);
        if (editMode) {
            final int idx = ci;
            hotspots.add(new Hotspot(x, cy, colW, 16, () -> {
                openTitleDropdown = (openTitleDropdown == idx) ? -1 : idx;
                titleDropdownScroll = 0;
            }));
        }
        cy += 19;

        g.fill(x, cy, x + colW, cy + 1, DIVIDER);
        cy += 4;

        // ── 效果列表（滚动 + 自动换行） ──
        int listTop = cy;
        int listH = Math.max(20, colBottom - listTop);
        g.enableScissor(x, listTop, x + colW, listTop + listH);
        int y = listTop - effectScroll[ci];

        if (editMode) {
            boolean nhov = inRect(mouseX, mouseY, x, y, colW - 5, 12);
            g.fill(x, y, x + colW - 5, y + 12, nhov ? 0x60FFFFFF : 0x30FFFFFF);
            g.drawString(font, Component.translatable(LangKeys.PHASE_ROW_NEW), x + 4, y + 2,
                    TEXT_PRIMARY, false);
            final int ciNew = ci;
            hotspots.add(new Hotspot(x, y, colW - 5, 12, () -> openPhaseEditor(ciNew, null)));
            y += 14;
        }

        int textW = colW - (editMode ? 22 : 10);
        for (EffectRow eff : col.effects) {
            List<String> lines = wrap(eff.label(), textW);
            for (int li = 0; li < lines.size(); li++) {
                if (y + ROW_H >= listTop && y <= listTop + listH) {
                    g.drawString(font, Component.literal(lines.get(li)), x + 4, y, TEXT_SECONDARY, false);
                    if (li == lines.size() - 1 && !eff.plain()) {
                        String v = eff.percent()
                                ? String.format("%+.0f%%", eff.value() * 100)
                                : String.format("%+.1f", eff.value());
                        int clr = eff.value() > 0 ? TEXT_POS : (eff.value() < 0 ? TEXT_NEG : TEXT_DIM);
                        g.drawString(font, Component.literal(v),
                                x + colW - font.width(v) - (editMode ? 18 : 6), y, clr, false);
                    }
                }
                y += ROW_H;
            }
            // 编辑模式：末尾 × 删除
            if (editMode) {
                int bx = x + colW - 14;
                int by = y - ROW_H;
                if (by + 10 >= listTop && by <= listTop + listH) {
                    boolean xh = inRect(mouseX, mouseY, bx, by, 10, 10);
                    g.fill(bx, by, bx + 10, by + 10, xh ? 0xFF8B3030 : 0x40FFFFFF);
                    g.drawString(font, Component.literal("x"), bx + 3, by + 1, TEXT_PRIMARY, false);
                    final int ciX = ci;
                    final Column colX = col;
                    final EffectRow effX = eff;
                    hotspots.add(new Hotspot(bx, by, 10, 10, () -> {
                        if (effX.tag() != null) requestRemoveEffect(ciX, colX, effX.tag());
                        else openPhaseEditor(ciX, colX.phaseId);
                    }));
                }
            }
        }
        g.disableScissor();
        effectContentH[ci] = (y + effectScroll[ci]) - listTop;
        drawScrollbar(g, x + colW - 3, listTop, listH, effectContentH[ci], effectScroll[ci]);
    }

    private int renderPicker(GuiGraphics g, int x, int y, int mouseX, int mouseY,
                             String label, Runnable onClick) {
        boolean hov = inRect(mouseX, mouseY, x, y, colW, 13);
        g.fill(x, y, x + colW, y + 13, hov ? 0x50FFFFFF : 0x28FFFFFF);
        g.drawString(font, Component.literal(trunc(label, colW - 16)), x + 4, y + 2,
                TEXT_PRIMARY, false);
        g.drawString(font, Component.literal("\u21BB"), x + colW - 10, y + 2, TEXT_DIM, false);
        hotspots.add(new Hotspot(x, y, colW, 13, onClick));
        return y + 16;
    }

    /** 列标题下拉：列出该作用域全部阶段；左侧箭头点击 = 强制切换 */
    private void renderTitleDropdown(GuiGraphics g, int ci, int mouseX, int mouseY) {
        String scope = scopeOf(ci);
        List<JsonObject> defs = defsForScope(scope);
        if (defs.isEmpty()) return;

        int x = colX(ci);
        int y = colTop + TITLE_H;
        int rowH = 14;
        int maxVis = Math.min(8, Math.max(1, (colBottom - y) / rowH));
        int vis = Math.min(maxVis, defs.size());
        int h = vis * rowH + 4;

        GuiUtils.fillRoundedCard(g, x, y, colW, h, BG_DROPDOWN);

        for (int i = 0; i < vis; i++) {
            int idx = i + titleDropdownScroll;
            if (idx >= defs.size()) break;
            JsonObject o = defs.get(idx);
            String id = o.get("id").getAsString();
            String name = o.has("name") ? o.get("name").getAsString() : id;
            int ry = y + 2 + i * rowH;

            // 左侧箭头：强制切换
            boolean ah = inRect(mouseX, mouseY, x + 2, ry, 12, rowH);
            g.fill(x + 2, ry, x + 14, ry + rowH, ah ? 0xFF2E7D32 : 0x30FFFFFF);
            g.drawString(font, Component.literal("\u25B6"), x + 4, ry + 3,
                    ah ? TEXT_PRIMARY : TEXT_DIM, false);
            hotspots.add(new Hotspot(x + 2, ry, 12, rowH,
                    () -> confirmForceSwitch(ci, id, name)));

            // 名称：切换预览
            boolean nh = inRect(mouseX, mouseY, x + 15, ry, colW - 17, rowH);
            if (nh) g.fill(x + 15, ry, x + colW - 2, ry + rowH, 0x30FFFFFF);
            g.drawString(font, Component.literal(trunc(name, colW - 22)), x + 18, ry + 3,
                    nh ? TEXT_PRIMARY : TEXT_SECONDARY, false);
            hotspots.add(new Hotspot(x + 15, ry, colW - 17, rowH, () -> {
                previewPhase(ci, id);
                openTitleDropdown = -1;
            }));
        }
    }

    /** 底部：当前实际生效效果（全局 + 维度 + 玩家 合并数值，可滚动） */
    private void renderBottomCurrentEffects(GuiGraphics g) {
        int top = panelTop + effectiveH - BOTTOM_H;
        int left = panelLeft + PADDING_X;
        int w = effectiveW - 2 * PADDING_X;

        g.fill(left, top - 4, left + w, top - 3, DIVIDER);
        Component header = Component.translatable(LangKeys.PHASE_CURRENT_EFFECTS);
        g.drawString(font, header, left, top, TEXT_DIM, false);

        int listTop = top + 12;
        int listH = panelTop + effectiveH - 6 - listTop;
        if (listH <= 0) return;

        g.enableScissor(left, listTop, left + w, listTop + listH);
        int y = listTop - bottomScroll;
        if (currentEffects.isEmpty()) {
            g.drawString(font, Component.translatable(LangKeys.PHASE_CURRENT_EMPTY),
                    left + 4, y, TEXT_DIM, false);
            y += ROW_H;
        } else {
            // 双列排布，减少滚动量
            int colHalf = w / 2;
            for (int i = 0; i < currentEffects.size(); i++) {
                EffectRow eff = currentEffects.get(i);
                int cx = left + (i % 2) * colHalf;
                int cyy = y + (i / 2) * ROW_H;
                if (cyy + ROW_H >= listTop && cyy <= listTop + listH) {
                    g.drawString(font, Component.literal(trunc(eff.label(), colHalf - 46)),
                            cx + 4, cyy, TEXT_SECONDARY, false);
                    if (!eff.plain()) {
                        String v = eff.percent()
                                ? String.format("%+.0f%%", eff.value() * 100)
                                : String.format("%+.1f", eff.value());
                        int clr = eff.value() > 0 ? TEXT_POS : (eff.value() < 0 ? TEXT_NEG : TEXT_DIM);
                        g.drawString(font, Component.literal(v),
                                cx + colHalf - font.width(v) - 8, cyy, clr, false);
                    }
                }
            }
            y += ((currentEffects.size() + 1) / 2) * ROW_H;
        }
        g.disableScissor();

        int contentH = (y + bottomScroll) - listTop;
        drawScrollbar(g, left + w - 3, listTop, listH, contentH, bottomScroll);
    }

    private void drawScrollbar(GuiGraphics g, int x, int y, int viewH, int contentH, int scroll) {
        if (contentH <= viewH || viewH <= 0) return;
        int barH = Math.max(10, viewH * viewH / contentH);
        int maxScroll = contentH - viewH;
        int barY = y + (maxScroll <= 0 ? 0 : (viewH - barH) * scroll / maxScroll);
        g.fill(x, y, x + 3, y + viewH, SCROLL_BG);
        g.fill(x, barY, x + 3, barY + barH, SCROLL_BAR);
    }

    // ────────────────────────── 交互 ──────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int mx = (int) mouseX, my = (int) mouseY;

        for (int i = hotspots.size() - 1; i >= 0; i--) {
            Hotspot h = hotspots.get(i);
            if (h.hit(mx, my)) {
                h.action().run();
                return true;
            }
        }
        if (openTitleDropdown >= 0) {
            openTitleDropdown = -1;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int mx = (int) mouseX, my = (int) mouseY;
        int delta = (int) Math.signum(scrollY) * 12;

        if (openTitleDropdown >= 0) {
            List<JsonObject> defs = defsForScope(scopeOf(openTitleDropdown));
            int max = Math.max(0, defs.size() - 8);
            titleDropdownScroll = Math.clamp(titleDropdownScroll - (int) Math.signum(scrollY), 0, max);
            return true;
        }
        // 底部当前效果区
        if (my >= panelTop + effectiveH - BOTTOM_H) {
            int listTop = panelTop + effectiveH - BOTTOM_H + 12;
            int listH = panelTop + effectiveH - 6 - listTop;
            int contentH = Math.max(1, ((currentEffects.size() + 1) / 2)) * ROW_H;
            bottomScroll = Math.clamp(bottomScroll - delta, 0, Math.max(0, contentH - listH));
            return true;
        }
        // 三列效果区
        for (int ci = 0; ci < 3; ci++) {
            int x = colX(ci);
            if (mx >= x && mx < x + colW && my >= colTop && my < colBottom) {
                int listH = Math.max(20, colBottom - colTop);
                effectScroll[ci] = Math.clamp(effectScroll[ci] - delta, 0,
                        Math.max(0, effectContentH[ci] - listH));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ────────────────────────── 动作 ──────────────────────────

    private void confirmForceSwitch(int ci, String phaseId, String phaseName) {
        openTitleDropdown = -1;
        if (minecraft == null) return;
        String scopeLabel = Component.translatable(getColumn(ci).title).getString();
        minecraft.setScreen(new ConfirmScreen(ok -> {
            if (ok) {
                runClientCommand("/adv phase force " + scopeOf(ci) + " " + phaseId);
            }
            if (minecraft != null) minecraft.setScreen(this);
        },
                Component.translatable(LangKeys.PHASE_SWITCH_TO),
                Component.translatable(LangKeys.PHASE_SWITCH_CONFIRM, scopeLabel, phaseName)));
    }

    /** 仅本地预览某阶段的效果显示（不改变实际状态） */
    private void previewPhase(int ci, String phaseId) {
        Column col = getColumn(ci);
        JsonObject def = findDef(phaseId);
        if (def == null) return;
        col.phaseId = phaseId;
        col.stateName = def.has("name") ? def.get("name").getAsString() : phaseId;
        col.effects = buildEffectRows(def);
        effectScroll[ci] = 0;
        recomputeCurrentEffects();
    }

    private void openPhaseEditor(int ci, String editingId) {
        openTitleDropdown = -1;
        if (minecraft != null) minecraft.setScreen(new PhaseEditScreen(this, editingId, scopeOf(ci)));
    }

    /** × 直接移除该定义中的某条效果并存盘 */
    private void requestRemoveEffect(int ci, Column col, String tag) {
        if (col.phaseId == null || col.phaseId.isBlank()) return;
        JsonObject payload = new JsonObject();
        String[] parts = tag.split(":", 2);
        payload.addProperty("cat", parts[0]);
        if (tag.startsWith("attributes:") || tag.startsWith("mob_mults:")) {
            payload.addProperty("key", parts[1]);
        } else if (tag.startsWith("mob_effects:")) {
            payload.addProperty("effectId", parts[1]);
        } else if (tag.startsWith("equipment:")) {
            payload.addProperty("index", Integer.parseInt(parts[1]));
        }
        NetworkHandler.sendPhaseDefEdit(new PhaseDefEditPayload("remove_effect", col.phaseId, payload.toString()));
        // 立即重新拉取定义，刷新面板
        refreshAllColumns();
    }

    private void cycleDimension() {
        if (availableDims.isEmpty()) return;
        int i = availableDims.indexOf(selectedDimKey);
        selectedDimKey = availableDims.get((i + 1) % availableDims.size());
        refreshDimColumn();
        recomputeCurrentEffects();
    }

    private void cyclePlayer() {
        if (availablePlayers.isEmpty()) return;
        int i = availablePlayers.indexOf(selectedPlayer);
        selectedPlayer = availablePlayers.get((i + 1) % availablePlayers.size());
        refreshPlayerColumn();
        recomputeCurrentEffects();
    }

    private void runClientCommand(String cmd) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.connection.sendCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
        }
    }

    // ────────────────────────── 数据 ──────────────────────────

    private void refreshAllColumns() {
        refreshWorldColumn();
        refreshDimColumn();
        refreshPlayerColumn();
        recomputeCurrentEffects();
    }

    private void refreshDimensionList() {
        availableDims.clear();
        Set<String> dims = new LinkedHashSet<>();
        dims.add("minecraft:overworld");
        dims.add("minecraft:the_nether");
        dims.add("minecraft:the_end");
        var mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            try {
                var access = mc.getConnection().registryAccess();
                if (access != null) {
                    access.registryOrThrow(Registries.DIMENSION_TYPE)
                            .keySet().forEach(rl -> dims.add(rl.toString()));
                }
            } catch (Exception ignored) {
            }
        }
        availableDims.addAll(dims);
        if (!availableDims.contains(selectedDimKey) && !availableDims.isEmpty()) {
            selectedDimKey = availableDims.get(0);
        }
    }

    private void refreshPlayerList() {
        availablePlayers.clear();
        var mc = Minecraft.getInstance();
        if (mc.player != null) availablePlayers.add(mc.player.getUUID());
        if (mc.level != null) {
            for (var p : mc.level.players()) {
                if (!availablePlayers.contains(p.getUUID())) availablePlayers.add(p.getUUID());
            }
        }
    }

    private void refreshWorldColumn() {
        colWorld.title = LangKeys.PHASE_WORLD;
        String id = ClientDataStore.getInstance().getPhaseWorldPhase();
        applyDef(colWorld, id);
    }

    private void refreshDimColumn() {
        colDim.title = LangKeys.PHASE_DIMENSION;
        String id = ClientDataStore.getInstance().getPhaseDimensionPhases().get(selectedDimKey);
        applyDef(colDim, id);
    }

    private void refreshPlayerColumn() {
        colPlayer.title = LangKeys.PHASE_PLAYER;
        var cds = ClientDataStore.getInstance();
        String id = cds.getPhaseTempPhase() != null ? cds.getPhaseTempPhase() : cds.getPhasePlayerPhase();
        applyDef(colPlayer, id);
    }

    private void applyDef(Column col, String id) {
        JsonObject def = findDef(id);
        if (def != null) {
            col.phaseId = id;
            col.stateName = def.has("name") ? def.get("name").getAsString() : id;
            col.effects = buildEffectRows(def);
        } else {
            col.phaseId = null;
            col.stateName = Component.translatable(LangKeys.PHASE_NONE).getString();
            col.effects = List.of();
        }
    }

    /** 合并三层效果（同 key 数值相加），用于底部当前效果展示 */
    private void recomputeCurrentEffects() {
        currentEffects.clear();
        Map<String, double[]> merged = new LinkedHashMap<>();
        List<String> plains = new ArrayList<>();

        for (Column c : List.of(colWorld, colDim, colPlayer)) {
            for (EffectRow r : c.effects) {
                if (r.plain()) {
                    if (!plains.contains(r.label())) plains.add(r.label());
                    continue;
                }
                double[] acc = merged.get(r.label());
                if (acc == null) {
                    merged.put(r.label(), new double[]{r.value(), r.percent() ? 1 : 0});
                } else {
                    acc[0] += r.value();
                }
            }
        }
        for (Map.Entry<String, double[]> e : merged.entrySet()) {
            currentEffects.add(EffectRow.num(e.getKey(), e.getValue()[0], e.getValue()[1] == 1));
        }
        for (String p : plains) {
            currentEffects.add(EffectRow.text(p));
        }
        bottomScroll = 0;
    }

    private JsonObject findDef(String id) {
        if (id == null) return null;
        for (String brief : ClientDataStore.getInstance().getPhaseDefBriefs()) {
            JsonObject o = PhaseSyncPayload.briefToJson(brief);
            if (o.has("id") && id.equals(o.get("id").getAsString())) return o;
        }
        return null;
    }

    private List<JsonObject> defsForScope(String scope) {
        List<JsonObject> out = new ArrayList<>();
        for (String brief : ClientDataStore.getInstance().getPhaseDefBriefs()) {
            JsonObject o = PhaseSyncPayload.briefToJson(brief);
            String s = o.has("scope") ? o.get("scope").getAsString() : "";
            if (s.equals(scope)) out.add(o);
        }
        return out;
    }

    private List<EffectRow> buildEffectRows(JsonObject def) {
        List<EffectRow> rows = new ArrayList<>();
        if (!def.has("effects")) return rows;
        JsonObject eff = def.getAsJsonObject("effects");
        if (eff.has("attributes")) {
            for (var e : eff.getAsJsonObject("attributes").entrySet()) {
                rows.add(EffectRow.num(localized(attributeName(e.getKey())),
                        jsonToDouble(e.getValue(), 0.0), true).withTag("attributes:" + e.getKey()));
            }
        }
        if (eff.has("mob_mults")) {
            for (var e : eff.getAsJsonObject("mob_mults").entrySet()) {
                rows.add(EffectRow.num(localized(multName(e.getKey())),
                        jsonToDouble(e.getValue(), 0.0), true).withTag("mob_mults:" + e.getKey()));
            }
        }
        if (eff.has("mob_effects")) {
            for (var el : eff.getAsJsonArray("mob_effects")) {
                JsonObject o = el.getAsJsonObject();
                String id = o.has("id") ? jsonToPlainString(o.get("id")) : "";
                rows.add(EffectRow.text(effectDisplayName(id,
                        o.has("level") ? (int) jsonToDouble(o.get("level"), 0) : 0,
                        o.has("seconds") ? (int) jsonToDouble(o.get("seconds"), 0) : 0))
                        .withTag("mob_effects:" + id));
            }
        }
        if (eff.has("equipment")) {
            int idx = 0;
            for (var el : eff.getAsJsonArray("equipment")) {
                JsonObject o = el.getAsJsonObject();
                String chance = o.has("chance") ? jsonToPlainString(o.get("chance")) : "1.0";
                String ent = o.has("entity") ? jsonToPlainString(o.get("entity")) : "?";
                StringBuilder sb = new StringBuilder(localized(LangKeys.PHASE_EFFECT_EQUIP) + " [")
                        .append(chance).append("] ").append(ent);
                if (o.has("slots")) {
                    for (var s : o.getAsJsonObject("slots").entrySet()) {
                        sb.append("  ").append(s.getKey()).append("=").append(jsonToPlainString(s.getValue()));
                    }
                }
                rows.add(EffectRow.text(sb.toString()).withTag("equipment:" + idx));
                idx++;
            }
        }
        return rows;
    }

    private static String jsonToPlainString(JsonElement el) {
        if (el == null) return "";
        if (el.isJsonObject() || el.isJsonArray()) return el.toString();
        if (el.isJsonPrimitive()) {
            var p = el.getAsJsonPrimitive();
            if (p.isString()) return p.getAsString();
            return p.getAsString();
        }
        return el.toString();
    }

    private static double jsonToDouble(JsonElement el, double fallback) {
        if (el == null || !el.isJsonPrimitive()) return fallback;
        try {
            return el.getAsJsonPrimitive().getAsDouble();
        } catch (NumberFormatException | UnsupportedOperationException e) {
            return fallback;
        }
    }

    private String localized(String keyOrText) {
        return keyOrText.contains(".") ? Component.translatable(keyOrText).getString() : keyOrText;
    }

    private String attributeName(String key) {
        return switch (key) {
            case "max_health" -> LangKeys.EFFECT_MAX_HEALTH;
            case "armor" -> LangKeys.EFFECT_ARMOR;
            case "armor_toughness" -> LangKeys.EFFECT_ARMOR_TOUGHNESS;
            case "knockback_resistance" -> LangKeys.EFFECT_KNOCKBACK_RESIST;
            case "movement_speed" -> LangKeys.EFFECT_MOVE_SPEED;
            case "attack_damage" -> LangKeys.EFFECT_ATTACK_DAMAGE;
            case "attack_speed" -> LangKeys.EFFECT_ATTACK_SPEED;
            case "luck" -> LangKeys.EFFECT_LUCK;
            case "scale" -> LangKeys.EFFECT_SCALE;
            default -> key;
        };
    }

    private String multName(String key) {
        return switch (key) {
            case "mob_health_mult" -> LangKeys.EFFECT_MOB_HEALTH;
            case "mob_damage_mult" -> LangKeys.EFFECT_MOB_ATTACK;
            case "mob_speed_mult" -> LangKeys.EFFECT_MOB_SPEED;
            case "mob_spawn_rate_mult" -> LangKeys.EFFECT_SPAWN_RATE;
            case "mob_armor_mult" -> LangKeys.EFFECT_MOB_ARMOR;
            case "boss_damage_mult" -> LangKeys.EFFECT_BOSS_DAMAGE;
            default -> key;
        };
    }

    private String effectDisplayName(String effectId, int level, int seconds) {
        ResourceLocation rl = ResourceLocation.parse(effectId);
        String transKey = "effect." + rl.getNamespace() + "." + rl.getPath();
        String name = effectId;
        var holder = BuiltInRegistries.MOB_EFFECT.getHolder(rl);
        if (holder != null && holder.isPresent()) {
            String t = Component.translatable(transKey).getString();
            if (!t.equals(transKey)) name = t;
        }
        if (level > 0) {
            name += " " + Component.translatable("enchantment.level." + (level + 1)).getString();
        }
        if (seconds > 0) name += " (" + seconds + "s)";
        return name;
    }

    // ────────────────────────── 工具 ──────────────────────────

    private List<String> wrap(String text, int maxPx) {
        List<String> out = new ArrayList<>();
        if (font.width(text) <= maxPx) {
            out.add(text);
            return out;
        }
        StringBuilder cur = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (font.width(cur.toString() + c) > maxPx && cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            cur.append(c);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private String trunc(String s, int maxPx) {
        if (s == null) return "";
        if (font.width(s) <= maxPx) return s;
        while (s.length() > 1 && font.width(s + "..") > maxPx) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "..";
    }

    private static boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int colX(int ci) {
        return panelLeft + PADDING_X + ci * (colW + COL_GAP);
    }

    private static String scopeOf(int ci) {
        return switch (ci) {
            case SCOPE_GLOBAL -> "world";
            case SCOPE_DIM -> "dimension";
            default -> "player";
        };
    }

    private Column getColumn(int ci) {
        return switch (ci) {
            case SCOPE_GLOBAL -> colWorld;
            case SCOPE_DIM -> colDim;
            case SCOPE_PLAYER -> colPlayer;
            default -> throw new IllegalArgumentException("Invalid column: " + ci);
        };
    }

    private String getPlayerDisplayName(UUID pid) {
        if (pid == null) return "???";
        var mc = Minecraft.getInstance();
        if (mc.player != null && pid.equals(mc.player.getUUID())) {
            return mc.player.getGameProfile().getName();
        }
        if (mc.level != null) {
            var p = mc.level.getPlayerByUUID(pid);
            if (p != null) return p.getGameProfile().getName();
        }
        return pid.toString().substring(0, 8);
    }
}
