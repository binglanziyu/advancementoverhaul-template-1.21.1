package com.example.advancementoverhaul.client.gui.render;

/**
 * 覆盖层面板渲染器：绘制所有模态弹窗的视觉呈现。
 * <p>
 * 负责渲染详情面板、编辑面板、统计面板、上下文菜单、
 * 确认对话框、标签页管理面板等覆盖层 UI。
 * 每个覆盖层有独立的布局和渲染逻辑。
 */
import com.example.advancementoverhaul.LangKeys;
import com.example.advancementoverhaul.client.gui.AdvancementScreen;
import com.example.advancementoverhaul.client.gui.GuiUtils;
import com.example.advancementoverhaul.client.gui.TranslatedStrings;
import com.example.advancementoverhaul.client.gui.state.OverlayLayout;
import com.example.advancementoverhaul.data.ClientDataStore;
import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.DisplayNameResolver;
import com.example.advancementoverhaul.data.model.CustomAdvancement;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import com.example.advancementoverhaul.client.gui.ConditionTypeStyle;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.*;

import static com.example.advancementoverhaul.client.gui.Theme.*;

public class OverlayRenderer {

    private final AdvancementScreen screen;

    public OverlayRenderer(AdvancementScreen screen) { this.screen = screen; }

    // ═══════════════ TOOLTIP ═══════════════

    public void renderTooltip(GuiGraphics g, int mx, int my, String id) {
        Font font = screen.getFont();
        List<String> lines = new ArrayList<>();
        ClientDataStore cs = ClientDataStore.getInstance();
        boolean isVanilla = screen.isVanillaAdvId(id);
        CustomAdvancement adv = cs.getAdvancement(id);
        AdvancementScreen.VanillaAdv va = screen.getVanillaAdv(id);
        String name = isVanilla ? (va != null ? va.getLocalizedName() : id) : (adv != null ? adv.getName() : id);
        lines.add(name);
        if (isVanilla) {
            lines.add(TranslatedStrings.get(LangKeys.TIP_VANILLA_RO));
            if (va != null && va.getLocalizedDesc() != null && !va.getLocalizedDesc().isEmpty()) lines.add(va.getLocalizedDesc());
            lines.add(TranslatedStrings.get(cs.isVanillaEnabled(id) ? LangKeys.ADV_TT_ENABLED : LangKeys.ADV_TT_DISABLED));
        } else {
            if (adv != null) {
                if (adv.getDescription() != null && !adv.getDescription().isEmpty()) lines.add(adv.getDescription());
                lines.add(TranslatedStrings.get(cs.isCompleted(id) ? LangKeys.DETAIL_COMPLETED : LangKeys.DETAIL_NOT_COMPLETED));
                if (adv.getPrerequisites() != null && !adv.getPrerequisites().isEmpty()) {
                    List<String> pNames = new ArrayList<>();
                    for (String pid : adv.getPrerequisites()) pNames.add(screen.prereqDisplayName(pid));
                    lines.add(Component.translatable(LangKeys.DETAIL_PREREQ_PREFIX, String.join(", ", pNames)).getString());
                }
                if (adv.getTab() != null && !adv.getTab().isEmpty())
                    lines.add(Component.translatable(LangKeys.DETAIL_TAB_PREFIX, adv.getTab()).getString());
            }
        }
        // 自动换行：当单行超出屏幕宽度 60% 时按单词拆分
        int maxLineW = (int) (screen.getScreenWidth() * 0.6);
        List<String> wrappedLines = new ArrayList<>();
        for (String l : lines) {
            if (font.width(l) <= maxLineW) {
                wrappedLines.add(l);
            } else {
                StringBuilder current = new StringBuilder();
                for (String word : l.split(" ")) {
                    if (current.length() > 0 && font.width(current + " " + word) > maxLineW) {
                        wrappedLines.add(current.toString());
                        current = new StringBuilder(word);
                    } else {
                        if (current.length() > 0) current.append(" ");
                        current.append(word);
                    }
                }
                if (current.length() > 0) wrappedLines.add(current.toString());
            }
        }
        lines = wrappedLines;

        int maxW = 0; for (String l : lines) maxW = Math.max(maxW, font.width(l));
        int tw = maxW + 12, th = lines.size() * 12 + 8;

        // 优先显示在鼠标上方
        int tx = mx + 12, ty = my - 4 - th;
        // 上方空间不够时放右边
        if (ty < 4) ty = my + 16;
        // 右边溢出时移到左边
        if (tx + tw > screen.getScreenWidth()) tx = mx - tw - 4;
        // 下方溢出
        if (ty + th > screen.getScreenHeight()) ty = screen.getScreenHeight() - th - 4;
        if (ty < 4) ty = 4;
        // 左边溢出
        if (tx < 4) tx = 4;

        g.fill(tx - 2, ty - 2, tx + tw + 2, ty + th + 2, TOOLTIP_BG);
        g.renderOutline(tx - 2, ty - 2, tw + 4, th + 4, TOOLTIP_BORDER);
        for (int i = 0; i < lines.size(); i++)
            g.drawString(font, lines.get(i), tx + 4, ty + 4 + i * 12, i == 0 ? TEXT_BR : TEXT_DIM, false);
    }

    // ═══════════════ DETAIL ═══════════════

