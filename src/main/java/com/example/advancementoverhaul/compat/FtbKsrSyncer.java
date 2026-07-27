package com.example.advancementoverhaul.compat;

import com.example.advancementoverhaul.data.DataStore;
import com.example.advancementoverhaul.data.ServerDataStore;
import com.example.advancementoverhaul.data.model.CustomAdvancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * FTB Quests KnownServerRegistries 同步模块。
 * <p>
 * 从 {@link FtbQuestsBridge} 拆分而来，专门负责：
 * <ul>
 *   <li>服务端 KSR 注入（修复 AdvancementReward NPE 崩溃）</li>
 *   <li>客户端 KSR 注入与维护（修复 FTB Quests UI 的 NPE）</li>
 *   <li>客户端进度树扫描（获取显示名/图标）</li>
 * </ul>
 *
 * <h2>KSR 清理的安全策略</h2>
 * <p>
 * {@code KnownServerRegistries.client.advancements} 是一个<b>共享</b>的 Map，
 * 同时被本模组和 FTB Quests 原生代码（如 {@code AdvancementReward}）使用。
 * <p>
 * 如果清理时遍历整个 Map 并删除"不在我们允许列表中的"条目（旧实现），
 * 会误删 FTB Quests 原生条目——当 FTB Quests 渲染 AdvancementReward 配置界面时，
 * 其中的 {@code Map.get()} 返回 null → NPE → 游戏崩溃。
 * <p>
 * <b>正确做法</b>：用 {@link #serverInjectedIds} 和 {@link #clientInjectedIds}
 * 分别追踪服务端和客户端注入的 ID，清理时<b>只遍历自己的追踪集合</b>，
 * 只删除那些我们曾经注入、但现在已失效的条目。FTB Quests 的原生条目完全不受影响。
 */
public final class FtbKsrSyncer {

    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/KsrSyncer");

    /** KnownServerRegistries 是否已成功注入 */
    private static volatile boolean ksrSynced = false;

    /**
     * 服务端侧注入的 advancement ID 集合。
     * <p>
     * 作用：客户端清理时，只删除 {@link #clientInjectedIds} 中不再有效的 ID。
     * 绝不能遍历 KSR 的 clientMap.keySet() 做删除——那会误删 FTB Quests 原生条目。
     *
     * @see #removeStaleClientKsrEntries
     */
    private static final Set<String> serverInjectedIds = new HashSet<>();
    /**
     * 客户端侧已注入的 advancement ID 集合。
     * <p>
     * 每次 {@link #syncClientKnownServerRegistries} 中成功 put 新条目时记录，
     * 在 {@link #removeStaleClientKsrEntries} 中清理失效条目时作为遍历依据。
     */
    private static final Set<String> clientInjectedIds = new HashSet<>();

    /** 客户端侧连续失败次数（用于减少日志噪音） */
    private static int clientKsrConsecutiveFailures = 0;

    private FtbKsrSyncer() {}

    public static boolean isKsrSynced() { return ksrSynced; }

    // ═══════════════ 服务端 KSR 同步 ═══════════════

    /**
     * 将自定义进度同步到 FTB 的 KnownServerRegistries（服务端侧）。
     * <p>
     * <b>为什么需要同时写入 clientMap 和 serverMap？</b>
     * 服务端 KSR 的 advancements 用于 FTB Quests 的 AdvancementReward 下拉菜单数据源，
     * 需要包含所有可选择的 advancement ID。同时写入两个 map 确保服务端和客户端
     * 都拥有完整的数据，防止 AdvancementReward 在任一侧查询时返回 null。
     * <p>
     * 注入的 ID 会被记录到 {@link #serverInjectedIds}，供客户端清理阶段参考。
     */
    public static void syncToKnownServerRegistries(MinecraftServer server) {
        if (!FtbQuestsBridge.isLoaded()) return;

        try {
            Object clientKsr = FtbReflectionHelper.getKsrClient();
            Object serverKsr = FtbReflectionHelper.getKsrServer();

            if (clientKsr == null || serverKsr == null) {
                LOGGER.debug("KnownServerRegistries not initialized yet, skipping server-side sync");
                return;
            }

            Map<ResourceLocation, Object> clientMap = FtbReflectionHelper.getKsrAdvancements(clientKsr);
            Map<ResourceLocation, Object> serverMap = FtbReflectionHelper.getKsrAdvancements(serverKsr);

            if (clientMap == null || serverMap == null) {
                LOGGER.debug("KSR advancements maps not initialized yet, skipping server-side sync");
                return;
            }

            ServerDataStore store = ServerDataStore.getInstance();
            int added = 0;

            // 1. 注入自定义进度
            for (CustomAdvancement adv : store.getAdvancements().values()) {
                ResourceLocation vanillaId = AdvancementRegistry.toVanillaId(adv.getId());
                if (!clientMap.containsKey(vanillaId)) {
                    Component name = resolveServerName(server, vanillaId, adv.getName());
                    ItemStack icon = resolveServerIcon(server, vanillaId, adv.getIcon());
                    Object info = FtbReflectionHelper.createAdvancementInfo(vanillaId, name, icon);
                    if (info != null) {
                        clientMap.put(vanillaId, info);
                        serverMap.put(vanillaId, info);
                        serverInjectedIds.add(vanillaId.toString());
                        added++;
                    }
                }
            }

            // 2. 注入已启用的原版/模组进度
            Map<String, com.google.gson.JsonElement> vanillaCache = store.getVanillaAdvRawCache();
            if (vanillaCache != null) {
                for (String advId : vanillaCache.keySet()) {
                    if (!store.isVanillaEnabled(advId)) continue;
                    ResourceLocation rl = ResourceLocation.tryParse(advId);
                    if (rl == null || clientMap.containsKey(rl)) continue;
                    Component name = resolveServerName(server, rl, advId);
                    ItemStack icon = resolveServerIcon(server, rl, null);
                    Object info = FtbReflectionHelper.createAdvancementInfo(rl, name, icon);
                    if (info != null) {
                        clientMap.put(rl, info);
                        serverMap.put(rl, info);
                        serverInjectedIds.add(rl.toString());
                        added++;
                    }
                }
            }

            if (added > 0) {
                ksrSynced = true;
                LOGGER.info("Synced {} advancements to KSR (custom + enabled vanilla)", added);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to sync KnownServerRegistries (FTB version: {}): {}",
                    FtbQuestsBridge.getFtbVersion(), e.getMessage());
        }
    }

    // ═══════════════ 客户端 KSR 同步 ═══════════════

    /**
     * 客户端侧 KnownServerRegistries 注入与维护。
     * <p>
     * 只向 KSR 中添加自定义进度条目，不删除 FTB Quests 原生条目。
     * 使用 {@link #serverInjectedIds} 追踪服务端注入的 ID，
     * 使用 {@link #clientInjectedIds} 追踪客户端注入的 ID。
     */
    public static boolean syncClientKnownServerRegistries(Collection<String> advancementIds) {
        if (!FtbQuestsBridge.isLoaded()) return true;

        boolean customIdsFound = true;
        if (advancementIds == null || advancementIds.isEmpty()) {
            advancementIds = getCustomAdvancementIdsFromVanillaTree();
            if (advancementIds.isEmpty()) {
                clientKsrConsecutiveFailures++;
                if (clientKsrConsecutiveFailures % 100 == 1) {
                    LOGGER.warn("Cannot find custom advancement IDs in client tree (failures: {})",
                            clientKsrConsecutiveFailures);
                }
                customIdsFound = false;
            } else {
                clientKsrConsecutiveFailures = 0;
            }
        }

        try {
            Object clientKsr = FtbReflectionHelper.getKsrClient();
            if (clientKsr == null) {
                LOGGER.debug("KSR.client is null, will retry later");
                return false;
            }

            Map<ResourceLocation, Object> clientMap = FtbReflectionHelper.getKsrAdvancements(clientKsr);
            if (clientMap == null || clientMap.isEmpty()) {
                LOGGER.debug("KSR.client advancements map is null/empty, deferring injection");
                return true;
            }

            Map<ResourceLocation, DisplayInfo> clientDisplayMap = collectClientDisplayMap();

            int added = 0;
            int scanned = advancementIds.size();
            for (String customId : advancementIds) {
                ResourceLocation vanillaId;
                if (customId.contains(":")) {
                    vanillaId = ResourceLocation.parse(customId);
                } else {
                    vanillaId = AdvancementRegistry.toVanillaId(customId);
                }
                if (!clientMap.containsKey(vanillaId)) {
                    Component name;
                    ItemStack icon;
                    DisplayInfo display = clientDisplayMap.get(vanillaId);
                    if (display != null) {
                        name = display.getTitle();
                        icon = display.getIcon();
                    } else {
                        name = Component.literal(customId);
                        icon = ItemStack.EMPTY;
                    }
                    Object info = FtbReflectionHelper.createAdvancementInfo(vanillaId, name, icon);
                    if (info != null) {
                        clientMap.put(vanillaId, info);
                        clientInjectedIds.add(vanillaId.toString());
                        added++;
                    }
                }
            }

            if (customIdsFound) {
                int removed = removeStaleClientKsrEntries(clientMap, advancementIds);
                if (removed > 0) {
                    LOGGER.debug("Client KSR: cleaned up {} stale entries", removed);
                }
            }

            if (added > 0) {
                LOGGER.debug("Client KSR: injected {} custom IDs (scanned {}, map size {})",
                        added, scanned, clientMap.size());
            }
            return true;
        } catch (Exception e) {
            LOGGER.warn("Failed to sync client KnownServerRegistries (will retry): {}", e.getMessage());
            return false;
        }
    }

    // ═══════════════ 客户端进度树扫描 ═══════════════

    private static Set<String> getCustomAdvancementIdsFromVanillaTree() {
        Set<String> ids = new LinkedHashSet<>();
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.player.connection == null) return ids;
            var clientAdvancements = mc.player.connection.getAdvancements();
            if (clientAdvancements == null) return ids;

            try {
                var getTreeMethod = clientAdvancements.getClass().getMethod("getTree");
                var tree = getTreeMethod.invoke(clientAdvancements);
                if (tree != null) {
                    var rootsMethod = tree.getClass().getMethod("roots");
                    @SuppressWarnings("unchecked")
                    var roots = (Iterable<AdvancementNode>) rootsMethod.invoke(tree);
                    for (AdvancementNode root : roots) {
                        collectCustomAdvancementIds(root, ids);
                    }
                    if (!ids.isEmpty()) return ids;
                }
            } catch (NoSuchMethodException e) {
                LOGGER.debug("getTree() not available, trying progress map fallback");
            }

            try {
                var progressMapMethod = clientAdvancements.getClass().getMethod("progress");
                @SuppressWarnings("unchecked")
                var progressMap = (Map<ResourceLocation, ?>) progressMapMethod.invoke(clientAdvancements);
                if (progressMap != null) {
                    for (ResourceLocation key : progressMap.keySet()) {
                        if (AdvancementRegistry.isCustomAdvancement(key)) {
                            ids.add(key.toString());
                        }
                    }
                }
            } catch (NoSuchMethodException e) {
                LOGGER.debug("progress() not available either");
            }
        } catch (Exception e) {
            LOGGER.warn("Client KSR scan failed: {}", e.getMessage());
        }
        return ids;
    }

    private static void collectCustomAdvancementIds(AdvancementNode node, Set<String> ids) {
        String id = node.holder().id().toString();
        if (AdvancementRegistry.isCustomAdvancement(node.holder().id())) {
            ids.add(id);
        }
        for (AdvancementNode child : node.children()) {
            collectCustomAdvancementIds(child, ids);
        }
    }

    // ═══════════════ KSR 清理（仅清理我们注入的条目） ═══════════════

    /**
     * 清理客户端 KSR 中由本模组注入但已失效的条目。
     * <p>
     * <b>为什么不能遍历 clientMap.keySet()？</b>
     * KSR 的 advancements Map 是 FTB Quests 和我们共用的。FTB Quests 原生代码
     * （如 AdvancementReward）需要其中某些条目在渲染时存在。如果直接遍历 Map
     * 的 keySet 并删除"不在 keepSet 中"的条目，会误删 FTB Quests 自己的数据，
     * 导致 {@code Map.get() → null → NPE} 崩溃。
     * <p>
     * <b>正确做法</b>：只遍历 {@link #clientInjectedIds}——这是我们自己记录过的
     * 注入 ID 集合。检查其中哪些已不再有效（不在当前的 advancementIds 和
     * serverInjectedIds 中），只从 Map 中移除这些。FTB Quests 的原生条目
     * 完全不会被我们的迭代器触及。
     *
     * @param clientMap KSR.client 中的 advancements Map（只读引用，我们不移除 FTB 原生条目）
     * @param advancementIds 当前客户端进度树中的自定义进度 ID 集合
     * @return 被移除的条目数量
     */
    private static int removeStaleClientKsrEntries(Map<ResourceLocation, Object> clientMap,
                                                     Collection<String> advancementIds) {
        // 计算当前有效 ID 集合
        Set<String> validIds = new HashSet<>();
        for (String id : advancementIds) {
            if (id.contains(":")) {
                validIds.add(id);
            } else {
                validIds.add(AdvancementRegistry.toVanillaId(id).toString());
            }
        }
        // 服务端注入的 ID 也视为有效
        validIds.addAll(serverInjectedIds);

        int removed = 0;
        // 只遍历我们注入过的 ID
        Iterator<String> iterator = clientInjectedIds.iterator();
        while (iterator.hasNext()) {
            String injectedId = iterator.next();
            if (!validIds.contains(injectedId)) {
                ResourceLocation rl = ResourceLocation.tryParse(injectedId);
                if (rl != null) {
                    clientMap.remove(rl);
                }
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    // ═══════════════ 显示名/图标解析 ═══════════════

    public static Component resolveServerName(MinecraftServer server, ResourceLocation id, String fallbackName) {
        try {
            AdvancementHolder holder = server.getAdvancements().get(id);
            if (holder != null && holder.value().display().isPresent()) {
                return holder.value().display().get().getTitle();
            }
        } catch (Exception ignored) {
        }
        if (fallbackName != null && !fallbackName.isEmpty()) {
            return Component.literal(fallbackName);
        }
        return Component.literal(id.toString());
    }

    public static ItemStack resolveServerIcon(MinecraftServer server, ResourceLocation id, String fallbackIcon) {
        try {
            AdvancementHolder holder = server.getAdvancements().get(id);
            if (holder != null && holder.value().display().isPresent()) {
                return holder.value().display().get().getIcon();
            }
        } catch (Exception ignored) {
        }
        return parseItemIcon(fallbackIcon);
    }

    public static ItemStack parseItemIcon(String itemId) {
        if (itemId == null || itemId.isEmpty()) return ItemStack.EMPTY;
        try {
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl == null) return ItemStack.EMPTY;
            Item item = BuiltInRegistries.ITEM.getOptional(rl).orElse(Items.NETHER_STAR);
            return new ItemStack(item);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    // ═══════════════ 客户端显示 Map ═══════════════

    private static Map<ResourceLocation, DisplayInfo> collectClientDisplayMap() {
        Map<ResourceLocation, DisplayInfo> map = new HashMap<>();
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.player.connection == null) return map;
            var clientAdvancements = mc.player.connection.getAdvancements();
            if (clientAdvancements == null) return map;
            var getTreeMethod = clientAdvancements.getClass().getMethod("getTree");
            var tree = getTreeMethod.invoke(clientAdvancements);
            if (tree != null) {
                var rootsMethod = tree.getClass().getMethod("roots");
                @SuppressWarnings("unchecked")
                Iterable<AdvancementNode> roots = (Iterable<AdvancementNode>) rootsMethod.invoke(tree);
                for (AdvancementNode root : roots) {
                    collectDisplayInfo(root, map);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to collect client advancement display map: {}", e.getMessage());
        }
        return map;
    }

    private static void collectDisplayInfo(AdvancementNode node, Map<ResourceLocation, DisplayInfo> map) {
        node.holder().value().display().ifPresent(d -> map.put(node.holder().id(), d));
        for (AdvancementNode child : node.children()) {
            collectDisplayInfo(child, map);
        }
    }

    public static DisplayInfo findDisplayInfo(AdvancementNode node, ResourceLocation id) {
        if (id.equals(node.holder().id()) && node.holder().value().display().isPresent()) {
            return node.holder().value().display().get();
        }
        for (AdvancementNode child : node.children()) {
            DisplayInfo found = findDisplayInfo(child, id);
            if (found != null) return found;
        }
        return null;
    }
}
