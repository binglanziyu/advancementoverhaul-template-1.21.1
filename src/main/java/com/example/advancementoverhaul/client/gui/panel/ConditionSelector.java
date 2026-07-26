package com.example.advancementoverhaul.client.gui.panel;

import com.example.advancementoverhaul.LangKeys;
import com.example.advancementoverhaul.client.gui.ConditionTypeStyle;
import com.example.advancementoverhaul.client.gui.GuiUtils;
import com.example.advancementoverhaul.client.gui.TranslatedStrings;
import com.example.advancementoverhaul.client.gui.cache.RegistryCache;
import com.example.advancementoverhaul.client.gui.widget.ScrollBar;
import com.example.advancementoverhaul.data.ClientDataStore;
import com.example.advancementoverhaul.data.DataStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.Consumer;

import static com.example.advancementoverhaul.client.gui.Theme.*;

public class ConditionSelector {

    // ── 物品来源模式（仅 ITEM / BLOCK 时可见） ──
    private enum ItemSource { REGISTRY, BACKPACK }

    private static final int ENTRY_H = 22;
    private static final int TAB_BAR_H = 22;

    private record Layout(int px, int py, int pw, int ph, int sidebarW,
                          int contentY, int sidebarH, int mainX, int mainW) {}

    private Layout calcLayout(int sw, int sh) {
        int pw = Math.min(380, sw - 40);
        int ph = Math.min(360, sh - 40);
        int px = (sw - pw) / 2;
        int py = Math.max(20, (sh - ph) / 2);
        int sidebarW = 76;
        int contentY = py + 26;
        int sidebarH = ph - 30;
        int mainX = px + sidebarW + 6;
        int mainW = pw - sidebarW - 10;
        return new Layout(px, py, pw, ph, sidebarW, contentY, sidebarH, mainX, mainW);
    }
    private static final int TYPE_ENTRY_H = 20;

    private boolean active = false;
    private DataStore.ConditionType selectedType = DataStore.ConditionType.KILL_ENTITY;
    private String search = "";
    private final ScrollBar mainScrollBar = new ScrollBar(4, 0xFF222238, 0xFF6666BB);
    private final ScrollBar sidebarScrollBar = new ScrollBar(4, 0xFF222238, ACCENT);
    private boolean targetEditMode = false;
    private int searchCursor = 0;

    // ── 物品来源与背包渲染栈 ──
    private ItemSource itemSource = ItemSource.REGISTRY;
    private final Map<String, ItemStack> backpackStacks = new LinkedHashMap<>();

    private record CondEntry(String id, String display, String nbt) {}
    private final List<CondEntry> entries = new ArrayList<>();
    private final List<CondEntry> filtered = new ArrayList<>();

    private boolean showSelectedDD = false;

    private Consumer<DataStore.AdvancementCondition> onAdd;
    private Consumer<DataStore.AdvancementCondition> onRemove;
    private List<DataStore.AdvancementCondition> existingConds = Collections.emptyList();

    public void setOnAdd(Consumer<DataStore.AdvancementCondition> cb) { this.onAdd = cb; }
    public void setOnRemove(Consumer<DataStore.AdvancementCondition> cb) { this.onRemove = cb; }
    public void setExistingConditions(List<DataStore.AdvancementCondition> c) { this.existingConds = c; }
    public boolean isActive() { return active; }

    /**
     * 当前条件类型的 DataSource 是否为 ITEM 或 BLOCK（支持来源选项卡）
     */
    private boolean hasSourceTabs() {
        return selectedType.getDataSource() == DataStore.DataSource.ITEM
                || selectedType.getDataSource() == DataStore.DataSource.BLOCK;
    }

    public void open(DataStore.ConditionType initialType) {
        active = true; selectedType = initialType; search = ""; searchCursor = 0;
        mainScrollBar.setScroll(0); sidebarScrollBar.setScroll(0);
        targetEditMode = false; showSelectedDD = false;
        itemSource = ItemSource.REGISTRY;
        backpackStacks.clear();
        loadEntries();
    }

    public void openForTargetEdit(DataStore.ConditionType type) {
        open(type);
        targetEditMode = true;
    }

    public void close() { active = false; search = ""; showSelectedDD = false; backpackStacks.clear(); }

    // ═══════════════ 条目加载 ═══════════════

