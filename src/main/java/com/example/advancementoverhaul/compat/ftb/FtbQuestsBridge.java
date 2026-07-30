/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.advancements.AdvancementNode
 *  net.minecraft.advancements.DisplayInfo
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.multiplayer.ClientAdvancements
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.world.item.ItemStack
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.example.advancementoverhaul.compat.ftb;

import com.example.advancementoverhaul.compat.ftb.FtbKsrSyncer;
import com.example.advancementoverhaul.compat.ftb.FtbQuestListener;
import com.example.advancementoverhaul.compat.ftb.FtbReflectionHelper;
import com.example.advancementoverhaul.data.ClientDataStore;
import dev.ftb.mods.ftblibrary.util.KnownServerRegistries;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FtbQuestsBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger((String)"AdvancementOverhaul/FTBQuests");
    private static volatile Boolean loaded = null;
    private static volatile String ftbVersion = null;

    private FtbQuestsBridge() {
    }

    public static boolean isLoaded() {
        if (loaded == null) {
            try {
                Class.forName("dev.ftb.mods.ftbquests.FTBQuests");
                loaded = true;
                try {
                    Class<?> cls = Class.forName("dev.ftb.mods.ftbquests.FTBQuests");
                    ftbVersion = cls.getPackage().getImplementationVersion();
                    if (ftbVersion == null) {
                        ftbVersion = "unknown";
                    }
                }
                catch (Exception e) {
                    ftbVersion = "unknown";
                }
                LOGGER.info("FTB Quests detected (version: {}) \u2014 full integration enabled", (Object)ftbVersion);
                FtbReflectionHelper.init();
            }
            catch (ClassNotFoundException e) {
                loaded = false;
                LOGGER.info("FTB Quests not detected");
            }
        }
        return loaded;
    }

    public static String getFtbVersion() {
        FtbQuestsBridge.isLoaded();
        return ftbVersion;
    }

    public static boolean isKsrSynced() {
        return FtbKsrSyncer.isKsrSynced();
    }

    public static void syncToKnownServerRegistries(MinecraftServer server) {
        FtbKsrSyncer.syncToKnownServerRegistries(server);
        FtbQuestListener.tryRegisterEventListener(server);
    }

    public static boolean syncClientKnownServerRegistries(Collection<String> advancementIds) {
        return FtbKsrSyncer.syncClientKnownServerRegistries(advancementIds);
    }

    public static void onServerTick(MinecraftServer server) {
        FtbQuestListener.onServerTick(server);
    }

    public static void onPlayerLogout(UUID uuid) {
        FtbQuestListener.onPlayerLogout(uuid);
    }

    public static void notifyAttributeChange(MinecraftServer server) {
        if (!FtbQuestsBridge.isLoaded()) {
            return;
        }
        FtbQuestsBridge.markDirty();
    }

    public static void notifyPositionOrCategory(MinecraftServer server) {
        if (!FtbQuestsBridge.isLoaded()) {
            return;
        }
        FtbQuestsBridge.markDirty();
    }

    private static void markDirty() {
        try {
            Object sqf = FtbReflectionHelper.getServerQuestFileInstance();
            if (sqf != null) {
                Method md = sqf.getClass().getMethod("markDirty", new Class[0]);
                md.invoke(sqf, new Object[0]);
            }
        }
        catch (NoSuchMethodException e) {
            LOGGER.debug("markDirty() method not found: {}", (Object)e.getMessage());
        }
        catch (ReflectiveOperationException e) {
            LOGGER.debug("markDirty failed (reflection): {}", (Object)e.getMessage());
        }
        catch (Exception e) {
            LOGGER.debug("markDirty failed: {}", (Object)e.getMessage());
        }
    }

    public static KnownServerRegistries.AdvancementInfo createClientAdvancementInfo(ResourceLocation id) {
        Component name = Component.literal(id.toString());
        ItemStack icon = ItemStack.EMPTY;
        Optional<DisplayInfo> display = FtbQuestsBridge.getClientDisplayInfo(id);
        if (display.isPresent()) {
            name = display.get().getTitle();
            icon = display.get().getIcon();
        } else {
            ClientDataStore.VanillaAdvEntry entry;
            ClientDataStore cs = ClientDataStore.getInstance();
            if (cs != null && (entry = cs.getVanillaAdvEntry(id.toString())) != null) {
                if (entry.name() != null && !entry.name().isEmpty()) {
                    name = Component.literal(entry.name());
                }
                if (entry.icon() != null && !entry.icon().isEmpty()) {
                    icon = FtbKsrSyncer.parseItemIcon(entry.icon());
                }
            }
        }
        return new KnownServerRegistries.AdvancementInfo(id, name, icon);
    }

    private static Optional<DisplayInfo> getClientDisplayInfo(ResourceLocation id) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.player.connection == null) {
                return Optional.empty();
            }
            ClientAdvancements clientAdvancements = mc.player.connection.getAdvancements();
            if (clientAdvancements == null) {
                return Optional.empty();
            }
            Method getTreeMethod = clientAdvancements.getClass().getMethod("getTree", new Class[0]);
            Object tree = getTreeMethod.invoke((Object)clientAdvancements, new Object[0]);
            if (tree == null) {
                return Optional.empty();
            }
            Method rootsMethod = tree.getClass().getMethod("roots", new Class[0]);
            @SuppressWarnings("unchecked")
            Iterable<AdvancementNode> roots = (Iterable<AdvancementNode>)rootsMethod.invoke(tree, new Object[0]);
            for (AdvancementNode root : roots) {
                DisplayInfo found = FtbKsrSyncer.findDisplayInfo(root, id);
                if (found == null) continue;
                return Optional.of(found);
            }
        }
        catch (NoSuchMethodException e) {
            LOGGER.debug("getTree/roots not available: {}", (Object)e.getMessage());
        }
        catch (ReflectiveOperationException e) {
            LOGGER.debug("Failed to get client display for {}: {}", (Object)id, (Object)e.getMessage());
        }
        catch (Exception e) {
            LOGGER.debug("Failed to get client display for {}: {}", (Object)id, (Object)e.getMessage());
        }
        return Optional.empty();
    }
}

