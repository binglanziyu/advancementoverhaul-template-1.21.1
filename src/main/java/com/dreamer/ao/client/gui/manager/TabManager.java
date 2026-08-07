package com.dreamer.ao.client.gui.manager;

import com.dreamer.ao.LangKeys;
import com.dreamer.ao.client.gui.AdvancementScreen;
import com.dreamer.ao.client.gui.state.OverlayState.Ov;
import com.dreamer.ao.client.gui.state.ScreenState;
import com.dreamer.ao.client.gui.GuiUtils;
import com.dreamer.ao.client.gui.TranslatedStrings;
import com.dreamer.ao.client.gui.panel.ListSelector;
import com.dreamer.ao.data.ClientDataStore;
import com.dreamer.ao.data.DataStore;
import com.dreamer.ao.data.model.CustomAdvancement;


import java.util.*;

import static com.dreamer.ao.client.gui.Theme.*;

/**
 * 标签栏交互：点击切换、拖拽排序、溢出下拉、新建/选择面板。
 */
public class TabManager {

    final AdvancementScreen screen;

    public TabManager(AdvancementScreen screen) { this.screen = screen; }

    // ═══════════════ 标签点击 ═══════════════

    public void tabClick(double mx) {
        if (screen.editPanel.isVisible()) { screen.editPanel.saveIfVisible(); screen.overlay.close(); }
        ClientDataStore s = ClientDataStore.getInstance();
        int x = 4, w = screen.getFont().width(TranslatedStrings.get(LangKeys.ALL)) + 12;
        if (mx < x + w) { screen.curTab = null; screen.tabDrag.overDDOpen = false; screen.tabDrag.overflowScroll = 0; screen.screenState.markDirty(ScreenState.DIRTY_VANILLA_POS); return; }
        x += w + 3;
        for (String tab : screen.tabRenderer.getBarTabs()) {
            boolean isBuiltin = s.isBuiltinTab(tab);
            int d = s.getTabTotalCount(tab);
            String label = d > 0 ? tab + " " + s.getTabCompletedCount(tab) + "/" + d : tab;
            w = screen.getFont().width(label) + 12;
            if (mx >= x && mx < x + w) {
                screen.curTab = tab; screen.screenState.markDirty(ScreenState.DIRTY_VANILLA_POS);
                screen.tabDrag.overDDOpen = false; screen.tabDrag.overflowScroll = 0; return;
            }
            x += w + 3;
        }
        if (screen.tabRenderer.hasOverflow()) {
            int ddw = 20;
            if (mx >= x && mx < x + ddw) { screen.tabDrag.overDDOpen = !screen.tabDrag.overDDOpen; screen.tabDrag.overflowScroll = 0; return; }
            x += ddw + 3;
        }
        if (screen.editMode) {
            w = screen.getFont().width(TranslatedStrings.get(LangKeys.HIDDEN)) + 12;
            if (mx >= x && mx < x + w) { screen.curTab = "hidden"; screen.tabDrag.overDDOpen = false; screen.screenState.markDirty(ScreenState.DIRTY_VANILLA_POS); return; }
            x += w + 3;
            if (mx >= x && mx < x + 20) { openTabAddSelector(); return; }
        }
        screen.tabDrag.overDDOpen = false; screen.tabDrag.overflowScroll = 0;
    }

    int getTabIndexAt(int mx) {
        ClientDataStore store = ClientDataStore.getInstance();
        List<String> allTabs = store.getTabs();
        int x = 4 + screen.getFont().width(TranslatedStrings.get(LangKeys.ALL)) + 12 + 3;
        for (int i = 0; i < allTabs.size(); i++) {
            String tab = allTabs.get(i);
            int d = store.getTabTotalCount(tab);
            String label = d > 0 ? tab + " " + store.getTabCompletedCount(tab) + "/" + d : tab;
            int w = screen.getFont().width(label) + 12;
            if (mx >= x && mx < x + w) return i;
            x += w + 3;
        }
        return -1;
    }

