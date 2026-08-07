package com.dreamer.ao.client.gui.render;

import com.dreamer.ao.LangKeys;
import com.dreamer.ao.client.gui.AdvancementScreen;
import com.dreamer.ao.client.gui.ConditionTypeStyle;
import com.dreamer.ao.client.gui.GuiUtils;
import com.dreamer.ao.client.gui.TranslatedStrings;
import com.dreamer.ao.client.gui.state.OverlayLayout;
import com.dreamer.ao.data.ClientDataStore;
import com.dreamer.ao.data.DisplayNameResolver;
import com.dreamer.ao.data.model.CustomAdvancement;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import static com.dreamer.ao.client.gui.Theme.*;

/**
 * 成就详情面板渲染器。
 * <p>
 * 包含详情弹出面板的布局计算、内容渲染（名称、描述、条件列表）、
 * 滑动动画、滚动条、底部固定 ID 行与内联复制按钮。
 * <p>
 * 从 {@link OverlayRenderer} 拆分而来。
 */
final class DetailPanelRenderer {

    private final AdvancementScreen screen;

    DetailPanelRenderer(AdvancementScreen screen) {
        this.screen = screen;
    }

    /**
     * 渲染详情面板并返回当前帧的最大滚动值（供外部 InputManager 读取）。
     */
    int render(GuiGraphics g, int mx, int my) {
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
                float t = 1f - (float) elapsed / 150f;
                t = t * t;
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

        int contentTop = dy + OverlayLayout.DETAIL_CONTENT_TOP;
        int contentBottom = dy + dh - 42;
        int contentH = contentBottom - contentTop;

        java.util.List<DetailLine> lines = new java.util.ArrayList<>();

        boolean hiddenUncompleted = !isVanilla && adv != null && adv.isHidden() && !cs.isCompleted(id);
        String name = isVanilla ? (va != null ? va.getLocalizedName() : id)
                : (hiddenUncompleted ? TranslatedStrings.get(LangKeys.HIDDEN_LOCKED)
                        : (adv != null ? adv.getName() : id));
        lines.add(new DetailLine(name, TEXT_BR, 20));

        if (isVanilla) {
            if (va != null && va.getLocalizedDesc() != null)
                lines.add(new DetailLine(va.getLocalizedDesc(), TEXT, 16));
            boolean enabled = cs.isVanillaEnabled(id);
            lines.add(new DetailLine(TranslatedStrings.get(enabled ? LangKeys.ADV_TT_ENABLED : LangKeys.ADV_TT_DISABLED),
                    enabled ? 0xFF55FF55 : 0xFFFF5555, 16));
            lines.add(new DetailLine(TranslatedStrings.get(LangKeys.TIP_VANILLA_RO), TEXT_DIM, 24));
        } else if (adv != null) {
            if (hiddenUncompleted) {
                lines.add(new DetailLine(TranslatedStrings.get(LangKeys.HIDDEN), TEXT_DIM, 16));
            } else {
                if (adv.getDescription() != null)
                    lines.add(new DetailLine(adv.getDescription(), TEXT, 16));
                boolean done = cs.isCompleted(id);
                lines.add(new DetailLine(TranslatedStrings.get(done ? LangKeys.DETAIL_COMPLETED : LangKeys.DETAIL_NOT_COMPLETED),
                        done ? 0xFF55FF55 : 0xFFFF5555, 16));
                if (done && adv.getLore() != null && !adv.getLore().isEmpty()) {
                    lines.add(new DetailLine("", TEXT, 6));
                    lines.add(new DetailLine("\u2728 " + adv.getLore(), 0xFFFFD700, 16));
                }
                if (adv.getTab() != null)
                    lines.add(new DetailLine(Component.translatable(LangKeys.DETAIL_TAB_PREFIX, adv.getTab()).getString(), TEXT_DIM, 16));
                if (adv.getPrerequisites() != null) {
                    for (String pid : adv.getPrerequisites())
                        lines.add(new DetailLine(Component.translatable(LangKeys.DETAIL_PREREQ_PREFIX,
                                screen.prereqDisplayName(pid)).getString(), TEXT_DIM, 14));
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
        }

        // 计算总内容高度
        int totalContentH = 0;
        for (DetailLine dl : lines) totalContentH += dl.height;

        int maxScroll = Math.max(0, totalContentH - contentH + 8);
        if (screen.overlay.detailScrollOff > maxScroll) screen.overlay.detailScrollOff = maxScroll;
        if (screen.overlay.detailScrollOff < 0) screen.overlay.detailScrollOff = 0;

        // 渲染滚动条
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

        // ── 底部固定区域：ID 行 + 内联复制按钮 ──
        int infoY = contentBottom + 6;
        String idDisplay = Component.translatable(LangKeys.DETAIL_ID_PREFIX, id).getString();
        int idTextWidth = font.width(idDisplay);
        g.drawString(font, idDisplay, dx + indentBase, infoY, TEXT_DIM, false);

        String copyIcon = "\uD83D\uDCCB";
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

        g.pose().popPose();
        return maxScroll;
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
}
