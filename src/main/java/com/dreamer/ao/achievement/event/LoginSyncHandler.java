package com.dreamer.ao.achievement.event;

import com.dreamer.ao.compat.AdvancementRegistry;
import com.dreamer.ao.compat.ftb.FtbQuestsBridge;
import com.dreamer.ao.data.ServerDataStore;
import com.dreamer.ao.network.NetworkHandler;
import com.dreamer.ao.network.SyncManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家登录 / 登出同步处理。
 * <p>
 * 由 {@link ServerEventHandler} 的 {@code @SubscribeEvent} 方法委托调用。
 * 本类不直接订阅事件——事件注册入口统一保留在 {@code ServerEventHandler}，
 * 以保持外部注册方式与调用契约不变。
 *
 * <h2>延迟同步</h2>
 * 在服务端极端情况下（数据尚未加载完成时 {@code PlayerLoggedInEvent} 先触发），
 * 玩家 UUID 会被暂存到 {@link #pendingLoginSyncPlayers}，并在后续 tick 中重试。
 */
final class LoginSyncHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoginSyncHandler.class);

    /** 待重试登录同步的玩家集合（数据未就绪时暂存） */
    private static final Set<UUID> pendingLoginSyncPlayers = ConcurrentHashMap.newKeySet();

    private LoginSyncHandler() {}

    /**
     * 玩家登录：撤销禁用的原版进度 → 同步自定义进度 → 发送全量数据。
     * <p>
     * 数据未就绪时跳过同步并登记到待重试集合。
     */
    static void onPlayerLogin(ServerPlayer player) {
        ServerDataStore store = ServerDataStore.getInstance();
        if (store.getDataFolder() == null) {
            LOGGER.warn("Player {} logged in before data init completed, deferring sync to tick",
                    player.getName().getString());
            pendingLoginSyncPlayers.add(player.getUUID());
            return;
        }
        doLoginSync(player);
    }

    /**
     * 玩家登出：保存脏数据并回收该玩家占用的各类缓存条目。
     * <p>
     * 登出是最精确的回收时机——使各冷却表规模收敛于「在线玩家数」，
     * 而非「服务器生命周期内累计登录过的 UUID 数」。
     */
    static void onPlayerLogout(UUID uuid) {
        ServerDataStore.getInstance().savePlayerDataIfDirty();
        pendingLoginSyncPlayers.remove(uuid);
        DimensionLockHandler.onPlayerLogout(uuid);
        // 清理 FTB Quests 任务完成缓存，防止内存泄漏
        FtbQuestsBridge.onPlayerLogout(uuid);
        // 回收 C2S 命令 / 导入冷却记录
        NetworkHandler.onPlayerLogout(uuid);
    }

    /** 是否存在待重试的延迟登录同步（供 tick 快速短路判断）。 */
    static boolean hasPending() {
        return !pendingLoginSyncPlayers.isEmpty();
    }

    /** 重试因数据未就绪而延迟的登录同步。 */
    static void retryPending(MinecraftServer server) {
        Set<UUID> retrySet = new HashSet<>(pendingLoginSyncPlayers);
        for (UUID uuid : retrySet) {
            var sp = server.getPlayerList().getPlayer(uuid);
            if (sp != null) {
                LOGGER.info("Retrying deferred login sync for {}", sp.getName().getString());
                doLoginSync(sp);
            }
            pendingLoginSyncPlayers.remove(uuid);
        }
    }

    /** 执行登录同步的核心逻辑，从登录事件与 tick 重试中复用。 */
    private static void doLoginSync(ServerPlayer player) {
        suppressVanillaAdvancements(player);
        AdvancementRegistry.syncToVanilla(player);
        SyncManager.syncPlayer(player);
        // 玩家登录时 FTB Library 的 SyncKnownServerRegistriesPacket 会替换
        // 整个 KSR.client，需要重新注入自定义进度以防止 NPE 崩溃
        FtbQuestsBridge.syncToKnownServerRegistries(player.server);
    }

    /**
     * 玩家登录时撤销所有禁用的原版进度。
     * 委托给 {@link AdvancementRegistry#suppressAllDisabled}。
     */
    private static void suppressVanillaAdvancements(ServerPlayer player) {
        AdvancementRegistry.suppressAllDisabled(player);
    }
}
