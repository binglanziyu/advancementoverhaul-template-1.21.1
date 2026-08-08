package com.dreamer.ao.data;

import com.google.gson.*;

import java.lang.reflect.Type;

/**
 * 十一种进度条件类型。
 */
public enum ConditionType {
    KILL_ENTITY(DataSource.ENTITY_TYPE),
    CRAFT_ITEM(DataSource.ITEM),
    GET_ITEM(DataSource.ITEM),
    BREAK_BLOCK(DataSource.BLOCK),
    PLACE_BLOCK(DataSource.BLOCK),
    CHANGE_DIMENSION(DataSource.DIMENSION),
    DEAL_DAMAGE(DataSource.NONE),
    TAKE_DAMAGE(DataSource.NONE),
    FISH_ITEM(DataSource.ITEM),
    FTB_QUEST_COMPLETE(DataSource.NONE),
    STAT_REACH(DataSource.NONE);

    private final DataSource dataSource;

    ConditionType(DataSource dataSource) { this.dataSource = dataSource; }

    public DataSource getDataSource() { return dataSource; }

    // ═══════════════ 类型适配器 ═══════════════

    public static class ConditionTypeAdapter
            implements JsonSerializer<ConditionType>, JsonDeserializer<ConditionType> {

        @Override
        public JsonElement serialize(ConditionType src, Type t, JsonSerializationContext ctx) {
            return new JsonPrimitive(src.name().toLowerCase());
        }

        @Override
        public ConditionType deserialize(JsonElement json, Type t,
                                         JsonDeserializationContext ctx) throws JsonParseException {
            String name = json.getAsString();
            try {
                return ConditionType.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new JsonParseException("Unknown condition type '" + name
                        + "' in data file. Valid types: "
                        + java.util.Arrays.stream(ConditionType.values())
                                .map(ct -> ct.name().toLowerCase())
                                .collect(java.util.stream.Collectors.joining(", ")));
            }
        }
    }
}
