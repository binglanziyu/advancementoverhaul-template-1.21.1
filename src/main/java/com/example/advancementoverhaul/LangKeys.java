package com.example.advancementoverhaul;

/**
 * 国际化翻译键常量。
 * <p>
 * <b>一句话作用：</b>集中管理所有 UI 文本、命令反馈、条件名称等的翻译键，
 * 避免在代码中散布硬编码字符串，确保中英文翻译的一致性。
 * <p>
 * 每个常量对应 {@code assets/advancementoverhaul/lang/} 下 JSON 文件中的一个条目。
 * 添加新翻译时：1) 在此处定义常量 2) 在 en_us.json 和 zh_cn.json 中分别添加对应条目。
 */
public final class LangKeys {
    // ── UI 通用 ──
    public static final String TITLE           = "advancementoverhaul.ui.title";
    public static final String ALL             = "advancementoverhaul.ui.all";
    public static final String HIDDEN          = "advancementoverhaul.ui.hidden";
    public static final String STATISTICS      = "advancementoverhaul.ui.statistics";
    public static final String CREATE_TITLE    = "advancementoverhaul.ui.create";
    public static final String EDIT_TITLE      = "advancementoverhaul.ui.edit";
    public static final String DELETE          = "advancementoverhaul.ui.delete";
    public static final String SAVE            = "advancementoverhaul.ui.save";
    public static final String TAB_MANAGE_TITLE = "advancementoverhaul.gui.tab_manage_title";
    public static final String IMAGE_LOAD_FAIL = "advancementoverhaul.image.load_fail";
    public static final String CANCEL          = "advancementoverhaul.ui.cancel";
    public static final String CONFIRM         = "advancementoverhaul.ui.confirm";
    public static final String SELECT          = "advancementoverhaul.ui.select";
    public static final String SEARCH_HINT     = "advancementoverhaul.ui.search";
    public static final String NO_RESULTS      = "advancementoverhaul.ui.no_results";
    public static final String DIM_MGMT        = "advancementoverhaul.ui.dimension_management";
    public static final String NAME            = "advancementoverhaul.ui.name";
    public static final String DESC            = "advancementoverhaul.ui.description";
    public static final String TAB             = "advancementoverhaul.ui.tab";
    public static final String CONDITIONS      = "advancementoverhaul.ui.conditions";
    public static final String CREATE_IMAGE    = "advancementoverhaul.gui.create_image";
    public static final String LOCKED          = "advancementoverhaul.ui.locked";
    public static final String UNLOCKED        = "advancementoverhaul.ui.unlocked";
    public static final String NEW_TAB         = "advancementoverhaul.ui.new_tab";
    public static final String NO_TAB          = "advancementoverhaul.ui.no_tab";
    public static final String NONE            = "advancementoverhaul.ui.none";
    public static final String SHOW            = "advancementoverhaul.ui.show";
    public static final String HIDE            = "advancementoverhaul.ui.hide";
    public static final String BATCH_DELETE    = "advancementoverhaul.ui.batch_delete";
    public static final String BATCH_HIDE      = "advancementoverhaul.ui.batch_hide";
    public static final String BATCH_SHOW      = "advancementoverhaul.ui.batch_show";
    public static final String ITEMS_COUNT     = "advancementoverhaul.ui.items_count";
    public static final String COND_LABEL      = "advancementoverhaul.ui.condition_label";
    public static final String SET_CONDITION   = "advancementoverhaul.ui.set_condition";
    public static final String COND_SELECTOR   = "advancementoverhaul.ui.cond_selector";
    public static final String COND_ALL        = "advancementoverhaul.ui.cond_all";
    public static final String COND_BACKPACK   = "advancementoverhaul.ui.cond_backpack";
    public static final String EMPTY_BACKPACK  = "advancementoverhaul.ui.empty_backpack";
    public static final String COND_ANY        = "advancementoverhaul.ui.cond_any";
    public static final String VANILLA_ASSIGN_TAB = "advancementoverhaul.ui.vanilla_assign_tab";
    public static final String HIDDEN_SHORT    = "advancementoverhaul.ui.hidden_short";
    public static final String PREREQ_ADD      = "advancementoverhaul.ui.prereq_add";
    public static final String PREREQ_ADDED    = "advancementoverhaul.ui.prereq_added";
    public static final String PREREQ_ADD_NEW  = "advancementoverhaul.ui.prereq_add_new";
    public static final String ICON            = "advancementoverhaul.ui.icon";

