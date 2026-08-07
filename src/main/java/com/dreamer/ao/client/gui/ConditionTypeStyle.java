package com.dreamer.ao.client.gui;

import com.dreamer.ao.LangKeys;
import com.dreamer.ao.data.DataStore.ConditionType;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Client-side display properties for ConditionType.
 *
 * <p>Separates UI concerns (color, langKey) from the data model enum,
 * which should only carry domain logic (DataSource).
 *
 * <p>Usage:
 * <pre>
 *   ConditionTypeStyle.Style style = ConditionTypeStyle.of(conditionType);
 *   int color = style.color();
 *   String name = style.displayName();
 * </pre>
 */
public final class ConditionTypeStyle {

    public record Style(String langKey, int color) {
        public String displayName() {
            return Component.translatable(langKey).getString();
        }
    }

    private static final Map<ConditionType, Style> STYLES = new EnumMap<>(ConditionType.class);
    static {
        STYLES.put(ConditionType.KILL_ENTITY,     new Style(LangKeys.COND_KILL_ENTITY,     0xFFE91E63));
        STYLES.put(ConditionType.CRAFT_ITEM,       new Style(LangKeys.COND_CRAFT_ITEM,      0xFF42A5F5));
        STYLES.put(ConditionType.GET_ITEM,         new Style(LangKeys.COND_GET_ITEM,        0xFFFF9800));
        STYLES.put(ConditionType.BREAK_BLOCK,      new Style(LangKeys.COND_BREAK_BLOCK,     0xFFAB47BC));
        STYLES.put(ConditionType.PLACE_BLOCK,      new Style(LangKeys.COND_PLACE_BLOCK,     0xFF66BB6A));
        STYLES.put(ConditionType.CHANGE_DIMENSION, new Style(LangKeys.COND_CHANGE_DIMENSION, 0xFF26C6DA));
        STYLES.put(ConditionType.DEAL_DAMAGE,      new Style(LangKeys.COND_DEAL_DAMAGE,     0xFFFF5722));
        STYLES.put(ConditionType.TAKE_DAMAGE,      new Style(LangKeys.COND_TAKE_DAMAGE,     0xFF795548));
        STYLES.put(ConditionType.FISH_ITEM,        new Style(LangKeys.COND_FISH_ITEM,       0xFF00BCD4));
        STYLES.put(ConditionType.FTB_QUEST_COMPLETE, new Style(LangKeys.COND_FTB_QUEST,      0xFF00C853));
        STYLES.put(ConditionType.STAT_REACH,         new Style(LangKeys.COND_STAT_REACH,      0xFF9C27B0));
    }

    private static final Style DEFAULT_STYLE = new Style("unknown", 0xFFFFFFFF);

    private ConditionTypeStyle() {}

    /**
     * Returns the display style for a condition type.
     * Falls back to a default style if the type has no mapping (e.g. newly added types).
     */
    public static Style of(ConditionType type) {
        return STYLES.getOrDefault(type, DEFAULT_STYLE);
    }
}