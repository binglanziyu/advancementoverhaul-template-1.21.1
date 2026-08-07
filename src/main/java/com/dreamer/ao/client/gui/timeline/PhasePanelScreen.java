package com.dreamer.ao.client.gui.timeline;

import com.dreamer.ao.LangKeys;
import com.dreamer.ao.client.gui.GuiUtils;
import com.dreamer.ao.data.DisplayNameResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/**
 * 故事阶段管理面板 —— 三列（世界/维度/玩家）状态可视化界面。
 * <p>
 * 玩家模式：查看当前世界、各维度和指定玩家的阶段状态及效果。
 * OP 模式（需权限等级 &ge; 2）：额外暴露强制切换、编辑定义、新建状态、
 * 施加/清除临时状态等管理入口。
 * <p>
 * 注意：本文件仅负责 UI 面板的布局、交互与显示规则。
 * 后端状态管理逻辑、网络协议、命令执行由其他模块负责，
 * 此处仅预留调用桩方法（以 {@code // TODO: 后端对接} 标记）。
 */
public class PhasePanelScreen extends Screen {

    // ── 布局常量 ──
    private static final int PANEL_W       = 520;
    private static final int PANEL_H       = 360;
    private static final int HEADER_H      = 26;
    private static final int COL_W         = 165;
    private static final int COL_GAP       = 5;
    private static final int PADDING_X     = 8;
    private static final int COL_Y_START   = 38;  // header 下方
    private static final int EFFECT_ROW_H  = 14;
    private static final int BTN_H         = 16;
    private static final int BTN_GAP       = 3;

    // ── 色彩 ──
    private static final int BG_DARK          = 0xE62A2A36;
    private static final int BG_HEADER        = 0x50333344;
    private static final int BG_COL_HEADER    = 0x30FFFFFF;
    private static final int BG_STATE_BOX     = 0x30FFFFFF;
    private static final int BG_STATE_PREVIEW = 0x30F59E0B;
    private static final int DIVIDER          = 0x40FFFFFF;
    private static final int TEXT_PRIMARY     = 0xFFFFFFFF;
    private static final int TEXT_SECONDARY   = 0xFFAAAAAA;
    private static final int TEXT_DIM         = 0xFF777777;
    private static final int OP_BTN_PRIMARY   = 0xFF6AB4BC;
    private static final int OP_BTN_SECONDARY = 0xFF3C9BB0;
    private static final int TEXT_OP_BTN      = 0xFFFFFFFF;
    private static final int TEXT_EFFECT_POS  = 0xFF4ADE80;  // 正向效果
    private static final int TEXT_EFFECT_NEG  = 0xFFF87171;  // 负向效果

    // ── 状态 ──
    private final Screen parent;
    private final boolean isOp;
    private boolean opMode = false;               // OP 管理模式开关
    private boolean previewMode = false;
    private int previewColumn = -1;               // 当前预览列索引 (0=世界, 1=维度, 2=玩家)

    private int panelLeft, panelTop;
    private int effectiveW, effectiveH, colW;    // 响应式尺寸，由 init() 根据屏幕大小计算

    // 维度选择
    private String selectedDimKey = "minecraft:overworld";
    private List<String> availableDims = new ArrayList<>();

    // 玩家选择
    private UUID selectedPlayer;
    private List<UUID> availablePlayers = new ArrayList<>();

    // 下拉状态
    private boolean dimDropdownOpen = false;
    private boolean playerDropdownOpen = false;
    private int dimDropdownScroll = 0;
    private int playerDropdownScroll = 0;

    /**
     * 三列数据容器（含 mock 数据，后续改为从 ClientDataStore 读取）。
     */
    private static final class Column {
        String title;
        String stateName;
        List<EffectRow> effects;

        // 仅世界/维度列
        String nextState    = null;
        String transitionCnd = null;

        // 仅玩家列
        String tempState    = null;  // null = 无临时状态
        String history      = null;
    }

    private static final class EffectRow {
        final String key;
        final double value;
        final boolean isPercentage;

        EffectRow(String key, double value, boolean isPercentage) {
            this.key = key;
            this.value = value;
            this.isPercentage = isPercentage;
        }
    }

