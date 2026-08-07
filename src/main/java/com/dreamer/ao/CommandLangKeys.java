package com.dreamer.ao;

/**
 * 命令反馈相关的本地化键常量。
 * <p>
 * 包含 {@code CMD_} 和 {@code JSON_ERR_} 前缀的所有命令反馈消息键。
 * 这些常量从 {@link LangKeys} 中分离出来，便于命令模块独立引用。
 *
 * @see LangKeys 主本地化键类（保持向后兼容引用）
 */
public final class CommandLangKeys {
    private CommandLangKeys() {}

    // ── 权限与通用 ──
    public static final String CMD_PERM_DENIED       = "advancementoverhaul.cmd.perm_denied";
    public static final String CMD_ADV_NOT_FOUND     = "advancementoverhaul.cmd.adv_not_found";
    public static final String CMD_PLAYER_ONLY       = "advancementoverhaul.cmd.player_only";
    public static final String CMD_PLAYER_NOT_FOUND  = "advancementoverhaul.cmd.player_not_found";
    public static final String CMD_PARSE_FAILED      = "advancementoverhaul.cmd.parse_failed";
    public static final String CMD_RATE_LIMITED      = "advancementoverhaul.cmd.rate_limited";
    public static final String CMD_INPUT_TOO_LONG    = "advancementoverhaul.cmd.input_too_long";
    public static final String CMD_INPUT_EMPTY       = "advancementoverhaul.cmd.input_empty";

    // ── 成就 CRUD 命令 ──
    public static final String CMD_ADV_CREATED       = "advancementoverhaul.cmd.adv_created";
    public static final String CMD_ADV_UPDATEJSON    = "advancementoverhaul.cmd.adv_updatejson";
    public static final String CMD_ADV_DELETED       = "advancementoverhaul.cmd.adv_deleted";
    public static final String CMD_ADV_BATCH_DELETED = "advancementoverhaul.cmd.adv_batch_deleted";
    public static final String CMD_ADV_NAME_CHANGED  = "advancementoverhaul.cmd.adv_name_changed";
    public static final String CMD_ADV_DESC_SET      = "advancementoverhaul.cmd.adv_desc_set";
    public static final String CMD_ADV_HIDDEN_STATE  = "advancementoverhaul.cmd.adv_hidden_state";
    public static final String CMD_ADV_PREREQ_SET    = "advancementoverhaul.cmd.adv_prereq_set";
    public static final String CMD_ADV_COMPLETED     = "advancementoverhaul.cmd.adv_completed";
    public static final String CMD_ADV_RESET_ALL     = "advancementoverhaul.cmd.adv_reset_all";
    public static final String CMD_ADV_RESET_ONE     = "advancementoverhaul.cmd.adv_reset_one";
    public static final String CMD_ADV_GIVEN         = "advancementoverhaul.cmd.adv_given";
    public static final String CMD_ADV_REVOKED       = "advancementoverhaul.cmd.adv_revoked";
    public static final String CMD_ADV_CHECK         = "advancementoverhaul.cmd.adv_check";
    public static final String CMD_ADV_CONDITIONS_NOT_MET = "advancementoverhaul.cmd.adv_conditions_not_met";

    // ── JSON 命令 ──
    public static final String CMD_JSON_MISSING_NAME = "advancementoverhaul.cmd.json_missing_name";
    public static final String CMD_JSON_EMPTY        = "advancementoverhaul.cmd.json_empty";
    public static final String CMD_JSON_ERROR        = "advancementoverhaul.cmd.json_error";
    public static final String JSON_ERR_BRACE        = "advancementoverhaul.cmd.json_err_brace";
    public static final String JSON_ERR_BRACKET      = "advancementoverhaul.cmd.json_err_bracket";
    public static final String JSON_ERR_STRING       = "advancementoverhaul.cmd.json_err_string";
    public static final String JSON_ERR_NUMBER       = "advancementoverhaul.cmd.json_err_number";

