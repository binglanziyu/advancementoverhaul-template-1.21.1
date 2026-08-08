package com.dreamer.ao;

import net.minecraft.resources.ResourceLocation;

/**
 * 模组元信息常量。
 * <p>
 * 包含 {@code ${...}} 占位符的字段会在编译前由 
 * {@code injectModInfo} Gradle 任务替换为 gradle.properties 中的值。
 * 修改协议版本号等配置时请编辑 gradle.properties 而非直接改动此文件。
 */
public final class ModInfo {

    /** 模组 ID（命名空间），所有注册项和资源路径使用此前缀 */
    public static final String MOD_ID = "advancementoverhaul";

    /** 模组显示名称 */
    public static final String MOD_NAME = "Advancement Overhaul";

    /** 网络协议版本号（自定义 Payload 的协议标识）。
     *  由 injectModInfo task 在编译前替换为 gradle.properties 中的 network_protocol 值 */
    public static final String NETWORK_PROTOCOL = "7";

    private ModInfo() {}

    /**
     * 以本模组命名空间构造 {@link ResourceLocation}。
     * 用于收敛散落的 {@code new ResourceLocation("advancementoverhaul", path)} 字面量。
     */
    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