    private final Column colWorld = new Column();
    private final Column colDim   = new Column();
    private final Column colPlayer = new Column();

    // ── 构造 ──

    public PhasePanelScreen(Screen parent) {
        super(Component.translatable(LangKeys.PHASE_PANEL_TITLE));
        this.parent = parent;
        var player = Minecraft.getInstance().player;
        this.isOp = player != null && player.hasPermissions(2);
        if (player != null) {
            this.selectedPlayer = player.getUUID();
        }
    }

    // ── 布局初始化 ──

    @Override
    protected void init() {
        this.effectiveW = Math.min(PANEL_W, this.width - 10);
        this.effectiveH = Math.min(PANEL_H, this.height - 10);
        this.colW = effectiveW == PANEL_W ? COL_W : (effectiveW - 2 * PADDING_X - 2 * COL_GAP) / 3;
        this.panelLeft = (this.width  - effectiveW) / 2;
        this.panelTop  = (this.height - effectiveH) / 2;
        refreshDimensionList();
        refreshPlayerList();
        refreshAllColumns();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── 主渲染 ──

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        // 裁剪区域，防止面板内容溢出屏幕边界
        g.enableScissor(panelLeft, panelTop, panelLeft + effectiveW, panelTop + effectiveH);

        renderPanelBackground(g);
        renderHeader(g, mouseX, mouseY);
        renderColumn(g, 0, mouseX, mouseY);  // 世界
        renderColumn(g, 1, mouseX, mouseY);  // 维度
        renderColumn(g, 2, mouseX, mouseY);  // 玩家

        g.disableScissor();

        if (dimDropdownOpen)  renderDimDropdown(g, mouseX, mouseY);
        if (playerDropdownOpen) renderPlayerDropdown(g, mouseX, mouseY);
    }

    // ── 面板背景 ──

    private void renderPanelBackground(GuiGraphics g) {
        GuiUtils.fillRoundedCard(g, panelLeft, panelTop, effectiveW, effectiveH, BG_DARK);
    }

    // ── 标题栏 ──

    private void renderHeader(GuiGraphics g, int mouseX, int mouseY) {
        int headerRight = panelLeft + effectiveW;

        // 标题栏背景
        g.fill(panelLeft, panelTop, panelLeft + effectiveW, panelTop + HEADER_H, BG_HEADER);

        // 标题
        Component title = Component.translatable(LangKeys.PHASE_PANEL_TITLE);
        int titleX = panelLeft + (effectiveW - font.width(title)) / 2;
        g.drawString(font, title, titleX, panelTop + 6, TEXT_PRIMARY, false);

        // 关闭按钮 (右上角 X)
        Component closeText = Component.literal("✕");
        int closeW = font.width(closeText);
        int closeX = headerRight - closeW - 10;
        int closeY = panelTop + 6;
        g.drawString(font, closeText, closeX, closeY, TEXT_SECONDARY, false);

        // OP 切换按钮（仅 OP 玩家可见）
        if (isOp) {
            Component opTag = Component.translatable(LangKeys.PHASE_OP_TAG);
            String opLabel = opTag.getString() + (opMode ? " §a管理" : "§7管理");
            int opW = font.width(Component.literal(opTag.getString() + " 管理"));
            int opX = closeX - opW - 8;
            boolean hovered = mouseX >= opX && mouseX < opX + opW && mouseY >= closeY - 1 && mouseY < closeY + 11;
            int color = opMode ? 0xFF6AB4BC : (hovered ? 0xFF8AAACC : TEXT_SECONDARY);
            g.drawString(font, Component.literal(opLabel), opX, closeY, color, false);
        }
    }

    // ── 列渲染 ──