    private void renderDetail(GuiGraphics g, int mx, int my) {
        Font font = screen.getFont();
        int dw = OverlayLayout.DETAIL_W, dh = OverlayLayout.DETAIL_H;
        int dx = screen.mid(dw), dy = screen.midY(dh);

        // 限制面板不超出屏幕边界
        int screenH = screen.getScreenHeight();
        if (dy + dh > screenH - 8) dy = Math.max(8, screenH - dh - 8);
        if (dy < 8) { dy = 8; dh = Math.min(dh, screenH - 16); }

        // P3: 从上滑入动画（translateY -10→0, 150ms ease-out）
        int slideOffset = 0;
        if (screen.overlay.detailOpenTime > 0) {
            long elapsed = System.currentTimeMillis() - screen.overlay.detailOpenTime;
            if (elapsed < 150) {
                float t = 1f - (float) elapsed / 150f; // 1→0
                t = t * t; // ease-out quadratic
                slideOffset = (int) (-10 * t);
            }
        }

        g.pose().pushPose();
        g.pose().translate(0, slideOffset, 0);
        GuiUtils.drawPanelBg(g, font, dx, dy, dw, dh,
                TranslatedStrings.get(LangKeys.DETAIL_TITLE),
                screen.getScreenWidth(), screenH);
        ClientDataStore cs = ClientDataStore.getInstance();
        String id = screen.overlay.detailId;
        boolean isVanilla = screen.isVanillaAdvId(id);
        CustomAdvancement adv = cs.getAdvancement(id);
        AdvancementScreen.VanillaAdv va = screen.getVanillaAdv(id);
        int textW = dw - 28;

        // 内容区边界（底部预留 42px 给固定 ID 行）
        int contentTop = dy + OverlayLayout.DETAIL_CONTENT_TOP;
        int contentBottom = dy + dh - 42;
        int contentH = contentBottom - contentTop;

        // 计算每一行的内容（先不渲染，只收集行数据以确定总高度）
        java.util.List<DetailLine> lines = new java.util.ArrayList<>();

        String name = isVanilla ? (va != null ? va.getLocalizedName() : id) : (adv != null ? adv.getName() : id);
        lines.add(new DetailLine(name, TEXT_BR, 20));

        if (isVanilla) {
            if (va != null && va.getLocalizedDesc() != null)
                lines.add(new DetailLine(va.getLocalizedDesc(), TEXT, 16));
            boolean enabled = cs.isVanillaEnabled(id);
            lines.add(new DetailLine(TranslatedStrings.get(enabled ? LangKeys.ADV_TT_ENABLED : LangKeys.ADV_TT_DISABLED),
                    enabled ? 0xFF55FF55 : 0xFFFF5555, 16));
            lines.add(new DetailLine(TranslatedStrings.get(LangKeys.TIP_VANILLA_RO), TEXT_DIM, 24));
        } else if (adv != null) {
            if (adv.getDescription() != null)
                lines.add(new DetailLine(adv.getDescription(), TEXT, 16));
            boolean done = cs.isCompleted(id);
            lines.add(new DetailLine(TranslatedStrings.get(done ? LangKeys.DETAIL_COMPLETED : LangKeys.DETAIL_NOT_COMPLETED),
                    done ? 0xFF55FF55 : 0xFFFF5555, 16));
            if (adv.getTab() != null)
                lines.add(new DetailLine(Component.translatable(LangKeys.DETAIL_TAB_PREFIX, adv.getTab()).getString(), TEXT_DIM, 16));
            if (adv.getPrerequisites() != null) {
                for (String pid : adv.getPrerequisites())
                    lines.add(new DetailLine(Component.translatable(LangKeys.DETAIL_PREREQ_PREFIX, screen.prereqDisplayName(pid)).getString(), TEXT_DIM, 14));
            }
            if (adv.getConditions() != null && !adv.getConditions().isEmpty()) {
                lines.add(new DetailLine(TranslatedStrings.get(LangKeys.CONDITIONS), TEXT_BR, 16));
                for (var c : adv.getConditions()) {
                    String line = c.getType() != null ? ConditionTypeStyle.of(c.getType()).displayName() : "???";
                    String tgtName = DisplayNameResolver.resolve(c.getType(), c.getTargetId());
                    if (!tgtName.isEmpty()) line += ": " + tgtName;
                    line += " x" + c.getCount();
                    lines.add(new DetailLine(line, TEXT_DIM, 14, 10));
                }
            }
        }

        // 计算总内容高度
        int totalContentH = 0;
        for (DetailLine dl : lines) totalContentH += dl.height;

        // 滚动范围
        int maxScroll = Math.max(0, totalContentH - contentH + 8);
        if (screen.overlay.detailScrollOff > maxScroll) screen.overlay.detailScrollOff = maxScroll;
        if (screen.overlay.detailScrollOff < 0) screen.overlay.detailScrollOff = 0;

        // 渲染滚动条（如果需要的话）
        boolean needsScroll = maxScroll > 0;
        if (needsScroll) {
            int scrollBarX = dx + dw - 6;
            int scrollBarH = contentH;
            g.fill(scrollBarX, contentTop, scrollBarX + 4, contentBottom, 0xFF222238);
            double vRatio = maxScroll > 0 ? (double) screen.overlay.detailScrollOff / maxScroll : 0;
            double vSize = Math.max(0.08, (double) contentH / totalContentH);
            int thumbH = Math.max(16, (int) (vSize * scrollBarH));
            int thumbY = contentTop + (int) (vRatio * (scrollBarH - thumbH));
            g.fill(scrollBarX, thumbY, scrollBarX + 4, thumbY + thumbH, 0xFF6666BB);
        }

        // 渲染内容
        g.enableScissor(dx + 1, contentTop, dx + dw - 1, contentBottom);
        int ty = contentTop - screen.overlay.detailScrollOff;
        int indentBase = 14;
        for (DetailLine dl : lines) {
            if (ty + dl.height > contentTop && ty < contentBottom) {
                int ix = dx + indentBase + dl.indent;
                String disp = GuiUtils.truncate(font, dl.text, textW - dl.indent - (needsScroll ? 8 : 0));
                g.drawString(font, disp, ix, ty + 2, dl.color, false);
            }
            ty += dl.height;
        }
        g.disableScissor();

        // ── 底部固定区域：ID 行 + 内联复制按钮（不可滚动，始终可见） ──
        int infoY = contentBottom + 6;
        String idDisplay = Component.translatable(LangKeys.DETAIL_ID_PREFIX, id).getString();
        int idTextWidth = font.width(idDisplay);

        // 绘制 ID 文本
        g.drawString(font, idDisplay, dx + indentBase, infoY, TEXT_DIM, false);

        // 内联复制按钮（ID 右侧）
        String copyIcon = "\uD83D\uDCCB"; // 📋
        int iconW = font.width(copyIcon);
        int btnX = dx + indentBase + idTextWidth + 6;
        int btnY = infoY - 1;
        int btnW = iconW + 6;
        int btnH = 15;
        boolean copyHov = GuiUtils.inRect(mx, my, btnX, btnY, btnW, btnH);
        boolean showCopied = screen.overlay.detailCopyTime > 0
                && System.currentTimeMillis() - screen.overlay.detailCopyTime < 1500;

        g.fill(btnX, btnY, btnX + btnW, btnY + btnH, copyHov ? BTN_HOV : 0xFF2E2E42);
        g.renderOutline(btnX, btnY, btnW, btnH, copyHov ? ACCENT : DIVIDER);
        int iconColor = showCopied ? ACCENT : (copyHov ? TEXT_BR : TEXT);
        g.drawString(font, showCopied ? "\u2713" : copyIcon, btnX + 3, btnY + 1, iconColor, false);

        // 存储内联按钮坐标供点击检测
        screen.overlay.detailInlineCopyX = btnX;
        screen.overlay.detailInlineCopyY = btnY;
        screen.overlay.detailInlineCopyW = btnW;
        screen.overlay.detailInlineCopyH = btnH;

        // 右侧图标
        if (!isVanilla && adv != null && adv.getIcon() != null) {
            var rl = ResourceLocation.tryParse(adv.getIcon());
            if (rl != null) {
                var item = BuiltInRegistries.ITEM.get(rl);
                if (item != null) g.renderItem(new ItemStack(item), dx + dw - 44, dy + 30);
            }
        } else if (isVanilla && va != null && va.icon() != null) {
            var rl = ResourceLocation.tryParse(va.icon());
            if (rl != null) {
                var item = BuiltInRegistries.ITEM.get(rl);
                if (item != null) g.renderItem(new ItemStack(item), dx + dw - 44, dy + 30);
            }
        }

        g.pose().popPose(); // P3: end slide animation scope
    }