    // ── 内置标签页 ──
    public static final String TAB_DEFAULT     = "advancementoverhaul.tab.default";
    public static final String TAB_VANILLA_DISPLAY = "advancementoverhaul.tab.vanilla";

    // ── 提示 ──
    public static final String HELP_HINT       = "advancementoverhaul.ui.help_hint";
    public static final String HELP_TITLE      = "advancementoverhaul.ui.help_title";
    public static final String TIP_VANILLA_RO  = "advancementoverhaul.tip.vanilla_readonly";
    public static final String RESET           = "advancementoverhaul.ui.reset";
    public static final String VALIDATION_REQUIRED = "advancementoverhaul.validation.required";

    // ── 图标选择器 ──
    public static final String ICON_ITEMS      = "advancementoverhaul.ui.icon_items";
    public static final String ICON_ENTITIES   = "advancementoverhaul.ui.icon_entities";
    public static final String ICON_BLOCKS     = "advancementoverhaul.ui.icon_blocks";

    // ── 按钮提示 ──
    public static final String BTN_TT_CLOSE     = "advancementoverhaul.btn.close";
    public static final String BTN_TT_STATS     = "advancementoverhaul.btn.stats";
    public static final String BTN_TT_RESET     = "advancementoverhaul.btn.reset";
    public static final String BTN_TT_EXPORT    = "advancementoverhaul.btn.export";
    public static final String BTN_TT_IMPORT    = "advancementoverhaul.btn.import";
    public static final String BTN_TT_DIMENSION = "advancementoverhaul.btn.dimension";
    public static final String BTN_TT_AUTOLAYOUT= "advancementoverhaul.btn.autolayout";
    public static final String BTN_TT_EDITMODE  = "advancementoverhaul.btn.editmode";
    public static final String BTN_TT_TABS      = "advancementoverhaul.btn.tabs";
    public static final String BTN_TT_FTB_MODE  = "advancementoverhaul.btn.ftb_mode";

    // ── 工具提示 ──
    public static final String ADV_TT_ENABLED    = "advancementoverhaul.tooltip.enabled";
    public static final String ADV_TT_DISABLED   = "advancementoverhaul.tooltip.disabled";
    public static final String ADV_TT_DISABLE_BTN = "advancementoverhaul.tooltip.disable_btn";

    // ── 维度名称 ──
    public static final String DIM_OVERWORLD   = "advancementoverhaul.dim.overworld";
    public static final String DIM_NETHER      = "advancementoverhaul.dim.nether";
    public static final String DIM_END         = "advancementoverhaul.dim.end";

    // ── 统计 ──
    public static final String STAT_CUSTOM     = "advancementoverhaul.stat.custom";
    public static final String STAT_DONE       = "advancementoverhaul.stat.done";
    public static final String STAT_RATE       = "advancementoverhaul.stat.rate";
    public static final String STAT_VANILLA    = "advancementoverhaul.stat.vanilla";
    public static final String STAT_TAB_PROG   = "advancementoverhaul.stat.tab_progress";

    // ── 消息 ──
    public static final String DIM_LOCKED_MSG  = "advancementoverhaul.msg.dim_locked";
    public static final String NEED_ADV_MSG    = "advancementoverhaul.msg.need_adv";

    // ── 条件名称 ──
    public static final String COND_KILL_ENTITY      = "advancementoverhaul.condition.kill_entity";
    public static final String COND_CRAFT_ITEM       = "advancementoverhaul.condition.craft_item";
    public static final String COND_GET_ITEM         = "advancementoverhaul.condition.get_item";
    public static final String COND_BREAK_BLOCK      = "advancementoverhaul.condition.break_block";
    public static final String COND_PLACE_BLOCK      = "advancementoverhaul.condition.place_block";
    public static final String COND_CHANGE_DIMENSION = "advancementoverhaul.condition.change_dimension";
    public static final String COND_DEAL_DAMAGE      = "advancementoverhaul.condition.deal_damage";
    public static final String COND_TAKE_DAMAGE      = "advancementoverhaul.condition.take_damage";
    public static final String COND_FISH_ITEM        = "advancementoverhaul.condition.fish_item";
    public static final String COND_FTB_QUEST        = "advancementoverhaul.condition.ftb_quest";

