package com.example.advancementoverhaul.client.gui;

import com.example.advancementoverhaul.data.ClientDataStore;
import com.example.advancementoverhaul.data.PlayerStats;
import com.example.advancementoverhaul.network.payload.StatsRequestPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stats;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NarrativeStatsScreen
extends NarrativeScreen {
    private static final Logger LOGGER = LoggerFactory.getLogger("AdvancementOverhaul/NarrativeStats");
    private static final int SIDEBAR_W = 96;
    private static final int CAT_H    = 22;
    private static final int CAT_GAP  = 2;
    private static final int CARD_H   = 22;
    private static final int CARD_GAP = 2;
    private final ClientDataStore store = ClientDataStore.getInstance();
    private Category selectedCategory = Category.JOURNEY;
    private final List<StatCard> cards = new ArrayList<StatCard>();
    private int lastStatsVersion = -1;
    private int sidebarScrollOff;
    private int sidebarMaxScroll;

    public NarrativeStatsScreen() {
        super(Component.translatable("advancementoverhaul.narrative.title"));
    }

    protected void init() {
        super.init();
        PacketDistributor.sendToServer(new StatsRequestPayload());
        this.rebuildCards();
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY) {
        int currentVersion = this.store.getStatsVersion();
        if (currentVersion != this.lastStatsVersion) {
            this.lastStatsVersion = currentVersion;
            this.rebuildCards();
        }
        Font font = Minecraft.getInstance().font;
        int sw = this.width;
        int sh = this.height;
        this.renderSidebar(g, mouseX, mouseY, font, sw, sh);
        this.renderCards(g, mouseX, mouseY, font, sw, sh);
    }

    private void renderSidebar(GuiGraphics g, int mouseX, int mouseY, Font font, int sw, int sh) {
        int sidebarX = 0;
        int sidebarTop = 32;
        int sidebarBottom = sh;
        g.fill(sidebarX, sidebarTop, sidebarX + SIDEBAR_W, sidebarBottom, 0x10101A28);
        g.renderOutline(sidebarX, sidebarTop, SIDEBAR_W, sidebarBottom - sidebarTop, 0x403A3A50);

        Category[] cats = Category.values();
        int totalH = cats.length * (CAT_H + CAT_GAP) + 8;
        sidebarMaxScroll = Math.max(0, totalH - (sidebarBottom - sidebarTop - 8));
        if (sidebarScrollOff > sidebarMaxScroll) sidebarScrollOff = sidebarMaxScroll;
        if (sidebarScrollOff < 0) sidebarScrollOff = 0;

        g.enableScissor(sidebarX, sidebarTop, sidebarX + SIDEBAR_W, sidebarBottom);
        int y = sidebarTop + 6 - sidebarScrollOff;
        for (Category cat : cats) {
            boolean sel = cat == this.selectedCategory;
            boolean hov = GuiUtils.inRect(mouseX, mouseY, sidebarX, y, SIDEBAR_W - 4, CAT_H);
            if (sel) {
                g.fill(sidebarX + 2, y, sidebarX + SIDEBAR_W - 4, y + CAT_H, 0x30303860);
                g.fill(sidebarX + 2, y, sidebarX + 5, y + CAT_H, cat.color);
            } else if (hov) {
                g.fill(sidebarX + 2, y, sidebarX + SIDEBAR_W - 4, y + CAT_H, 0x18282A40);
            }
            // 移除 \ufe0f (VS16) 并只保留基础 emoji
            String icon = cat.icon.replace("\ufe0f", "");
            g.drawString(font, icon, sidebarX + 10, y + 5, sel ? 0xFFE8E0D0 : 0xFFA09880, false);
            String name = Component.translatable(cat.key).getString();
            g.drawString(font, name, sidebarX + 30, y + 5, sel ? 0xFFF0E8D8 : 0xFF908878, false);
            y += CAT_H + CAT_GAP;
        }

        // 滚动条
        if (sidebarMaxScroll > 0) {
            int trackH = sidebarBottom - sidebarTop;
            int thumbH = Math.max(12, trackH * trackH / (trackH + sidebarMaxScroll));
            int thumbY = sidebarTop + sidebarScrollOff * (trackH - thumbH) / sidebarMaxScroll;
            g.fill(SIDEBAR_W - 3, sidebarTop, SIDEBAR_W - 1, sidebarBottom, 0x18182030);
            g.fill(SIDEBAR_W - 3, thumbY,  SIDEBAR_W - 1, thumbY + thumbH, 0x60505880);
        }
        g.disableScissor();
    }

    private void renderCards(GuiGraphics g, int mouseX, int mouseY, Font font, int sw, int sh) {
        int contentX = 106;
        int contentW = sw - contentX - 12;
        int contentTop = 44;
        int contentBottom = sh - 4;
        int usableW  = contentW - 8;

        if (this.cards.isEmpty()) {
            String emptyText = Component.translatable("advancementoverhaul.narrative.empty_hint").getString();
            int tw = font.width(emptyText);
            g.drawString(font, emptyText, contentX + (contentW - tw) / 2, contentTop + 40, 0xFF8B8B70, false);
            return;
        }

        int cols = 2;
        int colW = (usableW - CARD_GAP) / cols;
        int rows = (cards.size() + cols - 1) / cols;
        int totalContentH = rows * (CARD_H + CARD_GAP) + 4;
        this.maxScroll = Math.max(0, totalContentH - (contentBottom - contentTop));
        if (this.scrollOff > this.maxScroll) this.scrollOff = this.maxScroll;
        if (this.scrollOff < 0) this.scrollOff = 0;

        g.enableScissor(contentX, contentTop, contentX + contentW, contentBottom);
        int baseY = contentTop - this.scrollOff;
        for (int i = 0; i < cards.size(); i++) {
            StatCard card = cards.get(i);
            int col = i % cols;
            int row = i / cols;
            int cx = contentX + col * (colW + CARD_GAP);
            int cy = baseY + row * (CARD_H + CARD_GAP);
            if (cy + CARD_H < contentTop || cy > contentBottom) continue;

            // Card background with subtle gradient
            g.fill(cx, cy, cx + colW, cy + CARD_H, 0xFF444458);
            // Left color bar
            g.fill(cx, cy, cx + 3, cy + CARD_H, card.cardColor | 0xFF000000);
            // Color swatch dot
            g.fill(cx + 9, cy + 8, cx + 14, cy + 13, card.cardColor | 0xFF000000);

            int textX = cx + 20;
            int maxTextW = colW - 60;
            String name = GuiUtils.truncate(font, card.displayName, maxTextW);
            g.drawString(font, name, textX, cy + 5, 0xFFE8E0D0, false);

            int valW = font.width(card.displayValue);
            g.drawString(font, card.displayValue, cx + colW - valW - 8, cy + 5, 0xFFC0B890, false);

            if (card.narrativeExtra != null && !card.narrativeExtra.isEmpty()) {
                String extra = GuiUtils.truncate(font, card.narrativeExtra, maxTextW);
                g.drawString(font, extra, textX, cy + 15, 0xFF787060, false);
            }
        }
        g.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        int mx = (int)mouseX;
        int my = (int)mouseY;
        int y = 32 + 6 - sidebarScrollOff;
        for (Category cat : Category.values()) {
            if (GuiUtils.inRect(mx, my, 0, y, SIDEBAR_W - 4, CAT_H)) {
                if (this.selectedCategory != cat) {
                    this.selectedCategory = cat;
                    this.scrollOff = 0;
                    this.rebuildCards();
                    GuiUtils.playClickSound();
                }
                return true;
            }
            y += CAT_H + CAT_GAP;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < SIDEBAR_W) {
            sidebarScrollOff -= (int)(scrollY * 16);
            sidebarScrollOff = Math.max(0, Math.min(sidebarScrollOff, sidebarMaxScroll));
            return true;
        }
        // 右侧内容区域滚轮委托给父类 scrollOff
        scrollOff -= (int)(scrollY * 20);
        scrollOff = Math.max(0, Math.min(scrollOff, maxScroll));
        return true;
    }

    private void rebuildCards() {
        this.cards.clear();
        PlayerStats stats = this.store.getPlayerStats();
        if (stats == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        LOGGER.debug("NarrativeStats rebuildCards: category={}, cards={}", this.selectedCategory.name(), this.selectedCategory.entries.size());
        for (StatEntry entry : this.selectedCategory.entries) {
            String displayValue;
            String displayName = getDisplayName(entry);
            String narrativeExtra = null;
            if (entry instanceof CustomStat cs) {
                displayValue = formatCustomStat(stats, cs.fieldName());
            } else if (entry instanceof NarrativeStat ns) {
                NarrativeResult result = formatNarrativeField(stats, ns.narrativeKey());
                displayValue = result.value();
                narrativeExtra = result.extra();
            } else if (entry instanceof VanillaStat vs) {
                displayValue = formatVanillaStat(mc, vs.statId());
            } else {
                displayValue = "\u2014";
            }
            this.cards.add(new StatCard(entry, displayName, displayValue, narrativeExtra, this.selectedCategory.color));
        }
    }

    private static String getDisplayName(StatEntry entry) {
        return Component.translatable(entry.displayKey()).getString();
    }

    private static String formatCustomStat(PlayerStats stats, String fieldName) {
        long val = stats.getStatValue(fieldName);
        if (fieldName.equals("rainTicks") || fieldName.equals("snowTicks")) {
            if (val == 0L) return "\u2014";
            long seconds = val / 20L;
            if (seconds < 60L) return seconds + "s";
            long minutes = seconds / 60L;
            if (minutes < 60L) return minutes + "min";
            long hours = minutes / 60L;
            return hours + "h " + (minutes % 60L) + "min";
        }
        if (fieldName.equals("furthestDistance")) {
            double distance = stats.furthestDistance;
            if (distance == 0.0) return "\u2014";
            return String.format("%.1f m", distance);
        }
        if (fieldName.equals("mostFrequentBiome")) {
            if (stats.mostFrequentBiome == null || stats.mostFrequentBiome.isEmpty()) return "\u2014";
            return formatBiomeName(stats.mostFrequentBiome);
        }
        if (val == 0L) return "\u2014";
        return String.valueOf(val);
    }

    private static String formatVanillaStat(Minecraft mc, ResourceLocation statId) {
        if (mc.player == null) return "\u2014";
        int val = mc.player.getStats().getValue(Stats.CUSTOM.get(statId));
        if (val == 0) return "\u2014";
        String path = statId.getPath();
        if (path.endsWith("_one_cm")) {
            double meters = (double)val / 100.0;
            if (meters < 1.0) return String.format("%.0f cm", val);
            if (meters < 1000.0) return String.format("%.1f m", meters);
            return String.format("%.1f km", meters / 1000.0);
        }
        return String.valueOf(val);
    }

    private static NarrativeResult formatNarrativeField(PlayerStats stats, String actualKey) {
        return switch (actualKey) {
            case "firstNetherDay"     -> dayResult(stats.firstNetherDay);
            case "firstEndDay"        -> dayResult(stats.firstEndDay);
            case "firstDiamondDay"    -> dayResult(stats.firstDiamondDay);
            case "firstEnchantDay"    -> dayResult(stats.firstEnchantDay);
            case "firstTameDay"       -> dayResult(stats.firstTameDay);
            case "firstRainSleepDay"  -> dayResult(stats.firstRainSleepDay);
            case "firstDeathDay"      -> stats.firstDeathRecorded
                    ? new NarrativeResult(Component.translatable("advancementoverhaul.narrative.day", stats.firstDeathDay).getString(),
                                          formatCoords(stats.firstDeathX, stats.firstDeathY, stats.firstDeathZ))
                    : new NarrativeResult("\u2014", null);
            case "latestDeath"        -> stats.firstDeathRecorded
                    ? new NarrativeResult("", formatCoords(stats.latestDeathX, stats.latestDeathY, stats.latestDeathZ))
                    : new NarrativeResult("\u2014", null);
            case "firstBlockPlaced"   -> stats.firstBlockPlacedRecorded
                    ? new NarrativeResult("", formatCoords(stats.firstBlockPlacedX, stats.firstBlockPlacedY, stats.firstBlockPlacedZ))
                    : new NarrativeResult("\u2014", null);
            case "lowestY"            -> stats.hasLowestY() ? new NarrativeResult("Y=" + stats.lowestY, null) : new NarrativeResult("\u2014", null);
            case "highestY"           -> stats.hasHighestY() ? new NarrativeResult("Y=" + stats.highestY, null) : new NarrativeResult("\u2014", null);
            default                   -> new NarrativeResult("\u2014", null);
        };
    }

    private static NarrativeResult dayResult(int day) {
        if (day > 0) return new NarrativeResult(Component.translatable("advancementoverhaul.narrative.day", day).getString(), null);
        return new NarrativeResult("\u2014", null);
    }

    private static String formatCoords(int x, int y, int z) {
        return "X=" + x + "  Y=" + y + "  Z=" + z;
    }

    private static String formatBiomeName(String biomeId) {
        String name = biomeId.contains(":") ? biomeId.substring(biomeId.indexOf(':') + 1) : biomeId;
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (char c : name.toCharArray()) {
            if (c == '_') { sb.append(' '); capitalize = true; continue; }
            sb.append(capitalize ? Character.toUpperCase(c) : c);
            capitalize = false;
        }
        return sb.toString();
    }

    // ── 去掉 VS16 (\ufe0f) 的 emoji ──
    private enum Category {
        JOURNEY ("\ud83d\uddfa",  "advancementoverhaul.narrative.cat_journey",   0xFFE68A3C,
                CustomStat.of("sunrisesViewed"), CustomStat.of("sunsetsViewed"),
                CustomStat.of("rainTicks"), CustomStat.of("snowTicks"),
                NarrativeStat.of("firstNetherDay"), NarrativeStat.of("firstEndDay"),
                NarrativeStat.of("firstRainSleepDay"),
                VanillaStat.of(Stats.WALK_ONE_CM,  "advancementoverhaul.narrative.stat_distanceWalked"),
                VanillaStat.of(Stats.SWIM_ONE_CM,  "advancementoverhaul.narrative.stat_distanceSwum"),
                VanillaStat.of(Stats.SPRINT_ONE_CM,"advancementoverhaul.narrative.stat_distanceSprint"),
                VanillaStat.of(Stats.FLY_ONE_CM,   "advancementoverhaul.narrative.stat_distanceFlown"),
                VanillaStat.of(Stats.JUMP,          "advancementoverhaul.narrative.stat_jumps")),
        BUILDING("\ud83c\udfd7",  "advancementoverhaul.narrative.cat_building",  0xFF3A8FC4,
                CustomStat.of("blocksPlaced"), CustomStat.of("blocksBroken"),
                CustomStat.of("torchesPlaced"), CustomStat.of("blocksPlacedInWater"),
                NarrativeStat.of("firstBlockPlaced")),
        COMBAT  ("\u2694",       "advancementoverhaul.narrative.cat_combat",    0xFFC84040,
                CustomStat.of("lightningStrikes"), CustomStat.of("fallDamageEvents"),
                NarrativeStat.of("firstDeathDay"), NarrativeStat.of("latestDeath"),
                VanillaStat.of(Stats.DAMAGE_DEALT, "advancementoverhaul.narrative.stat_damageDealt"),
                VanillaStat.of(Stats.DAMAGE_TAKEN, "advancementoverhaul.narrative.stat_damageTaken"),
                VanillaStat.of(Stats.MOB_KILLS,    "advancementoverhaul.narrative.stat_mobKills"),
                VanillaStat.of(Stats.PLAYER_KILLS, "advancementoverhaul.narrative.stat_playerKills"),
                VanillaStat.of(Stats.DEATHS,       "advancementoverhaul.narrative.stat_deaths")),
        SURVIVAL("\ud83c\udf3e", "advancementoverhaul.narrative.cat_survival",  0xFF80B840,
                CustomStat.of("animalsTamed"), CustomStat.of("animalsFed"),
                CustomStat.of("cropsPlanted"), CustomStat.of("nameTagsUsed"),
                CustomStat.of("wanderingTraderTrades"),
                NarrativeStat.of("firstTameDay"),
                VanillaStat.of(Stats.FISH_CAUGHT,      "advancementoverhaul.narrative.stat_fishCaught"),
                VanillaStat.of(Stats.ANIMALS_BRED,     "advancementoverhaul.narrative.stat_animalsBred"),
                VanillaStat.of(Stats.EAT_CAKE_SLICE,   "advancementoverhaul.narrative.stat_cakeSlicesEaten")),
        CRAFTING("\ud83d\udce6", "advancementoverhaul.narrative.cat_crafting",  0xFF9B60C0,
                CustomStat.of("itemsCrafted"),
                NarrativeStat.of("firstDiamondDay"), NarrativeStat.of("firstEnchantDay"),
                VanillaStat.of(Stats.INTERACT_WITH_CRAFTING_TABLE, "advancementoverhaul.narrative.stat_craftingTableUses"),
                VanillaStat.of(Stats.INTERACT_WITH_ANVIL,          "advancementoverhaul.narrative.stat_anvilUses"),
                VanillaStat.of(Stats.INTERACT_WITH_GRINDSTONE,     "advancementoverhaul.narrative.stat_grindstoneUses"),
                VanillaStat.of(Stats.ENCHANT_ITEM,                  "advancementoverhaul.narrative.stat_itemsEnchanted")),
        EXPLORE ("\ud83e\udded", "advancementoverhaul.narrative.cat_explore",   0xFF50A8B0,
                CustomStat.of("furthestDistance"),
                NarrativeStat.of("lowestY"), NarrativeStat.of("highestY"),
                CustomStat.of("mostFrequentBiome"),
                VanillaStat.of(Stats.INTERACT_WITH_BEACON,    "advancementoverhaul.narrative.stat_beaconUses"),
                VanillaStat.of(Stats.TRADED_WITH_VILLAGER,    "advancementoverhaul.narrative.stat_villagerTrades"),
                VanillaStat.of(Stats.RAID_WIN,                "advancementoverhaul.narrative.stat_raidsWon"),
                VanillaStat.of(Stats.TARGET_HIT,              "advancementoverhaul.narrative.stat_targetsHit"),
                VanillaStat.of(Stats.BELL_RING,               "advancementoverhaul.narrative.stat_bellsRung"));

        final String icon;
        final String key;
        final int color;
        final List<StatEntry> entries;

        Category(String icon, String key, int color, StatEntry... entries) {
            this.icon = icon;
            this.key = key;
            this.color = color;
            this.entries = List.of(entries);
        }
    }

    private record StatCard(StatEntry entry, String displayName, String displayValue, String narrativeExtra, int cardColor) {}
    private interface StatEntry { String displayKey(); }
    private record VanillaStat(String displayKey, ResourceLocation statId) implements StatEntry {
        static VanillaStat of(ResourceLocation statId, String displayKey) { return new VanillaStat(displayKey, statId); }
    }
    private record CustomStat(String fieldName, String displayKey) implements StatEntry {
        static CustomStat of(String fieldName) { return new CustomStat(fieldName, "advancementoverhaul.narrative.stat_" + fieldName); }
    }
    private record NarrativeStat(String narrativeKey, String displayKey) implements StatEntry {
        static NarrativeStat of(String narrativeKey) { return new NarrativeStat(narrativeKey, "advancementoverhaul.narrative.stat_" + narrativeKey); }
    }
    private record NarrativeResult(String value, String extra) {}
}