    private void renderColumn(GuiGraphics g, int colIndex, int mouseX, int mouseY) {
        int x = colX(colIndex);
        Column col = getColumn(colIndex);

        // 列标题背景
        g.fill(x, panelTop + COL_Y_START - 2, x + colW, panelTop + COL_Y_START + 12, BG_COL_HEADER);
        Component titleText = Component.translatable(col.title);
        g.drawString(font, titleText, x + 6, panelTop + COL_Y_START, TEXT_SECONDARY, false);

        int cy = panelTop + COL_Y_START + 16;

        // 维度选择器（仅维度列）
        if (colIndex == 1 && !availableDims.isEmpty()) {
            cy = renderDimSelector(g, x, cy, mouseX, mouseY);
            cy += 4;
        }
        // 玩家选择器（仅玩家列）
        if (colIndex == 2 && !availablePlayers.isEmpty()) {
            cy = renderPlayerSelector(g, x, cy, mouseX, mouseY);
            cy += 4;
        }

        // 当前阶段状态框
        boolean isPreviewCol = previewMode && colIndex == previewColumn;
        cy = renderStateBox(g, x, cy, col.stateName, isPreviewCol, mouseX, mouseY, colIndex);

        cy += 4;

        // 分隔线
        g.fill(x, cy, x + colW, cy + 1, DIVIDER);
        cy += 5;

        // 效果列表
        Component effectsHeader = Component.translatable(LangKeys.PHASE_EFFECTS);
        g.drawString(font, effectsHeader, x + 4, cy, TEXT_DIM, false);
        cy += 12;
        for (EffectRow eff : col.effects) {
            cy = renderEffectRow(g, x, cy, eff);
        }

        cy += 4;

        // 维度/世界列特有信息
        if (colIndex != 2) {
            if (col.nextState != null) {
                g.fill(x, cy, x + colW, cy + 1, DIVIDER);
                cy += 5;
                cy = renderNextState(g, x, cy, col);
            }
        }

        // 玩家列特有：历史和临时状态
        if (colIndex == 2) {
            g.fill(x, cy, x + colW, cy + 1, DIVIDER);
            cy += 5;
            cy = renderTempState(g, x, cy, col);
            cy += 6;
            g.fill(x, cy, x + colW, cy + 1, DIVIDER);
            cy += 5;
            cy = renderHistory(g, x, cy, col);
        }

        // OP 操作区
        if (isOp && opMode) {
            int dividerY = panelTop + effectiveH - 88;
            g.fill(x, dividerY, x + colW, dividerY + 1, DIVIDER);
            renderOpButtons(g, x, dividerY + 4, colIndex, mouseX, mouseY);
        }
    }

    // ── 阶段状态框 ──

    private int renderStateBox(GuiGraphics g, int x, int y, String name, boolean preview, int mouseX, int mouseY, int colIndex) {
        int boxH = 20;
        boolean hovered = mouseX >= x && mouseX < x + colW && mouseY >= y && mouseY < y + boxH;
        int bg = preview ? BG_STATE_PREVIEW : (hovered ? 0x40FFFFFF : BG_STATE_BOX);
        g.fill(x, y, x + colW, y + boxH, bg);

        Component label = Component.literal(name);
        int nameW = font.width(label);
        g.drawString(font, label, x + 8, y + 6, preview ? 0xFFFFFFFF : TEXT_PRIMARY, false);

        if (preview) {
            Component tag = Component.translatable(LangKeys.PHASE_PREVIEW);
            int tagW = font.width(tag);
            g.fill(x + colW - tagW - 12, y + 2, x + colW - 4, y + 18, TimelineTheme.PREVIEW_TAG_BG);
            g.drawString(font, tag, x + colW - tagW - 8, y + 4, TimelineTheme.PREVIEW_TAG_FG, false);
        }

        return y + boxH;
    }

    // ── 效果行 ──

    private int renderEffectRow(GuiGraphics g, int x, int y, EffectRow eff) {
        Component keyText = Component.translatable(eff.key);
        g.drawString(font, keyText, x + 8, y, TEXT_SECONDARY, false);

        String valStr = eff.isPercentage ? String.format("%+.0f%%", eff.value * 100) : String.format("%+.1f", eff.value);
        int color = eff.value > 0 ? TEXT_EFFECT_POS : (eff.value < 0 ? TEXT_EFFECT_NEG : TEXT_DIM);
        int valW = font.width(valStr);
        g.drawString(font, Component.literal(valStr), x + colW - valW - 8, y, color, false);

        return y + EFFECT_ROW_H;
    }

    // ── 下一阶段 ──

