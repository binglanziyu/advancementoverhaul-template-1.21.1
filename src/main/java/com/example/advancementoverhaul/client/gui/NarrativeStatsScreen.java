/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.Font
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.protocol.common.custom.CustomPacketPayload
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.stats.Stats
 *  net.neoforged.neoforge.network.PacketDistributor
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package com.example.advancementoverhaul.client.gui;

import com.example.advancementoverhaul.client.gui.GuiUtils;
import com.example.advancementoverhaul.client.gui.NarrativeScreen;
import com.example.advancementoverhaul.data.ClientDataStore;
import com.example.advancementoverhaul.data.PlayerStats;
import com.example.advancementoverhaul.network.payload.StatsRequestPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stats;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NarrativeStatsScreen
extends NarrativeScreen {
    private static final Logger LOGGER = LoggerFactory.getLogger((String)"AdvancementOverhaul/NarrativeStats");
    private static final int SIDEBAR_W = 92;
    private static final int CARD_H = 40;
    private static final int CARD_GAP = 4;
    private final ClientDataStore store = ClientDataStore.getInstance();
    private Category selectedCategory = Category.JOURNEY;
    private final List<StatCard> cards = new ArrayList<StatCard>();
    private int lastStatsVersion = -1;

    public NarrativeStatsScreen() {
        super((Component)Component.translatable((String)"advancementoverhaul.narrative.title"));
    }

    protected void init() {
        super.init();
        PacketDistributor.sendToServer((CustomPacketPayload)new StatsRequestPayload(), (CustomPacketPayload[])new CustomPacketPayload[0]);
        this.rebuildCards();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
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
        this.renderSidebar(g, mouseX, mouseY, sw, sh);
        this.renderCards(g, mouseX, mouseY, font, sw, sh);
    }

    private void renderSidebar(GuiGraphics g, int mouseX, int mouseY, int sw, int sh) {
        Font font = Minecraft.getInstance().font;
        int sidebarX = 0;
        g.fill(sidebarX, 32, sidebarX + 92, sh, -267382760);
        g.renderOutline(sidebarX, 32, 92, sh - 32, -12961200);
        int y = 44;
        for (Category cat : Category.values()) {
            boolean sel = cat == this.selectedCategory;
            boolean hov = GuiUtils.inRect(mouseX, mouseY, sidebarX, y, 92, 36);
            if (sel) {
                g.fill(sidebarX + 2, y + 2, sidebarX + 92 - 2, y + 34, -12961198);
                g.fill(sidebarX + 2, y + 2, sidebarX + 5, y + 34, cat.color);
            } else if (hov) {
                g.fill(sidebarX + 2, y + 2, sidebarX + 92 - 2, y + 34, -14013888);
            }
            g.drawString(font, cat.icon, sidebarX + 14, y + 10, sel ? -1 : -2565912, false);
            String name = Component.translatable((String)cat.key).getString();
            g.drawString(font, name, sidebarX + 34, y + 10, sel ? -1 : -7303000, false);
            y += 38;
        }
    }

    private void renderCards(GuiGraphics g, int mouseX, int mouseY, Font font, int sw, int sh) {
        int contentX = 104;
        int contentW = sw - contentX - 12;
        int contentTop = 44;
        int contentBottom = sh - 4;
        if (this.cards.isEmpty()) {
            String emptyText = Component.translatable((String)"advancementoverhaul.narrative.empty_hint").getString();
            int tw = font.width(emptyText);
            g.drawString(font, emptyText, contentX + (contentW - tw) / 2, contentTop + 40, -7303000, false);
            return;
        }
        g.enableScissor(contentX, contentTop, contentX + contentW, contentBottom);
        int y = contentTop - this.scrollOff;
        for (StatCard card : this.cards) {
            int cardTotalH = 44;
            if (y + cardTotalH < contentTop || y > contentBottom) {
                y += cardTotalH;
                continue;
            }
            GuiUtils.drawCardShadow(g, contentX, y, contentW - 4, 40);
            g.fill(contentX, y, contentX + contentW - 4, y + 40, -12303264);
            int colorAlpha = card.cardColor & 0xFFFFFF | Integer.MIN_VALUE;
            g.fill(contentX, y, contentX + 4, y + 40, colorAlpha);
            g.fill(contentX + 12, y + 16, contentX + 20, y + 24, card.cardColor | 0xFF000000);
            int textX = contentX + 30;
            boolean isVanilla = card.entry instanceof VanillaStat;
            String srcTag = isVanilla ? "\u00a77V\u00a7r " : "";
            g.drawString(font, srcTag + card.displayName, textX, y + 7, -1, false);
            int narrativeY = y + 22;
            if (card.narrativeExtra != null && !card.narrativeExtra.isEmpty()) {
                g.drawString(font, card.narrativeExtra, textX, narrativeY, -7303000, false);
            }
            int valW = font.width(card.displayValue);
            g.drawString(font, card.displayValue, contentX + contentW - 4 - 12 - valW, y + 16, -1, false);
            g.fill(textX, y + 40 - 1, contentX + contentW - 4 - 12, y + 40, 0x10FFFFFF);
            y += cardTotalH;
        }
        int totalContentH = this.cards.size() * 44;
        this.maxScroll = Math.max(0, totalContentH - (contentBottom - contentTop));
        if (this.scrollOff > this.maxScroll) {
            this.scrollOff = this.maxScroll;
        }
        g.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        int mx = (int)mouseX;
        int my = (int)mouseY;
        int y = 44;
        for (Category cat : Category.values()) {
            if (GuiUtils.inRect(mx, my, 0, y, 92, 36)) {
                if (this.selectedCategory != cat) {
                    this.selectedCategory = cat;
                    this.scrollOff = 0;
                    this.rebuildCards();
                    GuiUtils.playClickSound();
                }
                return true;
            }
            y += 38;
        }
        return false;
    }

    private void rebuildCards() {
        this.cards.clear();
        PlayerStats stats = this.store.getPlayerStats();
        if (stats == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return;
        }
        int cardCount = this.selectedCategory.entries.size();
        LOGGER.debug("NarrativeStats rebuildCards: category={}, cards={}, hasAnyData={}", new Object[]{this.selectedCategory.name(), cardCount, stats.hasAnyData()});
        for (StatEntry entry : this.selectedCategory.entries) {
            String displayValue;
            String displayName = NarrativeStatsScreen.getDisplayName(entry);
            String narrativeExtra = null;
            if (entry instanceof CustomStat) {
                CustomStat cs = (CustomStat)entry;
                displayValue = NarrativeStatsScreen.formatCustomStat(stats, cs.fieldName());
            } else if (entry instanceof NarrativeStat) {
                NarrativeStat ns = (NarrativeStat)entry;
                NarrativeResult result = NarrativeStatsScreen.formatNarrativeField(stats, ns.narrativeKey());
                displayValue = result.value();
                narrativeExtra = result.extra();
            } else if (entry instanceof VanillaStat) {
                VanillaStat vs = (VanillaStat)entry;
                displayValue = NarrativeStatsScreen.formatVanillaStat(mc, vs.statId());
            } else {
                displayValue = "\u2014";
            }
            this.cards.add(new StatCard(entry, displayName, displayValue, narrativeExtra, this.selectedCategory.color));
        }
    }

    private static String getDisplayName(StatEntry entry) {
        return Component.translatable((String)entry.displayKey()).getString();
    }

    private static String formatCustomStat(PlayerStats stats, String fieldName) {
        long val = stats.getStatValue(fieldName);
        if (fieldName.equals("rainTicks") || fieldName.equals("snowTicks")) {
            if (val == 0L) {
                return "\u2014";
            }
            long seconds = val / 20L;
            if (seconds < 60L) {
                return seconds + "s";
            }
            long minutes = seconds / 60L;
            if (minutes < 60L) {
                return minutes + "min";
            }
            long hours = minutes / 60L;
            return hours + "h " + (minutes %= 60L) + "min";
        }
        if (fieldName.equals("furthestDistance")) {
            double distance = stats.furthestDistance;
            if (distance == 0.0) {
                return "\u2014";
            }
            return String.format("%.1f m", distance);
        }
        if (fieldName.equals("mostFrequentBiome")) {
            if (stats.mostFrequentBiome == null || stats.mostFrequentBiome.isEmpty()) {
                return "\u2014";
            }
            return NarrativeStatsScreen.formatBiomeName(stats.mostFrequentBiome);
        }
        if (val == 0L) {
            return "\u2014";
        }
        return String.valueOf(val);
    }

    private static String formatVanillaStat(Minecraft mc, ResourceLocation statId) {
        if (mc.player == null) {
            return "\u2014";
        }
        int val = mc.player.getStats().getValue(Stats.CUSTOM.get(statId));
        if (val == 0) {
            return "\u2014";
        }
        String path = statId.getPath();
        if (path.endsWith("_one_cm")) {
            double meters = (double)val / 100.0;
            if (meters < 1.0) {
                return String.format("%.0f cm", val);
            }
            if (meters < 1000.0) {
                return String.format("%.1f m", meters);
            }
            return String.format("%.1f km", meters / 1000.0);
        }
        return String.valueOf(val);
    }

    private static NarrativeResult formatNarrativeField(PlayerStats stats, String actualKey) {
        return switch (actualKey) {
            case "firstNetherDay" -> {
                if (stats.firstNetherDay > 0) {
                    yield new NarrativeResult(Component.translatable((String)"advancementoverhaul.narrative.day", (Object[])new Object[]{stats.firstNetherDay}).getString(), null);
                }
                yield new NarrativeResult("\u2014", null);
            }
            case "firstEndDay" -> {
                if (stats.firstEndDay > 0) {
                    yield new NarrativeResult(Component.translatable((String)"advancementoverhaul.narrative.day", (Object[])new Object[]{stats.firstEndDay}).getString(), null);
                }
                yield new NarrativeResult("\u2014", null);
            }
            case "firstDiamondDay" -> {
                if (stats.firstDiamondDay > 0) {
                    yield new NarrativeResult(Component.translatable((String)"advancementoverhaul.narrative.day", (Object[])new Object[]{stats.firstDiamondDay}).getString(), null);
                }
                yield new NarrativeResult("\u2014", null);
            }
            case "firstEnchantDay" -> {
                if (stats.firstEnchantDay > 0) {
                    yield new NarrativeResult(Component.translatable((String)"advancementoverhaul.narrative.day", (Object[])new Object[]{stats.firstEnchantDay}).getString(), null);
                }
                yield new NarrativeResult("\u2014", null);
            }
            case "firstTameDay" -> {
                if (stats.firstTameDay > 0) {
                    yield new NarrativeResult(Component.translatable((String)"advancementoverhaul.narrative.day", (Object[])new Object[]{stats.firstTameDay}).getString(), null);
                }
                yield new NarrativeResult("\u2014", null);
            }
            case "firstRainSleepDay" -> {
                if (stats.firstRainSleepDay > 0) {
                    yield new NarrativeResult(Component.translatable((String)"advancementoverhaul.narrative.day", (Object[])new Object[]{stats.firstRainSleepDay}).getString(), null);
                }
                yield new NarrativeResult("\u2014", null);
            }
            case "firstDeathDay" -> {
                if (stats.firstDeathRecorded) {
                    yield new NarrativeResult(Component.translatable((String)"advancementoverhaul.narrative.day", (Object[])new Object[]{stats.firstDeathDay}).getString(), NarrativeStatsScreen.formatCoords(stats.firstDeathX, stats.firstDeathY, stats.firstDeathZ));
                }
                yield new NarrativeResult("\u2014", null);
            }
            case "latestDeath" -> {
                if (stats.firstDeathRecorded) {
                    yield new NarrativeResult("", NarrativeStatsScreen.formatCoords(stats.latestDeathX, stats.latestDeathY, stats.latestDeathZ));
                }
                yield new NarrativeResult("\u2014", null);
            }
            case "firstBlockPlaced" -> {
                if (stats.firstBlockPlacedRecorded) {
                    yield new NarrativeResult("", NarrativeStatsScreen.formatCoords(stats.firstBlockPlacedX, stats.firstBlockPlacedY, stats.firstBlockPlacedZ));
                }
                yield new NarrativeResult("\u2014", null);
            }
            case "lowestY" -> {
                if (stats.hasLowestY()) {
                    yield new NarrativeResult("Y=" + stats.lowestY, null);
                }
                yield new NarrativeResult("\u2014", null);
            }
            case "highestY" -> {
                if (stats.hasHighestY()) {
                    yield new NarrativeResult("Y=" + stats.highestY, null);
                }
                yield new NarrativeResult("\u2014", null);
            }
            default -> new NarrativeResult("\u2014", null);
        };
    }

    private static String formatCoords(int x, int y, int z) {
        return "X=" + x + "  Y=" + y + "  Z=" + z;
    }

    private static String formatBiomeName(String biomeId) {
        String name = biomeId.contains(":") ? biomeId.substring(biomeId.indexOf(58) + 1) : biomeId;
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (char c : name.toCharArray()) {
            if (c == '_') {
                sb.append(' ');
                capitalize = true;
                continue;
            }
            if (capitalize) {
                sb.append(Character.toUpperCase(c));
                capitalize = false;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static enum Category {
        JOURNEY("\ud83d\uddfa\ufe0f", "advancementoverhaul.narrative.cat_journey", -1671646, CustomStat.of("sunrisesViewed"), CustomStat.of("sunsetsViewed"), CustomStat.of("rainTicks"), CustomStat.of("snowTicks"), NarrativeStat.of("firstNetherDay"), NarrativeStat.of("firstEndDay"), NarrativeStat.of("firstRainSleepDay"), VanillaStat.of(Stats.WALK_ONE_CM, "advancementoverhaul.narrative.stat_distanceWalked"), VanillaStat.of(Stats.SWIM_ONE_CM, "advancementoverhaul.narrative.stat_distanceSwum"), VanillaStat.of(Stats.SPRINT_ONE_CM, "advancementoverhaul.narrative.stat_distanceSprint"), VanillaStat.of(Stats.FLY_ONE_CM, "advancementoverhaul.narrative.stat_distanceFlown"), VanillaStat.of(Stats.JUMP, "advancementoverhaul.narrative.stat_jumps")),
        BUILDING("\ud83c\udfd7\ufe0f", "advancementoverhaul.narrative.cat_building", -13330213, CustomStat.of("blocksPlaced"), CustomStat.of("blocksBroken"), CustomStat.of("torchesPlaced"), CustomStat.of("blocksPlacedInWater"), NarrativeStat.of("firstBlockPlaced")),
        COMBAT("\u2694\ufe0f", "advancementoverhaul.narrative.cat_combat", -1618884, CustomStat.of("lightningStrikes"), CustomStat.of("fallDamageEvents"), NarrativeStat.of("firstDeathDay"), NarrativeStat.of("latestDeath"), VanillaStat.of(Stats.DAMAGE_DEALT, "advancementoverhaul.narrative.stat_damageDealt"), VanillaStat.of(Stats.DAMAGE_TAKEN, "advancementoverhaul.narrative.stat_damageTaken"), VanillaStat.of(Stats.MOB_KILLS, "advancementoverhaul.narrative.stat_mobKills"), VanillaStat.of(Stats.PLAYER_KILLS, "advancementoverhaul.narrative.stat_playerKills"), VanillaStat.of(Stats.DEATHS, "advancementoverhaul.narrative.stat_deaths")),
        SURVIVAL("\ud83c\udf3e", "advancementoverhaul.narrative.cat_survival", -13710223, CustomStat.of("animalsTamed"), CustomStat.of("animalsFed"), CustomStat.of("cropsPlanted"), CustomStat.of("nameTagsUsed"), CustomStat.of("wanderingTraderTrades"), NarrativeStat.of("firstTameDay"), VanillaStat.of(Stats.FISH_CAUGHT, "advancementoverhaul.narrative.stat_fishCaught"), VanillaStat.of(Stats.ANIMALS_BRED, "advancementoverhaul.narrative.stat_animalsBred"), VanillaStat.of(Stats.EAT_CAKE_SLICE, "advancementoverhaul.narrative.stat_cakeSlicesEaten")),
        CRAFTING("\ud83d\udce6", "advancementoverhaul.narrative.cat_crafting", -6596170, CustomStat.of("itemsCrafted"), NarrativeStat.of("firstDiamondDay"), NarrativeStat.of("firstEnchantDay"), VanillaStat.of(Stats.INTERACT_WITH_CRAFTING_TABLE, "advancementoverhaul.narrative.stat_craftingTableUses"), VanillaStat.of(Stats.INTERACT_WITH_ANVIL, "advancementoverhaul.narrative.stat_anvilUses"), VanillaStat.of(Stats.INTERACT_WITH_GRINDSTONE, "advancementoverhaul.narrative.stat_grindstoneUses"), VanillaStat.of(Stats.ENCHANT_ITEM, "advancementoverhaul.narrative.stat_itemsEnchanted")),
        EXPLORE("\ud83e\udded", "advancementoverhaul.narrative.cat_explore", -15024996, CustomStat.of("furthestDistance"), NarrativeStat.of("lowestY"), NarrativeStat.of("highestY"), CustomStat.of("mostFrequentBiome"), VanillaStat.of(Stats.INTERACT_WITH_BEACON, "advancementoverhaul.narrative.stat_beaconUses"), VanillaStat.of(Stats.TRADED_WITH_VILLAGER, "advancementoverhaul.narrative.stat_villagerTrades"), VanillaStat.of(Stats.RAID_WIN, "advancementoverhaul.narrative.stat_raidsWon"), VanillaStat.of(Stats.TARGET_HIT, "advancementoverhaul.narrative.stat_targetsHit"), VanillaStat.of(Stats.BELL_RING, "advancementoverhaul.narrative.stat_bellsRung"));

        final String icon;
        final String key;
        final int color;
        final List<StatEntry> entries;

        private Category(String icon, String key, int color, StatEntry ... entries) {
            this.icon = icon;
            this.key = key;
            this.color = color;
            this.entries = List.of(entries);
        }
    }

    private record StatCard(StatEntry entry, String displayName, String displayValue, String narrativeExtra, int cardColor) {
    }

    private static interface StatEntry {
        public String displayKey();
    }

    private record VanillaStat(String displayKey, ResourceLocation statId) implements StatEntry
    {
        static VanillaStat of(ResourceLocation statId, String displayKey) {
            return new VanillaStat(displayKey, statId);
        }
    }

    private record CustomStat(String fieldName, String displayKey) implements StatEntry
    {
        static CustomStat of(String fieldName) {
            return new CustomStat(fieldName, "advancementoverhaul.narrative.stat_" + fieldName);
        }
    }

    private record NarrativeStat(String narrativeKey, String displayKey) implements StatEntry
    {
        static NarrativeStat of(String narrativeKey) {
            return new NarrativeStat(narrativeKey, "advancementoverhaul.narrative.stat_" + narrativeKey);
        }
    }

    private record NarrativeResult(String value, String extra) {
    }
}