    // ── 命令反馈 ──
    public static final String CMD_PERM_DENIED       = "advancementoverhaul.cmd.perm_denied";
    public static final String CMD_ADV_NOT_FOUND     = "advancementoverhaul.cmd.adv_not_found";
    public static final String CMD_PLAYER_ONLY       = "advancementoverhaul.cmd.player_only";
    public static final String CMD_PLAYER_NOT_FOUND  = "advancementoverhaul.cmd.player_not_found";
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
    public static final String CMD_JSON_MISSING_NAME = "advancementoverhaul.cmd.json_missing_name";
    public static final String CMD_JSON_EMPTY        = "advancementoverhaul.cmd.json_empty";
    public static final String CMD_JSON_ERROR        = "advancementoverhaul.cmd.json_error";
    public static final String CMD_PARSE_FAILED      = "advancementoverhaul.cmd.parse_failed";
    public static final String CMD_AUTOLAYOUT_DONE   = "advancementoverhaul.cmd.autolayout_done";
    public static final String CMD_TAB_ADDED         = "advancementoverhaul.cmd.tab_added";
    public static final String CMD_TAB_DELETED       = "advancementoverhaul.cmd.tab_deleted";
    public static final String CMD_TAB_ORDERED       = "advancementoverhaul.cmd.tab_ordered";
    public static final String CMD_TAB_BUILTIN_NODELETE = "advancementoverhaul.cmd.tab_builtin_nodelete";
    public static final String CMD_IMPORT_NOT_FOUND  = "advancementoverhaul.cmd.import_not_found";
    public static final String CMD_IMPORT_DONE       = "advancementoverhaul.cmd.import_done";
    public static final String CMD_IMPORT_FAILED     = "advancementoverhaul.cmd.import_failed";
    public static final String CMD_IMPORT_TOO_LARGE  = "advancementoverhaul.cmd.import_too_large";
    public static final String CMD_IMPORT_EMPTY      = "advancementoverhaul.cmd.import_empty";
    public static final String CMD_IMPORT_NO_FILES   = "advancementoverhaul.cmd.import_no_files";
    public static final String CMD_IMPORT_MULTIPLE   = "advancementoverhaul.cmd.import_multiple";
    public static final String CMD_RATE_LIMITED      = "advancementoverhaul.cmd.rate_limited";
    public static final String CMD_EXPORT_DONE       = "advancementoverhaul.cmd.export_done";
    public static final String CMD_EXPORT_FAILED     = "advancementoverhaul.cmd.export_failed";
    public static final String CMD_DIM_LOCKED        = "advancementoverhaul.cmd.dim_locked";
    public static final String CMD_DIM_UNLOCKED      = "advancementoverhaul.cmd.dim_unlocked";
    public static final String CMD_DIM_COND_SET      = "advancementoverhaul.cmd.dim_cond_set";
    public static final String CMD_DIM_COND_REMOVED  = "advancementoverhaul.cmd.dim_cond_removed";
    public static final String CMD_VANILLA_ENABLED   = "advancementoverhaul.cmd.vanilla_enabled";
    public static final String CMD_VANILLA_DISABLED  = "advancementoverhaul.cmd.vanilla_disabled";
    public static final String CMD_VANILLA_ALL_EN    = "advancementoverhaul.cmd.vanilla_all_enabled";
    public static final String CMD_VANILLA_ALL_DIS   = "advancementoverhaul.cmd.vanilla_all_disabled";
    public static final String CMD_PREREQ_NOT_FOUND     = "advancementoverhaul.cmd.prereq_not_found";
    public static final String CMD_PREREQ_CYCLE_DETECTED = "advancementoverhaul.cmd.prereq_cycle_detected";
    public static final String CMD_PREREQ_SELF_REFERENCE = "advancementoverhaul.cmd.prereq_self_reference";
    public static final String CMD_BATCH_SKIPPED_NOT_FOUND = "advancementoverhaul.cmd.batch_skipped_not_found";
    public static final String CMD_VANILLA_SET_POS   = "advancementoverhaul.cmd.vanilla_set_pos";
    public static final String CMD_VANILLA_SET_TAB   = "advancementoverhaul.cmd.vanilla_set_tab";
    public static final String CMD_VANILLA_CLEAR_TAB = "advancementoverhaul.cmd.vanilla_clear_tab";
    public static final String CMD_ICON_SET          = "advancementoverhaul.cmd.icon_set";
    public static final String CMD_CASCADE_DEPTH_EXCEEDED = "advancementoverhaul.cmd.cascade_depth_exceeded";
    public static final String CMD_RELOAD_DONE       = "advancementoverhaul.cmd.reload_done";
    public static final String CMD_ADV_CONDITIONS_NOT_MET = "advancementoverhaul.cmd.adv_conditions_not_met";

