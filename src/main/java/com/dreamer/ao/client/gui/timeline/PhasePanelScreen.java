package com.dreamer.ao.client.gui.timeline;

import com.dreamer.ao.LangKeys;
import com.dreamer.ao.client.gui.GuiUtils;
import com.dreamer.ao.data.ClientDataStore;
import com.dreamer.ao.data.DisplayNameResolver;
import com.dreamer.ao.data.model.PhaseDefinition;
import com.dreamer.ao.data.model.PhaseEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * 阶段管理面板 — 三列（全局/维度/玩家）实时数据可视化界面。
 * <p>
 * 数据来源：{@link ClientDataStore} 缓存的服务端同步阶段数据。
 * 所有交互均为只读展示（设置阶段通过 /adv phase 命令）。
 */
public class PhasePanelScreen extends Screen {

    // ── 布局常量 ──
    private static final int PANEL_W = 520;
    private static final int PANEL_H = 380;
    private static final int HEADER_H = 26;
    private static final int COL_GAP = 6;
    private static final int PADDING_X = 10;
    private static final int COL_Y_START = 38;
    private static final int EFFECT_ROW_H = 16;
    private static final int ROW_GAP = 2;

    // ── 色彩 ──
    private static final int BG_DARK = 0xE61E1E2A;
    private static final int BG_HEADER = 0x50333344;
    private static final int BG_COL_HEADER = 0x30FFFFFF;
    private static final int BG_STATE_BOX = 0x30FFFFFF;
    private static final int BG_STATE_ACTIVE = 0x406AB4BC;
    private static final int DIVIDER = 0x30FFFFFF;
    private static final int TEXT_PRIMARY = 0xFFE0E0E0;
    private static final int TEXT_SECONDARY = 0xFFAAAAAA;
    private static final int TEXT_DIM = 0xFF777777;
    private static final int TEXT_ACCENT = 0xFF6AB4BC;
    private static final int TEXT_POS = 0xFF4ADE80;
    private static final int TEXT_NEG = 0xFFF87171;

    // ── 属性翻译键映射 ──
    private static final Map<String, String> ATTR_LANG_MAP = Map.of(
            "generic.max_health", LangKeys.PHASE_EFFECT_HEALTH,
            "generic.attack_damage", LangKeys.PHASE_EFFECT_DAMAGE,
            "generic.armor", LangKeys.PHASE_EFFECT_ARMOR,
            "generic.movement_speed", LangKeys.PHASE_EFFECT_SPEED,
            "generic.attack_knockback", LangKeys.PHASE_EFFECT_KNOCKBACK,
            "generic.knockback_resistance", LangKeys.PHASE_EFFECT_KB_RESIST,
            "generic.attack_speed", LangKeys.PHASE_EFFECT_ATK_SPEED,
            "zombie.spawn_reinforcements", LangKeys.PHASE_EFFECT_REINFORCE
    );

    private final Screen parent;
    private final ClientDataStore store = ClientDataStore.getInstance();

    private int panelLeft, panelTop, effectiveW, effectiveH, colW;
    private int lastPhaseVersion = -1;

    // 维度选择
    private String selectedDimKey = "minecraft:overworld";
    private boolean dimDropdownOpen = false;

    public PhasePanelScreen(Screen parent) {
        super(Component.translatable(LangKeys.PHASE_PANEL_TITLE));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.effectiveW = Math.min(PANEL_W, this.width - 10);
        this.effectiveH = Math.min(PANEL_H, this.height - 10);
        this.colW = (effectiveW - 2 * PADDING_X - 2 * COL_GAP) / 3;
        this.panelLeft = (this.width - effectiveW) / 2;
        this.panelTop = (this.height - effectiveH) / 2;
        refreshDimList();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ═══════════════ 主渲染 ═══════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.enableScissor(panelLeft, panelTop, panelLeft + effectiveW, panelTop + effectiveH);
        renderBackground(g);
        renderHeader(g, mouseX, mouseY);
        renderColumn(g, 0, mouseX, mouseY);
        renderColumn(g, 1, mouseX, mouseY);
        renderColumn(g, 2, mouseX, mouseY);
        g.disableScissor();
        if (dimDropdownOpen) renderDimDropdown(g, mouseX, mouseY);
    }

    private void renderBackground(GuiGraphics g) {
        GuiUtils.fillRoundedCard(g, panelLeft, panelTop, effectiveW, effectiveH, BG_DARK);
    }

    private void renderHeader(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(panelLeft, panelTop, panelLeft + effectiveW, panelTop + HEADER_H, BG_HEADER);
        Component title = Component.translatable(LangKeys.PHASE_PANEL_TITLE);
        int titleX = panelLeft + (effectiveW - font.width(title)) / 2;
        g.drawString(font, title, titleX, panelTop + 6, TEXT_PRIMARY, false);
        int closeX = panelLeft + effectiveW - 20;
        boolean closeHov = GuiUtils.inRect(mouseX, mouseY, closeX, panelTop + 6, 14, 14);
        g.drawString(font, "\u2715", closeX, panelTop + 6, closeHov ? 0xFFFF6666 : TEXT_SECONDARY, false);
    }

