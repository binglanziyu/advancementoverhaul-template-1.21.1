package com.dreamer.ao.compat.ftb;

import java.util.Collection;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * FTB Quests 未安装时使用的空实现（Null Object 模式）。
 *
 * <h2>存在意义</h2>
 * 让调用方彻底摆脱 {@code if (FtbQuestsBridge.isLoaded())} 这类散落各处的守卫代码：
 * 无论 FTB 是否安装，调用方拿到的都是一个可安全调用的对象。
 *
 * <h2>类加载安全</h2>
 * 本类<b>不引用任何 {@code dev.ftb.mods.*} 类型</b>，因此在 FTB 缺席的环境中
 * 可以被安全加载，不会触发 {@link NoClassDefFoundError}。这正是
 * {@link FtbCompatProvider} 能在未安装场景下完全避开
 * {@link FtbQuestsBridge}（其字段签名含 FTB 类型）的前提。
 */
final class NoOpFtbCompatService implements FtbCompatService {

    /** 无状态单例，避免重复分配。 */
    static final NoOpFtbCompatService INSTANCE = new NoOpFtbCompatService();

    private NoOpFtbCompatService() {
    }

    @Override
    public boolean isLoaded() {
        return false;
    }

    @Override
    public String getFtbVersion() {
        return null;
    }

    @Override
    public boolean isKsrSynced() {
        // 返回 true 可让服务端侧的 KSR 重试循环立即终止，
        // 避免在没有 FTB 的环境里空转重试直到次数耗尽。
        return true;
    }

    @Override
    public void syncToKnownServerRegistries(MinecraftServer server) {
        // 无 FTB，无需注入。
    }

    @Override
    public boolean syncClientKnownServerRegistries(Collection<String> advancementIds) {
        // 语义为「无需同步即视作成功」，与真实实现在 FTB 缺席时的返回值保持一致，
        // 使调用方的重试逻辑不会被无谓地触发。
        return true;
    }

    @Override
    public void onServerTick(MinecraftServer server) {
        // 无 FTB，无需驱动监听与轮询。
    }

    @Override
    public void onPlayerLogout(UUID uuid) {
        // 无 FTB 侧缓存需要回收。
    }

    @Override
    public void notifyAttributeChange(MinecraftServer server) {
        // 无 FTB，无需标脏。
    }
}
