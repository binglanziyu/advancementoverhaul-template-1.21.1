package com.dreamer.ao.client;

import com.dreamer.ao.client.gui.CompletionPlaque;
import com.dreamer.ao.client.gui.TranslatedStrings;
import com.dreamer.ao.compat.ftb.FtbQuestsBridge;
import net.minecraft.client.Minecraft;
import com.dreamer.ao.ModInfo;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 客户端事件处理器。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>原版进度界面替换由 {@code SetScreenMixin} 在字节码层面处理，无闪烁</li>
 *   <li>检测客户端语言切换，清除翻译缓存</li>
 *   <li>注册成就完成牌匾 HUD 覆盖层</li>
 *   <li>客户端侧 FTB Quests KnownServerRegistries 注入</li>
 * </ul>
 */
public class ClientEvents {

    /** 上次检测到的语言代码，用于检测语言切换 */
    private static String lastLanguage = "";

    /** FTB KSR 重试计数 */
    private static int ftbKsrRetryTick = 0;
    /** FTB KSR 是否至少成功过一次（用于减少日志噪音） */
    private static boolean ftbKsrEverSucceeded = false;
    /** FTB KSR 连续失败计数 */
    private static int ftbKsrFailCount = 0;

    /** 牌匾 GUI Layer ID */
    private static final ResourceLocation PLAQUE_LAYER =
            ModInfo.rl("completion_plaque");

    /**
     * 初始化客户端事件。
     * 在模组构造器中仅当物理端为客户端时调用。
     *
     * @param modBus 模组事件总线
     */
    public static void init(IEventBus modBus) {
        // 将事件处理器注册到 NeoForge 主总线（不能注册整个类，因为 RegisterGuiLayersEvent 是 ModBus 事件）
        NeoForge.EVENT_BUS.addListener(ClientEvents::onTick);
        modBus.addListener(ClientEvents::onRegisterGuiLayers);
    }

    /**
     * 注册牌匾 GUI 覆盖层（在所有 vanilla 层之上渲染）。
     */
    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(PLAQUE_LAYER,
                (guiGraphics, deltaTracker) -> CompletionPlaque.render(guiGraphics, deltaTracker.getGameTimeDeltaTicks()));
    }

    /**
     * 每帧客户端 Tick 后执行。
     * <ol>
     *   <li>检测语言切换 → 清除 {@link TranslatedStrings} 缓存</li>
     *   <li>尝试向 FTB Quests KnownServerRegistries 注入客户端侧进度 ID</li>
     * </ol>
     */
    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        // 语言切换检测：每帧比对语言代码
        if (mc.getLanguageManager() != null) {
            String lang = mc.getLanguageManager().getSelected();
            if (!lang.equals(lastLanguage)) {
                lastLanguage = lang;
                TranslatedStrings.invalidate();
            }
        }

        // FTB Quests 客户端侧 KSR 维护（每 200 tick = 10 秒检查一次，持续进行）
        // 因为 SyncKnownServerRegistriesPacket 会在玩家登录时替换整个 KSR.client，
        // 所以必须持续检测缺失并重新注入，不能只做一次就停止。
        // 传入 null 让 syncClientKnownServerRegistries 从原版 AdvancementTree 自动扫描，
        // 不依赖 ClientDataStore 是否已同步。
        ftbKsrRetryTick++;
        if (ftbKsrRetryTick % 200 == 0) {
            if (FtbQuestsBridge.syncClientKnownServerRegistries(null)) {
                ftbKsrEverSucceeded = true;
                ftbKsrFailCount = 0;
            } else {
                ftbKsrFailCount++;
                if (ftbKsrFailCount == 30) {
                    // 连续失败 30 次（约 60 秒）时输出一次警告
                    org.slf4j.LoggerFactory.getLogger(ClientEvents.class)
                            .warn("Client KSR sync has failed 60 consecutive times — " +
                            "AdvancementReward NPE crash risk remains");
                }
            }
        }
    }
}
