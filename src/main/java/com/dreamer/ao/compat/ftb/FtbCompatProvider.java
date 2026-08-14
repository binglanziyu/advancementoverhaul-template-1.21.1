package com.dreamer.ao.compat.ftb;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * {@link FtbCompatService} 的唯一获取入口（工厂 + 弱依赖闸门）。
 *
 * <h2>为什么必须用反射实例化实现类</h2>
 * {@link FtbQuestsBridge} 的方法签名中出现了 {@code KnownServerRegistries.AdvancementInfo}。
 * 只要本类在源码里直接写出 {@code new FtbQuestsBridge()}，编译产物中就会留下对
 * {@code FtbQuestsBridge} 的符号引用；当 JVM 在缺少 FTB 的环境里解析该类时，
 * 其方法签名所引用的 FTB 类型将无法解析，抛出 {@link NoClassDefFoundError}。
 * 因此这里改用 {@link Class#forName} 在 {@code ModList} 闸门<b>之后</b>才加载实现类——
 * 闸门为假时，{@code FtbQuestsBridge} 这个类根本不会被 JVM 触碰。
 *
 * <h2>线程安全</h2>
 * 采用 volatile + 双重检查，兼顾首次解析的互斥与后续访问的无锁读取。
 * tick 路径每 tick 都会调用 {@link #get()}，不能引入锁竞争。
 */
public final class FtbCompatProvider {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** FTB Quests 的 mod id，弱依赖闸门的判定依据。 */
    private static final String FTB_MOD_ID = "ftbquests";

    /** 真实实现的全限定名，仅在闸门通过后才交给类加载器解析。 */
    private static final String IMPL_CLASS = "com.dreamer.ao.compat.ftb.FtbQuestsBridge";

    private static volatile FtbCompatService instance;

    private FtbCompatProvider() {
    }

    /**
     * 获取当前环境下可用的 FTB 兼容服务。
     * <p>
     * 返回值恒不为 {@code null}：FTB 缺席或实现类加载失败时返回
     * {@link NoOpFtbCompatService}，调用方可无条件直接调用。
     *
     * @return FTB 兼容服务实例
     */
    public static FtbCompatService get() {
        FtbCompatService local = instance;
        if (local == null) {
            synchronized (FtbCompatProvider.class) {
                local = instance;
                if (local == null) {
                    local = resolve();
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * 解析实现：先过 {@code ModList} 闸门，再反射加载实现类。
     *
     * @return 真实实现或空实现，绝不返回 {@code null}
     */
    private static FtbCompatService resolve() {
        if (!ModList.get().isLoaded(FTB_MOD_ID)) {
            LOGGER.info("FTB Quests not detected \u2014 using no-op compat service");
            return NoOpFtbCompatService.INSTANCE;
        }
        try {
            Class<?> implClass = Class.forName(IMPL_CLASS);
            FtbCompatService service = (FtbCompatService) implClass.getDeclaredConstructor().newInstance();
            LOGGER.info("FTB Quests compat service initialized");
            return service;
        } catch (ReflectiveOperationException | LinkageError e) {
            // LinkageError 覆盖 NoClassDefFoundError：FTB 存在但版本不兼容导致签名解析失败时，
            // 降级为空实现而非让整个模组崩溃。
            LOGGER.error("Failed to initialize FTB compat service, falling back to no-op", e);
            return NoOpFtbCompatService.INSTANCE;
        }
    }
}
