package com.dreamer.ao.data.model;

import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 单个装备条目：物品 + 穿戴概率 + 掉落概率。
 *
 * @param item        物品（含 NBT/附魔）
 * @param weight      在同一槽位的候选列表中的权重（加权随机选择）
 * @param probability 穿戴概率 0.0~1.0
 * @param dropChance  掉落概率 0.0~1.0
 */
public record PhaseEquipment(ItemStack item, int weight, float probability, float dropChance) {

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item.getItem());
        obj.addProperty("item", id != null ? id.toString() : "minecraft:air");
        obj.addProperty("count", item.getCount());
        if (item.hasTag()) {
            obj.addProperty("nbt", item.getTag().toString());
        }
        obj.addProperty("weight", weight);
        obj.addProperty("probability", probability);
        obj.addProperty("drop_chance", dropChance);
        return obj;
    }

    public static PhaseEquipment fromJson(JsonObject obj) {
        String itemId = obj.has("item") ? obj.get("item").getAsString() : "minecraft:air";
        int count = obj.has("count") ? obj.get("count").getAsInt() : 1;
        int weight = obj.has("weight") ? obj.get("weight").getAsInt() : 1;
        float prob = obj.has("probability") ? obj.get("probability").getAsFloat() : 0.0f;
        float drop = obj.has("drop_chance") ? obj.get("drop_chance").getAsFloat() : 0.085f;

        var itemOpt = BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(itemId));
        if (itemOpt.isEmpty()) {
            return new PhaseEquipment(ItemStack.EMPTY, weight, prob, drop);
        }
        ItemStack stack = new ItemStack(itemOpt.get(), count);
        if (obj.has("nbt")) {
            try {
                stack.getOrCreateTag().merge(
                        net.minecraft.nbt.TagParser.parseTag(obj.get("nbt").getAsString()));
            } catch (Exception ignored) {}
        }
        return new PhaseEquipment(stack, weight, prob, drop);
    }
}
