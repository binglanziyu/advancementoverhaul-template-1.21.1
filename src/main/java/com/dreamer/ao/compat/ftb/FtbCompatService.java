package com.dreamer.ao.compat.ftb;

import java.util.Collection;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * FTB Quests 兼容层的对外契约。
 *
 * <h2>设计意图</h2>
 * 主逻辑（网络层、命令层、事件层）只依赖本接口，不直接引用
 * {@link FtbQuestsBridge} 等具体实现，从而把「FTB 是否安装」「FTB API 如何变动」
 * 这两类波动全部收敛在 {@code com.dreamer.ao.compat.ftb} 包内部。
 *
 * <h2>类型洁净约束</h2>
 * 本接口的方法签名<b>不得</b>出现任何 {@code dev.ftb.mods.*} 类型。
 * 一旦 FTB 类型出现在签名上，调用方在类加载阶段就会被迫解析 FTB 类，
 * 弱依赖随即退化为硬依赖。需要 FTB 原生类型的场景（如 Mixin 内部构造
 * {@code KnownServerRegistries.AdvancementInfo}）保留在实现类上单独暴露，
 * 因为那些 Mixin 本身只在 FTB 存在时才会被织入。
 *
 * <h2>获取实例</h2>
 * 统一经 {@link FtbCompatProvider#get()} 获取。FTB 未安装时返回
 * {@link NoOpFtbCompatService}（Null Object），调用方无需判空、也无需
 * 重复书写 {@code ModList.isLoaded} 判断。
 */
public interface FtbCompatService {

    /**
     * FTB Quests 是否可用。
     * <p>
     * 仅用于「向用户展示状态」或「决定是否渲染 FTB 相关 UI」等场景。
     * 常规调用路径不必先判断本方法——未安装时各方法本身即为安全空操作。
     *
     * @return 已安装且核心 API 校验通过返回 {@code true}
     */
    boolean isLoaded();

    /**
     * 返回已检测到的 FTB Quests 版本号。
     *
     * @return 版本字符串；无法解析时为 {@code "unknown"}，未安装时为 {@code null}
     */
    String getFtbVersion();

    /**
     * 服务端侧 KnownServerRegistries 是否已成功注入。
     * <p>
     * 服务端启动瞬间 KSR 可能尚未初始化，需要由调用方按退避策略重试，
     * 本方法即重试循环的终止条件。
     *
     * @return 已同步返回 {@code true}
     */
    boolean isKsrSynced();

    /**
     * 将自定义进度注入服务端 KnownServerRegistries，并尝试挂载任务完成监听器。
     * <p>
     * 注入的目的是防止 FTB 的 {@code AdvancementReward.fillConfigGroup}
     * 在查找未知进度 ID 时抛出 NPE。
     *
     * @param server 服务端实例；为 {@code null} 时应安全返回
     */
    void syncToKnownServerRegistries(MinecraftServer server);

    /**
     * 将进度 ID 注入客户端 KnownServerRegistries。
     *
     * @param advancementIds 待注入的进度 ID；传 {@code null} 表示从原版
     *                       AdvancementTree 自动扫描，不依赖数据是否已同步
     * @return 同步成功（或因 FTB 未安装而无需同步）返回 {@code true}；
     *         KSR 尚未就绪等可重试的失败返回 {@code false}
     */
    boolean syncClientKnownServerRegistries(Collection<String> advancementIds);

    /**
     * 服务端 tick 回调，驱动 FTB 任务完成的事件监听与轮询兜底。
     *
     * @param server 服务端实例
     */
    void onServerTick(MinecraftServer server);

    /**
     * 玩家登出，回收该玩家占用的 FTB 侧缓存条目。
     *
     * @param uuid 登出玩家的 UUID
     */
    void onPlayerLogout(UUID uuid);

    /**
     * 通知 FTB 自定义进度的属性已变更，促使其持久化并刷新界面。
     *
     * @param server 服务端实例
     */
    void notifyAttributeChange(MinecraftServer server);
}