    private int renderNextState(GuiGraphics g, int x, int y, Column col) {
        Component nextHeader = Component.translatable(LangKeys.PHASE_NEXT_STATE);
        g.drawString(font, nextHeader, x + 4, y, TEXT_DIM, false);
        y += 11;
        Component nameComp = Component.literal(col.nextState);
        g.drawString(font, nameComp, x + 8, y, TEXT_PRIMARY, false);
        y += 12;
        if (col.transitionCnd != null) {
            Component condHeader = Component.translatable(LangKeys.PHASE_TRANSITION_COND);
            g.drawString(font, condHeader, x + 4, y, TEXT_DIM, false);
            y += 11;
            Component condVal = Component.literal(col.transitionCnd);
            g.drawString(font, condVal, x + 8, y, TEXT_SECONDARY, false);
            y += 12;
        }
        return y;
    }

    // ── 历史 ──

    private int renderHistory(GuiGraphics g, int x, int y, Column col) {
        Component histHeader = Component.translatable(LangKeys.PHASE_HISTORY);
        g.drawString(font, histHeader, x + 4, y, TEXT_DIM, false);
        y += 11;
        String content = col.history != null ? col.history : "-";
        g.drawString(font, Component.literal(content), x + 8, y, TEXT_SECONDARY, false);
        return y + 12;
    }

    // ── 临时状态 ──

    private int renderTempState(GuiGraphics g, int x, int y, Column col) {
        Component tempHeader = Component.translatable(LangKeys.PHASE_TEMP_STATE);
        g.drawString(font, tempHeader, x + 4, y, TEXT_DIM, false);
        y += 11;
        String content = col.tempState != null ? col.tempState : Component.translatable(LangKeys.PHASE_NO_TEMP).getString();
        int clr = col.tempState != null ? 0xFFFACC15 : TEXT_DIM;
        g.drawString(font, Component.literal(content), x + 8, y, clr, false);
        return y + 12;
    }

    // ── 维度选择器（内嵌下拉） ──

    private int renderDimSelector(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        Component label = Component.translatable(LangKeys.PHASE_SELECT_DIMENSION);
        g.drawString(font, label, x + 4, y, TEXT_DIM, false);
        y += 10;

        String friendly = DisplayNameResolver.friendlyDimension(selectedDimKey);
        int btnW = colW - 12;
        int btnH = 14;
        boolean hovered = mouseX >= x + 6 && mouseX < x + 6 + btnW && mouseY >= y && mouseY < y + btnH;
        int bg = hovered ? 0x50FFFFFF : 0x30FFFFFF;
        g.fill(x + 6, y, x + 6 + btnW, y + btnH, bg);
        g.drawString(font, Component.literal(friendly), x + 10, y + 3, TEXT_PRIMARY, false);

        // 下拉箭头
        String arrow = dimDropdownOpen ? "▲" : "▼";
        int arrowW = font.width(arrow);
        g.drawString(font, Component.literal(arrow), x + 6 + btnW - arrowW - 6, y + 3, TEXT_DIM, false);

        return y + btnH + 2;
    }

    private void renderDimDropdown(GuiGraphics g, int mouseX, int mouseY) {
        int x = colX(1) + 6;
        int baseY = getDimDropdownY();
        int ddW = colW - 12;
        int maxVis = 6;
        int rowH = 14;
        int total = availableDims.size();
        int vis  = Math.min(maxVis, total);

        int ddH = vis * rowH + 4;
        GuiUtils.fillRoundedCard(g, x, baseY - 2, ddW, ddH, 0xE61E1E2A);

        for (int i = 0; i < vis; i++) {
            int idx = i + dimDropdownScroll;
            if (idx >= total) break;
            int ry = baseY + i * rowH;
            boolean hovered = mouseX >= x && mouseX < x + ddW && mouseY >= ry && mouseY < ry + rowH;
            if (hovered) g.fill(x + 2, ry, x + ddW - 2, ry + rowH, 0x30FFFFFF);

            String dimKey = availableDims.get(idx);
            String name = DisplayNameResolver.friendlyDimension(dimKey);
            int clr = dimKey.equals(selectedDimKey) ? 0xFF6AB4BC : (hovered ? TEXT_PRIMARY : TEXT_SECONDARY);
            g.drawString(font, Component.literal(name), x + 6, ry + 2, clr, false);
        }
    }