    private void loadEntries() {
        entries.clear();
        backpackStacks.clear();
        DataStore.DataSource ds = selectedType.getDataSource();

        // ITEM / BLOCK 类型支持来源切换
        if ((ds == DataStore.DataSource.ITEM || ds == DataStore.DataSource.BLOCK)
                && itemSource == ItemSource.BACKPACK) {
            loadBackpackEntries(ds);
            return;
        }

        switch (ds) {
            case ENTITY_TYPE -> {
                for (var e : RegistryCache.getEntities())
                    entries.add(new CondEntry(e.id(), e.displayName(), null));
            }
            case ITEM -> {
                for (var e : RegistryCache.getItems())
                    entries.add(new CondEntry(e.id(), e.displayName(), null));
            }
            case BLOCK -> {
                for (var e : RegistryCache.getBlocks())
                    entries.add(new CondEntry(e.id(), e.displayName(), null));
            }
            case DIMENSION -> {
                entries.add(new CondEntry("minecraft:overworld", TranslatedStrings.get(LangKeys.DIM_OVERWORLD), null));
                entries.add(new CondEntry("minecraft:the_nether", TranslatedStrings.get(LangKeys.DIM_NETHER), null));
                entries.add(new CondEntry("minecraft:the_end", TranslatedStrings.get(LangKeys.DIM_END), null));
                var lk = ClientDataStore.getInstance().getDimensionLocks();
                if (lk != null) for (String d : lk.keySet()) entries.add(new CondEntry(d, d, null));
                entries.sort(Comparator.comparing(CondEntry::display, String.CASE_INSENSITIVE_ORDER));
            }
            case NONE -> entries.add(new CondEntry("", TranslatedStrings.get(LangKeys.COND_ANY), null));
        }
        filterEntries();
    }

    /**
     * 从玩家背包加载物品/方块条目，保留 NBT 组件用于精确匹配。
     * BLOCK 类型只取方块物品，ITEM 类型取全部。
     * 同 ID + 同组件 的物品去重为一条，count 归一化为 1。
     */
    private void loadBackpackEntries(DataStore.DataSource ds) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { filterEntries(); return; }

        var registryAccess = mc.player.registryAccess();
        Inventory inv = mc.player.getInventory();
        Set<String> seen = new LinkedHashSet<>();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            // BLOCK 类型只取方块物品
            if (ds == DataStore.DataSource.BLOCK && !(stack.getItem() instanceof BlockItem)) continue;

            ResourceLocation rl = BuiltInRegistries.ITEM.getKey(stack.getItem());
            String itemId = rl.toString();

            // 归一化 count=1 后序列化，用于去重和匹配
            ItemStack normalized = stack.copy();
            normalized.setCount(1);
            CompoundTag tag = (CompoundTag) normalized.save(registryAccess);
            String nbtStr = tag.toString();

            String dedupKey = itemId + "|" + nbtStr;
            if (seen.contains(dedupKey)) continue;
            seen.add(dedupKey);

