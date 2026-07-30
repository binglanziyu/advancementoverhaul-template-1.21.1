/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonElement
 *  net.minecraft.advancements.AdvancementHolder
 *  net.minecraft.advancements.AdvancementNode
 *  net.minecraft.advancements.DisplayInfo
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.multiplayer.ClientAdvancements
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.network.chat.Component
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.Items
 *  net.minecraft.world.level.ItemLike
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.example.advancementoverhaul.compat.ftb;

import com.example.advancementoverhaul.compat.AdvancementRegistry;
import com.example.advancementoverhaul.compat.ftb.FtbQuestsBridge;
import com.example.advancementoverhaul.compat.ftb.FtbReflectionHelper;
import com.example.advancementoverhaul.data.ServerDataStore;
import com.example.advancementoverhaul.data.model.CustomAdvancement;
import com.google.gson.JsonElement;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FtbKsrSyncer {
    private static final Logger LOGGER = LoggerFactory.getLogger((String)"AdvancementOverhaul/KsrSyncer");
    private static volatile boolean ksrSynced = false;
    private static final Set<String> serverInjectedIds = new HashSet<String>();
    private static final Set<String> clientInjectedIds = new HashSet<String>();
    private static int clientKsrConsecutiveFailures = 0;

    private FtbKsrSyncer() {
    }

    public static boolean isKsrSynced() {
        return ksrSynced;
    }

    public static void syncToKnownServerRegistries(MinecraftServer server) {
        if (!FtbQuestsBridge.isLoaded()) {
            return;
        }
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
            for (CustomAdvancement adv : store.getAdvancements().values()) {
                ItemStack icon;
                Component name;
                Object info;
                ResourceLocation vanillaId = AdvancementRegistry.toVanillaId(adv.getId());
                if (clientMap.containsKey(vanillaId) || (info = FtbReflectionHelper.createAdvancementInfo(vanillaId, name = FtbKsrSyncer.resolveServerName(server, vanillaId, adv.getName()), icon = FtbKsrSyncer.resolveServerIcon(server, vanillaId, adv.getIcon()))) == null) continue;
                clientMap.put(vanillaId, info);
                serverMap.put(vanillaId, info);
                serverInjectedIds.add(vanillaId.toString());
                ++added;
            }
            Map<String, JsonElement> vanillaCache = store.getVanillaAdvRawCache();
            if (vanillaCache != null) {
                for (String advId : vanillaCache.keySet()) {
                    ItemStack icon;
                    Component name;
                    Object info;
                    ResourceLocation rl;
                    if (!store.isVanillaEnabled(advId) || (rl = ResourceLocation.tryParse((String)advId)) == null || clientMap.containsKey(rl) || (info = FtbReflectionHelper.createAdvancementInfo(rl, name = FtbKsrSyncer.resolveServerName(server, rl, advId), icon = FtbKsrSyncer.resolveServerIcon(server, rl, null))) == null) continue;
                    clientMap.put(rl, info);
                    serverMap.put(rl, info);
                    serverInjectedIds.add(rl.toString());
                    ++added;
                }
            }
            if (added > 0) {
                ksrSynced = true;
                LOGGER.info("Synced {} advancements to KSR (custom + enabled vanilla)", (Object)added);
            }
        }
        catch (Exception e) {
            LOGGER.warn("Failed to sync KnownServerRegistries (FTB version: {}): {}", (Object)FtbQuestsBridge.getFtbVersion(), (Object)e.getMessage());
        }
    }

    public static boolean syncClientKnownServerRegistries(Collection<String> advancementIds) {
        if (!FtbQuestsBridge.isLoaded()) {
            return true;
        }
        boolean customIdsFound = true;
        if (advancementIds == null || advancementIds.isEmpty()) {
            advancementIds = FtbKsrSyncer.getCustomAdvancementIdsFromVanillaTree();
            if (advancementIds.isEmpty()) {
                if (++clientKsrConsecutiveFailures % 100 == 1) {
                    LOGGER.warn("Cannot find custom advancement IDs in client tree (failures: {})", (Object)clientKsrConsecutiveFailures);
                }
                customIdsFound = false;
            } else {
                clientKsrConsecutiveFailures = 0;
            }
        }
        try {
            int removed;
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
            Map<ResourceLocation, DisplayInfo> clientDisplayMap = FtbKsrSyncer.collectClientDisplayMap();
            int added = 0;
            int scanned = advancementIds.size();
            for (String customId : advancementIds) {
                Object info;
                ItemStack icon;
                Component name;
                ResourceLocation vanillaId = customId.contains(":") ? ResourceLocation.parse((String)customId) : AdvancementRegistry.toVanillaId(customId);
                if (clientMap.containsKey(vanillaId)) continue;
                DisplayInfo display = clientDisplayMap.get(vanillaId);
                if (display != null) {
                    name = display.getTitle();
                    icon = display.getIcon();
                } else {
                    name = Component.literal((String)customId);
                    icon = ItemStack.EMPTY;
                }
                if ((info = FtbReflectionHelper.createAdvancementInfo(vanillaId, name, icon)) == null) continue;
                clientMap.put(vanillaId, info);
                clientInjectedIds.add(vanillaId.toString());
                ++added;
            }
            if (customIdsFound && (removed = FtbKsrSyncer.removeStaleClientKsrEntries(clientMap, advancementIds)) > 0) {
                LOGGER.debug("Client KSR: cleaned up {} stale entries", (Object)removed);
            }
            if (added > 0) {
                LOGGER.debug("Client KSR: injected {} custom IDs (scanned {}, map size {})", new Object[]{added, scanned, clientMap.size()});
            }
            return true;
        }
        catch (Exception e) {
            LOGGER.warn("Failed to sync client KnownServerRegistries (will retry): {}", (Object)e.getMessage());
            return false;
        }
    }

    private static Set<String> getCustomAdvancementIdsFromVanillaTree() {
        LinkedHashSet<String> ids = new LinkedHashSet<String>();
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.player.connection == null) {
                return ids;
            }
            ClientAdvancements clientAdvancements = mc.player.connection.getAdvancements();
            if (clientAdvancements == null) {
                return ids;
            }
            try {
                Method getTreeMethod = clientAdvancements.getClass().getMethod("getTree", new Class[0]);
                Object tree = getTreeMethod.invoke((Object)clientAdvancements, new Object[0]);
                if (tree != null) {
                    Method rootsMethod = tree.getClass().getMethod("roots", new Class[0]);
                    @SuppressWarnings("unchecked")
                    Iterable<AdvancementNode> roots = (Iterable<AdvancementNode>)rootsMethod.invoke(tree, new Object[0]);
                    for (AdvancementNode root : roots) {
                        FtbKsrSyncer.collectCustomAdvancementIds(root, ids);
                    }
                    if (!ids.isEmpty()) {
                        return ids;
                    }
                }
            }
            catch (NoSuchMethodException e) {
                LOGGER.debug("getTree() not available, trying progress map fallback");
            }
            try {
                Method progressMapMethod = clientAdvancements.getClass().getMethod("progress", new Class[0]);
                @SuppressWarnings("unchecked")
                Map<ResourceLocation, ?> progressMap = (Map<ResourceLocation, ?>)progressMapMethod.invoke((Object)clientAdvancements, new Object[0]);
                if (progressMap != null) {
                    for (ResourceLocation key : progressMap.keySet()) {
                        if (!AdvancementRegistry.isCustomAdvancement(key)) continue;
                        ids.add(key.toString());
                    }
                }
            }
            catch (NoSuchMethodException e) {
                LOGGER.debug("progress() not available either");
            }
        }
        catch (Exception e) {
            LOGGER.warn("Client KSR scan failed: {}", (Object)e.getMessage());
        }
        return ids;
    }

    private static void collectCustomAdvancementIds(AdvancementNode node, Set<String> ids) {
        String id = node.holder().id().toString();
        if (AdvancementRegistry.isCustomAdvancement(node.holder().id())) {
            ids.add(id);
        }
        for (AdvancementNode child : node.children()) {
            FtbKsrSyncer.collectCustomAdvancementIds(child, ids);
        }
    }

    private static int removeStaleClientKsrEntries(Map<ResourceLocation, Object> clientMap, Collection<String> advancementIds) {
        HashSet<String> validIds = new HashSet<String>();
        for (String id : advancementIds) {
            if (id.contains(":")) {
                validIds.add(id);
                continue;
            }
            validIds.add(AdvancementRegistry.toVanillaId(id).toString());
        }
        validIds.addAll(serverInjectedIds);
        int removed = 0;
        Iterator<String> iterator = clientInjectedIds.iterator();
        while (iterator.hasNext()) {
            String injectedId = iterator.next();
            if (validIds.contains(injectedId)) continue;
            ResourceLocation rl = ResourceLocation.tryParse((String)injectedId);
            if (rl != null) {
                clientMap.remove(rl);
            }
            iterator.remove();
            ++removed;
        }
        return removed;
    }

    public static Component resolveServerName(MinecraftServer server, ResourceLocation id, String fallbackName) {
        try {
            AdvancementHolder holder = server.getAdvancements().get(id);
            if (holder != null && holder.value().display().isPresent()) {
                return ((DisplayInfo)holder.value().display().get()).getTitle();
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        if (fallbackName != null && !fallbackName.isEmpty()) {
            return Component.literal((String)fallbackName);
        }
        return Component.literal((String)id.toString());
    }

    public static ItemStack resolveServerIcon(MinecraftServer server, ResourceLocation id, String fallbackIcon) {
        try {
            AdvancementHolder holder = server.getAdvancements().get(id);
            if (holder != null && holder.value().display().isPresent()) {
                return ((DisplayInfo)holder.value().display().get()).getIcon();
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return FtbKsrSyncer.parseItemIcon(fallbackIcon);
    }

    public static ItemStack parseItemIcon(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return ItemStack.EMPTY;
        }
        try {
            ResourceLocation rl = ResourceLocation.tryParse((String)itemId);
            if (rl == null) {
                return ItemStack.EMPTY;
            }
            Item item = BuiltInRegistries.ITEM.getOptional(rl).orElse(Items.NETHER_STAR);
            return new ItemStack((ItemLike)item);
        }
        catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private static Map<ResourceLocation, DisplayInfo> collectClientDisplayMap() {
        HashMap<ResourceLocation, DisplayInfo> map = new HashMap<ResourceLocation, DisplayInfo>();
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.player.connection == null) {
                return map;
            }
            ClientAdvancements clientAdvancements = mc.player.connection.getAdvancements();
            if (clientAdvancements == null) {
                return map;
            }
            Method getTreeMethod = clientAdvancements.getClass().getMethod("getTree", new Class[0]);
            Object tree = getTreeMethod.invoke((Object)clientAdvancements, new Object[0]);
            if (tree != null) {
                Method rootsMethod = tree.getClass().getMethod("roots", new Class[0]);
                @SuppressWarnings("unchecked")
                Iterable<AdvancementNode> roots = (Iterable<AdvancementNode>)rootsMethod.invoke(tree, new Object[0]);
                for (AdvancementNode root : roots) {
                    FtbKsrSyncer.collectDisplayInfo(root, map);
                }
            }
        }
        catch (Exception e) {
            LOGGER.debug("Failed to collect client advancement display map: {}", (Object)e.getMessage());
        }
        return map;
    }

    private static void collectDisplayInfo(AdvancementNode node, Map<ResourceLocation, DisplayInfo> map) {
        node.holder().value().display().ifPresent(d -> map.put(node.holder().id(), d));
        for (AdvancementNode child : node.children()) {
            FtbKsrSyncer.collectDisplayInfo(child, map);
        }
    }

    public static DisplayInfo findDisplayInfo(AdvancementNode node, ResourceLocation id) {
        if (id.equals((Object)node.holder().id()) && node.holder().value().display().isPresent()) {
            return (DisplayInfo)node.holder().value().display().get();
        }
        for (AdvancementNode child : node.children()) {
            DisplayInfo found = FtbKsrSyncer.findDisplayInfo(child, id);
            if (found == null) continue;
            return found;
        }
        return null;
    }
}