    private int getDimDropdownY() {
        // 维度列状态框 y = panelTop + COL_Y_START + 16 + dimSelector高度 + 4 + stateBox高度 + ...
        // 简化：维度选择器下方
        int dimSelBaseY = panelTop + COL_Y_START + 16 + 10 + 16;
        return dimSelBaseY + 4;
    }

    // ── 玩家选择器（内嵌下拉） ──

    private int renderPlayerSelector(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        Component label = Component.translatable(LangKeys.PHASE_SELECT_PLAYER);
        g.drawString(font, label, x + 4, y, TEXT_DIM, false);
        y += 10;

        String name = getPlayerDisplayName(selectedPlayer);
        int btnW = colW - 12;
        int btnH = 14;
        boolean hovered = mouseX >= x + 6 && mouseX < x + 6 + btnW && mouseY >= y && mouseY < y + btnH;
        int bg = hovered ? 0x50FFFFFF : 0x30FFFFFF;
        g.fill(x + 6, y, x + 6 + btnW, y + btnH, bg);
        g.drawString(font, Component.literal(name), x + 10, y + 3, TEXT_PRIMARY, false);

        String arrow = playerDropdownOpen ? "▲" : "▼";
        int arrowW = font.width(arrow);
        g.drawString(font, Component.literal(arrow), x + 6 + btnW - arrowW - 6, y + 3, TEXT_DIM, false);

        return y + btnH + 2;
    }

    private void renderPlayerDropdown(GuiGraphics g, int mouseX, int mouseY) {
        int x = colX(2) + 6;
        int baseY = getPlayerDropdownY();
        int ddW = colW - 12;
        int maxVis = 6;
        int rowH = 14;
        int total = availablePlayers.size();
        int vis  = Math.min(maxVis, total);

        int ddH = vis * rowH + 4;
        GuiUtils.fillRoundedCard(g, x, baseY - 2, ddW, ddH, 0xE61E1E2A);

        for (int i = 0; i < vis; i++) {
            int idx = i + playerDropdownScroll;
            if (idx >= total) break;
            int ry = baseY + i * rowH;
            boolean hovered = mouseX >= x && mouseX < x + ddW && mouseY >= ry && mouseY < ry + rowH;
            if (hovered) g.fill(x + 2, ry, x + ddW - 2, ry + rowH, 0x30FFFFFF);

            UUID pid = availablePlayers.get(idx);
            String name = getPlayerDisplayName(pid);
            int clr = pid.equals(selectedPlayer) ? 0xFF6AB4BC : (hovered ? TEXT_PRIMARY : TEXT_SECONDARY);
            g.drawString(font, Component.literal(name), x + 6, ry + 2, clr, false);
        }
    }

    private int getPlayerDropdownY() {
        int playerSelBaseY = panelTop + COL_Y_START + 16 + 10 + 16;
        return playerSelBaseY + 4;
    }

    // ── OP 操作按钮 ──

    private void renderOpButtons(GuiGraphics g, int x, int y, int colIndex, int mouseX, int mouseY) {
        int btnW = colW - 16;
        int btnX = x + 8;
        int curY = y;

        if (colIndex == 2) {
            // 玩家列：施加临时 + 清除临时
            curY = renderOpButton(g, btnX, curY, btnW, LangKeys.PHASE_APPLY_TEMP, mouseX, mouseY);
            curY += BTN_GAP;
            curY = renderOpButton(g, btnX, curY, btnW, LangKeys.PHASE_CLEAR_TEMP, mouseX, mouseY);
        } else {
            // 世界/维度列：强制切换 + 编辑定义
            curY = renderOpButton(g, btnX, curY, btnW, LangKeys.PHASE_FORCE_TRANSITION, mouseX, mouseY);
            curY += BTN_GAP;
            curY = renderOpButton(g, btnX, curY, btnW, LangKeys.PHASE_EDIT_DEF, mouseX, mouseY);
        }
        curY += BTN_GAP;
        // 新建
        renderOpButton(g, btnX, curY, btnW, LangKeys.PHASE_NEW_STATE, mouseX, mouseY);
    }