    // ── 详情面板 ──
    public static final String DETAIL_COMPLETED      = "advancementoverhaul.ui.detail_completed";
    public static final String DETAIL_NOT_COMPLETED  = "advancementoverhaul.ui.detail_not_completed";
    public static final String DETAIL_PREREQ_PREFIX  = "advancementoverhaul.ui.detail_prereq";
    public static final String DETAIL_TAB_PREFIX     = "advancementoverhaul.ui.detail_tab";
    public static final String DETAIL_ID_PREFIX      = "advancementoverhaul.ui.detail_id";
    public static final String DETAIL_COPY           = "advancementoverhaul.ui.detail_copy";
    public static final String DETAIL_COPIED         = "advancementoverhaul.ui.detail_copied";
    public static final String CONFIRM_DELETE_PREFIX = "advancementoverhaul.ui.confirm_delete";
    public static final String CONFIRM_BATCH_DELETE  = "advancementoverhaul.ui.confirm_batch_delete";
    public static final String NAME_PLACEHOLDER      = "advancementoverhaul.ui.name_placeholder";
    public static final String DESC_PLACEHOLDER      = "advancementoverhaul.ui.desc_placeholder";
    public static final String DETAIL_TITLE          = "advancementoverhaul.ui.detail";

    // ── 分类管理 ──
    public static final String TAB_MANAGE_EMPTY      = "advancementoverhaul.gui.tab_manage_empty";
    public static final String TAB_CONFIRM_DELETE_MSG = "advancementoverhaul.gui.tab_confirm_delete_msg";

    // ── 图片管理 ──
    public static final String IMAGE_NO_FILES   = "advancementoverhaul.gui.image_no_files";
    public static final String IMAGE_SCALE_UP   = "advancementoverhaul.gui.image_scale_up";
    public static final String IMAGE_SCALE_DOWN = "advancementoverhaul.gui.image_scale_down";
    public static final String IMAGE_LOCK       = "advancementoverhaul.gui.image_lock";
    public static final String IMAGE_UNLOCK     = "advancementoverhaul.gui.image_unlock";
    public static final String IMAGE_DELETE     = "advancementoverhaul.gui.image_delete";

    // ── FTB 通知模式 ──
    public static final String FTB_MODE_DEFAULT = "advancementoverhaul.ftb.mode_default";
    public static final String FTB_MODE_DISABLE = "advancementoverhaul.ftb.mode_disable";
    public static final String FTB_MODE_REPLACE = "advancementoverhaul.ftb.mode_replace";

    // ── 帮助面板 ──
    public static final String HELP_CANVAS      = "advancementoverhaul.ui.help_canvas";
    public static final String HELP_ADV_EDIT    = "advancementoverhaul.ui.help_adv_edit";
    public static final String HELP_MANAGEMENT  = "advancementoverhaul.ui.help_management";
    public static final String HELP_TABS        = "advancementoverhaul.ui.help_tabs";
    public static final String HELP_DIMENSIONS  = "advancementoverhaul.ui.help_dimensions";
    public static final String HELP_VANILLA     = "advancementoverhaul.ui.help_vanilla";

    private LangKeys() {}
}
