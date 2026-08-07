package com.dreamer.ao.client.gui.render;

import com.dreamer.ao.LangKeys;
import com.dreamer.ao.client.gui.AdvancementScreen;
import com.dreamer.ao.client.gui.GuiUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

import static com.dreamer.ao.client.gui.Theme.*;

/**
 * 帮助面板渲染器。
 * <p>
 * 从 {@link OverlayRenderer} 中提取，专门负责帮助/指令说明面板的渲染，
 * 包括自适应宽度、自动换行、CJK 文字支持和可滚动内容区域。
 */
public class HelpRenderer {

    private static final int HELP_MAX_H = 370;
    private static final int HELP_SCREEN_MARGIN = 28;
    private static final int HELP_PAD = 12;
    private static final int HELP_TITLE_H = 22;

    private int helpScroll = 0;
    private int helpMaxScroll = 0;
    private int helpPx, helpPy, helpPw, helpPh;

    private final AdvancementScreen screen;

    public HelpRenderer(AdvancementScreen screen) {
        this.screen = screen;
    }

    // ═══════════════ Accessors for InputManager ═══════════════

    public int getHelpScroll() { return helpScroll; }
    public void setHelpScroll(int s) { this.helpScroll = s; }
    public void resetHelpScroll() { this.helpScroll = 0; }
    public int getHelpMaxScroll() { return helpMaxScroll; }
    public int getHelpPx() { return helpPx; }
    public int getHelpPy() { return helpPy; }
    public int getHelpPw() { return helpPw; }
    public int getHelpPh() { return helpPh; }

    // ═══════════════ Render ═══════════════

    public void renderHelp(GuiGraphics g, int mx, int my, Font font, int screenW, int screenH) {
        int pw = (int) Math.clamp(screenW * 0.62, 320, 420);
        int maxTextW = pw - HELP_PAD * 2 - 8;

        List<HelpSection> sections = buildHelpSections();

        // Pre-compute all render lines with word wrapping
        List<RenderLine> lines = new ArrayList<>();
        for (HelpSection sec : sections) {
            lines.add(new RenderLine(sec.header, ACCENT, 18));
            for (HelpCmd cmd : sec.commands) {
                lines.add(new RenderLine(cmd.cmd, TEXT_BR, 13));
                List<String> wrapped = wrapText(font, cmd.desc, maxTextW - 10);
                for (String w : wrapped)
                    lines.add(new RenderLine(w, TEXT_DIM, 12, 10));
                lines.add(new RenderLine(null, 0, 3));
            }
            lines.add(new RenderLine(null, 0, 2));
        }

        int contentH = 0;
        for (RenderLine rl : lines) contentH += rl.height;

        int ph = Math.min(HELP_MAX_H, Math.max(160, contentH + HELP_TITLE_H + HELP_PAD * 2));
        ph = Math.min(ph, screenH - HELP_SCREEN_MARGIN);
        int px = (screenW - pw) / 2;
        int py = Math.max(HELP_SCREEN_MARGIN / 2, (screenH - ph) / 2);

        helpPx = px; helpPy = py; helpPw = pw; helpPh = ph;

        int contentAreaH = ph - HELP_TITLE_H;
        int maxScroll = Math.max(0, contentH + HELP_PAD - contentAreaH);
        helpMaxScroll = maxScroll;
        if (helpScroll > maxScroll) helpScroll = maxScroll;
        if (helpScroll < 0) helpScroll = 0;

        // Background
        g.fill(px, py, px + pw, py + ph, PANEL);
        g.renderOutline(px, py, pw, ph, DIVIDER);
        g.fill(px, py, px + pw, py + 3, ACCENT);

        // Title bar
        String title = Component.translatable(LangKeys.HELP_TITLE).getString();
        g.drawString(font, title, px + HELP_PAD, py + (HELP_TITLE_H - 8) / 2 + 1, TEXT_BR, false);
        boolean closeHov = GuiUtils.inRect(mx, my, px + pw - 18, py + 3, 15, HELP_TITLE_H - 3);
        g.drawString(font, "\u2715", px + pw - 16, py + (HELP_TITLE_H - 8) / 2 + 1, closeHov ? TEXT_BR : TEXT_DIM, false);

        // Scrollable content
        int scrollBarW = maxScroll > 0 ? 5 : 0;
        g.enableScissor(px + 1, py + HELP_TITLE_H, px + pw - 1 - scrollBarW, py + ph - 1);
        int rowY = py + HELP_TITLE_H + HELP_PAD - helpScroll;

        for (RenderLine rl : lines) {
            if (rowY + rl.height > py + HELP_TITLE_H && rowY < py + ph) {
                if (rl.color == ACCENT) {
                    int tw = font.width(rl.text);
                    g.drawString(font, rl.text, px + (pw - tw) / 2, rowY + 1, ACCENT, false);
                } else if (rl.text != null) {
                    g.drawString(font, rl.text, px + HELP_PAD + rl.indent, rowY, rl.color, false);
                }
            }
            rowY += rl.height;
        }

        g.disableScissor();

        // Scrollbar
        if (maxScroll > 0) {
            int sbX = px + pw - scrollBarW - 3;
            int sbY = py + HELP_TITLE_H + 2;
            int sbH = contentAreaH - 4;
            int sbTrackH = contentH + HELP_PAD;
            g.fill(sbX, sbY, sbX + scrollBarW, sbY + sbH, 0xFF1A1A2E);
            int thumbH = Math.max(16, (int) ((long) sbH * sbH / sbTrackH));
            int thumbY = sbY + (int) ((long) helpScroll * (sbH - thumbH) / maxScroll);
            g.fill(sbX, thumbY, sbX + scrollBarW, thumbY + thumbH, 0xFF555577);
        }
    }