    /** 详情面板中的一行渲染数据 */
    private static class DetailLine {
        final String text;
        final int color;
        final int height;
        final int indent;
        DetailLine(String text, int color, int height) { this(text, color, height, 0); }
        DetailLine(String text, int color, int height, int indent) {
            this.text = text; this.color = color; this.height = height; this.indent = indent;
        }
    }

    // ═══════════════ EDITOR ═══════════════

    private void renderEditor(GuiGraphics g, int mx, int my) {
        Font font = screen.getFont();
        screen.editPanel.render(g, mx, my, font, screen.getScreenWidth(), screen.getScreenHeight());
    }

    // ═══════════════ STATS ═══════════════

    /** 统计面板的实际渲染高度（不超过屏幕边界），供点击/关闭判断与渲染保持一致 */
    public static int statsActualH = OverlayLayout.STATS_H;
    /** 统计面板渲染坐标（供滚动条拖拽使用） */
    public static int statsPanelX, statsPanelY, statsPanelW, statsContentTop, statsContentBottom;
    /** 统计面板滚动条拖拽状态 */
    public static boolean statsScrollDrag = false;
    /** 统计面板最大滚动值 */
    public static int statsMaxScroll = 0;

    private void renderStats(GuiGraphics g, int mx, int my) {
        Font font = screen.getFont();
        int sw = OverlayLayout.STATS_W;
        int screenH = screen.getScreenHeight();

        ClientDataStore cs = ClientDataStore.getInstance();
        int total = cs.getTotalCount();
        int done = cs.getCompletedCount();
        double rate = total > 0 ? (done * 100.0 / total) : 0;
        List<String> lines = new ArrayList<>();
        lines.add(TranslatedStrings.get(LangKeys.STAT_CUSTOM) + ": " + total);
        lines.add(TranslatedStrings.get(LangKeys.STAT_DONE) + ": " + done);
        lines.add(TranslatedStrings.get(LangKeys.STAT_RATE) + ": " + String.format("%.1f%%", rate));
        int vanillaTotal = 0, vanillaDone = 0;
        for (var va : cs.getVanillaAdvancements()) {
            if (cs.isVanillaEnabled(va.id())) {
                vanillaTotal++;
                if (cs.isCompleted(va.id())) vanillaDone++;
            }
        }
        lines.add(TranslatedStrings.get(LangKeys.STAT_VANILLA) + ": " + vanillaDone + "/" + vanillaTotal);
        lines.add("");
        lines.add(TranslatedStrings.get(LangKeys.STAT_TAB_PROG));
        for (String tab : cs.getTabs()) {
            int tabT = cs.getTabTotalCount(tab);
            int tabD = cs.getTabCompletedCount(tab);
            if (tabT > 0) lines.add("  " + tab + ": " + tabD + "/" + tabT);
        }

        // 动态面板高度：至少 STATS_H，最多不超过屏幕边缘
        int sh = Math.min(OverlayLayout.STATS_H, screenH - 16);
        statsActualH = sh;

        int sx = screen.mid(sw);
        int sy = Math.max(8, (screenH - sh) / 2);
        if (sy + sh > screenH - 8) sy = Math.max(8, screenH - sh - 8);

        // 存储面板坐标供滚动条拖拽使用
        statsPanelX = sx;
        statsPanelY = sy;
        statsPanelW = sw;
        int contentTop = sy + 28;
        int contentBottom = sy + sh - 4;
        statsContentTop = contentTop;
        statsContentBottom = contentBottom;

        GuiUtils.drawPanelBg(g, font, sx, sy, sw, sh,
                TranslatedStrings.get(LangKeys.STATISTICS),
                screen.getScreenWidth(), screenH);

        // 计算内容总高度和滚动范围
        int contentH = lines.size() * 16 + 8;
        int availH = contentBottom - contentTop;
        int maxScroll = Math.max(0, contentH - availH);
        statsMaxScroll = maxScroll;
        if (screen.overlay.statsScrollOff > maxScroll) screen.overlay.statsScrollOff = maxScroll;
        if (screen.overlay.statsScrollOff < 0) screen.overlay.statsScrollOff = 0;

        // 渲染滚动条
        boolean needsScroll = maxScroll > 0;
        if (needsScroll) {
            int scrollBarX = sx + sw - 6;
            int scrollBarH = availH;
            g.fill(scrollBarX, contentTop, scrollBarX + 4, contentBottom, 0xFF222238);
            double vRatio = maxScroll > 0 ? (double) screen.overlay.statsScrollOff / maxScroll : 0;
            double vSize = Math.max(0.08, (double) availH / contentH);
            int thumbH = Math.max(16, (int) (vSize * scrollBarH));
            int thumbY = contentTop + (int) (vRatio * (scrollBarH - thumbH));
            g.fill(scrollBarX, thumbY, scrollBarX + 4, thumbY + thumbH, 0xFF6666BB);
        }

        // 渲染内容
        g.enableScissor(sx + 2, contentTop, sx + sw - 2, contentBottom);
        int lineY = contentTop - screen.overlay.statsScrollOff;
        for (String line : lines) {
            if (lineY + 14 >= contentTop && lineY < contentBottom)
                g.drawString(font, line, sx + 14, lineY, TEXT, false);
            lineY += 16;
        }
        g.disableScissor();
    }

