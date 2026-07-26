package com.example.advancementoverhaul.compat;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public final class AdvancementMapHolder {
    public static volatile Map<ResourceLocation, AdvancementHolder> runtimeMap;

    private AdvancementMapHolder() {}
}