    // ── 前驱/批量 ──
    public static final String CMD_PREREQ_NOT_FOUND     = "advancementoverhaul.cmd.prereq_not_found";
    public static final String CMD_PREREQ_CYCLE_DETECTED = "advancementoverhaul.cmd.prereq_cycle_detected";
    public static final String CMD_PREREQ_SELF_REFERENCE = "advancementoverhaul.cmd.prereq_self_reference";
    public static final String CMD_BATCH_SKIPPED_NOT_FOUND = "advancementoverhaul.cmd.batch_skipped_not_found";
    public static final String CMD_BATCH_DELETE_CONFIRM     = "advancementoverhaul.cmd.batch_delete_confirm";

    // ── 导入/导出 ──
    public static final String CMD_IMPORT_NOT_FOUND  = "advancementoverhaul.cmd.import_not_found";
    public static final String CMD_IMPORT_DONE       = "advancementoverhaul.cmd.import_done";
    public static final String CMD_IMPORT_FAILED     = "advancementoverhaul.cmd.import_failed";
    public static final String CMD_IMPORT_TOO_LARGE  = "advancementoverhaul.cmd.import_too_large";
    public static final String CMD_IMPORT_EMPTY      = "advancementoverhaul.cmd.import_empty";
    public static final String CMD_IMPORT_NO_FILES   = "advancementoverhaul.cmd.import_no_files";
    public static final String CMD_IMPORT_MULTIPLE   = "advancementoverhaul.cmd.import_multiple";
    public static final String CMD_EXPORT_DONE       = "advancementoverhaul.cmd.export_done";
    public static final String CMD_EXPORT_FAILED     = "advancementoverhaul.cmd.export_failed";

    // ── 维度锁定 ──
    public static final String CMD_DIM_LOCKED        = "advancementoverhaul.cmd.dim_locked";
    public static final String CMD_DIM_UNLOCKED      = "advancementoverhaul.cmd.dim_unlocked";
    public static final String CMD_DIM_COND_SET      = "advancementoverhaul.cmd.dim_cond_set";
    public static final String CMD_DIM_COND_REMOVED  = "advancementoverhaul.cmd.dim_cond_removed";

    // ── 原版进度管理 ──
    public static final String CMD_VANILLA_ENABLED   = "advancementoverhaul.cmd.vanilla_enabled";
    public static final String CMD_VANILLA_DISABLED  = "advancementoverhaul.cmd.vanilla_disabled";
    public static final String CMD_VANILLA_ALL_EN    = "advancementoverhaul.cmd.vanilla_all_enabled";
    public static final String CMD_VANILLA_ALL_DIS   = "advancementoverhaul.cmd.vanilla_all_disabled";
    public static final String CMD_VANILLA_SET_POS   = "advancementoverhaul.cmd.vanilla_set_pos";
    public static final String CMD_VANILLA_SET_TAB   = "advancementoverhaul.cmd.vanilla_set_tab";
    public static final String CMD_VANILLA_CLEAR_TAB = "advancementoverhaul.cmd.vanilla_clear_tab";

    // ── 标签页 ──
    public static final String CMD_TAB_ADDED         = "advancementoverhaul.cmd.tab_added";
    public static final String CMD_TAB_DELETED       = "advancementoverhaul.cmd.tab_deleted";
    public static final String CMD_TAB_ORDERED       = "advancementoverhaul.cmd.tab_ordered";
    public static final String CMD_TAB_BUILTIN_NODELETE = "advancementoverhaul.cmd.tab_builtin_nodelete";

    // ── 其他 ──
    public static final String CMD_AUTOLAYOUT_DONE   = "advancementoverhaul.cmd.autolayout_done";
    public static final String CMD_ICON_SET          = "advancementoverhaul.cmd.icon_set";
    public static final String CMD_CASCADE_DEPTH_EXCEEDED = "advancementoverhaul.cmd.cascade_depth_exceeded";
    public static final String CMD_RELOAD_DONE       = "advancementoverhaul.cmd.reload_done";
}
