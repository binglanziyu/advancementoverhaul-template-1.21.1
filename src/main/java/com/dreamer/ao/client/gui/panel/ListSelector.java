package com.dreamer.ao.client.gui.panel;

/**
 * 通用列表/注册表选择器：条件编辑时用于从注册表中选择目标实体/物品/方块/维度。
 * <p>
 * 支持模糊搜索过滤、滚动浏览、键盘导航和点击选择。
 * 数据源通过 {@link com.dreamer.ao.client.gui.cache.RegistryCache} 预加载。
 */
import com.dreamer.ao.LangKeys;
import com.dreamer.ao.client.gui.GuiUtils;
import com.dreamer.ao.client.gui.TranslatedStrings;
import com.dreamer.ao.client.gui.widget.ScrollBar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

import static com.dreamer.ao.client.gui.Theme.*;

public class ListSelector {

    private static final Logger LOGGER = LoggerFactory.getLogger(ListSelector.class);

    public record Entry(String id, String display, String nbt) {
        public Entry(String id, String display) { this(id, display, null); }
    }

    private List<Entry> all = new ArrayList<>();
    private List<Entry> filtered = new ArrayList<>();
    private Consumer<Entry> callback;
    private boolean visible = false;
    private String search = "";
    private final ScrollBar scrollBar = new ScrollBar(6, 0xFF222238, 0xFF6666BB);
    private int px, py, pw, ph;
    private static final int ENTRY_H = 20;
    private static final int HEADER_H = 24, SEARCH_H = 22, FOOTER_H = 18;

    public void show(List<Entry> entries, Consumer<Entry> cb) {
        all = new ArrayList<>(entries); filtered = new ArrayList<>(all);
        callback = cb; visible = true; search = ""; scrollBar.setScroll(0);
    }

    public boolean isVisible() { return visible; }

    public void render(GuiGraphics g, int mx, int my) {
        if (!visible) return;
        Font font = Minecraft.getInstance().font;
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        pw = 260;
        int contentPh = HEADER_H + SEARCH_H + filtered.size() * ENTRY_H + FOOTER_H + 8;
        ph = Math.min(contentPh, Math.min(sh - 60, 300));
        px = (sw - pw) / 2; py = Math.max(20, (sh - ph) / 2);
        int listY = py + HEADER_H + SEARCH_H;
        int listH = ph - HEADER_H - SEARCH_H - FOOTER_H - 4;
        scrollBar.update(filtered.size() * ENTRY_H, listH);

        // 全屏暗色遮罩由 AdvancementScreen 统一管理，此处仅绘制面板自身
        g.fill(px, py, px + pw, py + ph, PANEL);
        g.renderOutline(px, py, pw, ph, DIVIDER);
        g.fill(px, py, px + pw, py + 3, ACCENT);
        g.drawString(font, TranslatedStrings.get(LangKeys.SELECT), px + 10, py + 8, TEXT_BR, false);
        boolean ch = GuiUtils.closeHit(mx, my, px, py, pw);
        g.drawString(font, "\u2715", px + pw - 16, py + 8, ch ? TEXT_BR : TEXT_DIM, false);

        int sy = py + HEADER_H;
        g.fill(px + 6, sy, px + pw - 6, sy + SEARCH_H - 2, 0xFF222238);
        g.renderOutline(px + 6, sy, pw - 12, SEARCH_H - 2, DIVIDER);
        String cursorBlink = (System.currentTimeMillis() / 500 % 2 == 0) ? "\u258C" : " ";
        if (search.isEmpty())
            g.drawString(font, TranslatedStrings.get(LangKeys.SEARCH_HINT), px + 12, sy + 4, 0xFF7070A0, false);
        else g.drawString(font, search + cursorBlink, px + 12, sy + 4, TEXT, false);

        Entry hoveredEntry = null;
        int hoveredScreenY = 0;

        g.enableScissor(px + 1, listY, px + pw - 1, listY + listH);
        int y = listY - scrollBar.getScroll();
        for (Entry e : filtered) {
            if (y + ENTRY_H > listY && y < listY + listH) {
                boolean isSep = e.id() == null || e.id().isEmpty() || e.id().startsWith("__sep_");
                if (isSep) {
                    if (!e.display().startsWith("__sep_"))
                        g.drawString(font, e.display(), px + (pw - font.width(e.display())) / 2, y + 3, 0xFF7070A0, false);
                    else {
                        String title = e.display().replace("__sep_", "").replace("__", "");
                        g.drawString(font, "--- " + title + " ---", px + (pw - font.width("--- " + title + " ---")) / 2, y + 3, 0xFF7070A0, false);
                    }
                } else {
                    boolean hov = GuiUtils.inRect(mx, my, px + 4, y, pw - 8, ENTRY_H);
                    if (hov) {
                        g.fill(px + 4, y, px + pw - 4, y + ENTRY_H, 0xFF3A3A55);
                        hoveredEntry = e; hoveredScreenY = y;
                    }

                    boolean iconRendered = false;
                    String entryId = e.id();
                    if (entryId != null && !entryId.startsWith("entity:") && !entryId.startsWith("__")) {
                        try {
                            ResourceLocation rl = ResourceLocation.tryParse(entryId);
                            if (rl != null) {
                                var item = BuiltInRegistries.ITEM.get(rl);
                                if (item != null) {
                                    g.renderItem(new ItemStack(item), px + 8, y + 2);
                                    g.drawString(font, GuiUtils.truncate(font, e.display(), pw - 44), px + 28, y + 3, TEXT, false);
                                    iconRendered = true;
                                }
                            }
                        } catch (Exception ex) {
                            LOGGER.debug("Failed to render item icon for {}: {}", entryId, ex.getMessage());
                        }
                    }
                    if (!iconRendered) {
                        g.drawString(font, GuiUtils.truncate(font, e.display(), pw - 24), px + 10, y + 3, TEXT, false);
                    }
                }
            }
            y += ENTRY_H;
        }
        g.disableScissor();

        if (filtered.isEmpty() && !search.isEmpty()) {
            String nr = TranslatedStrings.get(LangKeys.NO_RESULTS);
            g.drawString(font, nr, px + pw / 2 - font.width(nr) / 2, listY + listH / 2 - 6, 0xFF7070A0, false);
        }

        scrollBar.render(g, px + pw - 8, listY);

        // 悬浮 ID 提示
        if (hoveredEntry != null && hoveredEntry.id() != null && !hoveredEntry.id().isEmpty()) {
            String tooltipId = hoveredEntry.id().startsWith("entity:") ? hoveredEntry.id().substring(7) : hoveredEntry.id();
            int tx = px + pw + 4, tw = font.width(tooltipId) + 8;
            if (tx + tw > sw) tx = px - tw - 4;
            int tt = Math.clamp(hoveredScreenY, 2, listY + listH - 16);
            g.fill(tx, tt, tx + tw, tt + 16, TOOLTIP_BG);
            g.renderOutline(tx, tt, tw, 16, TOOLTIP_BORDER);
            g.drawString(font, tooltipId, tx + 4, tt + 3, TEXT_DIM, false);
        }

        g.drawString(font, TranslatedStrings.get(LangKeys.ITEMS_COUNT, filtered.size()),
                px + 10, py + ph - FOOTER_H, 0xFF7070A0, false);
    }

