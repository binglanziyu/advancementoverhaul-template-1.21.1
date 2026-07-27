package com.example.advancementoverhaul.compat;

import com.example.advancementoverhaul.ModInfo;
import com.example.advancementoverhaul.data.ClientDataStore;
import com.example.advancementoverhaul.data.ServerDataStore;
import dev.ftb.mods.ftblibrary.util.KnownServerRegistries;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * FTB Quests 兼容桥接（外观类）。
 * <p>
 * 将具体职责委托给以下子模块：
 * <ul>
 *   <li>{@link FtbReflectionHelper} — 反射句柄缓存与初始化</li>
 *   <li>{@link FtbKsrSyncer} — KnownServerRegistries 同步（服务端 + 客户端）</li>
 *   <li>{@link FtbQuestListener} — 任务完成监听（事件 + 轮询）</li>
 * </ul>
 * <p>
 * 本类仅保留：
 * <ul>
 *   <li>FTB Quests 加载检测</li>
 *   <li>属性变更通知（markDirty）</li>
 *   <li>客户端 AdvancementInfo 创建</li>
 *   <li>对各子模块的统一委托</li>
 * </ul>
 */
public final class FtbQuestsBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/FTBQuests");

    /** FTB Quests 是否已加载 */
    private static volatile Boolean loaded = null;

    /** 检测到的 FTB Quests 版本字符串 */
    private static volatile String ftbVersion = null;

    private FtbQuestsBridge() {}

    // ═══════════════ 加载检测 ═══════════════

    /**
     * 检测 FTB Quests 模组是否已加载。
     * 首次调用会触发 {@link FtbReflectionHelper#init()} 初始化反射句柄。
     */
    public static boolean isLoaded() {
        if (loaded == null) {
            try {
                Class.forName("dev.ftb.mods.ftbquests.FTBQuests");
                loaded = true;
                try {
                    Class<?> cls = Class.forName("dev.ftb.mods.ftbquests.FTBQuests");
                    ftbVersion = cls.getPackage().getImplementationVersion();
                    if (ftbVersion == null) ftbVersion = "unknown";
                } catch (Exception e) {
                    ftbVersion = "unknown";
                }
                LOGGER.info("FTB Quests detected (version: {}) — full integration enabled", ftbVersion);
                // Initialize reflection handles
                FtbReflectionHelper.init();
            } catch (ClassNotFoundException e) {
                loaded = false;
                LOGGER.info("FTB Quests not detected");
            }
        }
        return loaded;
    }

    /** 获取检测到的 FTB Quests 版本 */
    public static String getFtbVersion() { isLoaded(); return ftbVersion; }

    // ═══════════════ KSR 同步（委托给 FtbKsrSyncer） ═══════════════

    /** @see FtbKsrSyncer#isKsrSynced() */
    public static boolean isKsrSynced() {
        return FtbKsrSyncer.isKsrSynced();
    }

    /** @see FtbKsrSyncer#syncToKnownServerRegistries(MinecraftServer) */
    public static void syncToKnownServerRegistries(MinecraftServer server) {
        FtbKsrSyncer.syncToKnownServerRegistries(server);
        // Try to register event listener after first sync
        FtbQuestListener.tryRegisterEventListener(server);
    }

    /** @see FtbKsrSyncer#syncClientKnownServerRegistries(java.util.Collection) */
    public static boolean syncClientKnownServerRegistries(java.util.Collection<String> advancementIds) {
        return FtbKsrSyncer.syncClientKnownServerRegistries(advancementIds);
    }

    // ═══════════════ 任务完成监听（委托给 FtbQuestListener） ═══════════════

    /** @see FtbQuestListener#onServerTick(MinecraftServer) */
    public static void onServerTick(MinecraftServer server) {
        FtbQuestListener.onServerTick(server);
    }

    /** @see FtbQuestListener#onPlayerLogout(java.util.UUID) */
    public static void onPlayerLogout(java.util.UUID uuid) {
        FtbQuestListener.onPlayerLogout(uuid);
    }

    // ═══════════════ 属性变更通知 ═══════════════

    /**
     * 通知 FTB Quests 发生了属性级变更（名称、图标、描述、隐藏状态、前置条件等）。
     * 仅标记任务文件需要存盘。
     */
    public static void notifyAttributeChange(MinecraftServer server) {
        if (!isLoaded()) return;
        markDirty();
    }

    /**
     * 仅标记 FTB Quests 数据脏，用于纯布局操作。
     */
    public static void notifyPositionOrCategory(MinecraftServer server) {
        if (!isLoaded()) return;
        markDirty();
    }

    /** 反射调用 ServerQuestFile.markDirty() */
    private static void markDirty() {
        try {
            Object sqf = FtbReflectionHelper.getServerQuestFileInstance();
            if (sqf != null) {
                var md = sqf.getClass().getMethod("markDirty");
                md.invoke(sqf);
            }
        } catch (NoSuchMethodException e) {
            LOGGER.debug("markDirty() method not found: {}", e.getMessage());
        } catch (ReflectiveOperationException e) {
            LOGGER.debug("markDirty failed (reflection): {}", e.getMessage());
        } catch (Exception e) {
            LOGGER.debug("markDirty failed: {}", e.getMessage());
        }
    }

    // ═══════════════ 客户端 AdvancementInfo 创建 ═══════════════

    /**
     * 为指定成就 ID 创建客户端 AdvancementInfo。
     * 供 {@link AdvancementRewardMixin} 使用。
     */
    public static KnownServerRegistries.AdvancementInfo createClientAdvancementInfo(ResourceLocation id) {
        Component name = Component.literal(id.toString());
        ItemStack icon = ItemStack.EMPTY;
        Optional<DisplayInfo> display = getClientDisplayInfo(id);
        if (display.isPresent()) {
            name = display.get().getTitle();
            icon = display.get().getIcon();
        } else {
            ClientDataStore cs = ClientDataStore.getInstance();
            if (cs != null) {
                var entry = cs.getVanillaAdvEntry(id.toString());
                if (entry != null) {
                    if (entry.name() != null && !entry.name().isEmpty()) {
                        name = Component.literal(entry.name());
                    }
                    if (entry.icon() != null && !entry.icon().isEmpty()) {
                        icon = FtbKsrSyncer.parseItemIcon(entry.icon());
                    }
                }
            }
        }
        return new KnownServerRegistries.AdvancementInfo(id, name, icon);
    }

    private static Optional<DisplayInfo> getClientDisplayInfo(ResourceLocation id) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.player.connection == null) return Optional.empty();
            var clientAdvancements = mc.player.connection.getAdvancements();
            if (clientAdvancements == null) return Optional.empty();
            var getTreeMethod = clientAdvancements.getClass().getMethod("getTree");
            var tree = getTreeMethod.invoke(clientAdvancements);
            if (tree == null) return Optional.empty();
            var rootsMethod = tree.getClass().getMethod("roots");
            @SuppressWarnings("unchecked")
            Iterable<AdvancementNode> roots = (Iterable<AdvancementNode>) rootsMethod.invoke(tree);
            for (AdvancementNode root : roots) {
                DisplayInfo found = FtbKsrSyncer.findDisplayInfo(root, id);
                if (found != null) return Optional.of(found);
            }
        } catch (NoSuchMethodException e) {
            LOGGER.debug("getTree/roots not available: {}", e.getMessage());
        } catch (ReflectiveOperationException e) {
            LOGGER.debug("Failed to get client display for {}: {}", id, e.getMessage());
        } catch (Exception e) {
            LOGGER.debug("Failed to get client display for {}: {}", id, e.getMessage());
        }
        return Optional.empty();
    }
}
