package com.dreamer.ao.client.gui.timeline;

import com.dreamer.ao.LangKeys;
import com.dreamer.ao.data.ClientDataStore;
import com.dreamer.ao.milestone.store.TimelineDefinitionLoader;
import com.dreamer.ao.network.NetworkHandler;
import com.dreamer.ao.network.payload.PhaseDefEditPayload;
import com.dreamer.ao.network.payload.PhaseSyncPayload;
import com.dreamer.ao.phase.PhaseAttrLimits;
import com.dreamer.ao.phase.PhaseEffectSet;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.core.registries.Registries;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/**
 * 阶段定义可视化编辑器（响应式布局）。
 * <p>
 * 左列为基本字段，中列为可增删的效果行（key 为下拉选择），右列为实时预览。
 * 面板尺寸随窗口自适应，内容超出时中列/预览列各自滚动。
 */
public final class PhaseEditScreen extends Screen {

    private static final int MIN_W = 420;
    private static final int MIN_H = 260;
    private static final int MAX_W = 700;
    private static final int MAX_H = 460;
    private static final int MARGIN = 20;
    private static final int ROW_H = 18;
    private static final int LINE_H = 11;

    private static final int C_PANEL = 0xF01B1B1B;
    private static final int C_FIELD = 0xFF2A2A2A;
    private static final int C_BTN = 0xFF3A3A3A;
    private static final int C_BTNH = 0xFF505050;
    private static final int C_PRIMARY = 0xFF2E5A88;
    private static final int C_TXT = 0xFFE0E0E0;
    private static final int C_LABEL = 0xFF9ACBE0;
    private static final int C_SCROLL = 0xFF5A5A5A;

    private static final String[] SCOPES = {"world", "dimension", "player"};
    private static final String[] SCOPE_KEYS = {
            LangKeys.PHASE_SCOPE_GLOBAL, LangKeys.PHASE_SCOPE_DIMENSION, LangKeys.PHASE_SCOPE_PLAYER
    };

    /** 状态效果可选列表（来自注册表） */
    private static final List<String> MOB_EFFECT_OPTIONS = new ArrayList<>();
    /** 实体可选列表（运行时扫描注册表，仅含能穿装备的生物 + all） */
    private static final List<String> ENTITY_OPTIONS = new ArrayList<>();
    /** 装备槽位可选 key（5 部位，不含 offhand 以贴合用户需求；offhand 仍保留兼容） */
    private static final List<String> EQUIP_SLOTS = List.of("head", "chest", "legs", "feet", "mainhand");
    /** 装备物品可选 id（常用装备 + 运行时扫描注册表补充） */
    private static final List<String> ITEM_OPTIONS = new ArrayList<>();
    /** 全部附魔可选 id（来自注册表） */
    private static final List<String> ALL_ENCHANTS = new ArrayList<>();
    /** 里程碑可选 id（来自已加载的 TimelineDefinitionLoader） */
    private static final List<String> MILESTONE_OPTIONS = new ArrayList<>();
    /** 里程碑下拉哨兵（非 Row 类，单独处理） */
    private static final Object MILESTONE_SENTINEL = new Object();

    static {
        for (MobEffect e : BuiltInRegistries.MOB_EFFECT) {
            MOB_EFFECT_OPTIONS.add(BuiltInRegistries.MOB_EFFECT.getKey(e).toString());
        }
        MOB_EFFECT_OPTIONS.sort(String::compareToIgnoreCase);
        // 运行时扫描注册表：仅保留能穿装备的实体（Mob 子类，排除玩家）
        ENTITY_OPTIONS.add("minecraft:all");
        for (EntityType<?> t : BuiltInRegistries.ENTITY_TYPE) {
            Class<?> base = t.getBaseClass();
            if (net.minecraft.world.entity.Mob.class.isAssignableFrom(base)
                    && base != net.minecraft.world.entity.player.Player.class) {
                ENTITY_OPTIONS.add(BuiltInRegistries.ENTITY_TYPE.getKey(t).toString());
            }
        }
        ENTITY_OPTIONS.sort(String::compareToIgnoreCase);
        for (String it : new String[]{"minecraft:diamond_helmet", "minecraft:diamond_chestplate",
                "minecraft:diamond_leggings", "minecraft:diamond_boots", "minecraft:netherite_helmet",
                "minecraft:netherite_chestplate", "minecraft:netherite_leggings", "minecraft:netherite_boots",
                "minecraft:iron_helmet", "minecraft:iron_chestplate", "minecraft:iron_leggings",
                "minecraft:iron_boots", "minecraft:shield", "minecraft:bow", "minecraft:crossbow",
                "minecraft:diamond_sword", "minecraft:netherite_sword", "minecraft:trident",
                "minecraft:golden_helmet", "minecraft:golden_chestplate", "minecraft:golden_leggings",
                "minecraft:golden_boots", "minecraft:chainmail_helmet", "minecraft:chainmail_chestplate",
                "minecraft:chainmail_leggings", "minecraft:chainmail_boots", "minecraft:leather_helmet",
                "minecraft:leather_chestplate", "minecraft:leather_leggings", "minecraft:leather_boots"}) {
            ITEM_OPTIONS.add(it);
        }
        ITEM_OPTIONS.sort(String::compareToIgnoreCase);
        // 里程碑来自已加载的时间线定义（客户端复用服务端加载器）
        try {
            var all = TimelineDefinitionLoader.getInstance().getAllMilestones();
            for (var def : all) {
                MILESTONE_OPTIONS.add(def.getId());
            }
        } catch (Exception ignored) {
        }
        MILESTONE_OPTIONS.sort(String::compareToIgnoreCase);
        // 附魔列表在首次打开装备编辑器时从注册表懒加载（需要 level 的 registryAccess）
    }

