package com.example.advancementoverhaul.client;

import com.example.advancementoverhaul.data.NarrativeConfigLoader;
import com.example.advancementoverhaul.data.model.MonologueCategory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class MonologueManager {
    private static long globalCooldownMs = 60000L;
    private static long categoryCooldownMs = 300000L;
    private static long lastGlobalTrigger = 0L;
    private static final Map<String, Long> lastCategoryTriggers = new HashMap<>();
    private static final Random RNG = new Random();
    private static boolean cooldownsLoaded;

    private MonologueManager() {
    }

    private static void ensureCooldownsLoaded() {
        if (cooldownsLoaded) {
            return;
        }
        cooldownsLoaded = true;
    }

    public static void tryTrigger(String category) {
        ensureCooldownsLoaded();
        long now = System.currentTimeMillis();
        if (now - lastGlobalTrigger < globalCooldownMs) {
            return;
        }
        Long lastCat = lastCategoryTriggers.get(category);
        if (lastCat != null && now - lastCat < categoryCooldownMs) {
            return;
        }
        String monologue = selectMonologue(category);
        if (monologue != null) {
            displayMonologue(monologue);
            lastGlobalTrigger = now;
            lastCategoryTriggers.put(category, now);
        }
    }

    public static void showCustom(String text) {
        displayMonologue(text);
        lastGlobalTrigger = System.currentTimeMillis();
    }

    public static void resetCooldowns() {
        lastGlobalTrigger = 0L;
        lastCategoryTriggers.clear();
        cooldownsLoaded = false;
    }

    private static String selectMonologue(String category) {
        Map<String, MonologueCategory> data = NarrativeConfigLoader.getInstance().getMonologues();
        MonologueCategory cat = data.get(category);
        if (cat == null || cat.getTexts() == null || cat.getTexts().isEmpty()) {
            return selectFallback(category);
        }
        List<MonologueCategory.MonologueEntry> entries = cat.getTexts();
        if (entries.size() == 1) {
            return entries.get(0).getText();
        }
        double totalWeight = 0.0;
        for (MonologueCategory.MonologueEntry entry : entries) {
            totalWeight += Math.max(entry.getWeight(), 0.0);
        }
        if (totalWeight <= 0.0) {
            return entries.get(RNG.nextInt(entries.size())).getText();
        }
        double roll = RNG.nextDouble() * totalWeight;
        double cumulative = 0.0;
        for (MonologueCategory.MonologueEntry entry : entries) {
            cumulative += Math.max(entry.getWeight(), 0.0);
            if (roll <= cumulative) {
                return entry.getText();
            }
        }
        return entries.get(entries.size() - 1).getText();
    }

    private static String selectFallback(String category) {
        String[] pool = switch (category) {
            case "sunrise" -> new String[]{
                "\u00a76\u2726 \u00a7o\u6668\u66e6\u8f7b\u629a\u5927\u5730\uff0c\u65b0\u7684\u4e00\u5929\u5f00\u59cb\u4e86\u00a7r",
                "\u00a7e\u263c \u00a7o\u9633\u5149\u7a7f\u900f\u8584\u96fe\uff0c\u4e07\u7269\u82cf\u9192\u00a7r",
                "\u00a7b\u2728 \u00a7o\u7b2c\u4e00\u7f15\u5149\u6d12\u5728\u4f60\u7684\u80a9\u5934\uff0c\u4e16\u754c\u5728\u547c\u5438\u00a7r"
            };
            case "sunset" -> new String[]{
                "\u00a7d\u2601 \u00a7o\u66ae\u8272\u6e10\u6c89\uff0c\u5929\u8fb9\u67d3\u4e0a\u6700\u540e\u4e00\u62b9\u7eef\u7ea2\u00a7r",
                "\u00a75\u263e \u00a7o\u5915\u9633\u897f\u4e0b\uff0c\u591c\u665a\u5373\u5c06\u964d\u4e34\u00a7r"
            };
            case "nether" -> new String[]{
                "\u00a7c\u2620 \u00a7o\u707c\u70ed\u7684\u98ce\u6251\u9762\u800c\u6765\uff0c\u4f60\u8e0f\u5165\u4e86\u53e6\u4e00\u4e2a\u4e16\u754c\u00a7r"
            };
            case "end" -> new String[]{
                "\u00a7d\u2606 \u00a7o\u65e0\u5c3d\u7684\u865a\u7a7a\u4e4b\u4e2d\uff0c\u661f\u8fb0\u5728\u9759\u9759\u6ce8\u89c6\u7740\u4f60\u00a7r"
            };
            case "death" -> new String[]{
                "\u00a7c\u271d \u00a7o\u6b7b\u4ea1\u4e0d\u662f\u7ec8\u70b9\uff0c\u662f\u53e6\u4e00\u6bb5\u65c5\u7a0b\u7684\u5f00\u59cb\u00a7r"
            };
            case "diamond" -> new String[]{
                "\u00a7b\u25c7 \u00a7o\u84dd\u8272\u5149\u8292\u5728\u624b\u4e2d\u95ea\u8000\u2014\u2014\u8fd9\u662f\u5927\u5730\u7684\u9988\u8d60\u00a7r"
            };
            case "enchant" -> new String[]{
                "\u00a7d\u2736 \u00a7o\u5965\u672f\u7684\u80fd\u91cf\u5728\u7a7a\u6c14\u4e2d\u6d41\u6dcc\uff0c\u77e5\u8bc6\u5373\u662f\u529b\u91cf\u00a7r"
            };
            case "distance" -> new String[]{
                "\u00a7a\u279c \u00a7o\u4f60\u8d70\u5f97\u66f4\u8fdc\u4e86\uff0c\u4e16\u754c\u6bd4\u4f60\u60f3\u8c61\u7684\u66f4\u52a0\u8fbd\u9614\u00a7r"
            };
            case "depth" -> new String[]{
                "\u00a78\u25bc \u00a7o\u4f60\u8d8a\u6f5c\u8d8a\u6df1\uff0c\u5730\u5e95\u7684\u79d8\u5bc6\u6b63\u5728\u63ed\u5f00\u00a7r"
            };
            case "height" -> new String[]{
                "\u00a7f\u25b2 \u00a7o\u4f60\u6500\u4e0a\u65b0\u7684\u9ad8\u5cf0\uff0c\u5929\u9645\u7ebf\u5728\u811a\u4e0b\u5ef6\u5c55\u00a7r"
            };
            default -> null;
        };
        if (pool == null || pool.length == 0) {
            return null;
        }
        return pool[RNG.nextInt(pool.length)];
    }

    private static void displayMonologue(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return;
        }
        mc.player.displayClientMessage(Component.literal(text), true);
    }
}
