package com.example.advancementoverhaul.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.*;

public class DataStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/DataStore");

    public static final String TAB_DEFAULT   = "默认";
    public static final String TAB_VANILLA   = "原有成就";
    public static final String TAB_STORY     = "生存";
    public static final String TAB_ADVENTURE = "冒险";
    public static final String TAB_NETHER    = "下界";
    public static final String TAB_END       = "末地";
    public static final String TAB_HUSBANDRY = "农牧";

    public static final List<String> BUILTIN_TABS = List.of(
            TAB_DEFAULT, TAB_VANILLA, TAB_STORY, TAB_ADVENTURE, TAB_NETHER, TAB_END, TAB_HUSBANDRY
    );

    public static boolean isBuiltinTab(String tab) {
        return BUILTIN_TABS.contains(tab);
    }

    public enum DataSource {
        ENTITY_TYPE, ITEM, BLOCK, DIMENSION, NONE
    }

    public enum ConditionType {
        KILL_ENTITY(DataSource.ENTITY_TYPE),
        CRAFT_ITEM(DataSource.ITEM),
        GET_ITEM(DataSource.ITEM),
        BREAK_BLOCK(DataSource.BLOCK),
        PLACE_BLOCK(DataSource.BLOCK),
        CHANGE_DIMENSION(DataSource.DIMENSION),
        DEAL_DAMAGE(DataSource.NONE),
        TAKE_DAMAGE(DataSource.NONE),
        FISH_ITEM(DataSource.ITEM);

        private final DataSource dataSource;

        ConditionType(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        public DataSource getDataSource() { return dataSource; }
    }

    public enum NbtMatchMode {
        IGNORE("ignore"), CONTAINS("contains"), EXACT("exact"), NONE_EMPTY("none_empty");
        private final String saveName;
        NbtMatchMode(String saveName) { this.saveName = saveName; }
        public String getSaveName() { return saveName; }
        public static NbtMatchMode fromSaveName(String name) {
            if (name == null) return IGNORE;
            for (NbtMatchMode m : values()) if (m.saveName.equalsIgnoreCase(name)) return m;
            return IGNORE;
        }
    }

    public static class CustomAdvancement {
        private String id;
        private String name;
        private String description;
        private int x, y;
        private String tab;
        private boolean hidden;
        private String icon;
        private List<String> prerequisites;
        private List<AdvancementCondition> conditions;

        public CustomAdvancement() {
            this.prerequisites = new ArrayList<>();
            this.conditions = new ArrayList<>();
            this.hidden = false;
        }

        public CustomAdvancement(String id, String name, String description, int x, int y) {
            this.id = id; this.name = name; this.description = description; this.x = x; this.y = y;
            this.hidden = false; this.prerequisites = new ArrayList<>(); this.conditions = new ArrayList<>();
        }

        public CustomAdvancement deepCopy() {
            CustomAdvancement c = new CustomAdvancement(id, name, description, x, y);
            c.tab = tab; c.hidden = hidden; c.icon = icon;
            c.prerequisites = new ArrayList<>(prerequisites != null ? prerequisites : List.of());
            c.conditions = new ArrayList<>();
            if (conditions != null) for (AdvancementCondition ac : conditions) c.conditions.add(ac.deepCopy());
            return c;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public int getX() { return x; }
        public int getY() { return y; }
        public String getTab() { return tab; }
        public boolean isHidden() { return hidden; }
        public String getIcon() { return icon; }
        public List<String> getPrerequisites() { return prerequisites != null ? Collections.unmodifiableList(prerequisites) : List.of(); }
        public List<AdvancementCondition> getConditions() { return conditions != null ? Collections.unmodifiableList(conditions) : List.of(); }

        public void setId(String id) { this.id = id; }
        public void setName(String name) { this.name = name; }
        public void setDescription(String description) { this.description = description; }
        public void setX(int x) { this.x = x; }
        public void setY(int y) { this.y = y; }
        public void setTab(String tab) { this.tab = tab; }
        public void setHidden(boolean hidden) { this.hidden = hidden; }
        public void setIcon(String icon) { this.icon = icon; }
        public void setPrerequisites(List<String> prerequisites) { this.prerequisites = prerequisites; }
        public void setConditions(List<AdvancementCondition> conditions) { this.conditions = conditions; }
    }

    public static class AdvancementCondition {
        private ConditionType type;
        private String targetId;
        private int count;
        private String nbtMatchMode;
        private String targetNbt;

        // [D3] 枚举缓存，避免每次 getter 做字符串遍历
        private transient NbtMatchMode nbtMatchModeParsed;

        public AdvancementCondition() {
            this.type = ConditionType.KILL_ENTITY;
            this.count = 1;
            this.nbtMatchMode = "ignore";
            this.nbtMatchModeParsed = NbtMatchMode.IGNORE;
        }

        public AdvancementCondition(ConditionType type, String targetId, int count) {
            this.type = type; this.targetId = targetId; this.count = count;
            this.nbtMatchMode = "ignore";
            this.nbtMatchModeParsed = NbtMatchMode.IGNORE;
        }

        public AdvancementCondition deepCopy() {
            AdvancementCondition c = new AdvancementCondition(type, targetId, count);
            c.nbtMatchMode = nbtMatchMode;
            c.nbtMatchModeParsed = nbtMatchModeParsed;
            c.targetNbt = targetNbt;
            return c;
        }

        public ConditionType getType() { return type; }
        public String getTargetId() { return targetId; }
        public int getCount() { return count; }

        // [D3] 使用缓存的枚举值
        public NbtMatchMode getNbtMatchMode() {
            if (nbtMatchModeParsed == null) {
                nbtMatchModeParsed = NbtMatchMode.fromSaveName(nbtMatchMode);
            }
            return nbtMatchModeParsed;
        }

        public String getTargetNbt() { return targetNbt; }

        public void setType(ConditionType type) { this.type = type; }
        public void setTargetId(String targetId) { this.targetId = targetId; }
        public void setCount(int count) { this.count = count; }

        // [D3] 设置时清除缓存
        public void setNbtMatchMode(String mode) {
            this.nbtMatchModeParsed = null;
            this.nbtMatchMode = mode != null ? NbtMatchMode.fromSaveName(mode).getSaveName() : "ignore";
        }

        public void setNbtMatchMode(NbtMatchMode mode) {
            this.nbtMatchModeParsed = mode;
            this.nbtMatchMode = mode != null ? mode.getSaveName() : "ignore";
        }

        public void setTargetNbt(String nbt) { this.targetNbt = nbt; }
    }

    public static class VanillaAdvMeta {
        private int x = -1, y = -1;
        private String tab;
        private List<String> prerequisites;
        public VanillaAdvMeta() { this.prerequisites = new ArrayList<>(); }
        public VanillaAdvMeta(int x, int y) { this.x = x; this.y = y; this.prerequisites = new ArrayList<>(); }
        public int getX() { return x; }
        public void setX(int x) { this.x = x; }
        public int getY() { return y; }
        public void setY(int y) { this.y = y; }
        public String getTab() { return tab; }
        public void setTab(String tab) { this.tab = tab; }
        public boolean hasPosition() { return x >= 0 && y >= 0; }
        public List<String> getPrerequisites() { return prerequisites != null ? prerequisites : new ArrayList<>(); }
        public void setPrerequisites(List<String> prerequisites) { this.prerequisites = prerequisites; }
    }

    private static final ConditionTypeAdapter COND_TYPE_ADAPTER = new ConditionTypeAdapter();

    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(ConditionType.class, COND_TYPE_ADAPTER)
            .create();

    public static final Gson GSON_PRETTY = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(ConditionType.class, COND_TYPE_ADAPTER)
            .create();

    public static String advancementToJson(CustomAdvancement adv) { return GSON.toJson(adv); }
    public static CustomAdvancement advancementFromJson(String json) { return GSON.fromJson(json, CustomAdvancement.class); }

    public static Map<String, CustomAdvancement> mapFromJson(String json) {
        Type t = new TypeToken<Map<String, CustomAdvancement>>(){}.getType();
        Map<String, CustomAdvancement> r = GSON.fromJson(json, t);
        return r != null ? r : new HashMap<>();
    }

    private static class ConditionTypeAdapter implements JsonSerializer<ConditionType>, JsonDeserializer<ConditionType> {
        @Override
        public JsonElement serialize(ConditionType src, Type t, JsonSerializationContext ctx) {
            return new JsonPrimitive(src.name().toLowerCase());
        }

        @Override
        public ConditionType deserialize(JsonElement json, Type t, JsonDeserializationContext ctx) throws JsonParseException {
            String name = json.getAsString();
            try { return ConditionType.valueOf(name.toUpperCase()); }
            catch (IllegalArgumentException e) {
                LOGGER.warn("Unknown condition type '{}' in data file, defaulting to KILL_ENTITY", name);
                return ConditionType.KILL_ENTITY;
            }
        }
    }
}