package com.dreamer.ao.data.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 单个装备槽位的候选装备列表。
 * <p>
 * 支持加权随机选择：从 items 列表中按 weight 加权随机选一个，
 * 然后按该条目的 probability 判定是否穿戴。
 */
public class PhaseEquipmentSlot {

    private final List<PhaseEquipment> items;

    public PhaseEquipmentSlot(List<PhaseEquipment> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

    public List<PhaseEquipment> getItems() {
        return items;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * 加权随机选择一个装备条目。
     *
     * @return 选中的条目，或 null（列表为空时）
     */
    public PhaseEquipment roll() {
        if (items.isEmpty()) return null;
        int totalWeight = 0;
        for (PhaseEquipment e : items) {
            totalWeight += Math.max(1, e.weight());
        }
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;
        for (PhaseEquipment e : items) {
            cumulative += Math.max(1, e.weight());
            if (roll < cumulative) return e;
        }
        return items.get(items.size() - 1);
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        JsonArray arr = new JsonArray();
        for (PhaseEquipment e : items) {
            arr.add(e.toJson());
        }
        obj.add("items", arr);
        return obj;
    }

    public static PhaseEquipmentSlot fromJson(JsonObject obj) {
        List<PhaseEquipment> list = new ArrayList<>();
        if (obj.has("items") && obj.get("items").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("items")) {
                if (el.isJsonObject()) {
                    list.add(PhaseEquipment.fromJson(el.getAsJsonObject()));
                }
            }
        }
        return new PhaseEquipmentSlot(list);
    }
}
