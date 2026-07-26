package com.example.advancementoverhaul.data;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;

/**
 * Client-side data cache for advancements, completions, tabs, and vanilla state.
 *
 * <p><b>Threading model:</b> All field assignments use volatile references for
 * visibility. Bulk-update methods (e.g. {@link #setAdvancements}) replace the
 * entire reference atomically. Incremental methods (e.g. {@link #updateCompletion})
 * are expected to be called from the client main thread only — this is the
 * Minecraft client's network handler thread which processes all incoming packets
 * on the main thread. If this assumption changes, the incremental methods would
 * need synchronization.
 */
public class ClientDataStore {
    private static final ClientDataStore INSTANCE = new ClientDataStore();

    public record VanillaAdvEntry(String id, String name, String desc, boolean hidden,
                                  String nameKey, String descKey, String rootTab, int x, int y, String icon) {
        public VanillaAdvEntry(String id, String name, String desc, boolean hidden) {
            this(id, name, desc, hidden, null, null, null, 0, 0, null);
        }
    }

    private volatile Map<String, DataStore.CustomAdvancement> advancements = new HashMap<>();
    private volatile Map<String, DimensionLock> dimensionLocks = new HashMap<>();
    private volatile Map<String, Boolean> completedAdvancements = new HashMap<>();
    private final AtomicInteger completedCount = new AtomicInteger(0);
    private volatile Map<String, Integer> advancementProgress = new HashMap<>();
    private volatile List<String> customTabs = new ArrayList<>();
    private volatile Set<String> disabledVanillaAdvancements = new HashSet<>();
    private volatile Set<String> enabledVanillaAdvancements = new HashSet<>();
    private volatile Set<String> pendingAdvancements = new HashSet<>();
    private volatile List<VanillaAdvEntry> vanillaAdvancements = new ArrayList<>();
    private volatile Map<String, VanillaAdvEntry> vanillaAdvEntryMap = new HashMap<>();
    private volatile Map<String, DataStore.VanillaAdvMeta> vanillaMeta = new HashMap<>();
    private volatile Map<String, String> vanillaParentMap = new HashMap<>();
    private volatile List<String> tabOrder = new ArrayList<>();

    private volatile boolean tabsDirty = true;
    private volatile List<String> cachedTabs = Collections.emptyList();
    private volatile boolean tabIndexDirty = true;
    private volatile Map<String, List<DataStore.CustomAdvancement>> cachedTabIndex = Collections.emptyMap();

    private ClientDataStore() {}
    public static ClientDataStore getInstance() { return INSTANCE; }

    public void markTabsDirty() { tabsDirty = true; tabIndexDirty = true; }

    public void setAdvancements(Map<String, DataStore.CustomAdvancement> map) { this.advancements = map; markTabsDirty(); }
    public Map<String, DataStore.CustomAdvancement> getAdvancements() { return advancements; }
    public DataStore.CustomAdvancement getAdvancement(String id) { return advancements.get(id); }

    public void setDimensionLocks(Map<String, DimensionLock> map) { this.dimensionLocks = map; }
    public Map<String, DimensionLock> getDimensionLocks() { return dimensionLocks; }

    public void setCompletedAdvancements(Map<String, Boolean> map) {
        this.completedAdvancements = map;
        int count = 0;
        for (Boolean v : completedAdvancements.values())
            if (Boolean.TRUE.equals(v)) count++;
        completedCount.set(count);
    }
    public boolean isCompleted(String advId) { return Boolean.TRUE.equals(completedAdvancements.get(advId)); }
    public void updateCompletion(String advId, boolean completed) {
        Boolean old = completedAdvancements.get(advId);
        completedAdvancements.put(advId, completed);
        if (completed && !Boolean.TRUE.equals(old)) completedCount.incrementAndGet();
        else if (!completed && Boolean.TRUE.equals(old)) completedCount.decrementAndGet();
    }
    public void setAdvancementProgress(Map<String, Integer> map) { this.advancementProgress = map; }
    public int getProgress(String advId) { return advancementProgress.getOrDefault(advId, 0); }
    public void updateProgress(String advId, int progress) { advancementProgress.put(advId, progress); }