    public int calcStatsMaxScroll() {
        int lineCount = 5 + ClientDataStore.getInstance().getTabs().size();
        int contentH = lineCount * 16 + 8;
        int availH = (statsContentBottom > statsContentTop) ? (statsContentBottom - statsContentTop) : 230;
        return Math.max(0, contentH - availH);
    }

    // ═══════════════ CTX ═══════════════

    private void renderCtx(GuiGraphics g, int mx, int my) {
        Font font = screen.getFont();
        int cx = screen.overlay.ctxX, cy = screen.overlay.ctxY;
        int mw = OverlayLayout.CTX_ITEM_W;
        int mh = screen.overlay.ctxActions.size() * OverlayLayout.CTX_ITEM_H + OverlayLayout.CTX_PAD * 2;
        if (cx + mw > screen.getScreenWidth()) cx = screen.getScreenWidth() - mw - 2;
        if (cy + mh > screen.getScreenHeight()) cy = screen.getScreenHeight() - mh - 2;
        // 完全不透明确保右键菜单不穿透看到画布内容
        g.fill(cx, cy, cx + mw, cy + mh, 0xFF2E2E42);
        g.renderOutline(cx, cy, mw, mh, ACCENT);
        for (int i = 0; i < screen.overlay.ctxActions.size(); i++) {
            int iy = cy + OverlayLayout.CTX_PAD + i * OverlayLayout.CTX_ITEM_H;
            boolean hov = GuiUtils.inRect(mx, my, cx + 2, iy, mw - 4, OverlayLayout.CTX_ITEM_H - 2);
            if (hov) {
                g.fill(cx + 2, iy, cx + mw - 2, iy + OverlayLayout.CTX_ITEM_H - 2, CTX_HOV);
                // P2: 左侧 2px accent 色竖条作为选中指示符
                g.fill(cx + 2, iy, cx + 2 + CTX_ACCENT_W, iy + OverlayLayout.CTX_ITEM_H - 2, ACCENT);
            }
            g.drawString(font, screen.overlay.ctxActions.get(i).label(), cx + 8, iy + 4, TEXT, false);
        }
    }

    // ═══════════════ CONFIRM ═══════════════