    // ═══════════════ 列渲染 ═══════════════

    private void renderColumn(GuiGraphics g, int colIndex, int mouseX, int mouseY) {
        int x = colX(colIndex);
        int y = panelTop + COL_Y_START;
        String titleKey;
        PhaseDefinition phase;

        if (colIndex == 0) {
            titleKey = LangKeys.PHASE_GLOBAL_LABEL;
            phase = store.getPhaseById(store.getGlobalPhaseId());
        } else if (colIndex == 1) {
            titleKey = LangKeys.PHASE_DIM_LABEL;
            phase = store.getPhaseById(store.getDimensionPhaseId(selectedDimKey));
        } else {
            titleKey = LangKeys.PHASE_PLAYER;
            phase = null; // 玩家阶段暂不支持
        }

        // 列标题
        g.fill(x, y, x + colW, y + 14, BG_COL_HEADER);
        g.drawString(font, Component.translatable(titleKey).getString(), x + 6, y + 3, TEXT_SECONDARY, false);
        y += 18;

        // 维度选择器（仅维度列）
        if (colIndex == 1) {
            y = renderDimSelector(g, x, y, mouseX, mouseY);
            y += 4;
        }

        // 当前阶段名
        y = renderPhaseName(g, x, y, phase);
        y += 4;

        // 分隔线
        g.fill(x, y, x + colW, y + 1, DIVIDER);
        y += 5;

        // 效果列表
        g.drawString(font, Component.translatable(LangKeys.PHASE_EFFECTS).getString(), x + 4, y, TEXT_DIM, false);
        y += 14;
        if (phase != null) {
            for (PhaseEffect eff : phase.effects()) {
                y = renderEffectRow(g, x, y, eff);
            }
        } else {
            g.drawString(font, "-", x + 8, y, TEXT_DIM, false);
            y += EFFECT_ROW_H;
        }

        y += 4;

        // 装备信息（全局和维度列）
        if (colIndex < 2 && phase != null) {
            g.fill(x, y, x + colW, y + 1, DIVIDER);
            y += 5;
            g.drawString(font, Component.translatable(LangKeys.PHASE_EQUIP_PROBABILITY).getString(), x + 4, y, TEXT_DIM, false);
            y += 14;
            y = renderEquipmentSummary(g, x, y, phase);
        }

        // 玩家列提示
        if (colIndex == 2) {
            g.fill(x, y, x + colW, y + 1, DIVIDER);
            y += 5;
            g.drawString(font, Component.translatable(LangKeys.PHASE_NONE).getString(), x + 8, y, TEXT_DIM, false);
        }
    }

    private int renderPhaseName(GuiGraphics g, int x, int y, PhaseDefinition phase) {
        int boxH = 22;
        boolean isActive = phase != null;
        int bg = isActive ? BG_STATE_ACTIVE : BG_STATE_BOX;
        g.fill(x, y, x + colW, y + boxH, bg);

        if (isActive) {
            String name = Component.translatable(phase.nameKey()).getString();
            g.drawString(font, name, x + 8, y + 6, TEXT_PRIMARY, false);
            String statusKey = LangKeys.PHASE_STATUS_ACTIVE;
            int statusW = font.width(Component.translatable(statusKey).getString());
            g.drawString(font, Component.translatable(statusKey).getString(), x + colW - statusW - 8, y + 6, TEXT_ACCENT, false);
        } else {
            g.drawString(font, Component.translatable(LangKeys.PHASE_NONE).getString(), x + 8, y + 6, TEXT_DIM, false);
        }
        return y + boxH;
    }

    private int renderEffectRow(GuiGraphics g, int x, int y, PhaseEffect eff) {
        String attrId = eff.attributeId();
        String nameKey = ATTR_LANG_MAP.getOrDefault(attrId, attrId);
        String name = Component.translatable(nameKey).getString();
        g.drawString(font, name, x + 8, y, TEXT_SECONDARY, false);

        double mult = eff.multiplier();
        String valStr;
        int color;
        if (mult >= 1.0) {
            valStr = String.format("+%.0f%%", (mult - 1.0) * 100);
            color = TEXT_POS;
        } else {
            valStr = String.format("-%.0f%%", (1.0 - mult) * 100);
            color = TEXT_NEG;
        }
        int valW = font.width(valStr);
        g.drawString(font, valStr, x + colW - valW - 8, y, color, false);
        return y + EFFECT_ROW_H;
    }

