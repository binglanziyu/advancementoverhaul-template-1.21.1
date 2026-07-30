package com.example.advancementoverhaul;

import com.example.advancementoverhaul.achievement.bridge.AchievementBridgeImpl;
import com.example.advancementoverhaul.client.ClientEvents;
import com.example.advancementoverhaul.command.CommandHandler;
import com.example.advancementoverhaul.compat.AdvancementRegistry;
import com.example.advancementoverhaul.data.ServerDataStore;
import com.example.advancementoverhaul.narrative.event.MonologueEventHandler;
import com.example.advancementoverhaul.achievement.event.ServerEventHandler;
import com.example.advancementoverhaul.event.StatsEventHandler;
import com.example.advancementoverhaul.milestone.event.TimelineEventHandler;
import com.example.advancementoverhaul.milestone.bridge.BridgeRegistry;
import com.example.advancementoverhaul.network.NetworkHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Advancement Overhaul 模组主入口。
 *
 * <h2>架构分层</h2>
 * 模组采用分层架构，核心分为两大子系统：
 * <ul>
 *   <li><b>成就系统 (achievement/)</b> — 自定义 Canvas UI、9 种条件类型、前置依赖、维度锁定</li>
 *   <li><b>里程碑系统 (milestone/)</b> — 时间线解锁、统计计数器、自动成就的里程碑</li>
 * </ul>
 * 两大系统通过 {@link com.example.advancementoverhaul.milestone.bridge.AchievementBridge} 桥接接口解耦。
 *
 * <h2>初始化流程</h2>
 * <ol>
 *   <li>注册配置文件（COMMON 类型）</li>
 *   <li>注册桥接实现（里程碑 ↔ 成就）</li>
 *   <li>注册网络包处理器（Sync / Progress / C2S Command）</li>
 *   <li>注册服务端事件处理器（游戏事件 → 条件评估）</li>
 *   <li>注册命令系统（/adv）</li>
 *   <li>注册服务端停止回调（数据持久化）</li>
 *   <li>客户端侧初始化 UI 替换</li>
 * </ol>
 */
@Mod(ModInfo.MOD_ID)
public class AdvancementOverhaul {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModInfo.MOD_NAME);

    /**
     * 模组构造器，由 NeoForge 框架自动调用。
     *
     * @param modBus    模组事件总线
     * @param container 模组容器（用于注册配置）
     */
    public AdvancementOverhaul(IEventBus modBus, ModContainer container) {
        // 注册配置文件（COMMON 类型，服务端+客户端共享）
        container.registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);

        // 注册桥接实现：里程碑系统 ↔ 成就系统
        BridgeRegistry.setAchievementBridge(new AchievementBridgeImpl());

        // 注册网络包处理器（必须在 CommonSetup 之前）
        modBus.addListener(NetworkHandler::registerPayloads);

        // 通用初始化（数据文件夹、图片缓存）
        modBus.addListener(this::onCommonSetup);

        // 注册到 NeoForge 事件总线
        NeoForge.EVENT_BUS.register(ServerEventHandler.class);
        NeoForge.EVENT_BUS.register(StatsEventHandler.class);
        NeoForge.EVENT_BUS.register(MonologueEventHandler.class);
        NeoForge.EVENT_BUS.register(TimelineEventHandler.class);
        NeoForge.EVENT_BUS.addListener(CommandHandler::registerCommands);
        NeoForge.EVENT_BUS.addListener(AdvancementOverhaul::onServerStopping);

        // 叙事配置加载器（客户端+服务端 都需要）
        com.example.advancementoverhaul.data.NarrativeConfigLoader.getInstance()
                .init(FMLPaths.CONFIGDIR.get());

        // 客户端专用初始化
        if (FMLEnvironment.dist.isClient()) {
            try {
                ClientEvents.init(modBus);
                com.example.advancementoverhaul.client.gui.ImageManager.init(FMLPaths.CONFIGDIR.get());
                com.example.advancementoverhaul.client.ResourceLoader.init(FMLPaths.CONFIGDIR.get());
            } catch (Exception e) {
                LOGGER.error("Failed to initialize client events", e);
            }
        }
    }

    /**
     * FML 通用初始化（物理端无关）。
     * 初始化数据存储目录、图片管理器和 FTB Quests 实时同步回调。
     */
    private void onCommonSetup(FMLCommonSetupEvent event) {
        ServerDataStore.getInstance().init(FMLPaths.CONFIGDIR.get());

        // 注册成就变更回调：每次新增/更新/删除成就时自动增量更新 runtime Map
        // 使 FTB Quests 能实时读取变更，无需全量 rebuild
        ServerDataStore.setOnAdvancementChanged(advId -> {
            var server = ServerDataStore.getInstance().getServer();
            if (server == null) return;
            if (ServerDataStore.getInstance().getAdvancement(advId) != null) {
                // 新增或更新 → 增量更新 runtime Map
                AdvancementRegistry.updateAdvancementInRuntime(server, advId);
            } else {
                // 删除 → 从 runtime Map 移除
                AdvancementRegistry.removeAdvancementFromRuntime(server, advId);
            }
        });

        LOGGER.info("Advancement Overhaul initialized");
    }

    /**
     * 服务端停止时回调。
     * 保存脏数据并安全关闭异步写线程。
     */
    private static void onServerStopping(
            net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        ServerDataStore.getInstance().shutdown();
        LOGGER.info("Advancement Overhaul data flushed");
    }
}