    private void renderConfirm(GuiGraphics g, int mx, int my) {
        Font font = screen.getFont();
        int cw = OverlayLayout.CONFIRM_W, ch = OverlayLayout.CONFIRM_H;
        int cx = screen.mid(cw), cy = screen.midY(ch);
        g.fill(0, 0, screen.getScreenWidth(), screen.getScreenHeight(), 0x80000000);
        g.fill(cx, cy, cx + cw, cy + ch, PANEL);
        g.renderOutline(cx, cy, cw, ch, PINK);
        g.drawString(font, screen.overlay.confirmText, cx + 14, cy + 14, TEXT_BR, false);
        int btnY = cy + ch - OverlayLayout.CONFIRM_BTN_BOTTOM;
        GuiUtils.drawSmallBtn(g, font, cx + cw - 170, btnY, OverlayLayout.CONFIRM_BTN_W,
                TranslatedStrings.get(LangKeys.CONFIRM),
                GuiUtils.inRect(mx, my, cx + cw - 170, btnY, OverlayLayout.CONFIRM_BTN_W, OverlayLayout.CONFIRM_BTN_H));
        GuiUtils.drawSmallBtn(g, font, cx + cw - 88, btnY, OverlayLayout.CONFIRM_BTN_W,
                TranslatedStrings.get(LangKeys.CANCEL),
                GuiUtils.inRect(mx, my, cx + cw - 88, btnY, OverlayLayout.CONFIRM_BTN_W, OverlayLayout.CONFIRM_BTN_H));
    }

    // ═══════════════ TAB INPUT ═══════════════

    private void renderTabInput(GuiGraphics g, int mx, int my) {
        Font font = screen.getFont();
        int pw = OverlayLayout.TAB_INPUT_W, ph = OverlayLayout.TAB_INPUT_H;
        int px = screen.mid(pw), py = screen.midY(ph);

        // 全屏暗色遮罩由 AdvancementScreen 统一管理，此处仅绘制面板自身
        g.fill(px, py, px + pw, py + ph, PANEL);
        g.renderOutline(px, py, pw, ph, DIVIDER);
        g.fill(px, py, px + pw, py + 3, ACCENT);
        g.drawString(font, TranslatedStrings.get(LangKeys.NEW_TAB), px + OverlayLayout.TAB_INPUT_INNER_PAD, py + 10, TEXT_BR, false);
        boolean ch = GuiUtils.closeHit(mx, my, px, py, pw);
        g.drawString(font, "\u2715", px + pw - 16, py + 10, ch ? TEXT_BR : TEXT_DIM, false);
        g.fill(px + 10, py + 60, px + pw - 10, py + 61, DIVIDER);
        boolean saveHov = GuiUtils.inRect(mx, my,
                px + pw - OverlayLayout.TAB_INPUT_OK_RIGHT, py + OverlayLayout.TAB_INPUT_BTN_Y,
                OverlayLayout.TAB_INPUT_BTN_W, OverlayLayout.TAB_INPUT_BTN_H);
        GuiUtils.drawSmallBtn(g, font,
                px + pw - OverlayLayout.TAB_INPUT_OK_RIGHT, py + OverlayLayout.TAB_INPUT_BTN_Y,
                OverlayLayout.TAB_INPUT_BTN_W, TranslatedStrings.get(LangKeys.SAVE), saveHov);
        boolean cancelHov = GuiUtils.inRect(mx, my,
                px + pw - OverlayLayout.TAB_INPUT_CANCEL_RIGHT, py + OverlayLayout.TAB_INPUT_BTN_Y,
                OverlayLayout.TAB_INPUT_BTN_W, OverlayLayout.TAB_INPUT_BTN_H);
        GuiUtils.drawSmallBtn(g, font,
                px + pw - OverlayLayout.TAB_INPUT_CANCEL_RIGHT, py + OverlayLayout.TAB_INPUT_BTN_Y,
                OverlayLayout.TAB_INPUT_BTN_W, TranslatedStrings.get(LangKeys.CANCEL), cancelHov);
    }

    // ═══════════════ TAB MANAGE ═══════════════

    private static final int TAB_MANAGE_W = 320;
    private static final int TAB_MANAGE_ROW_H = 26;

    private void renderTabManage(GuiGraphics g, int mx, int my) {
        Font font = screen.getFont();
        ClientDataStore cs = ClientDataStore.getInstance();
        List<String> customTabs = cs.getCustomTabs();

        int maxH = screen.getScreenHeight() - 80;
        int contentH = customTabs.size() * TAB_MANAGE_ROW_H + 50;
        int ph = Math.min(maxH, Math.max(120, contentH));
        int pw = TAB_MANAGE_W;
        int px = screen.mid(pw), py = screen.midY(ph);

        g.fill(0, 0, screen.getScreenWidth(), screen.getScreenHeight(), 0x80000000);

        GuiUtils.drawPanelBg(g, font, px, py, pw, ph,
                Component.translatable(LangKeys.TAB_MANAGE_TITLE).getString(),
                screen.getScreenWidth(), screen.getScreenHeight());

        int ty = py + 30;
        g.enableScissor(px + 1, py + 28, px + pw - 1, py + ph - 2);

        if (customTabs.isEmpty()) {
            g.drawString(font, Component.translatable(LangKeys.TAB_MANAGE_EMPTY).getString(),
                    px + 14, ty + 8, TEXT_DIM, false);
        } else {
            for (int i = 0; i < customTabs.size(); i++) {
                String tab = customTabs.get(i);
                if (ty + TAB_MANAGE_ROW_H > py + ph) break;

                int advCount = 0;
                int vanillaCount = 0;
                for (var adv : cs.getAdvancements().values())
                    if (tab.equals(adv.getTab())) advCount++;
                for (var meta : cs.getVanillaMeta().values())
                    if (tab.equals(meta.getTab())) vanillaCount++;

                String countStr = advCount + (vanillaCount > 0 ? "+" + vanillaCount : "");
                String label = tab + " (" + countStr + ")";

                boolean rowHov = GuiUtils.inRect(mx, my, px + 10, ty, pw - 40, TAB_MANAGE_ROW_H - 2);
                if (rowHov) g.fill(px + 10, ty, px + pw - 10, ty + TAB_MANAGE_ROW_H - 2, BTN_HOV);

                g.drawString(font, GuiUtils.truncate(font, label, pw - 56),
                        px + 14, ty + 7, TEXT, false);

                boolean delHov = GuiUtils.inRect(mx, my, px + pw - 30, ty, 20, TAB_MANAGE_ROW_H - 2);
                g.drawString(font, "\u2715", px + pw - 24, ty + 7, delHov ? PINK : TEXT_DIM, false);

                ty += TAB_MANAGE_ROW_H;
            }
        }

        g.disableScissor();

        boolean closeHov = GuiUtils.closeHit(mx, my, px, py, pw);
        g.drawString(font, "\u2715", px + pw - 16, py + 10, closeHov ? TEXT_BR : TEXT_DIM, false);
    }