            String display = stack.getHoverName().getString();
            entries.add(new CondEntry(itemId, display, nbtStr));
            backpackStacks.put(nbtStr, normalized);
        }

        entries.sort(Comparator.comparing(CondEntry::display, String.CASE_INSENSITIVE_ORDER));
        filterEntries();
    }

    private void filterEntries() {
        filtered.clear();
        String lq = search.toLowerCase();
        for (CondEntry e : entries) {
            if (lq.isEmpty() || e.display().toLowerCase().contains(lq) || e.id().toLowerCase().contains(lq))
                filtered.add(e);
        }
    }

    private DataStore.AdvancementCondition findExisting(CondEntry e) {
        for (var c : existingConds) {
            if (c.getType() != selectedType) continue;
            if (!Objects.equals(c.getTargetId(), e.id())) continue;
            if (e.nbt() != null) { if (e.nbt().equals(c.getTargetNbt())) return c; }
            else { if (c.getTargetNbt() == null || c.getTargetNbt().isEmpty()) return c; }
        }
        return null;
    }

    // ═══════════════ RENDER ═══════════════

    public void render(GuiGraphics g, Font font, int mx, int my, int sw, int sh) {
        Layout L = calcLayout(sw, sh);
        int px = L.px(), py = L.py(), pw = L.pw(), ph = L.ph();
        int SIDEBAR_W = L.sidebarW(), contentY = L.contentY(), sidebarH = L.sidebarH();
        int mainX = L.mainX(), mainW = L.mainW();

        boolean showTabs = hasSourceTabs();
        int tabOffset = showTabs ? TAB_BAR_H : 0;
        int searchY = contentY + tabOffset;

        // 背景
        g.fill(0, 0, sw, sh, 0x80000000);
        g.fill(px, py, px + pw, py + ph, PANEL);
        g.renderOutline(px, py, pw, ph, DIVIDER);
        g.fill(px, py, px + pw, py + 3, ACCENT);

        // 标题
        g.drawString(font, TranslatedStrings.get(LangKeys.COND_SELECTOR), px + 12, py + 8, TEXT_BR, false);
        boolean ch = GuiUtils.closeHit(mx, my, px, py, pw);
        g.drawString(font, "\u2715", px + pw - 16, py + 8, ch ? TEXT_BR : TEXT_DIM, false);

        // ── 左侧：条件类型 ──
        int totalTypeH = DataStore.ConditionType.values().length * TYPE_ENTRY_H;
        sidebarScrollBar.update(totalTypeH, sidebarH);

        int typeY = contentY - sidebarScrollBar.getScroll();
        g.enableScissor(px + 1, contentY, px + SIDEBAR_W - 1, contentY + sidebarH);
        for (DataStore.ConditionType type : DataStore.ConditionType.values()) {
            if (typeY + TYPE_ENTRY_H > contentY && typeY < contentY + sidebarH) {
                boolean sel = type == selectedType;
                boolean hov = GuiUtils.inRect(mx, my, px + 2, typeY, SIDEBAR_W - 4, TYPE_ENTRY_H);
                int color = ConditionTypeStyle.of(type).color();
                g.fill(px + 2, typeY, px + SIDEBAR_W - 2, typeY + TYPE_ENTRY_H, sel ? color : (hov ? BTN_HOV : BTN));
                g.renderOutline(px + 2, typeY, SIDEBAR_W - 4, TYPE_ENTRY_H, sel ? color : DIVIDER);
                g.drawString(font, GuiUtils.truncate(font, ConditionTypeStyle.of(type).displayName(), SIDEBAR_W - 10), px + 6, typeY + 5, sel ? 0xFFFFFFFF : TEXT, false);
            }
            typeY += TYPE_ENTRY_H;
        }
        g.disableScissor();

        sidebarScrollBar.render(g, px + SIDEBAR_W - 5, contentY);

        // ── 来源选项卡（仅 ITEM / BLOCK 类型显示） ──
        if (showTabs) {
            int tabW = mainW / 2;

            // 「全部」标签
            boolean allSel = itemSource == ItemSource.REGISTRY;
            boolean allHov = GuiUtils.inRect(mx, my, mainX, contentY, tabW, TAB_BAR_H);
            g.fill(mainX, contentY, mainX + tabW, contentY + TAB_BAR_H, allSel ? 0xFF3A3A65 : (allHov ? BTN_HOV : BTN));
            g.renderOutline(mainX, contentY, tabW, TAB_BAR_H, allSel ? ACCENT : DIVIDER);
            String allLabel = TranslatedStrings.get(LangKeys.COND_ALL);
            g.drawString(font, allLabel,
                    mainX + (tabW - font.width(allLabel)) / 2, contentY + 5,
                    allSel ? ACCENT : (allHov ? TEXT_BR : TEXT), false);

            // 「背包」标签
            boolean bpSel = itemSource == ItemSource.BACKPACK;
            boolean bpHov = GuiUtils.inRect(mx, my, mainX + tabW, contentY, mainW - tabW, TAB_BAR_H);
            g.fill(mainX + tabW, contentY, mainX + mainW, contentY + TAB_BAR_H, bpSel ? 0xFF3A3A65 : (bpHov ? BTN_HOV : BTN));
            g.renderOutline(mainX + tabW, contentY, mainW - tabW, TAB_BAR_H, bpSel ? ACCENT : DIVIDER);
            String bpLabel = TranslatedStrings.get(LangKeys.COND_BACKPACK);
            g.drawString(font, bpLabel,
                    mainX + tabW + ((mainW - tabW) - font.width(bpLabel)) / 2, contentY + 5,
                    bpSel ? ACCENT : (bpHov ? TEXT_BR : TEXT), false);
        }

        // ── 右侧：搜索 + 条目列表 ──
        g.fill(mainX, searchY, mainX + mainW, searchY + 18, 0xFF222238);
        g.renderOutline(mainX, searchY, mainW, 18, DIVIDER);
        String cursor = (System.currentTimeMillis() / 500 % 2 == 0) ? "\u258C" : " ";
        if (search.isEmpty())
            g.drawString(font, TranslatedStrings.get(LangKeys.SEARCH_HINT), mainX + 6, searchY + 4, 0xFF7070A0, false);
        else {
            String before = search.substring(0, searchCursor);
            String after = search.substring(searchCursor);
            g.drawString(font, before + cursor + after, mainX + 6, searchY + 4, TEXT, false);
        }

        int listY = searchY + 20;
        int listH = py + ph - listY - 4;
        mainScrollBar.update(filtered.size() * ENTRY_H, listH);

        boolean canShowIcon = selectedType.getDataSource() == DataStore.DataSource.ITEM
                || selectedType.getDataSource() == DataStore.DataSource.BLOCK;

        if (filtered.isEmpty()) {
            // 背包模式下无结果显示专门提示
            String msg = (itemSource == ItemSource.BACKPACK)
                    ? TranslatedStrings.get(LangKeys.EMPTY_BACKPACK)
                    : TranslatedStrings.get(LangKeys.NO_RESULTS);
            g.drawString(font, msg, mainX + (mainW - font.width(msg)) / 2, listY + listH / 2 - 6, TEXT_DIM, false);
        } else {
            g.enableScissor(mainX, listY, mainX + mainW, listY + listH);
            int ey = listY - mainScrollBar.getScroll();
            for (CondEntry e : filtered) {
                if (ey + ENTRY_H > listY && ey < listY + listH) {
                    boolean hov = GuiUtils.inRect(mx, my, mainX, ey, mainW, ENTRY_H);
                    boolean added = !targetEditMode && findExisting(e) != null;

                    if (added) g.fill(mainX + 2, ey, mainX + mainW - 4, ey + ENTRY_H, 0x3055FF55);
                    else if (hov) g.fill(mainX + 2, ey, mainX + mainW - 4, ey + ENTRY_H, 0xFF3A3A55);

                    int textX = mainX + 6;
                    if (canShowIcon) {
                        try {
                            // 背包物品优先使用带组件的实际栈渲染（附魔光效等）
                            ItemStack renderStack = null;
                            if (e.nbt() != null && backpackStacks.containsKey(e.nbt())) {
                                renderStack = backpackStacks.get(e.nbt());
                            } else {
                                ResourceLocation rl = ResourceLocation.tryParse(e.id());
                                if (rl != null) {
                                    var item = BuiltInRegistries.ITEM.get(rl);
                                    if (item != null) renderStack = new ItemStack(item);
                                }
                            }
                            if (renderStack != null) {
                                g.renderItem(renderStack, mainX + 3, ey + 2);
                                textX = mainX + 24;
                            }
                        } catch (Exception ignored) {}
                    }

                    int textCol = added ? ACCENT : (hov ? TEXT_BR : TEXT);
                    g.drawString(font, GuiUtils.truncate(font, e.display(), mainW - (textX - mainX) - 20), textX, ey + 4, textCol, false);
                    if (added) g.drawString(font, "\u2713", mainX + mainW - 16, ey + 4, ACCENT, false);
                }
                ey += ENTRY_H;
            }
            g.disableScissor();

            mainScrollBar.render(g, mainX + mainW - 4, listY);
        }

        // ── 已选择条件下拉 ──
        if (!targetEditMode && !existingConds.isEmpty()) {
            int ddBtnX = mainX + mainW - 20, ddBtnY = searchY;
            boolean ddHov = GuiUtils.inRect(mx, my, ddBtnX, ddBtnY, 18, 18);
            g.fill(ddBtnX, ddBtnY, ddBtnX + 18, ddBtnY + 18, ddHov ? BTN_HOV : BTN);
            g.drawString(font, "\u25BE", ddBtnX + 5, ddBtnY + 4, ddHov ? ACCENT : TEXT_DIM, false);

            if (showSelectedDD) {
                int ddX = mainX + mainW - 170;
                int ddY = ddBtnY + 20;
                int ddW = 168;
                int ddH = Math.min(existingConds.size() * ENTRY_H + 4, py + ph - ddY - 4);

                g.fill(ddX, ddY, ddX + ddW, ddY + ddH, PANEL);
                g.renderOutline(ddX, ddY, ddW, ddH, DIVIDER);
                g.enableScissor(ddX + 1, ddY + 1, ddX + ddW - 1, ddY + ddH - 1);
                int cy = ddY + 2;
                for (int i = 0; i < existingConds.size(); i++) {
                    if (cy + ENTRY_H > ddY + ddH) break;
                    DataStore.AdvancementCondition c = existingConds.get(i);
                    boolean itemHov = GuiUtils.inRect(mx, my, ddX + 2, cy, ddW - 4, 20);
                    if (itemHov) g.fill(ddX + 2, cy, ddX + ddW - 2, cy + ENTRY_H, BTN_HOV);
                    String typeStr = c.getType() != null ? ConditionTypeStyle.of(c.getType()).displayName() : "???";
                    String tgtStr = c.getTargetId() != null ? GuiUtils.truncate(font, c.getTargetId(), 70) : "";
                    String line = GuiUtils.truncate(font, typeStr + " " + tgtStr + " x" + c.getCount(), ddW - 24);
                    g.drawString(font, line, ddX + 6, cy + 5, TEXT, false);
                    boolean xHov = GuiUtils.inRect(mx, my, ddX + ddW - 16, cy, 14, ENTRY_H);
                    g.drawString(font, "\u2715", ddX + ddW - 14, cy + 5, xHov ? PINK : TEXT_DIM, false);
                    cy += ENTRY_H;
                }
                g.disableScissor();
            }
        }
    }

    // ═══════════════ 交互 ═══════════════

    public boolean handleClick(double mx, double my, Font font, int sw, int sh) {
        Layout L = calcLayout(sw, sh);
        int px = L.px(), py = L.py(), pw = L.pw(), ph = L.ph();
        int SIDEBAR_W = L.sidebarW(), contentY = L.contentY(), sidebarH = L.sidebarH();
        int mainX = L.mainX(), mainW = L.mainW();

        boolean showTabs = hasSourceTabs();
        int tabOffset = showTabs ? TAB_BAR_H : 0;
        int searchY = contentY + tabOffset;

        if (GuiUtils.closeHit(mx, my, px, py, pw)) { close(); return true; }
        if (GuiUtils.outsidePanel(mx, my, px, py, pw, ph)) { close(); return true; }

        // 已选择下拉
        if (showSelectedDD && !targetEditMode) {
            int ddBtnX = mainX + mainW - 20;
            int ddBtnY = searchY;
            int ddX = mainX + mainW - 170;
            int ddY = ddBtnY + 20;
            int ddW = 168;
            int ddH = Math.min(existingConds.size() * ENTRY_H + 4, py + ph - ddY - 4);
            if (mx >= ddX && mx < ddX + ddW && my >= ddY && my < ddY + ddH) {
                int cy = ddY + 2;
                for (int i = 0; i < existingConds.size(); i++) {
                    if (cy + ENTRY_H > ddY + ddH) break;
                    if (GuiUtils.inRect(mx, my, ddX + ddW - 16, cy, 14, ENTRY_H)) {
                        if (onRemove != null) onRemove.accept(existingConds.get(i));
                        return true;
                    }
                    cy += ENTRY_H;
                }
                showSelectedDD = false;
                return true;
            }
            showSelectedDD = false;
            return true;
        }

        // 左侧条件类型
        if (GuiUtils.inRect(mx, my, px, contentY, SIDEBAR_W, sidebarH)) {
            int typeY = contentY - sidebarScrollBar.getScroll();
            for (DataStore.ConditionType type : DataStore.ConditionType.values()) {
                if (GuiUtils.inRect(mx, my, px + 2, typeY, SIDEBAR_W - 4, TYPE_ENTRY_H)) {
                    selectedType = type; search = ""; mainScrollBar.setScroll(0);
                    itemSource = ItemSource.REGISTRY; // 切换类型时重置来源
                    loadEntries(); return true;
                }
                typeY += TYPE_ENTRY_H;
            }
            return true;
        }

        // ── 来源选项卡点击 ──
        if (showTabs && GuiUtils.inRect(mx, my, mainX, contentY, mainW, TAB_BAR_H)) {
            int tabW = mainW / 2;
            if (mx < mainX + tabW && itemSource != ItemSource.REGISTRY) {
                itemSource = ItemSource.REGISTRY;
                search = ""; mainScrollBar.setScroll(0);
                loadEntries();
            } else if (mx >= mainX + tabW && itemSource != ItemSource.BACKPACK) {
                itemSource = ItemSource.BACKPACK;
                search = ""; mainScrollBar.setScroll(0);
                loadEntries();
            }
            return true;
        }

        // 搜索
        if (GuiUtils.inRect(mx, my, mainX, searchY, mainW, 18)) return true;

        // 已选择下拉按钮
        if (!targetEditMode && !existingConds.isEmpty()) {
            int ddBtnX = mainX + mainW - 20;
            if (GuiUtils.inRect(mx, my, ddBtnX, searchY, 18, 18)) { showSelectedDD = !showSelectedDD; return true; }
        }

        // 条目列表
        int listY = searchY + 20;
        int listH = py + ph - listY - 4;
        if (mx >= mainX && mx < mainX + mainW && my >= listY && my < listY + listH) {
            int idx = (int) ((my - listY + mainScrollBar.getScroll()) / ENTRY_H);
            if (idx >= 0 && idx < filtered.size()) {
                CondEntry e = filtered.get(idx);
                if (targetEditMode) {
                    if (onAdd != null) {
                        DataStore.AdvancementCondition cond = new DataStore.AdvancementCondition(selectedType, e.id(), 1);
                        // 背包物品携带 NBT
                        if (e.nbt() != null && !e.nbt().isEmpty()) {
                            cond.setNbtMatchMode("exact");
                            cond.setTargetNbt(e.nbt());
                        }
                        onAdd.accept(cond);
                    }
                    close();
                } else {
                    DataStore.AdvancementCondition existing = findExisting(e);
                    if (existing != null) {
                        if (onRemove != null) onRemove.accept(existing);
                    } else {
                        if (onAdd != null) {
                            DataStore.AdvancementCondition cond = new DataStore.AdvancementCondition(selectedType, e.id(), 1);
                            // 背包物品携带 NBT，自动设置精确匹配
                            if (e.nbt() != null && !e.nbt().isEmpty()) {
                                cond.setNbtMatchMode("exact");
                                cond.setTargetNbt(e.nbt());
                            }
                            onAdd.accept(cond);
                        }
                    }
                }
            }
            return true;
        }
        return true;
    }

    public boolean handleScroll(double mx, double my, double sy, int sw, int sh) {
        Layout L = calcLayout(sw, sh);
        int px = L.px(), py = L.py(), pw = L.pw(), ph = L.ph();
        int SIDEBAR_W = L.sidebarW(), contentY = L.contentY(), sidebarH = L.sidebarH();
        int mainX = L.mainX(), mainW = L.mainW();

        if (GuiUtils.inRect(mx, my, px, contentY, SIDEBAR_W, sidebarH)) {
            return sidebarScrollBar.handleScroll(sy);
        }

        boolean showTabs = hasSourceTabs();
        int tabOffset = showTabs ? TAB_BAR_H : 0;
        int searchY = contentY + tabOffset;
        int listY = searchY + 20;
        int listH = py + ph - listY - 4;
        if (mx >= mainX && mx < mainX + mainW && my >= listY && my < listY + listH) {
            return mainScrollBar.handleScroll(sy);
        }
        return false;
    }

    public void handleChar(char chr) {
        if (chr >= 32) {
            search = search.substring(0, searchCursor) + chr + search.substring(searchCursor);
            searchCursor++;
            mainScrollBar.setScroll(0);
            filterEntries();
        }
    }
    public void handleKey(int keyCode) {
        if (keyCode == GuiUtils.KEY_BACKSPACE && !search.isEmpty() && searchCursor > 0) {
            search = search.substring(0, searchCursor - 1) + search.substring(searchCursor);
            searchCursor--;
            mainScrollBar.setScroll(0);
            filterEntries();
        } else if (keyCode == GuiUtils.KEY_LEFT && searchCursor > 0) {
            searchCursor--;
        } else if (keyCode == GuiUtils.KEY_RIGHT && searchCursor < search.length()) {
            searchCursor++;
        } else if (keyCode == GuiUtils.KEY_HOME) {
            searchCursor = 0;
        } else if (keyCode == GuiUtils.KEY_END) {
            searchCursor = search.length();
        } else if (keyCode == GuiUtils.KEY_DELETE && searchCursor < search.length()) {
            search = search.substring(0, searchCursor) + search.substring(searchCursor + 1);
            filterEntries();
        }
    }
}