    // ═══════════════ Text utilities ═══════════════

    private static List<String> wrapText(Font font, String text, int maxWidth) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;
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

    // ═══════════════ Data records ═══════════════

    private record RenderLine(String text, int color, int height, int indent) {
        RenderLine(String text, int color, int height) { this(text, color, height, 0); }
    }

    private record HelpSection(String header, List<HelpCmd> commands) {}
    private record HelpCmd(String cmd, String desc) {}

    private List<HelpSection> helpSectionsCache = new ArrayList<>();

    private List<HelpSection> buildHelpSections() {
        if (!helpSectionsCache.isEmpty()) return helpSectionsCache;

        List<HelpSection> sections = new ArrayList<>();

        // Canvas operations
        List<HelpCmd> canvas = new ArrayList<>();
        canvas.add(new HelpCmd("L-click", Component.translatable("advancementoverhaul.ui.help_lclick").getString()));
        canvas.add(new HelpCmd("L-hold drag", Component.translatable("advancementoverhaul.ui.help_boxsel").getString()));
        canvas.add(new HelpCmd("R-click blank", Component.translatable("advancementoverhaul.ui.help_rclick").getString()));
        canvas.add(new HelpCmd("Scroll", Component.translatable("advancementoverhaul.ui.help_scroll").getString()));
        sections.add(new HelpSection(Component.translatable(LangKeys.HELP_CANVAS).getString(), canvas));

        // Edit commands
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

        // Management commands
        List<HelpCmd> mgmt = new ArrayList<>();
        mgmt.add(new HelpCmd("/adv complete", Component.translatable("advancementoverhaul.ui.help_complete").getString()));
        mgmt.add(new HelpCmd("/adv reset", Component.translatable("advancementoverhaul.ui.help_reset").getString()));
        mgmt.add(new HelpCmd("/adv give", Component.translatable("advancementoverhaul.ui.help_give").getString()));
        mgmt.add(new HelpCmd("/adv revoke", Component.translatable("advancementoverhaul.ui.help_revoke").getString()));
        mgmt.add(new HelpCmd("/adv check", Component.translatable("advancementoverhaul.ui.help_check").getString()));
        mgmt.add(new HelpCmd("/adv reload", Component.translatable("advancementoverhaul.ui.help_reload").getString()));
        sections.add(new HelpSection(Component.translatable(LangKeys.HELP_MANAGEMENT).getString(), mgmt));

        // Tab commands
        List<HelpCmd> tabs = new ArrayList<>();
        tabs.add(new HelpCmd("/adv tab add", Component.translatable("advancementoverhaul.ui.help_tab_add").getString()));
        tabs.add(new HelpCmd("/adv tab delete", Component.translatable("advancementoverhaul.ui.help_tab_delete").getString()));
        tabs.add(new HelpCmd("/adv tab order", Component.translatable("advancementoverhaul.ui.help_tab_order").getString()));
        sections.add(new HelpSection(Component.translatable(LangKeys.HELP_TABS).getString(), tabs));

        // Dimension commands
        List<HelpCmd> dims = new ArrayList<>();
        dims.add(new HelpCmd("/adv dimension lock", Component.translatable("advancementoverhaul.ui.help_dim_lock").getString()));
        dims.add(new HelpCmd("/adv dimension unlock", Component.translatable("advancementoverhaul.ui.help_dim_unlock").getString()));
        dims.add(new HelpCmd("/adv dim setcondition", Component.translatable("advancementoverhaul.ui.help_dim_cond").getString()));
        sections.add(new HelpSection(Component.translatable(LangKeys.HELP_DIMENSIONS).getString(), dims));

        // Vanilla commands
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
}