    // ═══════════════ HELP PANEL ═══════════════

    private static final int HELP_MAX_H = 370;
    private static final int HELP_SCREEN_MARGIN = 28;
    private static final int HELP_PAD = 12;
    private static final int HELP_TITLE_H = 22;

    private int helpScroll = 0;
    private int helpMaxScroll = 0;
    private int helpPx, helpPy, helpPw, helpPh;

    /**
     * 帮助面板：列出本模组所有指令及其作用。
     * 采用自适应宽度 + 自动换行布局，在不同屏幕尺寸下保持紧凑美观。
     */
    public void renderHelp(GuiGraphics g, int mx, int my, Font font, int screenW, int screenH) {
        // 动态宽度：屏幕宽度的 62%，限制在 320~420 之间
        int pw = (int) Math.clamp(screenW * 0.62, 320, 420);
        int maxTextW = pw - HELP_PAD * 2 - 8; // 预留滚动条空间

        List<HelpSection> sections = buildHelpSections();

        // --- 预计算所有渲染行（含自动换行）---
        List<RenderLine> lines = new ArrayList<>();
        for (HelpSection sec : sections) {
            lines.add(new RenderLine(sec.header, ACCENT, 18));
            for (HelpCmd cmd : sec.commands) {
                lines.add(new RenderLine(cmd.cmd, TEXT_BR, 13));
                List<String> wrapped = wrapText(font, cmd.desc, maxTextW - 10);
                for (String w : wrapped)
                    lines.add(new RenderLine(w, TEXT_DIM, 12, 10));
                lines.add(new RenderLine(null, 0, 3)); // 命令间隙
            }
            lines.add(new RenderLine(null, 0, 2)); // 分组间隙
        }

        int contentH = 0;
        for (RenderLine rl : lines) contentH += rl.height;

        int ph = Math.min(HELP_MAX_H, Math.max(160, contentH + HELP_TITLE_H + HELP_PAD * 2));
        // 确保面板不超出屏幕高度（小窗口适配）
        ph = Math.min(ph, screenH - HELP_SCREEN_MARGIN);
        int px = (screenW - pw) / 2;
        int py = Math.max(HELP_SCREEN_MARGIN / 2, (screenH - ph) / 2);

        // 存储当前尺寸供 InputManager 使用
        helpPx = px; helpPy = py; helpPw = pw; helpPh = ph;

        int contentAreaH = ph - HELP_TITLE_H;
        int maxScroll = Math.max(0, contentH + HELP_PAD - contentAreaH);
        helpMaxScroll = maxScroll;
        if (helpScroll > maxScroll) helpScroll = maxScroll;
        if (helpScroll < 0) helpScroll = 0;

        // --- 背景（遮罩由 renderBackgroundMask 提供，此处仅画面板自身） ---
        g.fill(px, py, px + pw, py + ph, PANEL);
        g.renderOutline(px, py, pw, ph, DIVIDER);
        g.fill(px, py, px + pw, py + 3, ACCENT);

        // --- 标题栏 ---
        String title = Component.translatable(LangKeys.HELP_TITLE).getString();
        g.drawString(font, title, px + HELP_PAD, py + (HELP_TITLE_H - 8) / 2 + 1, TEXT_BR, false);
        boolean closeHov = GuiUtils.inRect(mx, my, px + pw - 18, py + 3, 15, HELP_TITLE_H - 3);
        g.drawString(font, "\u2715", px + pw - 16, py + (HELP_TITLE_H - 8) / 2 + 1, closeHov ? TEXT_BR : TEXT_DIM, false);

        // --- 可滚动内容区 ---
        int scrollBarW = maxScroll > 0 ? 5 : 0;
        int textAreaW = pw - HELP_PAD * 2 - scrollBarW - 2;
        g.enableScissor(px + 1, py + HELP_TITLE_H, px + pw - 1 - scrollBarW, py + ph - 1);
        int rowY = py + HELP_TITLE_H + HELP_PAD - helpScroll;

        for (RenderLine rl : lines) {
            if (rowY + rl.height > py + HELP_TITLE_H && rowY < py + ph) {
                if (rl.color == ACCENT) {
                    // 分组标题居中
                    int tw = font.width(rl.text);
                    g.drawString(font, rl.text, px + (pw - tw) / 2, rowY + 1, ACCENT, false);
                } else if (rl.text != null) {
                    g.drawString(font, rl.text, px + HELP_PAD + rl.indent, rowY, rl.color, false);
                }
            }
            rowY += rl.height;
        }

        g.disableScissor();

        // --- 滚动条 ---
        if (maxScroll > 0) {
            int sbX = px + pw - scrollBarW - 3;
            int sbY = py + HELP_TITLE_H + 2;
            int sbH = contentAreaH - 4;
            int sbTrackH = contentH + HELP_PAD;
            // 滚动条轨道
            g.fill(sbX, sbY, sbX + scrollBarW, sbY + sbH, 0xFF1A1A2E);
            // 滚动条滑块
            int thumbH = Math.max(16, (int) ((long) sbH * sbH / sbTrackH));
            int thumbY = sbY + (int) ((long) helpScroll * (sbH - thumbH) / maxScroll);
            g.fill(sbX, thumbY, sbX + scrollBarW, thumbY + thumbH, 0xFF555577);
        }
    }

