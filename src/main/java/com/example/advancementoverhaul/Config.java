package com.example.advancementoverhaul;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * 模组配置文件。
 * 使用 NeoForge COMMON 类型配置（服务端+客户端共享）。
 * 配置文件路径：{@code config/advancementoverhaul-common.toml}
 */
public class Config {

    /** 配置规格实例（在 Mod 构造器中通过 container.registerConfig 注册） */
    public static final ModConfigSpec COMMON_SPEC;

    // ═══════════════ 配置项 ═══════════════

    /** 是否用自定义 Canvas UI 替换原版进度界面 */
    public static final ModConfigSpec.BooleanValue HIDE_VANILLA;

    /** 编辑命令所需的最低权限等级（0-4，默认 2 = OP 级别） */
    public static final ModConfigSpec.IntValue EDIT_PERMISSION_LEVEL;

    /** Toast 通知显示时长（毫秒） */
    public static final ModConfigSpec.IntValue TOAST_DURATION;

    /** 玩家数据定期保存间隔（tick，6000 = 5 分钟） */
    public static final ModConfigSpec.IntValue PLAYER_DATA_SAVE_INTERVAL;

    /** 原版/模组进度的默认启用状态（false = 默认禁用，需手动启用） */
    public static final ModConfigSpec.BooleanValue VANILLA_DEFAULT_ENABLED;

    /** 自动启用的模组命名空间列表（如 ["minecraft", "create"]），该模组的所有进度会自动创建分类并启用 */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ENABLED_MODS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("interface");
        HIDE_VANILLA = b
                .comment("Replace vanilla advancements screen with custom canvas UI")
                .define("hideVanilla", true);
        b.pop();

        b.push("permission");
        EDIT_PERMISSION_LEVEL = b
                .comment("Permission level for edit commands (0=none, 2=OP, 4=admin)")
                .defineInRange("editPermissionLevel", 2, 0, 4);
        b.pop();

        b.push("toast");
        TOAST_DURATION = b
                .comment("Toast notification display duration in milliseconds")
                .defineInRange("duration", 3000, 500, 30000);
        b.pop();

        b.push("performance");
        PLAYER_DATA_SAVE_INTERVAL = b
                .comment("Periodic player data save interval in ticks (6000 = approx 5 minutes)")
                .defineInRange("playerDataSaveInterval", 6000, 200, 72000);
        b.pop();

        b.push("vanilla");
        VANILLA_DEFAULT_ENABLED = b
                .comment("Default state for vanilla/mod advancements. " +
                         "false = all disabled until explicitly enabled via /adv vanilla enable")
                .define("defaultEnabled", false);
        b.pop();

        b.push("mods");
        ENABLED_MODS = b
                .comment("List of mod namespaces whose advancements are automatically enabled. " +
                         "Each mod's advancement tree will be auto-assigned to tabs (one per root category). " +
                         "Example: [\"minecraft\", \"create\"]")
                .defineList("enabledMods", List::of, () -> "", o -> o instanceof String);
        b.pop();

        COMMON_SPEC = b.build();
    }
}
