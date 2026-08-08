package com.dreamer.ao.compat.ftb;

import com.dreamer.ao.data.ClientDataStore;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FtbQuestsBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(FtbQuestsBridge.class);
    private static volatile Boolean loaded = null;
    private static volatile String ftbVersion = null;

    private FtbQuestsBridge() {
    }

    public static boolean isLoaded() {
        if (loaded == null) {
            try {
                Class<?> ftbClass = Class.forName("dev.ftb.mods.ftbquests.FTBQuests");
                loaded = true;
                try {
                    ftbVersion = ftbClass.getPackage().getImplementationVersion();
                    if (ftbVersion == null) {
                        ftbVersion = "unknown";
                    }
                } catch (Exception e) {
                    ftbVersion = "unknown";
                }
                LOGGER.info("FTB Quests detected (version: {}) \u2014 full integration enabled", ftbVersion);
                // 校验关键 API 兼容性
                validateApiCompat();
                FtbReflectionHelper.init();
            } catch (ClassNotFoundException e) {
                loaded = false;
                LOGGER.info("FTB Quests not detected");
            }
        }
        return loaded;
    }

    /** 校验 FTB Quests 关键 API 是否存在，版本不匹配时输出警告。 */
    private static void validateApiCompat() {
        int failCount = 0;
        try {
            Class.forName("dev.ftb.mods.ftblibrary.util.KnownServerRegistries$AdvancementInfo");
        } catch (ClassNotFoundException e) {
            failCount++;
            LOGGER.warn("FTB Quests API incompatibility: AdvancementInfo class not found");
        }
        try {
            Class.forName("dev.ftb.mods.ftbquests.events.QuestCompletedEvent");
        } catch (ClassNotFoundException e) {
            failCount++;
            LOGGER.warn("FTB Quests API incompatibility: QuestCompletedEvent class not found");
        }
        try {
            Class.forName("dev.ftb.mods.ftbquests.quest.ServerQuestFile");
        } catch (ClassNotFoundException e) {
            failCount++;
            LOGGER.warn("FTB Quests API incompatibility: ServerQuestFile class not found");
        }
        try {
            Class.forName("dev.ftb.mods.ftbquests.quest.BaseQuestFile")
                    .getMethod("getAllTeamData");
        } catch (NoSuchMethodException e) {
            failCount++;
            LOGGER.warn("FTB Quests API incompatibility: BaseQuestFile.getAllTeamData() not found");
        } catch (ClassNotFoundException e) {
            failCount++;
            LOGGER.warn("FTB Quests API incompatibility: BaseQuestFile class not found");
        }
        try {
            Class.forName("dev.ftb.mods.ftbquests.quest.TeamData")
                    .getMethod("isCompleted", Object.class);
        } catch (NoSuchMethodException e) {
            failCount++;
            LOGGER.warn("FTB Quests API incompatibility: TeamData.isCompleted() not found");
        } catch (ClassNotFoundException e) {
            failCount++;
            LOGGER.warn("FTB Quests API incompatibility: TeamData class not found");
        }
        if (failCount > 0) {
            LOGGER.warn("FTB Quests API compatibility check failed on {} class(es). Integration may be limited.", failCount);
        }
    }

    public static String getFtbVersion() {
        isLoaded();
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
        if (!isLoaded()) {
            return;
        }
        markDirty();
    }

    private static void markDirty() {
        try {
            Object sqf = FtbReflectionHelper.getServerQuestFileInstance();
            if (sqf != null) {
                Method md = sqf.getClass().getMethod("markDirty");
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
                ClientDataStore.VanillaAdvEntry entry = cs.getVanillaAdvEntry(id.toString());
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
        // 客户端专属：Minecraft.getInstance() 仅能在 Dist.CLIENT 执行，
        // 服务端调用时直接跳过，避免加载 client 类触发 NoClassDefFoundError。
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) {
            return Optional.empty();
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.player.connection == null) {
                return Optional.empty();
            }
            ClientAdvancements clientAdvancements = mc.player.connection.getAdvancements();
            if (clientAdvancements == null) {
                return Optional.empty();
            }
            Method getTreeMethod = clientAdvancements.getClass().getMethod("getTree");
            Object tree = getTreeMethod.invoke(clientAdvancements);
            if (tree == null) {
                return Optional.empty();
            }
            Method rootsMethod = tree.getClass().getMethod("roots");
            @SuppressWarnings("unchecked")
            Iterable<AdvancementNode> roots = (Iterable<AdvancementNode>) rootsMethod.invoke(tree);
            for (AdvancementNode root : roots) {
                DisplayInfo found = FtbKsrSyncer.findDisplayInfo(root, id);
                if (found == null) continue;
                return Optional.of(found);
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