    private int renderEquipmentSummary(GuiGraphics g, int x, int y, PhaseDefinition phase) {
        var equipment = phase.equipment();
        if (equipment.isEmpty()) {
            g.drawString(font, "-", x + 8, y, TEXT_DIM, false);
            return y + EFFECT_ROW_H;
        }
        for (var entry : equipment.entrySet()) {
            String slotName = entry.getKey().getName();
            var slot = entry.getValue();
            int count = slot.getItems().size();
            String text = slotName + " (" + count + " items)";
            g.drawString(font, text, x + 8, y, TEXT_SECONDARY, false);
            y += EFFECT_ROW_H;
        }
        return y;
    }

    // ═══════════════ 维度选择器 ═══════════════

    private int renderDimSelector(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        Component label = Component.translatable(LangKeys.PHASE_SELECT_DIMENSION);
        g.drawString(font, label.getString(), x + 4, y, TEXT_DIM, false);
        y += 12;
        String friendly = DisplayNameResolver.friendlyDimension(selectedDimKey);
        int btnW = colW - 12;
        int btnH = 16;
        boolean hov = GuiUtils.inRect(mouseX, mouseY, x + 6, y, btnW, btnH);
        g.fill(x + 6, y, x + 6 + btnW, y + btnH, hov ? 0x50FFFFFF : 0x30FFFFFF);
        g.drawString(font, friendly, x + 10, y + 4, TEXT_PRIMARY, false);
        String arrow = dimDropdownOpen ? "\u25b4" : "\u25be";
        int arrowW = font.width(arrow);
        g.drawString(font, arrow, x + 6 + btnW - arrowW - 6, y + 4, TEXT_DIM, false);
        return y + btnH + 2;
    }

    private void renderDimDropdown(GuiGraphics g, int mouseX, int mouseY) {
        List<String> dims = getAvailableDims();
        int x = colX(1) + 6;
        int baseY = panelTop + COL_Y_START + 18 + 12 + 18;
        int ddW = colW - 12;
        int rowH = 14;
        int vis = Math.min(8, dims.size());
        int ddH = vis * rowH + 4;
        GuiUtils.fillRoundedCard(g, x, baseY - 2, ddW, ddH, 0xE61E1E2A);
        for (int i = 0; i < vis; i++) {
            int ry = baseY + i * rowH;
            boolean hov = GuiUtils.inRect(mouseX, mouseY, x, ry, ddW, rowH);
            if (hov) g.fill(x + 2, ry, x + ddW - 2, ry + rowH, 0x30FFFFFF);
            String dim = dims.get(i);
            String name = DisplayNameResolver.friendlyDimension(dim);
            int clr = dim.equals(selectedDimKey) ? TEXT_ACCENT : (hov ? TEXT_PRIMARY : TEXT_SECONDARY);
            g.drawString(font, name, x + 6, ry + 2, clr, false);
        }
    }

    private List<String> getAvailableDims() {
        Set<String> dims = new LinkedHashSet<>();
        dims.add("minecraft:overworld");
        dims.add("minecraft:the_nether");
        dims.add("minecraft:the_end");
        var mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            try {
                var access = mc.getConnection().registryAccess();
                if (access != null) {
                    access.registryOrThrow(net.minecraft.core.registries.Registries.DIMENSION_TYPE)
                            .keySet().forEach(rl -> dims.add(rl.toString()));
                }
            } catch (Exception ignored) {}
        }
        return new ArrayList<>(dims);
    }

    private void refreshDimList() {
        selectedDimKey = "minecraft:overworld";
    }

    // ═══════════════ 交互 ═══════════════

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int mx = (int) mouseX;
        int my = (int) mouseY;
        int closeX = panelLeft + effectiveW - 20;
        if (GuiUtils.inRect(mx, my, closeX, panelTop + 6, 14, 14)) { onClose(); return true; }

        // 维度选择器
        int dimBtnX = colX(1) + 6;
        int dimBtnY = panelTop + COL_Y_START + 18 + 12;
        if (GuiUtils.inRect(mx, my, dimBtnX, dimBtnY, colW - 12, 16)) {
            dimDropdownOpen = !dimDropdownOpen;
            return true;
        }

        // 维度下拉选择
        if (dimDropdownOpen) {
            List<String> dims = getAvailableDims();
            int x = colX(1) + 6;
            int baseY = panelTop + COL_Y_START + 18 + 12 + 18;
            int vis = Math.min(8, dims.size());
            for (int i = 0; i < vis; i++) {
                int ry = baseY + i * 14;
                if (GuiUtils.inRect(mx, my, x, ry, colW - 12, 14)) {
                    selectedDimKey = dims.get(i);
                    dimDropdownOpen = false;
                    return true;
                }
            }
            dimDropdownOpen = false;
            return true;
        }

        // 点击面板外关闭
        if (mx < panelLeft || mx > panelLeft + effectiveW || my < panelTop || my > panelTop + effectiveH) {
            dimDropdownOpen = false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int colX(int colIndex) {
        return panelLeft + PADDING_X + colIndex * (colW + COL_GAP);
    }
}