    private int renderOpButton(GuiGraphics g, int x, int y, int w, String langKey, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + BTN_H;
        int bg = hovered ? 0x60808090 : 0x30444450;
        g.fill(x, y, x + w, y + BTN_H, bg);

        Component label = Component.translatable(langKey);
        int textW = font.width(label);
        g.drawString(font, label, x + (w - textW) / 2, y + 2, TEXT_OP_BTN, false);
        return y + BTN_H;
    }

    // ── 鼠标交互 ──

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int mx = (int) mouseX;
        int my = (int) mouseY;

        // 关闭按钮
        if (hitClose(mx, my)) { onClose(); return true; }

        // OP 模式切换
        if (hitOpToggle(mx, my)) { opMode = !opMode; return true; }

        // 维度下拉
        if (hitDimSelector(mx, my)) { dimDropdownOpen = !dimDropdownOpen; playerDropdownOpen = false; return true; }

        // 维度下拉项
        if (dimDropdownOpen && hitDimDropdown(mx, my, this::onDimSelected)) { return true; }

        // 玩家下拉
        if (hitPlayerSelector(mx, my)) { playerDropdownOpen = !playerDropdownOpen; dimDropdownOpen = false; return true; }

        // 玩家下拉项
        if (playerDropdownOpen && hitPlayerDropdown(mx, my, this::onPlayerSelected)) { return true; }

        // 阶段状态框点击 → 预览模式
        for (int ci = 0; ci < 3; ci++) {
            if (hitStateBox(ci, mx, my)) {
                if (previewMode && previewColumn == ci) {
                    // 双击 → 进入状态选择器（TODO）
                    previewMode = false;
                    previewColumn = -1;
                } else {
                    previewMode = true;
                    previewColumn = ci;
                }
                return true;
            }
        }

        // OP 按钮
        if (isOp && opMode) {
            for (int ci = 0; ci < 3; ci++) {
                if (hitOpButton(ci, mx, my, this::onOpAction)) return true;
            }
        }