    private boolean enchantsLoaded = false;
    private void ensureEnchants() {
        if (enchantsLoaded) return;
        enchantsLoaded = true;
        try {
            var reg = Minecraft.getInstance().level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            for (var ench : reg) {
                ALL_ENCHANTS.add(reg.getKey(ench).toString());
            }
            ALL_ENCHANTS.sort(String::compareToIgnoreCase);
        } catch (Exception ignored) {
        }
    }

    private static String milestoneName(String id) {
        try {
            var def = TimelineDefinitionLoader.getInstance().getMilestone(id);
            if (def != null) return Component.translatable(def.getNameKey()).getString();
        } catch (Exception ignored) {
        }
        return id;
    }

    /** 状态效果显示名 */
    private static String mobEffectName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return id;
        MobEffect e = BuiltInRegistries.MOB_EFFECT.get(rl);
        return e.getDisplayName().getString();
    }

    /** 附魔显示名 */
    private static String enchantName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return id;
        try {
            var reg = Minecraft.getInstance().level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            var h = reg.getHolder(rl).orElse(null);
            if (h != null) return Enchantment.getFullname(h, 1).getString();
        } catch (Exception ignored) {
        }
        return id;
    }

    /** 实体显示名 */
    private static String entityName(String id) {
        if ("minecraft:all".equals(id)) return "全部";
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return id;
        EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.get(rl);
        return t.getDescription().getString();
    }

    /** 当前打开的下拉：行 + 字段名（用于区分同一行的多个下拉） */
    private record DropdownState(Object row, String field) {
    }

    private final Screen parent;
    private final String editingId; // null = 新建
    private final String editingScope; // world / dimension / player（不可选，由打开来源决定）

    private EditBox idBox, nameBox, tierBox, dimensionBox, unlockBox;
    private String unlockBoxValue = "";
    private int scopeIndex;

    private final List<AttrRow> attrRows = new ArrayList<>();
    private final List<AttrRow> mobMultRows = new ArrayList<>();
    private final List<MobEffectRow> mobEffectRows = new ArrayList<>();
    private final List<EquipRule> equipRules = new ArrayList<>();

    /** 本帧计算出的可点击热区，mouseClicked 时统一命中检测 */
    private final List<Hotspot> hotspots = new ArrayList<>();
    private final List<EditBox> activeBoxes = new ArrayList<>();

    // 布局（init 中计算）
    private int panelX, panelY, panelW, panelH;
    private int colLeftX, colLeftW, colMidX, colMidW, colRightX, colRightW;
    private int bodyTop, bodyBottom;

    /** 打开中的下拉：行 + 字段，null 表示未打开 */
    private DropdownState openKeyDropdown = null;

    private int midScroll = 0;
    private int midContentH = 0;
    private int previewScroll = 0;
    private final List<String> previewLines = new ArrayList<>();

    public PhaseEditScreen(Screen parent, String editingId, String scope) {
        super(Component.translatable(
                editingId == null ? LangKeys.PHASE_EDIT_NEW_TITLE : LangKeys.PHASE_EDIT_TITLE));
        this.parent = parent;
        this.editingId = editingId;
        this.editingScope = (scope == null || scope.isBlank()) ? "world" : scope;
        int si = 0;
        for (int i = 0; i < SCOPES.length; i++) {
            if (SCOPES[i].equals(this.editingScope)) { si = i; break; }
        }
        this.scopeIndex = si;
    }

    @Override
    protected void init() {
        layout();

        idBox = makeBox(180, editingId == null ? "" : editingId, 64);
        if (editingId != null) idBox.setEditable(false);
        nameBox = makeBox(180, "", 64);
        tierBox = makeBox(70, "0", 4);
        dimensionBox = makeBox(180, "", 64);
        unlockBox = makeBox(180, "", 64);

        if (attrRows.isEmpty() && mobMultRows.isEmpty()
                && mobEffectRows.isEmpty() && equipRules.isEmpty()) {
            if (editingId != null) {
                loadFromBrief(editingId);
            }
        }
        recomputePreview();
    }

    /** 依据当前窗口尺寸计算面板与分栏 */
    private void layout() {
        panelW = Math.max(MIN_W, Math.min(MAX_W, this.width - MARGIN * 2));
        panelH = Math.max(MIN_H, Math.min(MAX_H, this.height - MARGIN * 2));
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        int inner = panelW - 24;
        // 左 32% / 中 38% / 右 30%
        colLeftW = Math.max(150, inner * 32 / 100);
        colMidW = Math.max(170, inner * 38 / 100);
        colRightW = inner - colLeftW - colMidW - 12;
        if (colRightW < 110) {
            colRightW = 110;
            colMidW = inner - colLeftW - colRightW - 12;
        }
        colLeftX = panelX + 12;
        colMidX = colLeftX + colLeftW + 6;
        colRightX = colMidX + colMidW + 6;

        bodyTop = panelY + 26;
        bodyBottom = panelY + panelH - 28;
    }

    private EditBox makeBox(int w, String text, int maxLen) {
        EditBox box = new EditBox(this.font, 0, 0, w, 14, Component.literal(""));
        box.setMaxLength(maxLen);
        box.setValue(text);
        box.setResponder(s -> recomputePreview());
        return box;
    }

    private void loadFromBrief(String id) {
        for (String brief : ClientDataStore.getInstance().getPhaseDefBriefs()) {
            JsonObject o = PhaseSyncPayload.briefToJson(brief);
            if (!o.has("id") || !o.get("id").getAsString().equals(id)) continue;
            if (o.has("name")) nameBox.setValue(o.get("name").getAsString());
            if (o.has("tier")) tierBox.setValue(String.valueOf(o.get("tier").getAsInt()));
            if (o.has("dimension")) dimensionBox.setValue(o.get("dimension").getAsString());
            if (o.has("unlockMilestone")) {
                unlockBox.setValue(o.get("unlockMilestone").getAsString());
                unlockBoxValue = unlockBox.getValue();
            }
            if (o.has("effects")) {
                PhaseEffectSet set = PhaseEffectSet.fromJson(o.getAsJsonObject("effects"));
                for (Map.Entry<String, Double> e : set.getAttributes().entrySet()) {
                    attrRows.add(new AttrRow(e.getKey(), trimNum(e.getValue())));
                }
                for (Map.Entry<String, Double> e : set.getMobMults().entrySet()) {
                    mobMultRows.add(new AttrRow(e.getKey(), trimNum(e.getValue())));
                }
                for (PhaseEffectSet.MobEffectSpec spec : set.getMobEffects().values()) {
                    mobEffectRows.add(new MobEffectRow(spec.id(),
                            String.valueOf(spec.level()), String.valueOf(spec.seconds())));
                }
                for (PhaseEffectSet.MobEquipmentRule rule : set.getEquipmentRules()) {
                    EquipRule er = new EquipRule(rule.getEntityFilter() == null ? "" : rule.getEntityFilter());
                    for (Map.Entry<String, List<PhaseEffectSet.EquipmentEntry>> s : rule.getSlots().entrySet()) {
                        List<EquipEntry> list = er.slots.computeIfAbsent(s.getKey(), k -> new ArrayList<>());
                        for (PhaseEffectSet.EquipmentEntry e : s.getValue()) {
                            EquipEntry ee = new EquipEntry(trimNum(e.getChance()), e.getItem());
                            ee.enchants.putAll(e.getEnchants());
                            list.add(ee);
                        }
                    }
                    equipRules.add(er);
                }
            }
            return;
        }
    }

    private static String trimNum(double d) {
        if (d == Math.floor(d) && !Double.isInfinite(d)) {
            return String.valueOf((long) d);
        }
        return String.valueOf(d);
    }

    // ────────────────────────── 渲染 ──────────────────────────

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        layout();
        hotspots.clear();
        activeBoxes.clear();

        this.renderTransparentBackground(g);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, C_PANEL);
        g.drawCenteredString(font, this.title, panelX + panelW / 2, panelY + 8, C_LABEL);

        renderLeftColumn(g, mouseX, mouseY);
        renderMidColumn(g, mouseX, mouseY, partialTick);
        renderPreview(g);
        renderFooter(g, mouseX, mouseY);

        for (EditBox b : activeBoxes) {
            b.render(g, mouseX, mouseY, partialTick);
        }
        // 下拉浮层最后绘制，避免被覆盖
        renderKeyDropdown(g);
    }

    private void renderLeftColumn(GuiGraphics g, int mx, int my) {
        int x = colLeftX;
        int y = bodyTop;
        y = field(g, x, y, LangKeys.PHASE_EDIT_ID, idBox);
        y = field(g, x, y, LangKeys.PHASE_EDIT_NAME, nameBox);
        y = field(g, x, y, LangKeys.PHASE_EDIT_TIER, tierBox);

        // scope 作为不可选项，仅显示当前值（由打开来源列决定）
        g.drawString(font, Component.translatable(LangKeys.PHASE_EDIT_SCOPE), x, y, C_LABEL, false);
        y += 10;
        int ddW = Math.min(150, colLeftW);
        g.fill(x, y, x + ddW, y + 14, 0xFF262626);
        g.drawString(font, Component.translatable(SCOPE_KEYS[scopeIndex]), x + 4, y + 3, 0xFFB0B0B0, false);
        y += 18;

        if (scopeIndex == 1) {
            y = field(g, x, y, LangKeys.PHASE_EDIT_DIMENSION, dimensionBox);
        }
        if (y + 24 <= bodyBottom) {
            g.drawString(font, Component.translatable(LangKeys.PHASE_EDIT_UNLOCK_MS), x, y, C_LABEL, false);
            String cur = unlockBox.getValue().trim();
            String shown = cur.isEmpty() ? Component.translatable(LangKeys.PHASE_EDIT_SELECT_MS).getString() : milestoneName(cur);
            drawSelection(g, x, y + 10, Math.min(150, colLeftW), shown, mx, my,
                    new DropdownState(MILESTONE_SENTINEL, "ms"), MILESTONE_OPTIONS, PhaseEditScreen::milestoneName);
            y += 26;
        }
    }

    private int field(GuiGraphics g, int x, int y, String labelKey, EditBox box) {
        if (y + 24 > bodyBottom) return y;
        g.drawString(font, Component.translatable(labelKey), x, y, C_LABEL, false);
        box.setPosition(x, y + 10);
        box.setWidth(Math.min(box.getWidth(), colLeftW));
        activeBoxes.add(box);
        return y + 28;
    }

    // 中列：效果行，带滚动
    private void renderMidColumn(GuiGraphics g, int mx, int my, float pt) {
        int x = colMidX;
        int top = bodyTop;
        int bottom = bodyBottom;
        g.enableScissor(x, top, x + colMidW, bottom);

        int y = top - midScroll;
        y = section(g, x, y, LangKeys.PHASE_EDIT_ATTRS, attrRows,
                PhaseEffectSet.PLAYER_ATTR_KEYS, mx, my, () -> attrRows.add(new AttrRow()), "attr");
        y = section(g, x, y, LangKeys.PHASE_EDIT_MOB_MULTS, mobMultRows,
                PhaseEffectSet.MOB_MULT_KEYS, mx, my, () -> mobMultRows.add(new AttrRow()), "mobmult");
        y = mobEffectSection(g, x, y, mx, my);
        y = equipSection(g, x, y, mx, my);

        g.disableScissor();
        midContentH = (y + midScroll) - top;
        drawScrollbar(g, x + colMidW - 3, top, bottom - top, midContentH, midScroll);
    }

    private int section(GuiGraphics g, int x, int y, String titleKey, List<AttrRow> rows,
                        List<String> keyOptions, int mx, int my, Runnable onAdd, String field) {
        g.drawString(font, Component.translatable(titleKey), x, y, C_LABEL, false);
        y += 11;
        // 第一行：新建
        y = addRowButton(g, x, y, mx, my, onAdd);
        for (AttrRow row : new ArrayList<>(rows)) {
            if (visible(y)) {
                int kw = Math.max(70, colMidW * 52 / 100);
                int vw = Math.max(38, colMidW - kw - 22);
                drawSelection(g, x, y, kw, row.key, mx, my,
                        new DropdownState(row, field), keyOptions, displayFor(new DropdownState(row, field)));
                row.valBox.setPosition(x + kw + 2, y);
                row.valBox.setWidth(vw);
                activeBoxes.add(row.valBox);
                drawRemove(g, x + colMidW - 16, y, mx, my, () -> {
                    rows.remove(row);
                    recomputePreview();
                });
            }
            y += ROW_H;
        }
        return y + 4;
    }

    private int mobEffectSection(GuiGraphics g, int x, int y, int mx, int my) {
        g.drawString(font, Component.translatable(LangKeys.PHASE_EDIT_MOB_EFFECTS), x, y, C_LABEL, false);
        y += 11;
        y = addRowButton(g, x, y, mx, my, () -> mobEffectRows.add(new MobEffectRow()));
        for (MobEffectRow row : new ArrayList<>(mobEffectRows)) {
            if (visible(y)) {
                int idw = Math.max(70, colMidW - 90);
                drawSelection(g, x, y, idw, row.selected, mx, my,
                        new DropdownState(row, "effect"), MOB_EFFECT_OPTIONS,
                        PhaseEditScreen::mobEffectName);
                row.lvlBox.setPosition(x + idw + 2, y);
                row.lvlBox.setWidth(30);
                row.secBox.setPosition(x + idw + 34, y);
                row.secBox.setWidth(36);
                activeBoxes.add(row.lvlBox);
                activeBoxes.add(row.secBox);
                drawRemove(g, x + colMidW - 16, y, mx, my, () -> {
                    mobEffectRows.remove(row);
                    recomputePreview();
                });
            }
            y += ROW_H;
        }
        return y + 4;
    }

    private int equipSection(GuiGraphics g, int x, int y, int mx, int my) {
        ensureEnchants();
        g.drawString(font, Component.translatable(LangKeys.PHASE_EDIT_EQUIPMENT), x, y, C_LABEL, false);
        y += 11;
        y = addRowButton(g, x, y, mx, my, () -> equipRules.add(new EquipRule()));
        for (EquipRule rule : new ArrayList<>(equipRules)) {
            if (!visible(y)) { y += 16; continue; }
            // 规则头部：实体下拉 + 展开/收起 + 删除
            drawSelection(g, x, y, Math.max(80, colMidW * 45 / 100), rule.entity, mx, my,
                    new DropdownState(rule, "entity"), ENTITY_OPTIONS, PhaseEditScreen::entityName);
            drawToggle(g, x + colMidW - 52, y, mx, my,
                    Component.translatable(rule.expanded ? LangKeys.PHASE_HIDE : LangKeys.PHASE_SHOW).getString(),
                    () -> { rule.expanded = !rule.expanded; recomputePreview(); });
            drawRemove(g, x + colMidW - 16, y, mx, my, () -> {
                equipRules.remove(rule);
                recomputePreview();
            });
            y += ROW_H;
            if (rule.expanded) {
                for (String slot : EQUIP_SLOTS) {
                    if (!visible(y)) { y += 16; continue; }
                    g.drawString(font, Component.translatable(LangKeys.PHASE_EDIT_EQUIP_SLOT).getString() + ":" + slot,
                            x + 8, y + 3, C_LABEL, false);
                    drawRemove(g, x + colMidW - 16, y, mx, my, () -> {
                        rule.slots.get(slot).clear();
                        recomputePreview();
                    });
                    y += ROW_H;
                    List<EquipEntry> entries = rule.slots.get(slot);
                    for (EquipEntry en : new ArrayList<>(entries)) {
                        if (!visible(y)) { y += 16; continue; }
                        int iw = Math.max(60, colMidW * 50 / 100);
                        en.chanceBox.setPosition(x + 8, y);
                        en.chanceBox.setWidth(32);
                        activeBoxes.add(en.chanceBox);
                        drawSelection(g, x + 8 + 34, y, iw, en.itemId, mx, my,
                                new DropdownState(en, "item"), ITEM_OPTIONS, PhaseEditScreen::shortId);
                        // 附魔按钮
                        drawToggle(g, x + 8 + 34 + iw + 2, y, mx, my,
                                Component.translatable(LangKeys.PHASE_EDIT_EQUIP_ENCHANT).getString() + (en.enchants.isEmpty() ? "" : "(" + en.enchants.size() + ")"),
                                () -> { en.showEnch = !en.showEnch; recomputePreview(); });
                        drawRemove(g, x + colMidW - 16, y, mx, my, () -> {
                            entries.remove(en);
                            recomputePreview();
                        });
                        y += ROW_H;
                        if (en.showEnch) {
                            for (String encId : ALL_ENCHANTS) {
                                if (!visible(y)) { y += 16; continue; }
                                boolean on = en.enchants.containsKey(encId);
                                drawToggle(g, x + 16, y, mx, my,
                                        (on ? "[x] " : "[ ] ") + enchantName(encId),
                                        () -> {
                                            if (on) en.enchants.remove(encId);
                                            else en.enchants.put(encId, 1);
                                            recomputePreview();
                                        });
                                y += ROW_H;
                            }
                        }
                    }
                    y = addRowButton(g, x + 8, y, mx, my, () -> entries.add(new EquipEntry()));
                }
            }
        }
        return y + 4;
    }

    private void drawToggle(GuiGraphics g, int x, int y, int mx, int my, String text, Runnable onClick) {
        if (!visible(y)) return;
        boolean hov = inRect(mx, my, x, y, colMidW - 4, 14);
        g.fill(x, y, x + colMidW - 4, y + 14, hov ? C_BTNH : C_BTN);
        g.drawString(font, Component.literal(trunc(text, colMidW - 10)), x + 4, y + 3, C_TXT, false);
        hotspots.add(new Hotspot(x, y, colMidW - 4, 14, () -> { onClick.run(); }));
    }

    private int addRowButton(GuiGraphics g, int x, int y, int mx, int my, Runnable onAdd) {
        if (visible(y)) {
            boolean hov = inRect(mx, my, x, y, colMidW - 4, 14);
            g.fill(x, y, x + colMidW - 4, y + 14, hov ? C_BTNH : C_BTN);
            g.drawString(font, Component.translatable(LangKeys.PHASE_ROW_NEW), x + 4, y + 3, C_TXT, false);
            hotspots.add(new Hotspot(x, y, colMidW - 4, 14, () -> {
                onAdd.run();
                recomputePreview();
            }));
        }
        return y + 16;
    }

    private void drawSelection(GuiGraphics g, int x, int y, int w, String current,
                                int mx, int my, DropdownState state, List<String> options,
                                java.util.function.Function<String, String> display) {
        boolean hov = inRect(mx, my, x, y, w, 14);
        g.fill(x, y, x + w, y + 14, hov ? C_BTNH : C_FIELD);
        String label = current == null || current.isEmpty()
                ? Component.translatable(LangKeys.PHASE_NONE).getString() : display.apply(current);
        g.drawString(font, Component.literal(trunc(label, w - 14)), x + 3, y + 3, C_TXT, false);
        g.drawString(font, Component.literal("v"), x + w - 9, y + 3, C_TXT, false);
        hotspots.add(new Hotspot(x, y, w, 14, () -> {
            openKeyDropdown = openKeyDropdown != null && openKeyDropdown.equals(state) ? null : state;
        }));
        if (openKeyDropdown != null && openKeyDropdown.equals(state)) {
            pendingKeyDd = new KeyDropdown(x, y + 14, w, options, state);
        }
    }

    private KeyDropdown pendingKeyDd;

    /** 读取某下拉字段的当前值（用于应用选项时定位） */
    private String currentOf(DropdownState s) {
        if (s.row() instanceof AttrRow r) return r.key;
        if (s.row() instanceof MobEffectRow m) return m.selected;
        if (s.row() instanceof EquipRule e) {
            return switch (s.field()) {
                case "entity" -> e.entity;
                default -> "";
            };
        }
        if (s.row() instanceof EquipEntry e) {
            return switch (s.field()) {
                case "item" -> e.itemId;
                default -> "";
            };
        }
        if (s.row() == MILESTONE_SENTINEL) return unlockBoxValue;
        return "";
    }

    /** 将选中的选项写回对应字段 */
    private void applyOption(DropdownState s, String opt) {
        if (s.row() instanceof AttrRow r) {
            r.key = opt;
        } else if (s.row() instanceof MobEffectRow m) {
            m.selected = opt;
        } else if (s.row() instanceof EquipRule e) {
            if ("entity".equals(s.field())) e.entity = opt;
        } else if (s.row() instanceof EquipEntry e) {
            if ("item".equals(s.field())) e.itemId = opt;
        } else if (s.row() == MILESTONE_SENTINEL) {
            unlockBoxValue = opt;
            unlockBox.setValue(opt);
        }
    }

    /** 依据行类型 + 字段返回可选列表 */
    private List<String> optionsFor(DropdownState s) {
        if (s.row() instanceof MobEffectRow) return MOB_EFFECT_OPTIONS;
        if (s.row() instanceof EquipRule) {
            return "entity".equals(s.field()) ? ENTITY_OPTIONS : new ArrayList<>();
        }
        if (s.row() instanceof EquipEntry) {
            return "item".equals(s.field()) ? ITEM_OPTIONS : new ArrayList<>();
        }
        if (s.row() instanceof AttrRow) {
            return "mobmult".equals(s.field())
                    ? PhaseEffectSet.MOB_MULT_KEYS : PhaseEffectSet.PLAYER_ATTR_KEYS;
        }
        if (s.row() == MILESTONE_SENTINEL) return MILESTONE_OPTIONS;
        return new ArrayList<>();
    }

    /** 依据行类型 + 字段返回可选项的显示名 */
    private static java.util.function.Function<String, String> displayFor(DropdownState s) {
        if (s.row() instanceof MobEffectRow) return PhaseEditScreen::mobEffectName;
        if (s.row() instanceof EquipRule) {
            return "entity".equals(s.field()) ? PhaseEditScreen::entityName : (k) -> k;
        }
        if (s.row() instanceof EquipEntry) {
            return "item".equals(s.field()) ? PhaseEditScreen::shortId : (k) -> k;
        }
        if (s.row() instanceof AttrRow) {
            return "mobmult".equals(s.field())
                    ? PhaseEditScreen::localizedMobMultKey : PhaseEditScreen::localizedAttrKey;
        }
        if (s.row() == MILESTONE_SENTINEL) return PhaseEditScreen::milestoneName;
        return (k) -> k;
    }

    private void renderKeyDropdown(GuiGraphics g) {
        KeyDropdown dd = pendingKeyDd;
        pendingKeyDd = null;
        if (dd == null || openKeyDropdown == null) return;
        DropdownState st = (DropdownState) dd.row();
        List<String> opts = dd.options();
        int n = opts.size();
        int h = n * 13;
        int y = dd.y();
        if (y + h > panelY + panelH) y = Math.max(panelY + 20, dd.y() - 14 - h);
        g.fill(dd.x(), y, dd.x() + dd.w(), y + h, 0xFF1A1A1A);
        for (int i = 0; i < n; i++) {
            String opt = opts.get(i);
            int iy = y + i * 13;
            g.fill(dd.x(), iy, dd.x() + dd.w(), iy + 13, C_FIELD);
            g.drawString(font, Component.literal(trunc(displayFor(st).apply(opt), dd.w() - 6)),
                    dd.x() + 3, iy + 3, C_TXT, false);
            hotspots.add(new Hotspot(dd.x(), iy, dd.w(), 13, () -> {
                applyOption(st, opt);
                openKeyDropdown = null;
                recomputePreview();
            }));
        }
    }

    private void drawRemove(GuiGraphics g, int x, int y, int mx, int my, Runnable onRemove) {
        boolean hov = inRect(mx, my, x, y, 12, 14);
        g.fill(x, y, x + 12, y + 14, hov ? 0xFF8B3030 : C_BTN);
        g.drawString(font, Component.literal("x"), x + 4, y + 3, C_TXT, false);
        hotspots.add(new Hotspot(x, y, 12, 14, onRemove));
    }

    private boolean visible(int y) {
        return y + ROW_H >= bodyTop && y <= bodyBottom;
    }

    private void renderPreview(GuiGraphics g) {
        int x = colRightX;
        g.fill(x - 3, bodyTop, x + colRightW, bodyBottom, C_FIELD);
        g.drawString(font, Component.translatable(LangKeys.PHASE_EDIT_PREVIEW), x, bodyTop + 3, C_LABEL, false);
        int top = bodyTop + 15;
        g.enableScissor(x, top, x + colRightW, bodyBottom);
        int y = top - previewScroll;
        for (String line : previewLines) {
            if (y + LINE_H >= top && y <= bodyBottom) {
                g.drawString(font, Component.literal(line), x, y, C_TXT, false);
            }
            y += LINE_H;
        }
        g.disableScissor();
        drawScrollbar(g, x + colRightW - 3, top, bodyBottom - top,
                previewLines.size() * LINE_H, previewScroll);
    }

    private void drawScrollbar(GuiGraphics g, int x, int y, int viewH, int contentH, int scroll) {
        if (contentH <= viewH || viewH <= 0) return;
        int barH = Math.max(12, viewH * viewH / contentH);
        int maxScroll = contentH - viewH;
        int barY = y + (maxScroll <= 0 ? 0 : (viewH - barH) * scroll / maxScroll);
        g.fill(x, y, x + 3, y + viewH, 0xFF202020);
        g.fill(x, barY, x + 3, barY + barH, C_SCROLL);
    }

    private void renderFooter(GuiGraphics g, int mx, int my) {
        int by = panelY + panelH - 22;
        int bw = Math.min(80, (panelW - 40) / 3);
        int x = panelX + 12;
        button(g, x, by, bw, LangKeys.PHASE_EDIT_SAVE, mx, my, true, this::onSave);
        x += bw + 6;
        if (editingId != null) {
            button(g, x, by, bw, LangKeys.PHASE_EDIT_DELETE, mx, my, false, this::onDelete);
            x += bw + 6;
        }
        button(g, x, by, bw, LangKeys.CANCEL, mx, my, false, this::onClose);
    }

    private void button(GuiGraphics g, int x, int y, int w, String key,
                        int mx, int my, boolean primary, Runnable action) {
        boolean hov = inRect(mx, my, x, y, w, 16);
        g.fill(x, y, x + w, y + 16, hov ? C_BTNH : (primary ? C_PRIMARY : C_BTN));
        Component lbl = Component.translatable(key);
        g.drawString(font, lbl, x + Math.max(2, (w - font.width(lbl)) / 2), y + 4, C_TXT, false);
        hotspots.add(new Hotspot(x, y, w, 16, action));
    }

    private String trunc(String s, int maxPx) {
        if (font.width(s) <= maxPx) return s;
        while (s.length() > 1 && font.width(s + "..") > maxPx) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "..";
    }

    private static boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ────────────────────────── 交互 ──────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX, my = (int) mouseY;

        // 输入框优先获取焦点
        for (EditBox b : activeBoxes) {
            if (b.mouseClicked(mouseX, mouseY, button)) {
                b.setFocused(true);
                for (EditBox other : activeBoxes) {
                    if (other != b) other.setFocused(false);
                }
                return true;
            }
        }

        // 热区自后向前命中（后绘制的浮层优先）
        for (int i = hotspots.size() - 1; i >= 0; i--) {
            Hotspot h = hotspots.get(i);
            if (inRect(mx, my, h.x(), h.y(), h.w(), h.h())) {
                h.action().run();
                return true;
            }
        }

        openKeyDropdown = null;
        for (EditBox b : activeBoxes) b.setFocused(false);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        int mx = (int) mouseX;
        if (mx >= colMidX && mx < colMidX + colMidW) {
            int viewH = bodyBottom - bodyTop;
            int max = Math.max(0, midContentH - viewH);
            midScroll = Math.max(0, Math.min(max, midScroll - (int) (dy * 14)));
            return true;
        }
        if (mx >= colRightX) {
            int viewH = bodyBottom - (bodyTop + 15);
            int max = Math.max(0, previewLines.size() * LINE_H - viewH);
            previewScroll = Math.max(0, Math.min(max, previewScroll - (int) (dy * 14)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        for (EditBox b : activeBoxes) {
            if (b.isFocused() && b.charTyped(c, mods)) return true;
        }
        return super.charTyped(c, mods);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (openKeyDropdown != null) {
                openKeyDropdown = null;
                return true;
            }
            onClose();
            return true;
        }
        for (EditBox b : activeBoxes) {
            if (b.isFocused() && b.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ────────────────────────── 保存 / 预览 ──────────────────────────

    private void onSave() {
        String id = idBox.getValue().trim();
        if (!id.matches("[a-z0-9_]{1,64}")) {
            showMessage(LangKeys.PHASE_EDIT_INVALID_ID);
            return;
        }
        String json = buildJson(id).toString();
        if (json.length() > 8192) {
            showMessage(LangKeys.PHASE_EDIT_TOO_LARGE);
            return;
        }
        try {
            JsonParser.parseString(json);
        } catch (Exception e) {
            showMessage(LangKeys.PHASE_EDIT_INVALID_JSON);
            return;
        }
        NetworkHandler.sendPhaseDefEdit(new PhaseDefEditPayload("save", id, json));
        onClose();
    }

    private void onDelete() {
        NetworkHandler.sendPhaseDefEdit(new PhaseDefEditPayload("delete", editingId, null));
        onClose();
    }

    private void showMessage(String key) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable(key), false);
        }
    }

    private JsonObject buildJson(String id) {
        JsonObject root = new JsonObject();
        root.addProperty("id", id);
        String nm = nameBox.getValue().trim();
        root.addProperty("name", nm.isEmpty() ? id : nm);
        root.addProperty("tier", parseInt(tierBox.getValue(), 0));
        root.addProperty("scope", SCOPES[scopeIndex]);
        if (scopeIndex == 1 && !dimensionBox.getValue().trim().isEmpty()) {
            root.addProperty("dimension", dimensionBox.getValue().trim());
        }
        if (!unlockBox.getValue().trim().isEmpty()) {
            root.addProperty("unlockMilestone", unlockBox.getValue().trim());
        }

        JsonObject effects = new JsonObject();
        JsonObject attrs = new JsonObject();
        for (AttrRow r : attrRows) {
            if (r.key == null || r.key.isEmpty()) continue;
            Double v = parseDoubleOrNull(r.valBox.getValue());
            if (v != null) attrs.addProperty(r.key,
                    "player".equals(SCOPES[scopeIndex]) ? PhaseAttrLimits.clampPlayer(v) : PhaseAttrLimits.clamp(v));
        }
        if (!attrs.isEmpty()) effects.add("attributes", attrs);

        // 玩家作用域不支持怪物倍率/状态效果/装备规则
        boolean isPlayer = "player".equals(SCOPES[scopeIndex]);

        if (!isPlayer) {
            JsonObject mults = new JsonObject();
            for (AttrRow r : mobMultRows) {
                if (r.key == null || r.key.isEmpty()) continue;
                Double v = parseDoubleOrNull(r.valBox.getValue());
                if (v != null) mults.addProperty(r.key, PhaseAttrLimits.clampMob(v));
            }
            if (!mults.isEmpty()) effects.add("mob_mults", mults);

            JsonArray meArr = new JsonArray();
            for (MobEffectRow r : mobEffectRows) {
                String eid = r.selected == null ? "" : r.selected.trim();
                if (eid.isEmpty()) continue;
                JsonObject o = new JsonObject();
                o.addProperty("id", eid);
                o.addProperty("level", parseInt(r.lvlBox.getValue(), 0));
                o.addProperty("seconds", parseInt(r.secBox.getValue(), 30));
                meArr.add(o);
            }
            if (!meArr.isEmpty()) effects.add("mob_effects", meArr);

            JsonArray eqArr = new JsonArray();
            for (EquipRule r : equipRules) {
                String ent = r.entity == null ? "" : r.entity.trim();
                if (ent.isEmpty()) continue;
                JsonObject o = new JsonObject();
                o.addProperty("chance", 1.0);
                o.addProperty("entity", ent);
                JsonObject slots = new JsonObject();
                for (Map.Entry<String, List<EquipEntry>> se : r.slots.entrySet()) {
                    List<EquipEntry> kept = new ArrayList<>();
                    for (EquipEntry e : se.getValue()) {
                        Double ch = parseDoubleOrNull(e.chanceBox.getValue());
                        if (ch == null || ch <= 0) continue;
                        if (e.itemId == null || e.itemId.isEmpty()) continue;
                        kept.add(e);
                    }
                    if (!kept.isEmpty()) {
                        if (kept.size() == 1) {
                            EquipEntry e0 = kept.get(0);
                            JsonObject en = new JsonObject();
                            en.addProperty("item", e0.itemId);
                            en.addProperty("chance", parseDoubleOrNull(e0.chanceBox.getValue()));
                            if (!e0.enchants.isEmpty()) {
                                JsonObject enc = new JsonObject();
                                for (Map.Entry<String, Integer> x : e0.enchants.entrySet()) {
                                    enc.addProperty(x.getKey(), x.getValue());
                                }
                                en.add("enchants", enc);
                            }
                            slots.add(se.getKey(), en);
                        } else {
                            JsonArray arr = new JsonArray();
                            for (EquipEntry e : kept) {
                                JsonObject en = new JsonObject();
                                en.addProperty("item", e.itemId);
                                en.addProperty("chance", parseDoubleOrNull(e.chanceBox.getValue()));
                                if (!e.enchants.isEmpty()) {
                                    JsonObject enc = new JsonObject();
                                    for (Map.Entry<String, Integer> x : e.enchants.entrySet()) {
                                        enc.addProperty(x.getKey(), x.getValue());
                                    }
                                    en.add("enchants", enc);
                                }
                                arr.add(en);
                            }
                            slots.add(se.getKey(), arr);
                        }
                    }
                }
                o.add("slots", slots);
                eqArr.add(o);
            }
            if (!eqArr.isEmpty()) effects.add("equipment", eqArr);
        }

        root.add("effects", effects);
        return root;
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static Double parseDoubleOrNull(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void recomputePreview() {
        previewLines.clear();
        previewLines.add(Component.translatable(SCOPE_KEYS[scopeIndex]).getString());
        addPreviewSection(LangKeys.PHASE_EFFECT_ATTR, attrRows);
        addPreviewSection(LangKeys.PHASE_EFFECT_MOB, mobMultRows);

        previewLines.add(Component.translatable(LangKeys.PHASE_EFFECT_POTION).getString());
        for (MobEffectRow r : mobEffectRows) {
            if (r.selected == null || r.selected.isEmpty()) continue;
            previewLines.add(" " + shortId(r.selected) + " L" + r.lvlBox.getValue().trim()
                    + " " + r.secBox.getValue().trim() + "s");
        }
        previewLines.add(Component.translatable(LangKeys.PHASE_EFFECT_EQUIP).getString());
        for (EquipRule r : equipRules) {
            String ent = r.entity == null ? "" : r.entity;
            for (Map.Entry<String, List<EquipEntry>> se : r.slots.entrySet()) {
                for (EquipEntry e : se.getValue()) {
                    Double ch = parseDoubleOrNull(e.chanceBox.getValue());
                    if (ch == null) continue;
                    previewLines.add(" " + (ent.isEmpty() ? "*" : shortId(ent)) + "/" + se.getKey()
                            + " " + (int) (ch * 100) + "% " + shortId(e.itemId)
                            + (e.enchants.isEmpty() ? "" : " +" + e.enchants.size() + "Ench"));
                }
            }
        }
        previewScroll = 0;
    }

    private void addPreviewSection(String titleKey, List<AttrRow> rows) {
        previewLines.add(Component.translatable(titleKey).getString());
        for (AttrRow r : rows) {
            if (r.key == null || r.key.isEmpty()) continue;
            previewLines.add(" " + r.key + " = " + r.valBox.getValue().trim());
        }
    }

    private static String shortId(String id) {
        int i = id.indexOf(':');
        return i >= 0 ? id.substring(i + 1) : id;
    }

    private static String localized(String key) {
        return Component.translatable(key).getString();
    }

    private static String localizedAttrKey(String key) {
        return switch (key) {
            case "max_health" -> localized(LangKeys.EFFECT_MAX_HEALTH);
            case "armor" -> localized(LangKeys.EFFECT_ARMOR);
            case "armor_toughness" -> localized(LangKeys.EFFECT_ARMOR_TOUGHNESS);
            case "knockback_resistance" -> localized(LangKeys.EFFECT_KNOCKBACK_RESIST);
            case "movement_speed" -> localized(LangKeys.EFFECT_MOVE_SPEED);
            case "attack_damage" -> localized(LangKeys.EFFECT_ATTACK_DAMAGE);
            case "attack_speed" -> localized(LangKeys.EFFECT_ATTACK_SPEED);
            case "luck" -> localized(LangKeys.EFFECT_LUCK);
            case "scale" -> localized(LangKeys.EFFECT_SCALE);
            case "damage_taken" -> localized(LangKeys.EFFECT_DAMAGE_TAKEN);
            default -> key;
        };
    }

    private static String localizedMobMultKey(String key) {
        return switch (key) {
            case "mob_health_mult" -> localized(LangKeys.EFFECT_MOB_HEALTH);
            case "mob_attack_mult" -> localized(LangKeys.EFFECT_MOB_ATTACK);
            case "mob_speed_mult" -> localized(LangKeys.EFFECT_MOB_SPEED);
            case "spawn_rate_mult" -> localized(LangKeys.EFFECT_SPAWN_RATE);
            case "mob_armor_mult" -> localized(LangKeys.EFFECT_MOB_ARMOR);
            case "boss_damage_mult" -> localized(LangKeys.EFFECT_BOSS_DAMAGE);
            default -> key;
        };
    }

    @Override
    public void onClose() {
        if (parent != null && minecraft != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ────────────────────────── 数据结构 ──────────────────────────

    private static final class AttrRow {
        String key;
        final EditBox valBox;

        AttrRow() {
            this("", "0");
        }

        AttrRow(String k, String v) {
            this.key = k;
            valBox = new EditBox(Minecraft.getInstance().font, 0, 0, 40, 14, Component.literal(""));
            valBox.setValue(v);
            valBox.setMaxLength(12);
        }
    }

    private static final class MobEffectRow {
        String selected;
        final EditBox lvlBox, secBox;

        MobEffectRow() {
            this("", "0", "30");
        }

        MobEffectRow(String id, String lvl, String sec) {
            this.selected = id;
            lvlBox = new EditBox(Minecraft.getInstance().font, 0, 0, 30, 14, Component.literal(""));
            secBox = new EditBox(Minecraft.getInstance().font, 0, 0, 36, 14, Component.literal(""));
            lvlBox.setValue(lvl);
            secBox.setValue(sec);
            lvlBox.setMaxLength(3);
            secBox.setMaxLength(5);
        }
    }

    /** 单个装备条目：物品 + 自身概率 + 附魔（附魔 id -> 等级） */
    private static final class EquipEntry {
        final EditBox chanceBox;
        String itemId;
        final Map<String, Integer> enchants = new LinkedHashMap<>();
        boolean showEnch = false;

        EquipEntry() {
            this("1.0", "");
        }

        EquipEntry(String ch, String item) {
            chanceBox = new EditBox(Minecraft.getInstance().font, 0, 0, 32, 14, Component.literal(""));
            chanceBox.setValue(ch);
            chanceBox.setMaxLength(5);
            this.itemId = item;
        }
    }

    /** 一条装备规则：目标实体 + 5 个部位的条目列表（按部位分组） */
    private static final class EquipRule {
        String entity;
        final Map<String, List<EquipEntry>> slots = new LinkedHashMap<>();
        boolean expanded = false;

        EquipRule() {
            for (String s : EQUIP_SLOTS) slots.put(s, new ArrayList<>());
        }

        EquipRule(String entity) {
            this();
            this.entity = entity;
        }
    }

    private record Hotspot(int x, int y, int w, int h, Runnable action) {
    }

    private record KeyDropdown(int x, int y, int w, List<String> options, Object row) {
    }
}