    public boolean mouseClicked(double mx, double my, int btn) {
        if (!visible) return false;
        if (GuiUtils.closeHit(mx, my, px, py, pw)) { visible = false; return true; }
        if (GuiUtils.inRect(mx, my, px, py, pw, ph)) {
            int listY = py + HEADER_H + SEARCH_H;
            int listH = ph - HEADER_H - SEARCH_H - FOOTER_H - 4;
            // 滚动条点击
            if (scrollBar.needsScrollbar()) {
                if (scrollBar.handleClick(mx, my, px + pw - 8, listY)) return true;
            }
            if (my >= listY && my < listY + listH) {
                int idx = (int) ((my - listY + scrollBar.getScroll()) / ENTRY_H);
                if (idx >= 0 && idx < filtered.size() && callback != null) {
                    Entry entry = filtered.get(idx);
                    if (entry.id() != null && !entry.id().isEmpty() && !entry.id().startsWith("__")) {
                        callback.accept(entry); visible = false;
                    }
                    return true;
                }
            }
            return true;
        }
        visible = false; return false;
    }

    public void charTyped(char chr) {
        if (!visible || chr < 32) return;
        search += chr; filter();
    }

    public void keyPressed(int keyCode) {
        if (!visible) return;
        if (keyCode == GuiUtils.KEY_BACKSPACE && !search.isEmpty()) {
            search = search.substring(0, search.length() - 1); filter();
        } else if (keyCode == GuiUtils.KEY_ENTER && !filtered.isEmpty() && callback != null) {
            for (Entry e : filtered) {
                if (e.id() != null && !e.id().isEmpty() && !e.id().startsWith("__")) {
                    callback.accept(e); visible = false; break;
                }
            }
        }
    }

    private void filter() {
        scrollBar.setScroll(0);
        if (search.isEmpty()) { filtered = new ArrayList<>(all); return; }
        filtered = new ArrayList<>();
        String lq = search.toLowerCase();
        for (Entry e : all) {
            if (e.id() == null || e.id().isEmpty()) continue;
            if (e.display().toLowerCase().contains(lq) || e.id().toLowerCase().contains(lq))
                filtered.add(e);
        }
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