    boolean handleOverflowDDClick(double mx, double my) {
        if (screen.tabDrag.overflowDDX < 0) return false;
        List<String> over = screen.tabRenderer.getOverflowTabs();
        if (over.isEmpty()) return false;
        int mw = 160, availH = screen.getScreenHeight() - TAB_H - 4;
        int maxVisible = Math.min(over.size(), availH / 22);
        int showH = maxVisible * 22 + 4;
        if (mx >= screen.tabDrag.overflowDDX && mx < screen.tabDrag.overflowDDX + mw && my >= TAB_H && my < TAB_H + showH) {
            int idx = (int) ((my - TAB_H - 2 + screen.tabDrag.overflowScroll) / 22);
            if (idx >= 0 && idx < over.size()) { screen.curTab = over.get(idx); screen.screenState.markDirty(ScreenState.DIRTY_VANILLA_POS); }
            return true;
        }
        return false;
    }

    // ═══════════════ 选择器 ═══════════════

    void openTabAddSelector() {
        screen.tabNameBox.setValue("");
        screen.overlay.current = Ov.TAB_INPUT;
        screen.tabNameBox.setFocused(true);
        screen.setFocused(screen.tabNameBox);
    }

    public void openTabSel() {
        List<ListSelector.Entry> entries = new ArrayList<>();
        for (String t : ClientDataStore.getInstance().getTabs()) {
            if (t.equals(DataStore.TAB_VANILLA)) continue;
            entries.add(new ListSelector.Entry(t, DataStore.getTabDisplayName(t)));
        }
        entries.sort(Comparator.comparing(ListSelector.Entry::display, String.CASE_INSENSITIVE_ORDER));
        screen.showSelector(entries, e -> screen.editPanel.setEdTab(e.id()));
    }

    public void openPrereqSel(String forId) {
        List<ListSelector.Entry> entries = new ArrayList<>();
        ClientDataStore cs = ClientDataStore.getInstance();
        String edId = screen.editPanel.getEdId();
        var edPrereqs = screen.editPanel.getEdPrereqs();

        // ── Custom advancements grouped by tab ──
        Map<String, List<CustomAdvancement>> byTab = new LinkedHashMap<>();
        for (var a : cs.getAdvancements().values()) {
            if (a.getId().equals(forId)) continue;
            if (edId != null && a.getId().equals(edId)) continue;
            if (edPrereqs.contains(a.getId())) continue;
            // 循环检测：如果选了 a.getId() 作为前置会形成环（a → ... → edId），则跳过
            if (edId != null && isTransitiveDependent(cs, a.getId(), edId)) continue;
            String tab = (a.getTab() != null && !a.getTab().isEmpty()) ? a.getTab() : DataStore.TAB_DEFAULT;
            byTab.computeIfAbsent(tab, k -> new ArrayList<>()).add(a);
        }
        for (var entry : byTab.entrySet()) {
            entries.add(new ListSelector.Entry("__sep_" + entry.getKey(), "── " + DataStore.getTabDisplayName(entry.getKey()) + " ──"));
            entry.getValue().sort(Comparator.comparing(CustomAdvancement::getName, String.CASE_INSENSITIVE_ORDER));
            for (var a : entry.getValue()) entries.add(new ListSelector.Entry(a.getId(), a.getName()));
        }

        // ── Vanilla advancements grouped by assigned tab ──
        Map<String, List<String>> vanillaByTab = new LinkedHashMap<>();
        for (var va : cs.getVanillaAdvancements()) {
            if (!cs.isVanillaEnabled(va.id())) continue;
            if (va.id().equals(forId)) continue;
            if (edPrereqs.contains(va.id())) continue;
            if (edId != null && isTransitiveDependent(cs, va.id(), edId)) continue;
            String tab = cs.getVanillaDisplayTab(va.id());
            if (tab == null || tab.isEmpty()) tab = DataStore.TAB_VANILLA;
            vanillaByTab.computeIfAbsent(tab, k -> new ArrayList<>()).add(va.id());
        }
        for (var entry : vanillaByTab.entrySet()) {
            String sepKey = "__vanilla_sep_" + entry.getKey();
            if (!entries.isEmpty() || byTab.size() > 0)
                entries.add(new ListSelector.Entry(sepKey, "── " + DataStore.getTabDisplayName(entry.getKey()) + " (vanilla) ──"));
            entry.getValue().sort(String.CASE_INSENSITIVE_ORDER);
            for (String id : entry.getValue()) {
                var va = cs.getVanillaAdvEntry(id);
                String display = va != null ? va.name() : id;
                entries.add(new ListSelector.Entry(id, display));
            }
        }

        screen.showSelector(entries, e -> {
            if (e.id().startsWith("__sep_") || e.id().startsWith("__vanilla_sep_")) return;
            if (forId != null) GuiUtils.sendCommand("adv setprereq " + forId + " " + e.id());
            else screen.editPanel.addPrereq(e.id());
        });
    }

