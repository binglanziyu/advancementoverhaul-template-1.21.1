package com.dreamer.ao.compat.ftb;

import com.dreamer.ao.data.ClientDataStore;
import com.mojang.logging.LogUtils;
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
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * FTB Quests 兼容层的真实实现。
 *
 * <h2>可见性说明</h2>
 * 本类<b>仅</b>允许被两类代码引用：
 * <ol>
 *   <li>{@link FtbCompatProvider}——且只通过反射，不产生编译期符号引用；</li>
 *   <li>{@code com.dreamer.ao.mixin.ftb} 下的 Mixin——它们本身只在 FTB 存在时织入，
 *       且需要 {@link KnownServerRegistries.AdvancementInfo} 这类 FTB 原生类型。</li>
 * </ol>
 * 主逻辑（网络、命令、事件）一律改走 {@link FtbCompatService} 接口。
 *
 * <h2>为何同时保留静态方法与实例方法</h2>
 * 静态方法服务于 Mixin（Mixin 中注入点无法方便地持有服务实例）；
 * 实例方法用于满足 {@link FtbCompatService} 契约，内部直接委托给对应静态方法，
 * 二者共享同一份状态，不存在双份缓存不一致的风险。
 */
public final class FtbQuestsBridge implements FtbCompatService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Boolean loaded = null;
    private static volatile String ftbVersion = null;

    /**
     * 供 {@link FtbCompatProvider} 反射实例化。
     * <p>
     * 保持 public 是因为反射调用 {@code getDeclaredConstructor().newInstance()}
     * 时若为私有构造器需额外 {@code setAccessible}，在模块化环境下易被拒绝。
     */
    public FtbQuestsBridge() {
    }

    /**
     * 静态入口：供 Mixin 与本类内部使用。
     * <p>
     * 与实例方法 {@link #isLoaded()} 共享同一份 {@code loaded} 缓存。
     *
     * @return FTB Quests 是否可用
     */
    public static boolean isLoadedStatic() {
        if (loaded == null) {
            try {
                // 优先使用 ModList 做确定性弱依赖检查；未加载时直接短路，避免无谓反射探测。
                if (!ModList.get().isLoaded("ftbquests")) {
                    loaded = false;
                    LOGGER.info("FTB Quests not detected via ModList");
                    return loaded;
                }
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

    /**
     * 静态入口：供 Mixin 与本类内部使用。
     *
     * @return FTB Quests 版本号，未知时为 {@code "unknown"}
     */
    public static String getFtbVersionStatic() {
        isLoadedStatic();
        return ftbVersion;
    }

    /**
     * 静态入口：注入服务端 KSR 并挂载任务监听器。
     *
     * @param server 服务端实例
     */
    public static void syncToKnownServerRegistriesStatic(MinecraftServer server) {
        FtbKsrSyncer.syncToKnownServerRegistries(server);
        FtbQuestListener.tryRegisterEventListener(server);
    }

    /**
     * 静态入口：注入客户端 KSR。供 {@code SyncKsrMixin} 使用。
     *
     * @param advancementIds 待注入的进度 ID，{@code null} 表示自动扫描
     * @return 是否成功
     */
    public static boolean syncClientKnownServerRegistriesStatic(Collection<String> advancementIds) {
        return FtbKsrSyncer.syncClientKnownServerRegistries(advancementIds);
    }

    /**
     * 静态入口：通知 FTB 侧属性变更并标脏。
     *
     * @param server 服务端实例
     */
    public static void notifyAttributeChangeStatic(MinecraftServer server) {
        if (!isLoadedStatic()) {
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

    // ------------------------------------------------------------------
    // FtbCompatService 实现：统一委托给上方静态方法，保证状态单一来源。
    // ------------------------------------------------------------------

    @Override
    public boolean isLoaded() {
        return isLoadedStatic();
    }

    @Override
    public String getFtbVersion() {
        return getFtbVersionStatic();
    }

    @Override
    public boolean isKsrSynced() {
        return FtbKsrSyncer.isKsrSynced();
    }

    @Override
    public void syncToKnownServerRegistries(MinecraftServer server) {
        syncToKnownServerRegistriesStatic(server);
    }

    @Override
    public boolean syncClientKnownServerRegistries(Collection<String> advancementIds) {
        return syncClientKnownServerRegistriesStatic(advancementIds);
    }

    @Override
    public void onServerTick(MinecraftServer server) {
        FtbQuestListener.onServerTick(server);
    }

    @Override
    public void onPlayerLogout(UUID uuid) {
        FtbQuestListener.onPlayerLogout(uuid);
    }

    @Override
    public void notifyAttributeChange(MinecraftServer server) {
        notifyAttributeChangeStatic(server);
    }
}
