package com.dreamer.ao;

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
    public static final String BTN_TT_HELP      = "advancementoverhaul.btn.help";
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
    /** 客户端初始化降级提示（部分功能不可用） */
    public static final String CLIENT_INIT_DEGRADED = "advancementoverhaul.msg.client_init_degraded";

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
    public static final String COND_STAT_REACH       = "advancementoverhaul.condition.stat_reach";

    // ── 命令反馈（委托至 CommandLangKeys）──
    // 源码定义位于 CommandLangKeys，此处保留引用以保证向后兼容。
    // 新代码建议直接使用 CommandLangKeys.CMD_*。
    public static final String CMD_PERM_DENIED       = CommandLangKeys.CMD_PERM_DENIED;
    public static final String CMD_ADV_NOT_FOUND     = CommandLangKeys.CMD_ADV_NOT_FOUND;
    public static final String CMD_PLAYER_ONLY       = CommandLangKeys.CMD_PLAYER_ONLY;
    public static final String CMD_PLAYER_NOT_FOUND  = CommandLangKeys.CMD_PLAYER_NOT_FOUND;
    public static final String CMD_ADV_CREATED       = CommandLangKeys.CMD_ADV_CREATED;
    public static final String CMD_ADV_UPDATEJSON    = CommandLangKeys.CMD_ADV_UPDATEJSON;
    public static final String CMD_ADV_DELETED       = CommandLangKeys.CMD_ADV_DELETED;
    public static final String CMD_ADV_BATCH_DELETED = CommandLangKeys.CMD_ADV_BATCH_DELETED;
    public static final String CMD_ADV_NAME_CHANGED  = CommandLangKeys.CMD_ADV_NAME_CHANGED;
    public static final String CMD_ADV_DESC_SET      = CommandLangKeys.CMD_ADV_DESC_SET;
    public static final String CMD_ADV_HIDDEN_STATE  = CommandLangKeys.CMD_ADV_HIDDEN_STATE;
    public static final String CMD_ADV_PREREQ_SET    = CommandLangKeys.CMD_ADV_PREREQ_SET;
    public static final String CMD_ADV_COMPLETED     = CommandLangKeys.CMD_ADV_COMPLETED;
    public static final String CMD_ADV_RESET_ALL     = CommandLangKeys.CMD_ADV_RESET_ALL;
    public static final String CMD_ADV_RESET_ONE     = CommandLangKeys.CMD_ADV_RESET_ONE;
    public static final String CMD_ADV_GIVEN         = CommandLangKeys.CMD_ADV_GIVEN;
    public static final String CMD_ADV_REVOKED       = CommandLangKeys.CMD_ADV_REVOKED;
    public static final String CMD_ADV_CHECK         = CommandLangKeys.CMD_ADV_CHECK;
    public static final String CMD_JSON_MISSING_NAME = CommandLangKeys.CMD_JSON_MISSING_NAME;
    public static final String CMD_JSON_EMPTY        = CommandLangKeys.CMD_JSON_EMPTY;
    public static final String CMD_JSON_ERROR        = CommandLangKeys.CMD_JSON_ERROR;
    public static final String CMD_PARSE_FAILED      = CommandLangKeys.CMD_PARSE_FAILED;
    public static final String CMD_AUTOLAYOUT_DONE   = CommandLangKeys.CMD_AUTOLAYOUT_DONE;
    public static final String CMD_TAB_ADDED         = CommandLangKeys.CMD_TAB_ADDED;
    public static final String CMD_TAB_DELETED       = CommandLangKeys.CMD_TAB_DELETED;
    public static final String CMD_TAB_ORDERED       = CommandLangKeys.CMD_TAB_ORDERED;
    public static final String CMD_TAB_BUILTIN_NODELETE = CommandLangKeys.CMD_TAB_BUILTIN_NODELETE;
    public static final String CMD_IMPORT_NOT_FOUND  = CommandLangKeys.CMD_IMPORT_NOT_FOUND;
    public static final String CMD_IMPORT_DONE       = CommandLangKeys.CMD_IMPORT_DONE;
    public static final String CMD_IMPORT_FAILED     = CommandLangKeys.CMD_IMPORT_FAILED;
    public static final String CMD_IMPORT_TOO_LARGE  = CommandLangKeys.CMD_IMPORT_TOO_LARGE;
    public static final String CMD_IMPORT_EMPTY      = CommandLangKeys.CMD_IMPORT_EMPTY;
    public static final String CMD_IMPORT_NO_FILES   = CommandLangKeys.CMD_IMPORT_NO_FILES;
    public static final String CMD_IMPORT_MULTIPLE   = CommandLangKeys.CMD_IMPORT_MULTIPLE;
    public static final String CMD_RATE_LIMITED      = CommandLangKeys.CMD_RATE_LIMITED;
    public static final String CMD_EXPORT_DONE       = CommandLangKeys.CMD_EXPORT_DONE;
    public static final String CMD_EXPORT_FAILED     = CommandLangKeys.CMD_EXPORT_FAILED;
    public static final String CMD_DIM_LOCKED        = CommandLangKeys.CMD_DIM_LOCKED;
    public static final String CMD_DIM_UNLOCKED      = CommandLangKeys.CMD_DIM_UNLOCKED;
    public static final String CMD_DIM_COND_SET      = CommandLangKeys.CMD_DIM_COND_SET;
    public static final String CMD_DIM_COND_REMOVED  = CommandLangKeys.CMD_DIM_COND_REMOVED;
    public static final String CMD_VANILLA_ENABLED   = CommandLangKeys.CMD_VANILLA_ENABLED;
    public static final String CMD_VANILLA_DISABLED  = CommandLangKeys.CMD_VANILLA_DISABLED;
    public static final String CMD_VANILLA_ALL_EN    = CommandLangKeys.CMD_VANILLA_ALL_EN;
    public static final String CMD_VANILLA_ALL_DIS   = CommandLangKeys.CMD_VANILLA_ALL_DIS;
    public static final String CMD_PREREQ_NOT_FOUND     = CommandLangKeys.CMD_PREREQ_NOT_FOUND;
    public static final String CMD_PREREQ_CYCLE_DETECTED = CommandLangKeys.CMD_PREREQ_CYCLE_DETECTED;
    public static final String CMD_PREREQ_SELF_REFERENCE = CommandLangKeys.CMD_PREREQ_SELF_REFERENCE;
    public static final String CMD_BATCH_SKIPPED_NOT_FOUND = CommandLangKeys.CMD_BATCH_SKIPPED_NOT_FOUND;
    public static final String CMD_BATCH_DELETE_CONFIRM     = CommandLangKeys.CMD_BATCH_DELETE_CONFIRM;
    public static final String CMD_INPUT_TOO_LONG           = CommandLangKeys.CMD_INPUT_TOO_LONG;
    public static final String CMD_INPUT_EMPTY              = CommandLangKeys.CMD_INPUT_EMPTY;
    public static final String CMD_VANILLA_SET_POS   = CommandLangKeys.CMD_VANILLA_SET_POS;
    public static final String CMD_VANILLA_SET_TAB   = CommandLangKeys.CMD_VANILLA_SET_TAB;
    public static final String CMD_VANILLA_CLEAR_TAB = CommandLangKeys.CMD_VANILLA_CLEAR_TAB;
    public static final String CMD_ICON_SET          = CommandLangKeys.CMD_ICON_SET;
    public static final String CMD_CASCADE_DEPTH_EXCEEDED = CommandLangKeys.CMD_CASCADE_DEPTH_EXCEEDED;
    public static final String CMD_RELOAD_DONE       = CommandLangKeys.CMD_RELOAD_DONE;
    public static final String CMD_ADV_CONDITIONS_NOT_MET = CommandLangKeys.CMD_ADV_CONDITIONS_NOT_MET;

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
    public static final String IMAGE_NO_FILES    = "advancementoverhaul.gui.image_no_files";
    public static final String IMAGE_SCALE_UP    = "advancementoverhaul.gui.image_scale_up";
    public static final String IMAGE_SCALE_DOWN  = "advancementoverhaul.gui.image_scale_down";
    public static final String IMAGE_SCALE_RESET = "advancementoverhaul.gui.image_scale_reset";
    public static final String IMAGE_LOCK        = "advancementoverhaul.gui.image_lock";
    public static final String IMAGE_UNLOCK      = "advancementoverhaul.gui.image_unlock";
    public static final String IMAGE_DELETE      = "advancementoverhaul.gui.image_delete";
    public static final String IMAGE_TO_FRONT    = "advancementoverhaul.gui.image_to_front";
    public static final String IMAGE_TO_BACK     = "advancementoverhaul.gui.image_to_back";
    public static final String CONFIRM_IMAGE_DELETE = "advancementoverhaul.gui.confirm_image_delete";

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

    // ── Lore text ──
    public static final String LORE             = "advancementoverhaul.ui.lore";
    public static final String LORE_PLACEHOLDER = "advancementoverhaul.ui.lore_placeholder";

    // ── 冒险日志 ──
    public static final String JOURNAL_TITLE    = "advancementoverhaul.ui.journal_title";
    public static final String JOURNAL_EMPTY    = "advancementoverhaul.ui.journal_empty";
    public static final String JOURNAL_BTN_TT   = "advancementoverhaul.btn.journal";
    public static final String HIDDEN_LOCKED    = "advancementoverhaul.ui.hidden_locked";

    // ── 完成牌匾 ──
    public static final String COMPLETION_TITLE  = "advancementoverhaul.completion.title";

    // ── JSON 错误消息（委托至 CommandLangKeys）──
    public static final String JSON_ERR_BRACE    = CommandLangKeys.JSON_ERR_BRACE;
    public static final String JSON_ERR_BRACKET  = CommandLangKeys.JSON_ERR_BRACKET;
    public static final String JSON_ERR_STRING   = CommandLangKeys.JSON_ERR_STRING;
    public static final String JSON_ERR_NUMBER   = CommandLangKeys.JSON_ERR_NUMBER;

    // ── 图片错误消息 ──
    public static final String IMAGE_DIR_NOT_FOUND   = "advancementoverhaul.gui.image_dir_not_found";
    public static final String IMAGE_FILE_NOT_FOUND  = "advancementoverhaul.gui.image_file_not_found";
    public static final String IMAGE_FILE_TOO_LARGE  = "advancementoverhaul.gui.image_file_too_large";
    public static final String IMAGE_PARSE_FAIL      = "advancementoverhaul.gui.image_parse_fail";
    public static final String UNKNOWN_ERROR         = "advancementoverhaul.gui.unknown_error";

    // ── 底栏提示 ──
    public static final String UI_LABEL_TAB            = "advancementoverhaul.ui.label_tab";
    public static final String UI_HINT_OPEN_EDIT       = "advancementoverhaul.ui.hint_open_edit";
    public static final String UI_HINT_CLOSE_EDIT      = "advancementoverhaul.ui.hint_close_edit";
    public static final String UI_TAB_ALL              = "advancementoverhaul.ui.tab_all";
    public static final String UI_TAB_HIDDEN           = "advancementoverhaul.ui.tab_hidden";

    // ── 叙事统计（委托至 NarrativeLangKeys）──
    // 源码定义位于 NarrativeLangKeys，此处保留引用以保证向后兼容。
    public static final String NARR_TITLE          = NarrativeLangKeys.NARR_TITLE;
    public static final String NARR_CAT_JOURNEY    = NarrativeLangKeys.NARR_CAT_JOURNEY;
    public static final String NARR_CAT_BUILDING   = NarrativeLangKeys.NARR_CAT_BUILDING;
    public static final String NARR_CAT_COMBAT     = NarrativeLangKeys.NARR_CAT_COMBAT;
    public static final String NARR_CAT_SURVIVAL   = NarrativeLangKeys.NARR_CAT_SURVIVAL;
    public static final String NARR_CAT_CRAFTING   = NarrativeLangKeys.NARR_CAT_CRAFTING;
    public static final String NARR_CAT_EXPLORE    = NarrativeLangKeys.NARR_CAT_EXPLORE;
    public static final String NARR_DAY            = NarrativeLangKeys.NARR_DAY;
    public static final String NARR_SUNRISES_VIEWED       = NarrativeLangKeys.NARR_SUNRISES_VIEWED;
    public static final String NARR_SUNSETS_VIEWED        = NarrativeLangKeys.NARR_SUNSETS_VIEWED;
    public static final String NARR_RAIN_TICKS            = NarrativeLangKeys.NARR_RAIN_TICKS;
    public static final String NARR_SNOW_TICKS            = NarrativeLangKeys.NARR_SNOW_TICKS;
    public static final String NARR_BLOCKS_PLACED         = NarrativeLangKeys.NARR_BLOCKS_PLACED;
    public static final String NARR_BLOCKS_BROKEN         = NarrativeLangKeys.NARR_BLOCKS_BROKEN;
    public static final String NARR_TORCHES_PLACED        = NarrativeLangKeys.NARR_TORCHES_PLACED;
    public static final String NARR_BLOCKS_PLACED_IN_WATER = NarrativeLangKeys.NARR_BLOCKS_PLACED_IN_WATER;
    public static final String NARR_LIGHTNING_STRIKES     = NarrativeLangKeys.NARR_LIGHTNING_STRIKES;
    public static final String NARR_FALL_DAMAGE_EVENTS    = NarrativeLangKeys.NARR_FALL_DAMAGE_EVENTS;
    public static final String NARR_ANIMALS_TAMED         = NarrativeLangKeys.NARR_ANIMALS_TAMED;
    public static final String NARR_ANIMALS_FED           = NarrativeLangKeys.NARR_ANIMALS_FED;
    public static final String NARR_CROPS_PLANTED         = NarrativeLangKeys.NARR_CROPS_PLANTED;
    public static final String NARR_NAME_TAGS_USED        = NarrativeLangKeys.NARR_NAME_TAGS_USED;
    public static final String NARR_WANDERING_TRADER_TRADES = NarrativeLangKeys.NARR_WANDERING_TRADER_TRADES;
    public static final String NARR_ITEMS_CRAFTED         = NarrativeLangKeys.NARR_ITEMS_CRAFTED;
    public static final String NARR_FURTHEST_DISTANCE     = NarrativeLangKeys.NARR_FURTHEST_DISTANCE;
    public static final String NARR_MOST_FREQUENT_BIOME   = NarrativeLangKeys.NARR_MOST_FREQUENT_BIOME;
    public static final String NARR_FIRST_NETHER_DAY      = NarrativeLangKeys.NARR_FIRST_NETHER_DAY;
    public static final String NARR_FIRST_END_DAY         = NarrativeLangKeys.NARR_FIRST_END_DAY;
    public static final String NARR_FIRST_DIAMOND_DAY     = NarrativeLangKeys.NARR_FIRST_DIAMOND_DAY;
    public static final String NARR_FIRST_ENCHANT_DAY     = NarrativeLangKeys.NARR_FIRST_ENCHANT_DAY;
    public static final String NARR_FIRST_TAME_DAY        = NarrativeLangKeys.NARR_FIRST_TAME_DAY;
    public static final String NARR_FIRST_RAIN_SLEEP_DAY  = NarrativeLangKeys.NARR_FIRST_RAIN_SLEEP_DAY;
    public static final String NARR_FIRST_DEATH_DAY       = NarrativeLangKeys.NARR_FIRST_DEATH_DAY;
    public static final String NARR_LATEST_DEATH          = NarrativeLangKeys.NARR_LATEST_DEATH;
    public static final String NARR_FIRST_BLOCK_PLACED     = NarrativeLangKeys.NARR_FIRST_BLOCK_PLACED;
    public static final String NARR_LOWEST_Y              = NarrativeLangKeys.NARR_LOWEST_Y;
    public static final String NARR_HIGHEST_Y             = NarrativeLangKeys.NARR_HIGHEST_Y;
    public static final String NARR_DISTANCE_WALKED       = NarrativeLangKeys.NARR_DISTANCE_WALKED;
    public static final String NARR_DISTANCE_SWUM         = NarrativeLangKeys.NARR_DISTANCE_SWUM;
    public static final String NARR_DISTANCE_SPRINT       = NarrativeLangKeys.NARR_DISTANCE_SPRINT;
    public static final String NARR_DISTANCE_FLOWN        = NarrativeLangKeys.NARR_DISTANCE_FLOWN;
    public static final String NARR_JUMPS                 = NarrativeLangKeys.NARR_JUMPS;
    public static final String NARR_DAMAGE_DEALT          = NarrativeLangKeys.NARR_DAMAGE_DEALT;
    public static final String NARR_DAMAGE_TAKEN          = NarrativeLangKeys.NARR_DAMAGE_TAKEN;
    public static final String NARR_MOB_KILLS             = NarrativeLangKeys.NARR_MOB_KILLS;
    public static final String NARR_PLAYER_KILLS          = NarrativeLangKeys.NARR_PLAYER_KILLS;
    public static final String NARR_DEATHS                = NarrativeLangKeys.NARR_DEATHS;
    public static final String NARR_FISH_CAUGHT           = NarrativeLangKeys.NARR_FISH_CAUGHT;
    public static final String NARR_ANIMALS_BRED          = NarrativeLangKeys.NARR_ANIMALS_BRED;
    public static final String NARR_CAKE_SLICES           = NarrativeLangKeys.NARR_CAKE_SLICES;
    public static final String NARR_CRAFTING_TABLE_USES   = NarrativeLangKeys.NARR_CRAFTING_TABLE_USES;
    public static final String NARR_ANVIL_USES            = NarrativeLangKeys.NARR_ANVIL_USES;
    public static final String NARR_GRINDSTONE_USES       = NarrativeLangKeys.NARR_GRINDSTONE_USES;
    public static final String NARR_ITEMS_ENCHANTED       = NarrativeLangKeys.NARR_ITEMS_ENCHANTED;
    public static final String NARR_BEACON_USES           = NarrativeLangKeys.NARR_BEACON_USES;
    public static final String NARR_VILLAGER_TRADES       = NarrativeLangKeys.NARR_VILLAGER_TRADES;
    public static final String NARR_RAIDS_WON             = NarrativeLangKeys.NARR_RAIDS_WON;
    public static final String NARR_TARGETS_HIT           = NarrativeLangKeys.NARR_TARGETS_HIT;
    public static final String NARR_BELLS_RUNG            = NarrativeLangKeys.NARR_BELLS_RUNG;

    // ── 成就统计 ──
    public static final String STATS_TITLE               = "advancementoverhaul.stats.title";
    public static final String STATS_ALL_TAB             = "advancementoverhaul.stats.all_tab";
    public static final String STATS_UNCOMPLETED         = "advancementoverhaul.stats.uncompleted";
    public static final String STATS_EMPTY               = "advancementoverhaul.stats.empty";
    public static final String STATS_CLICK_HINT          = "advancementoverhaul.stats.click_hint";
    public static final String STATS_CUSTOM             = "advancementoverhaul.stats.custom";
    public static final String STATS_COUNT              = "advancementoverhaul.stats.count";

    // ── 阶段面板 ──
    public static final String PHASE_PANEL_TITLE       = "timeline.advancementoverhaul.phase_panel_title";
    public static final String PHASE_OP_TAG            = "timeline.advancementoverhaul.phase_op_tag";
    public static final String PHASE_PREVIEW           = "timeline.advancementoverhaul.phase_preview";
    public static final String PHASE_WORLD             = "timeline.advancementoverhaul.phase_world";
    public static final String PHASE_DIMENSION         = "timeline.advancementoverhaul.phase_dimension";
    public static final String PHASE_PLAYER            = "timeline.advancementoverhaul.phase_player";
    public static final String PHASE_EFFECTS           = "timeline.advancementoverhaul.phase_effects";
    public static final String PHASE_NEXT_STATE        = "timeline.advancementoverhaul.phase_next_state";
    public static final String PHASE_TRANSITION_COND   = "timeline.advancementoverhaul.phase_transition_cond";
    public static final String PHASE_HISTORY           = "timeline.advancementoverhaul.phase_history";
    public static final String PHASE_TEMP_STATE        = "timeline.advancementoverhaul.phase_temp_state";
    public static final String PHASE_NO_TEMP           = "timeline.advancementoverhaul.phase_no_temp";
    public static final String PHASE_OTHER_DIMS        = "timeline.advancementoverhaul.phase_other_dims";
    public static final String PHASE_FORCE_TRANSITION  = "timeline.advancementoverhaul.phase_force_transition";
    public static final String PHASE_EDIT_DEF          = "timeline.advancementoverhaul.phase_edit_def";
    public static final String PHASE_NEW_STATE         = "timeline.advancementoverhaul.phase_new_state";
    public static final String PHASE_APPLY_TEMP        = "timeline.advancementoverhaul.phase_apply_temp";
    public static final String PHASE_CLEAR_TEMP        = "timeline.advancementoverhaul.phase_clear_temp";
    public static final String PHASE_CONFIRM_TRANSITION = "timeline.advancementoverhaul.phase_confirm_transition";
    public static final String PHASE_CONFIRM_CLEAR_TEMP = "timeline.advancementoverhaul.phase_confirm_clear_temp";
    public static final String PHASE_SELECT_DIMENSION  = "timeline.advancementoverhaul.phase_select_dimension";
    public static final String PHASE_SELECT_PLAYER     = "timeline.advancementoverhaul.phase_select_player";
    public static final String EFFECT_MOB_HEALTH       = "timeline.advancementoverhaul.effect_mob_health";
    public static final String EFFECT_DAMAGE_RECV      = "timeline.advancementoverhaul.effect_damage_recv";
    public static final String EFFECT_DAMAGE_DEALT     = "timeline.advancementoverhaul.effect_damage_dealt";
    public static final String EFFECT_MOB_SPEED        = "timeline.advancementoverhaul.effect_mob_speed";
    public static final String EFFECT_MOB_ATTACK       = "timeline.advancementoverhaul.effect_mob_attack";
    public static final String EFFECT_MOB_ARMOR        = "timeline.advancementoverhaul.effect_mob_armor";
    public static final String EFFECT_SPAWN_RATE       = "timeline.advancementoverhaul.effect_spawn_rate";
    public static final String EFFECT_BOSS_DAMAGE       = "timeline.advancementoverhaul.effect_boss_damage";
    // A 类玩家属性效果名
    public static final String EFFECT_MAX_HEALTH         = "timeline.advancementoverhaul.effect_max_health";
    public static final String EFFECT_ARMOR              = "timeline.advancementoverhaul.effect_armor";
    public static final String EFFECT_ARMOR_TOUGHNESS    = "timeline.advancementoverhaul.effect_armor_toughness";
    public static final String EFFECT_KNOCKBACK_RESIST   = "timeline.advancementoverhaul.effect_knockback_resist";
    public static final String EFFECT_MOVE_SPEED         = "timeline.advancementoverhaul.effect_move_speed";
    public static final String EFFECT_ATTACK_DAMAGE      = "timeline.advancementoverhaul.effect_attack_damage";
    public static final String EFFECT_ATTACK_SPEED       = "timeline.advancementoverhaul.effect_attack_speed";
    public static final String EFFECT_LUCK               = "timeline.advancementoverhaul.effect_luck";
    public static final String EFFECT_SCALE              = "timeline.advancementoverhaul.effect_scale";
    public static final String EFFECT_DAMAGE_TAKEN      = "timeline.advancementoverhaul.effect_damage_taken";
    // 阶段面板补充键
    public static final String PHASE_NONE                = "timeline.advancementoverhaul.phase_none";
    public static final String PHASE_EFFECT_EQUIPMENT    = "timeline.advancementoverhaul.phase_effect_equipment";
    public static final String PHASE_EDIT_DEF_HINT       = "timeline.advancementoverhaul.phase_edit_def_hint";
    public static final String PHASE_NEW_STATE_HINT      = "timeline.advancementoverhaul.phase_new_state_hint";
    public static final String PHASE_CONFIRM_APPLY_TEMP  = "timeline.advancementoverhaul.phase_confirm_apply_temp";
    // 阶段定义编辑器（可视化编辑）
    public static final String PHASE_EDIT_TITLE         = "timeline.advancementoverhaul.phase_edit_title";
    public static final String PHASE_EDIT_NEW_TITLE     = "timeline.advancementoverhaul.phase_edit_new_title";
    public static final String PHASE_EDIT_ID            = "timeline.advancementoverhaul.phase_edit_id";
    public static final String PHASE_EDIT_NAME          = "timeline.advancementoverhaul.phase_edit_name";
    public static final String PHASE_EDIT_TIER          = "timeline.advancementoverhaul.phase_edit_tier";
    public static final String PHASE_EDIT_SCOPE         = "timeline.advancementoverhaul.phase_edit_scope";
    public static final String PHASE_EDIT_DIMENSION     = "timeline.advancementoverhaul.phase_edit_dimension";
    public static final String PHASE_EDIT_UNLOCK_MS     = "timeline.advancementoverhaul.phase_edit_unlock_ms";
    public static final String PHASE_EDIT_ATTRS         = "timeline.advancementoverhaul.phase_edit_attrs";
    public static final String PHASE_EDIT_ATTR_ADD      = "timeline.advancementoverhaul.phase_edit_attr_add";
    public static final String PHASE_EDIT_MOB_MULTS     = "timeline.advancementoverhaul.phase_edit_mob_mults";
    public static final String PHASE_EDIT_MOB_EFFECTS   = "timeline.advancementoverhaul.phase_edit_mob_effects";
    public static final String PHASE_EDIT_EQUIPMENT     = "timeline.advancementoverhaul.phase_edit_equipment";
    public static final String PHASE_EDIT_PREVIEW       = "timeline.advancementoverhaul.phase_edit_preview";
    public static final String PHASE_EDIT_SAVE          = "timeline.advancementoverhaul.phase_edit_save";
    public static final String PHASE_EDIT_DELETE        = "timeline.advancementoverhaul.phase_edit_delete";
    public static final String PHASE_EDIT_INVALID_ID    = "timeline.advancementoverhaul.phase_edit_invalid_id";
    public static final String PHASE_EDIT_DELETED       = "timeline.advancementoverhaul.phase_edit_deleted";
    public static final String PHASE_EDIT_EMPTY         = "timeline.advancementoverhaul.phase_edit_empty";
    public static final String PHASE_EDIT_TOO_LARGE     = "timeline.advancementoverhaul.phase_edit_too_large";
    public static final String PHASE_EDIT_INVALID_JSON  = "timeline.advancementoverhaul.phase_edit_invalid_json";
    public static final String PHASE_EDIT_SAVED         = "timeline.advancementoverhaul.phase_edit_saved";
    public static final String PHASE_EDIT_FAILED        = "timeline.advancementoverhaul.phase_edit_failed";
    public static final String PHASE_EDIT_CONFIRM_DELETE = "timeline.advancementoverhaul.phase_edit_confirm_delete";
    public static final String PHASE_EDIT_ID_RO         = "timeline.advancementoverhaul.phase_edit_id_readonly";
    public static final String PHASE_OP_FORCE           = "timeline.advancementoverhaul.phase_op_force";
    public static final String PHASE_OP_TEMP_SECONDS    = "timeline.advancementoverhaul.phase_op_temp_seconds";
    public static final String PHASE_RELOAD_DONE        = "timeline.advancementoverhaul.phase_reload_done";

    // ── 阶段面板 UI 重构 ──
    public static final String PHASE_MODE_BROWSE        = "timeline.advancementoverhaul.phase_mode_browse";
    public static final String PHASE_MODE_EDIT          = "timeline.advancementoverhaul.phase_mode_edit";
    public static final String PHASE_SCOPE_GLOBAL       = "timeline.advancementoverhaul.phase_scope_global";
    public static final String PHASE_SCOPE_DIMENSION    = "timeline.advancementoverhaul.phase_scope_dimension";
    public static final String PHASE_SCOPE_PLAYER       = "timeline.advancementoverhaul.phase_scope_player";
    public static final String PHASE_ROW_NEW            = "timeline.advancementoverhaul.phase_row_new";
    public static final String PHASE_ROW_REMOVE         = "timeline.advancementoverhaul.phase_row_remove";
    public static final String PHASE_SWITCH_TO          = "timeline.advancementoverhaul.phase_switch_to";
    public static final String PHASE_SWITCH_CONFIRM     = "timeline.advancementoverhaul.phase_switch_confirm";
    public static final String PHASE_CURRENT_EFFECTS    = "timeline.advancementoverhaul.phase_current_effects";
    public static final String PHASE_CURRENT_EMPTY      = "timeline.advancementoverhaul.phase_current_empty";
    public static final String PHASE_EFFECT_ATTR        = "timeline.advancementoverhaul.phase_effect_attr";
    public static final String PHASE_EFFECT_MOB         = "timeline.advancementoverhaul.phase_effect_mob";
    public static final String PHASE_EFFECT_POTION      = "timeline.advancementoverhaul.phase_effect_potion";
    public static final String PHASE_EFFECT_EQUIP       = "timeline.advancementoverhaul.phase_effect_equip";
    public static final String PHASE_EFFECT_REMOVED      = "timeline.advancementoverhaul.phase_effect_removed";
    public static final String PHASE_EDIT_NOT_FOUND      = "timeline.advancementoverhaul.phase_edit_not_found";
    // 阶段属性上下限说明
    public static final String PHASE_ATTR_LIMIT_HINT     = "timeline.advancementoverhaul.phase_attr_limit_hint";
    // 里程碑选择式
    public static final String PHASE_EDIT_SELECT_MS      = "timeline.advancementoverhaul.phase_edit_select_ms";
    public static final String PHASE_NO_MILESTONE         = "timeline.advancementoverhaul.phase_no_milestone";
    // 阶段行内切换阶段
    public static final String PHASE_SWITCH_PHASE         = "timeline.advancementoverhaul.phase_switch_phase";
    public static final String PHASE_EDIT_EQUIP_ENTITY    = "timeline.advancementoverhaul.phase_edit_equip_entity";
    public static final String PHASE_EDIT_EQUIP_SLOT      = "timeline.advancementoverhaul.phase_edit_equip_slot";
    public static final String PHASE_EDIT_EQUIP_ADD_ENTRY = "timeline.advancementoverhaul.phase_edit_equip_add_entry";
    public static final String PHASE_EDIT_EQUIP_ENCHANT   = "timeline.advancementoverhaul.phase_edit_equip_enchant";
    public static final String PHASE_SHOW                  = "timeline.advancementoverhaul.phase_show";
    public static final String PHASE_HIDE                  = "timeline.advancementoverhaul.phase_hide";

    // ── 系统消息 ──
    public static final String MSG_DATASTORE_INIT_FAILED = "advancementoverhaul.msg.datastore_init_failed";
    public static final String MSG_CONFIG_NEEDS_RELOAD = "advancementoverhaul.msg.config_needs_reload";

    private LangKeys() {}
}