    public void openVanillaTabSel(String vanillaId) {
        List<ListSelector.Entry> entries = new ArrayList<>();
        boolean enabled = ClientDataStore.getInstance().isVanillaEnabled(vanillaId);
        // ★ 未启用时不在选择器中显示"无标签"选项（分配分类=启用，不能选择空）
        if (enabled) {
            entries.add(new ListSelector.Entry("", TranslatedStrings.get(LangKeys.NO_TAB)));
        }
        for (String t : ClientDataStore.getInstance().getTabs()) entries.add(new ListSelector.Entry(t, DataStore.getTabDisplayName(t)));
        entries.sort(Comparator.comparing(ListSelector.Entry::display, String.CASE_INSENSITIVE_ORDER));
        screen.showSelector(entries, e -> {
            if (e.id().isEmpty()) {
                // "无标签"等同于禁用
                GuiUtils.sendCommand("adv vanilla disable " + vanillaId);
            } else {
                GuiUtils.sendCommand("adv vanilla settab " + vanillaId + " " + e.id());
            }
        });
    }

    // ═══════════════ TAB 输入框 ═══════════════

    public void closeTabInput() {
        screen.overlay.current = Ov.NONE;
        screen.tabNameBox.setVisible(false);
        screen.tabNameBox.setFocused(false);
        screen.setFocused(null);
    }

    // ═══════════════ 循环依赖检测 ═══════════════

    /**
     * 检查 candidateId 是否（通过前置条件链路）间接依赖 targetId。
     * 即：candidateId → ... → targetId 是否存在一条路径。
     * 如果存在，将 candidateId 添加为 targetId 的前置条件会形成环。
     */
    private boolean isTransitiveDependent(ClientDataStore cs, String candidateId, String targetId) {
        if (candidateId.equals(targetId)) return true;
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();

        // 检查 candidateId 的自定义成就前置条件
        var adv = cs.getAdvancement(candidateId);
        if (adv != null && adv.getPrerequisites() != null) {
            for (String p : adv.getPrerequisites()) {
                if (visited.add(p)) queue.add(p);
            }
        }
        // 检查 candidateId 的原版元数据前置条件
        var meta = cs.getVanillaMeta(candidateId);
        if (meta != null && meta.getPrerequisites() != null) {
            for (String p : meta.getPrerequisites()) {
                if (visited.add(p)) queue.add(p);
            }
        }

        while (!queue.isEmpty()) {
            String cur = queue.poll();
            if (cur.equals(targetId)) return true;
            var curAdv = cs.getAdvancement(cur);
            if (curAdv != null && curAdv.getPrerequisites() != null) {
                for (String p : curAdv.getPrerequisites()) {
                    if (visited.add(p)) queue.add(p);
                }
            }
            var curMeta = cs.getVanillaMeta(cur);
            if (curMeta != null && curMeta.getPrerequisites() != null) {
                for (String p : curMeta.getPrerequisites()) {
                    if (visited.add(p)) queue.add(p);
                }
            }
        }
        return false;
    }
}