    public void setCustomTabs(List<String> tabs) { this.customTabs = tabs != null ? tabs : new ArrayList<>(); markTabsDirty(); }
    public List<String> getCustomTabs() { return customTabs; }

    public void setTabOrder(List<String> order) { this.tabOrder = order != null ? order : new ArrayList<>(); markTabsDirty(); }
    public List<String> getTabOrder() { return tabOrder; }

    public boolean isVanillaEnabled(String id) {
        if (enabledVanillaAdvancements.contains(id)) return true;
        if (disabledVanillaAdvancements.contains(id)) return false;
        try {
            return com.example.advancementoverhaul.Config.VANILLA_DEFAULT_ENABLED.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }
    public void setDisabledVanilla(Set<String> set) { this.disabledVanillaAdvancements = set != null ? set : new HashSet<>(); }
    public Set<String> getDisabledVanilla() { return disabledVanillaAdvancements; }
    public void setEnabledVanilla(Set<String> set) { this.enabledVanillaAdvancements = set != null ? set : new HashSet<>(); }

    // ═══════════════ Pending ═══════════════

    public void setPendingAdvancements(Set<String> set) {
        this.pendingAdvancements = set != null ? new HashSet<>(set) : new HashSet<>();
    }
    public Set<String> getPendingAdvancements() { return pendingAdvancements; }
    public boolean isPending(String advId) { return pendingAdvancements.contains(advId); }
    public void updatePending(String advId, boolean pending) {
        if (pending) pendingAdvancements.add(advId);
        else pendingAdvancements.remove(advId);
    }

    public Set<String> getEnabledVanilla() { return enabledVanillaAdvancements; }

    public void setVanillaAdvancements(List<VanillaAdvEntry> list) {
        this.vanillaAdvancements = list != null ? list : new ArrayList<>();
        Map<String, VanillaAdvEntry> newMap = new HashMap<>();
        for (var e : vanillaAdvancements) newMap.put(e.id(), e);
        vanillaAdvEntryMap = newMap;
        markTabsDirty();
    }
    public List<VanillaAdvEntry> getVanillaAdvancements() { return vanillaAdvancements; }
    public VanillaAdvEntry getVanillaAdvEntry(String id) { return vanillaAdvEntryMap.get(id); }

    public void setVanillaMeta(Map<String, DataStore.VanillaAdvMeta> meta) { this.vanillaMeta = meta != null ? meta : new HashMap<>(); }
    public Map<String, DataStore.VanillaAdvMeta> getVanillaMeta() { return vanillaMeta; }
    public DataStore.VanillaAdvMeta getVanillaMeta(String id) { return vanillaMeta.get(id); }

    public void setVanillaParentMap(Map<String, String> map) { this.vanillaParentMap = map != null ? map : new HashMap<>(); }
    public Map<String, String> getVanillaParentMap() { return vanillaParentMap; }

    public String getVanillaDisplayTab(String vanillaId) {
        DataStore.VanillaAdvMeta meta = vanillaMeta.get(vanillaId);
        if (meta != null && meta.getTab() != null && !meta.getTab().isEmpty()) return meta.getTab();
        return DataStore.TAB_VANILLA;
    }

    public int[] getVanillaPosition(String vanillaId) {
        DataStore.VanillaAdvMeta meta = vanillaMeta.get(vanillaId);
        if (meta != null && meta.hasPosition()) return new int[]{meta.getX(), meta.getY()};
        return null;
    }

    // ═══════════════ 标签页查询 ═══════════════

    public List<String> getTabs() {
        if (!tabsDirty) return cachedTabs;

        Set<String> usedTabs = new HashSet<>();
        for (var adv : advancements.values())
            if (adv.getTab() != null && !adv.getTab().isEmpty()) usedTabs.add(adv.getTab());
        for (var meta : vanillaMeta.values())
            if (meta.getTab() != null && !meta.getTab().isEmpty()) usedTabs.add(meta.getTab());

        LinkedHashSet<String> all = new LinkedHashSet<>();
        all.add(DataStore.TAB_VANILLA);
        all.add(DataStore.TAB_DEFAULT);

        for (String t : tabOrder) {
            if (t.equals(DataStore.TAB_VANILLA) || t.equals(DataStore.TAB_DEFAULT)) continue;
            if (DataStore.isBuiltinTab(t) && !usedTabs.contains(t)) continue;
            all.add(t);
        }

        for (String t : DataStore.BUILTIN_TABS) {
            if (t.equals(DataStore.TAB_VANILLA) || t.equals(DataStore.TAB_DEFAULT)) continue;
            if (all.contains(t)) continue;
            if (usedTabs.contains(t)) all.add(t);
        }

        all.addAll(customTabs);

        for (String t : usedTabs) {
            if (!all.contains(t)) all.add(t);
        }

        cachedTabs = new ArrayList<>(all);
        tabsDirty = false;
        return cachedTabs;
    }

    public boolean isBuiltinTab(String tab) { return DataStore.isBuiltinTab(tab); }

    public List<DataStore.CustomAdvancement> getAdvancementsByTab(String tab) {
        if (tab == null || tab.isEmpty()) return new ArrayList<>(advancements.values());
        if (tabIndexDirty) rebuildTabIndex();
        List<DataStore.CustomAdvancement> result = cachedTabIndex.get(tab);
        return result != null ? new ArrayList<>(result) : Collections.emptyList();
    }



    /**
     * Returns all vanilla advancement IDs assigned to the given tab via metadata.
     */
    public List<String> getVanillaIdsByTab(String tab) {
        List<String> ids = new ArrayList<>();
        for (var entry : vanillaMeta.entrySet()) {
            if (tab.equals(entry.getValue().getTab())) ids.add(entry.getKey());
        }
        return ids;
    }

    /**
     * Returns all advancement IDs (custom + vanilla) that belong to the given tab,
     * suitable for use as prerequisite candidates.
     */
    public List<String> getAllIdsByTab(String tab) {
        List<String> ids = new ArrayList<>();
        for (var adv : getAdvancementsByTab(tab)) ids.add(adv.getId());
        ids.addAll(getVanillaIdsByTab(tab));
        return ids;
    }
    private void rebuildTabIndex() {
        Map<String, List<DataStore.CustomAdvancement>> index = new HashMap<>();
        for (DataStore.CustomAdvancement adv : advancements.values())
            if (adv.getTab() != null && !adv.getTab().isEmpty())
                index.computeIfAbsent(adv.getTab(), k -> new ArrayList<>()).add(adv);
        cachedTabIndex = index;
        tabIndexDirty = false;
    }

    public List<DataStore.CustomAdvancement> getHiddenAdvancements() {
        List<DataStore.CustomAdvancement> list = new ArrayList<>();
        for (DataStore.CustomAdvancement adv : advancements.values()) if (adv.isHidden()) list.add(adv);
        return list;
    }

    public int getTotalCount() { return advancements.size(); }
    public int getCompletedCount() { return completedCount.get(); }
    public int getTabTotalCount(String tab) {
        if (tabIndexDirty) rebuildTabIndex();
        int count = 0;
        List<DataStore.CustomAdvancement> list = cachedTabIndex.get(tab);
        if (list != null) count += list.size();
        if (DataStore.TAB_VANILLA.equals(tab)) {
            for (var e : vanillaAdvancements) {
                DataStore.VanillaAdvMeta meta = vanillaMeta.get(e.id());
                if (meta == null || meta.getTab() == null || meta.getTab().isEmpty()
                        || DataStore.TAB_VANILLA.equals(meta.getTab())) {
                    count++;
                }
            }
        }
        return count;
    }

    public int getTabCompletedCount(String tab) {
        if (tabIndexDirty) rebuildTabIndex();
        int count = 0;
        List<DataStore.CustomAdvancement> list = cachedTabIndex.get(tab);
        if (list != null) {
            for (var a : list) if (isCompleted(a.getId())) count++;
        }
        if (DataStore.TAB_VANILLA.equals(tab)) {
            for (var e : vanillaAdvancements) {
                DataStore.VanillaAdvMeta meta = vanillaMeta.get(e.id());
                if (meta == null || meta.getTab() == null || meta.getTab().isEmpty()
                        || DataStore.TAB_VANILLA.equals(meta.getTab())) {
                    if (isCompleted(e.id())) count++;
                }
            }
        }
        return count;
    }
}