package com.example.advancementoverhaul.data.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 自定义进度定义。
 * <p>
 * 支持通过 {@code prerequisites} 设置前置条件依赖链。
 * 通过 {@code conditions} 设置一个或多个条件（AND 逻辑）。
 * 未配置条件时，进度在首次触发时自动完成。
 */
public class CustomAdvancement {
    private String id;
    private String name;
    private String description;
    private int x, y;
    private String tab;
    private boolean hidden;
    private String icon;
    /** Narrative lore text revealed upon completion, enhancing immersion */
    private String lore;
    private List<String> prerequisites;
    private List<AdvancementCondition> conditions;

    public CustomAdvancement() {
        this.prerequisites = new ArrayList<>();
        this.conditions = new ArrayList<>();
        this.hidden = false;
        this.lore = null;
    }

    public CustomAdvancement(String id, String name, String description, int x, int y) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.x = x;
        this.y = y;
        this.hidden = false;
        this.lore = null;
        this.prerequisites = new ArrayList<>();
        this.conditions = new ArrayList<>();
    }

    /** 深拷贝，用于编辑场景中保持原始数据不受影响 */
    public CustomAdvancement deepCopy() {
        CustomAdvancement c = new CustomAdvancement(id, name, description, x, y);
        c.tab = tab;
        c.hidden = hidden;
        c.icon = icon;
        c.lore = lore;
        c.prerequisites = new ArrayList<>(prerequisites != null ? prerequisites : List.of());
        c.conditions = new ArrayList<>();
        if (conditions != null) {
            for (AdvancementCondition ac : conditions) {
                c.conditions.add(ac.deepCopy());
            }
        }
        return c;
    }

    // Getters — 返回不可变视图防止外部修改
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getX() { return x; }
    public int getY() { return y; }
    public String getTab() { return tab; }
    public boolean isHidden() { return hidden; }
    public String getIcon() { return icon; }
    public String getLore() { return lore; }
    public List<String> getPrerequisites() {
        return prerequisites != null ? Collections.unmodifiableList(prerequisites) : List.of();
    }
    public List<AdvancementCondition> getConditions() {
        return conditions != null ? Collections.unmodifiableList(conditions) : List.of();
    }

    // Setters — 包内可调用
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public void setTab(String tab) { this.tab = tab; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }
    public void setIcon(String icon) { this.icon = icon; }
    public void setLore(String lore) { this.lore = lore; }
    public void setPrerequisites(List<String> prerequisites) { this.prerequisites = prerequisites; }
    public void setConditions(List<AdvancementCondition> conditions) { this.conditions = conditions; }
}