    /** 将文本按像素宽度拆分为多行（按单词换行，支持 CJK 逐字换行）。 */
    private static List<String> wrapText(Font font, String text, int maxWidth) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;
        // 拆分：空格保留为独立 token，CJK/全角字符单独拆分
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                if (current.length() > 0) { tokens.add(current.toString()); current.setLength(0); }
                tokens.add(" ");
            } else if (isCJKOrFullwidth(c)) {
                if (current.length() > 0) { tokens.add(current.toString()); current.setLength(0); }
                tokens.add(String.valueOf(c));
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) tokens.add(current.toString());

        StringBuilder line = new StringBuilder();
        for (String token : tokens) {
            if (" ".equals(token)) {
                if (line.length() > 0 && font.width(line.toString() + " ") > maxWidth) {
                    result.add(line.toString());
                    line.setLength(0);
                } else if (line.length() > 0) {
                    line.append(" ");
                }
                continue;
            }
            String candidate = line.length() > 0 ? line + token : token;
            if (font.width(candidate) > maxWidth) {
                if (line.length() > 0) {
                    result.add(line.toString());
                    line = new StringBuilder(token);
                } else {
                    // 单个 token 超过整行宽度（极少见，但安全兜底）
                    result.add(token);
                }
            } else {
                if (line.length() > 0) line.append(token);
                else line = new StringBuilder(token);
            }
        }
        if (line.length() > 0) result.add(line.toString());
        return result;
    }

    /** 判断是否为 CJK 字符或全角标点（需要逐字换行的字符）。 */
    private static boolean isCJKOrFullwidth(char c) {
        return Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
            || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
            || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
            || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
            || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
            || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
            || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.HIRAGANA
            || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.KATAKANA
            || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.HANGUL_SYLLABLES
            || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.HANGUL_JAMO
            || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO;
    }

    /** 帮助面板渲染行描述符。 */
    private record RenderLine(String text, int color, int height, int indent) {
        RenderLine(String text, int color, int height) { this(text, color, height, 0); }
    }

    private List<HelpSection> buildHelpSections() {
        if (!helpSectionsCache.isEmpty()) return helpSectionsCache;

        List<HelpSection> sections = new ArrayList<>();

        // ── 画布操作 ──
        List<HelpCmd> canvas = new ArrayList<>();
        canvas.add(new HelpCmd("L-click", Component.translatable("advancementoverhaul.ui.help_lclick").getString()));
        canvas.add(new HelpCmd("L-hold drag", Component.translatable("advancementoverhaul.ui.help_boxsel").getString()));
        canvas.add(new HelpCmd("R-click blank", Component.translatable("advancementoverhaul.ui.help_rclick").getString()));
        canvas.add(new HelpCmd("Scroll", Component.translatable("advancementoverhaul.ui.help_scroll").getString()));
        sections.add(new HelpSection(Component.translatable(LangKeys.HELP_CANVAS).getString(), canvas));

        // ── 进度编辑命令 ──
        List<HelpCmd> advCmd = new ArrayList<>();
        advCmd.add(new HelpCmd("/adv createjson", Component.translatable("advancementoverhaul.ui.help_createjson").getString()));
        advCmd.add(new HelpCmd("/adv updatejson", Component.translatable("advancementoverhaul.ui.help_updatejson").getString()));
        advCmd.add(new HelpCmd("/adv delete", Component.translatable("advancementoverhaul.ui.help_delete").getString()));
        advCmd.add(new HelpCmd("/adv batchdelete", Component.translatable("advancementoverhaul.ui.help_batchdelete").getString()));
        advCmd.add(new HelpCmd("/adv setname", Component.translatable("advancementoverhaul.ui.help_setname").getString()));
        advCmd.add(new HelpCmd("/adv setdescription", Component.translatable("advancementoverhaul.ui.help_setdesc").getString()));
        advCmd.add(new HelpCmd("/adv seticon", Component.translatable("advancementoverhaul.ui.help_seticon").getString()));
        advCmd.add(new HelpCmd("/adv togglehidden", Component.translatable("advancementoverhaul.ui.help_togglehidden").getString()));
        advCmd.add(new HelpCmd("/adv setprereq", Component.translatable("advancementoverhaul.ui.help_setprereq").getString()));
        sections.add(new HelpSection(Component.translatable(LangKeys.HELP_ADV_EDIT).getString(), advCmd));

        // ── 进度管理命令 ──
        List<HelpCmd> mgmt = new ArrayList<>();
        mgmt.add(new HelpCmd("/adv complete", Component.translatable("advancementoverhaul.ui.help_complete").getString()));
        mgmt.add(new HelpCmd("/adv reset", Component.translatable("advancementoverhaul.ui.help_reset").getString()));
        mgmt.add(new HelpCmd("/adv give", Component.translatable("advancementoverhaul.ui.help_give").getString()));
        mgmt.add(new HelpCmd("/adv revoke", Component.translatable("advancementoverhaul.ui.help_revoke").getString()));
        mgmt.add(new HelpCmd("/adv check", Component.translatable("advancementoverhaul.ui.help_check").getString()));
        mgmt.add(new HelpCmd("/adv reload", Component.translatable("advancementoverhaul.ui.help_reload").getString()));
        sections.add(new HelpSection(Component.translatable(LangKeys.HELP_MANAGEMENT).getString(), mgmt));

        // ── 标签管理 ──
        List<HelpCmd> tabs = new ArrayList<>();
        tabs.add(new HelpCmd("/adv tab add", Component.translatable("advancementoverhaul.ui.help_tab_add").getString()));
        tabs.add(new HelpCmd("/adv tab delete", Component.translatable("advancementoverhaul.ui.help_tab_delete").getString()));
        tabs.add(new HelpCmd("/adv tab order", Component.translatable("advancementoverhaul.ui.help_tab_order").getString()));
        sections.add(new HelpSection(Component.translatable(LangKeys.HELP_TABS).getString(), tabs));

        // ── 维度管理 ──
        List<HelpCmd> dims = new ArrayList<>();
        dims.add(new HelpCmd("/adv dimension lock", Component.translatable("advancementoverhaul.ui.help_dim_lock").getString()));
        dims.add(new HelpCmd("/adv dimension unlock", Component.translatable("advancementoverhaul.ui.help_dim_unlock").getString()));
        dims.add(new HelpCmd("/adv dim setcondition", Component.translatable("advancementoverhaul.ui.help_dim_cond").getString()));
        sections.add(new HelpSection(Component.translatable(LangKeys.HELP_DIMENSIONS).getString(), dims));

        // ── 原版进度 ──
        List<HelpCmd> va = new ArrayList<>();
        va.add(new HelpCmd("/adv vanilla enable", Component.translatable("advancementoverhaul.ui.help_va_enable").getString()));
        va.add(new HelpCmd("/adv vanilla disable", Component.translatable("advancementoverhaul.ui.help_va_disable").getString()));
        va.add(new HelpCmd("/adv vanilla enableall", Component.translatable("advancementoverhaul.ui.help_va_enableall").getString()));
        va.add(new HelpCmd("/adv vanilla disableall", Component.translatable("advancementoverhaul.ui.help_va_disableall").getString()));
        va.add(new HelpCmd("/adv vanilla setpos", Component.translatable("advancementoverhaul.ui.help_va_setpos").getString()));
        va.add(new HelpCmd("/adv vanilla settab", Component.translatable("advancementoverhaul.ui.help_va_settab").getString()));
        va.add(new HelpCmd("/adv vanilla cleartab", Component.translatable("advancementoverhaul.ui.help_va_cleartab").getString()));
        va.add(new HelpCmd("/adv vanilla save", Component.translatable("advancementoverhaul.ui.help_va_save").getString()));
        sections.add(new HelpSection(Component.translatable(LangKeys.HELP_VANILLA).getString(), va));

        helpSectionsCache = sections;
        return sections;
    }

    private List<HelpSection> helpSectionsCache = new ArrayList<>();

    private record HelpSection(String header, List<HelpCmd> commands) {}
    private record HelpCmd(String cmd, String desc) {}

    public int getHelpScroll() { return helpScroll; }
    public void setHelpScroll(int s) { this.helpScroll = s; }
    public void resetHelpScroll() { this.helpScroll = 0; }
    public int getHelpMaxScroll() { return helpMaxScroll; }
    public int getHelpPx() { return helpPx; }
    public int getHelpPy() { return helpPy; }
    public int getHelpPw() { return helpPw; }
    public int getHelpPh() { return helpPh; }

    // ═══════════════ RENDER DISPATCH ═══════════════

    public void renderOv(GuiGraphics g, int mx, int my) {
        switch (screen.overlay.current) {
            case DETAIL: renderDetail(g, mx, my); break;
            case CREATE: case EDIT: renderEditor(g, mx, my); break;
            case STATS: renderStats(g, mx, my); break;
            case CTX: renderCtx(g, mx, my); break;
            case CONFIRM: renderConfirm(g, mx, my); break;
            case TAB_INPUT: renderTabInput(g, mx, my); break;
            case TAB_MANAGE: renderTabManage(g, mx, my); break;
            default: break;
        }
    }

    // ═══════════════ TOASTS ═══════════════

    public void renderToasts(GuiGraphics g) {
        Font font = screen.getFont();
        var iter = screen.anim.toasts.iterator();
        // P3: 底部居中布局，自下而上堆叠
        int baseY = screen.getScreenHeight() - 40;
        int spacing = 26;
        int idx = 0;
        while (iter.hasNext()) {
            com.example.advancementoverhaul.client.gui.state.AnimState.Toast t = iter.next();
            if (t.expired()) { iter.remove(); continue; }
            int a = t.alpha();
            if (a <= 0) continue;
            int col = (a << 24) | 0x00FFDD57;
            String text = "\u2713 " + t.name;
            int tw = font.width(text) + 16;
            int tx = (screen.getScreenWidth() - tw) / 2;
            int ty = baseY - idx * spacing;
            g.fill(tx, ty, tx + tw, ty + 22, (a << 24) | 0x001A1A2E);
            g.renderOutline(tx, ty, tw, 22, col);
            g.drawString(font, text, tx + 8, ty + 5, col, false);
            idx++;
        }
    }
}