// ═══════════════════════════════════════════════════════════════
// 2. Config.java — 删除误导注释，默认 false
// ═══════════════════════════════════════════════════════════════
package com.example.advancementoverhaul;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    public static final ModConfigSpec COMMON_SPEC;

    public static final ModConfigSpec.BooleanValue HIDE_VANILLA;
    public static final ModConfigSpec.IntValue EDIT_PERMISSION_LEVEL;
    public static final ModConfigSpec.IntValue TOAST_DURATION;
    public static final ModConfigSpec.IntValue PLAYER_DATA_SAVE_INTERVAL;
    public static final ModConfigSpec.BooleanValue VANILLA_DEFAULT_ENABLED;


    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("interface");
        HIDE_VANILLA = b.comment("Replace vanilla advancements screen with custom one")
                .define("hideVanilla", true);
        b.pop();

        b.push("permission");
        EDIT_PERMISSION_LEVEL = b.comment("Permission level for edit commands (0-4)")
                .defineInRange("editPermissionLevel", 2, 0, 4);
        b.pop();

        b.push("toast");
        TOAST_DURATION = b.comment("Toast display duration in milliseconds")
                .defineInRange("duration", 3000, 500, 30000);
        b.pop();

        b.push("performance");
        PLAYER_DATA_SAVE_INTERVAL = b.comment("Periodic player data save interval in ticks (6000 = 5 min)")
                .defineInRange("playerDataSaveInterval", 6000, 200, 72000);
        b.pop();

        b.push("vanilla");
        VANILLA_DEFAULT_ENABLED = b
                .comment("Default state for vanilla/mod advancements. false = disabled until explicitly enabled.")
                .define("defaultEnabled", false);
        b.pop();


        COMMON_SPEC = b.build();
    }
}