        // 点击面板外部 → 关闭
        if (mx < panelLeft || mx > panelLeft + effectiveW || my < panelTop || my > panelTop + effectiveH) {
            dimDropdownOpen = false;
            playerDropdownOpen = false;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (dimDropdownOpen) {
            int maxScroll = Math.max(0, availableDims.size() - 6);
            dimDropdownScroll = Math.clamp(dimDropdownScroll - (int) Math.signum(scrollY), 0, maxScroll);
            return true;
        }
        if (playerDropdownOpen) {
            int maxScroll = Math.max(0, availablePlayers.size() - 6);
            playerDropdownScroll = Math.clamp(playerDropdownScroll - (int) Math.signum(scrollY), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ── 命中检测 ──

    private boolean hitClose(int mx, int my) {
        int x = panelLeft + effectiveW - 20;
        return mx >= x && mx < x + 14 && my >= panelTop + 4 && my < panelTop + 18;
    }

    private boolean hitOpToggle(int mx, int my) {
        if (!isOp) return false;
        Component opTag = Component.translatable(LangKeys.PHASE_OP_TAG);
        int opW = font.width(Component.literal(opTag.getString() + " 管理"));
        int closeX = panelLeft + effectiveW - 20;
        int opX = closeX - opW - 8;
        return mx >= opX && mx < opX + opW && my >= panelTop + 4 && my < panelTop + 17;
    }

    private boolean hitDimSelector(int mx, int my) {
        int x = colX(1) + 6;
        int y = panelTop + COL_Y_START + 16 + 10;
        return mx >= x && mx < x + colW - 12 && my >= y && my < y + 14;
    }

    private boolean hitPlayerSelector(int mx, int my) {
        int x = colX(2) + 6;
        int y = panelTop + COL_Y_START + 16 + 10;
        return mx >= x && mx < x + colW - 12 && my >= y && my < y + 14;
    }

    private boolean hitDimDropdown(int mx, int my, Consumer<String> callback) {
        int x = colX(1) + 6;
        int baseY = getDimDropdownY();
        int ddW = colW - 12;
        int rowH = 14;
        int total = availableDims.size();
        int vis = Math.min(6, total);
        for (int i = 0; i < vis; i++) {
            int idx = i + dimDropdownScroll;
            if (idx >= total) break;
            int ry = baseY + i * rowH;
            if (mx >= x && mx < x + ddW && my >= ry && my < ry + rowH) {
                callback.accept(availableDims.get(idx));
                dimDropdownOpen = false;
                return true;
            }
        }
        // 点击下拉区域外
        if (mx < x || mx > x + ddW || my < baseY - 2 || my > baseY + vis * rowH + 2) {
            dimDropdownOpen = false;
            return true;
        }
        return false;
    }

    private boolean hitPlayerDropdown(int mx, int my, Consumer<UUID> callback) {
        int x = colX(2) + 6;
        int baseY = getPlayerDropdownY();
        int ddW = colW - 12;
        int rowH = 14;
        int total = availablePlayers.size();
        int vis = Math.min(6, total);
        for (int i = 0; i < vis; i++) {
            int idx = i + playerDropdownScroll;
            if (idx >= total) break;
            int ry = baseY + i * rowH;
            if (mx >= x && mx < x + ddW && my >= ry && my < ry + rowH) {
                callback.accept(availablePlayers.get(idx));
                playerDropdownOpen = false;
                return true;
            }
        }
        if (mx < x || mx > x + ddW || my < baseY - 2 || my > baseY + vis * rowH + 2) {
            playerDropdownOpen = false;
            return true;
        }
        return false;
    }

    private boolean hitStateBox(int colIndex, int mx, int my) {
        int x = colX(colIndex);
        int y = getStateBoxY(colIndex);
        return mx >= x && mx < x + colW && my >= y && my < y + 20;
    }

    private int getStateBoxY(int colIndex) {
        int baseY = panelTop + COL_Y_START + 16;
        if (colIndex == 1 || colIndex == 2) baseY += 10 + 14 + 2 + 4; // 下拉选择器
        return baseY;
    }

    private boolean hitOpButton(int colIndex, int mx, int my, java.util.function.BiConsumer<Integer, String> callback) {
        int x = colX(colIndex);
        int y = panelTop + effectiveH - 88 + 4;
        int btnW = colW - 16;
        int btnX = x + 8;

        // 玩家列有 2 个按钮，世界/维列也是 2 个，再加一个新建
        String[] keys;
        if (colIndex == 2) {
            keys = new String[]{LangKeys.PHASE_APPLY_TEMP, LangKeys.PHASE_CLEAR_TEMP, LangKeys.PHASE_NEW_STATE};
        } else {
            keys = new String[]{LangKeys.PHASE_FORCE_TRANSITION, LangKeys.PHASE_EDIT_DEF, LangKeys.PHASE_NEW_STATE};
        }

        int curY = y;
        for (String key : keys) {
            if (mx >= btnX && mx < btnX + btnW && my >= curY && my < curY + BTN_H) {
                callback.accept(colIndex, key);
                return true;
            }
            curY += BTN_H + BTN_GAP;
        }
        return false;
    }

    // ── 交互回调 ──

    private void onDimSelected(String dimKey) {
        selectedDimKey = dimKey;
        refreshDimColumn();
    }

    private void onPlayerSelected(UUID pid) {
        selectedPlayer = pid;
        refreshPlayerColumn();
    }

    private void onOpAction(int colIndex, String langKey) {
        // TODO: 后端对接 —— 通过命令或网络包执行实际的状态变更操作
        Column col = getColumn(colIndex);
        if (langKey.equals(LangKeys.PHASE_FORCE_TRANSITION)) {
            showConfirmDialog(Component.translatable(LangKeys.PHASE_CONFIRM_TRANSITION, col.stateName));
        } else if (langKey.equals(LangKeys.PHASE_EDIT_DEF)) {
            // TODO: 打开状态编辑器
        } else if (langKey.equals(LangKeys.PHASE_NEW_STATE)) {
            // TODO: 新建状态
        } else if (langKey.equals(LangKeys.PHASE_APPLY_TEMP)) {
            // TODO: 打开临时状态选择器
        } else if (langKey.equals(LangKeys.PHASE_CLEAR_TEMP)) {
            String playerName = getPlayerDisplayName(selectedPlayer);
            showConfirmDialog(Component.translatable(LangKeys.PHASE_CONFIRM_CLEAR_TEMP, playerName));
        }
    }

    // ── 占位确认对话框 ──

    private void showConfirmDialog(Component msg) {
        // TODO: 替换为真实确认 UI
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(msg, false);
        }
    }

    // ── 数据刷新 ──

    private void refreshAllColumns() {
        refreshWorldColumn();
        refreshDimColumn();
        refreshPlayerColumn();
    }

    private void refreshDimensionList() {
        availableDims.clear();
        // 从 ClientDataStore / 注册表读取所有已知维度
        Set<String> dims = new LinkedHashSet<>();
        dims.add("minecraft:overworld");
        dims.add("minecraft:the_nether");
        dims.add("minecraft:the_end");

        // 尝试读取连接中的维度列表
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

        availableDims.addAll(dims);
        if (!availableDims.contains(selectedDimKey) && !availableDims.isEmpty()) {
            selectedDimKey = availableDims.get(0);
        }
    }

    private void refreshPlayerList() {
        availablePlayers.clear();
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            availablePlayers.add(mc.player.getUUID());
        }
        // TODO: 从 ClientDataStore 读取在线玩家列表
    }

    private void refreshWorldColumn() {
        colWorld.title = LangKeys.PHASE_WORLD;
        // TODO: 从 ClientDataStore 获取世界阶段数据
        colWorld.stateName = "宁静之时";
        colWorld.effects = List.of(
            new EffectRow(LangKeys.EFFECT_MOB_HEALTH,   1.0, false),
            new EffectRow(LangKeys.EFFECT_DAMAGE_RECV,  1.0, false),
            new EffectRow(LangKeys.EFFECT_SPAWN_RATE,   1.0, false)
        );
        colWorld.nextState = "初火燎原";
        colWorld.transitionCnd = "世界加入天数 ≥ 3";
    }

    private void refreshDimColumn() {
        colDim.title = LangKeys.PHASE_DIMENSION;
        // TODO: 从 ClientDataStore 获取 selectedDimKey 对应的维度阶段
        colDim.stateName = "[" + DisplayNameResolver.friendlyDimension(selectedDimKey) + "] 当前阶段";
        colDim.effects = List.of(
            new EffectRow(LangKeys.EFFECT_MOB_SPEED,    0.0,  false),
            new EffectRow(LangKeys.EFFECT_DAMAGE_DEALT, 1.0,  false)
        );
        colDim.nextState = null;
        colDim.transitionCnd = null;
    }

    private void refreshPlayerColumn() {
        colPlayer.title = LangKeys.PHASE_PLAYER;
        // TODO: 从 ClientDataStore 获取 selectedPlayer 的持久/临时阶段
        colPlayer.stateName = "个人阶段";
        colPlayer.effects = List.of(
            new EffectRow(LangKeys.EFFECT_MOB_ATTACK,   1.0, false),
            new EffectRow(LangKeys.EFFECT_MOB_ARMOR,    1.0, false)
        );
        colPlayer.tempState = null; // TODO
        colPlayer.history = "无历史记录"; // TODO
    }

    // ── 辅助方法 ──

    private int colX(int colIndex) {
        return panelLeft + PADDING_X + colIndex * (colW + COL_GAP);
    }

    private Column getColumn(int colIndex) {
        return switch (colIndex) {
            case 0  -> colWorld;
            case 1  -> colDim;
            case 2  -> colPlayer;
            default -> throw new IllegalArgumentException("Invalid column index: " + colIndex);
        };
    }

    private String getPlayerDisplayName(UUID pid) {
        if (pid == null) return "???";
        var mc = Minecraft.getInstance();
        if (mc.player != null && pid.equals(mc.player.getUUID())) {
            return mc.player.getGameProfile().getName();
        }
        // TODO: 从 ClientDataStore 解析玩家名称
        return pid.toString().substring(0, 8);
    }
}
