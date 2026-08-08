package com.dreamer.ao.client.gui.timeline;

import com.dreamer.ao.client.gui.GuiUtils;
import com.dreamer.ao.milestone.model.MilestoneTrigger;
import com.dreamer.ao.milestone.model.TimeMilestone;
import com.dreamer.ao.milestone.model.TimelineCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import com.dreamer.ao.ModInfo;
import net.minecraft.resources.ResourceLocation;

public class MilestoneEditorScreen
extends Screen {
    private final Screen parent;
    private final TimeMilestone existing;
    private final Consumer<TimeMilestone> onSave;
    private final Consumer<String> onDelete;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int contentLeft;
    private int contentWidth;
    private EditBox nameInput;
    private EditBox descInput;
    private EditBox iconInput;
    private EditBox triggerParamInput;
    private EditBox thresholdInput;
    private String selectedCategory;
    private MilestoneTrigger selectedTrigger;
    private boolean iconSearchMode;
    private String iconSearchText = "";
    private final List<String> filteredItems = new ArrayList<String>();
    private boolean triggerPopupOpen;
    private int triggerPopupScroll;
    private boolean confirmDelete;
    private static final int ROW_H = 28;
    private static final ResourceLocation TEX_BTN_HOVER = ModInfo.rl("textures/gui/timeline/button_hover.png");
    private static final ResourceLocation TEX_BTN_NORMAL = ModInfo.rl("textures/gui/timeline/button_normal.png");
    private static final ResourceLocation TEX_BTN_PRESSED = ModInfo.rl("textures/gui/timeline/button_pressed.png");
    private static final int BTN_TEX_W = 160;
    private static final int BTN_TEX_H = 60;

    public MilestoneEditorScreen(Screen parent, TimeMilestone existing, Consumer<TimeMilestone> onSave, Consumer<String> onDelete) {
        super(Component.translatable(existing != null ? "timeline.advancementoverhaul.edit_milestone" : "timeline.advancementoverhaul.create_milestone"));
        this.parent = parent;
        this.existing = existing;
        this.onSave = onSave;
        this.onDelete = onDelete;
        this.selectedCategory = existing != null ? existing.category() : "normal";
        this.selectedTrigger = MilestoneTrigger.FIRST_OBTAIN;
        this.confirmDelete = false;
        this.triggerPopupOpen = false;
        this.triggerPopupScroll = 0;
    }

    protected void init() {
        super.init();
        this.panelWidth = Math.min(330, this.width - 36);
        this.panelHeight = Math.min(272, this.height - 36);
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;
        this.contentLeft = this.panelLeft + 12;
        this.contentWidth = this.panelWidth - 24;
        Font font = Minecraft.getInstance().font;
        this.nameInput = new EditBox(font, this.contentLeft, 0, this.contentWidth, 18, Component.empty());
        this.nameInput.setMaxLength(64);
        this.nameInput.setValue(this.existing != null ? Component.translatable((String)this.existing.nameKey()).getString() : "");
        this.nameInput.setBordered(false);
        this.addRenderableWidget(this.nameInput);
        this.descInput = new EditBox(font, this.contentLeft, 0, this.contentWidth, 18, Component.literal(""));
        this.descInput.setMaxLength(256);
        this.descInput.setValue(this.existing != null ? Component.translatable(this.existing.descriptionKey()).getString() : "");
        this.descInput.setBordered(false);
        this.addRenderableWidget(this.descInput);
        this.iconInput = new EditBox(font, this.contentLeft, 0, this.contentWidth - 24, 18, Component.literal(""));
        this.iconInput.setMaxLength(128);
        this.iconInput.setValue(this.existing != null ? this.existing.iconItem() : "minecraft:paper");
        this.iconInput.setResponder(s -> {
            this.iconSearchText = s.toLowerCase();
            this.updateIconFilter();
        });
        this.iconInput.setBordered(false);
        this.addRenderableWidget(this.iconInput);
        this.triggerParamInput = new EditBox(font, this.contentLeft, 0, this.contentWidth / 2 - 4, 18, Component.literal(""));
        this.triggerParamInput.setMaxLength(128);
        this.triggerParamInput.setBordered(false);
        this.addRenderableWidget(this.triggerParamInput);
        this.thresholdInput = new EditBox(font, this.contentLeft + this.contentWidth / 2 + 4, 0, this.contentWidth / 2 - 4, 18, Component.literal(""));
        this.thresholdInput.setMaxLength(20);
        this.thresholdInput.setBordered(false);
        this.addRenderableWidget(this.thresholdInput);
        this.updatePositions();
    }

    private void updatePositions() {
        int y = this.panelTop + 20;
        this.nameInput.setPosition(this.contentLeft, y);
        this.descInput.setPosition(this.contentLeft, y += 28);
        this.iconInput.setPosition(this.contentLeft, y += 28);
        y += 28;
        y += 34;
        this.triggerParamInput.setPosition(this.contentLeft, y += 28);
        this.thresholdInput.setPosition(this.contentLeft + this.contentWidth / 2 + 4, y);
    }

    private int categoryY() {
        return this.panelTop + 20 + 84;
    }

    private int triggerY() {
        return this.panelTop + 20 + 84 + 34;
    }

    private int paramRowY() {
        return this.panelTop + 20 + 112 + 34;
    }

    private void updateIconFilter() {
        this.filteredItems.clear();
        if (this.iconSearchText.isEmpty()) {
            BuiltInRegistries.ITEM.keySet().stream().map(ResourceLocation::toString).sorted().limit(50L).forEach(this.filteredItems::add);
        } else {
            BuiltInRegistries.ITEM.keySet().stream().map(ResourceLocation::toString).filter(s -> s.toLowerCase().contains(this.iconSearchText)).limit(30L).forEach(this.filteredItems::add);
        }
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, -2146691032);
        g.fill(this.panelLeft, this.panelTop, this.panelLeft + this.panelWidth, this.panelTop + this.panelHeight, -522788630);
        g.renderOutline(this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight, 949272784);
        g.fill(this.panelLeft + 1, this.panelTop, this.panelLeft + this.panelWidth - 1, this.panelTop + 1, 746506444);
        Font font = Minecraft.getInstance().font;
        String title = this.getTitle().getString();
        g.drawString(font, title, this.contentLeft, this.panelTop + 8, -3087122, false);
        int closeX = this.panelLeft + this.panelWidth - 20;
        boolean closeHov = GuiUtils.inRect(mouseX, mouseY, closeX, this.panelTop + 6, 16, 16);
        g.drawString(font, "\u2715", closeX, this.panelTop + 8, closeHov ? -21846 : -1431384872, false);
        int y = this.panelTop + 20;
        g.drawString(font, Component.translatable((String)"timeline.advancementoverhaul.editor_name").getString(), this.contentLeft, y - 10, -1431384872, false);
        this.drawGlassInputBg(g, this.contentLeft, y, this.contentWidth, 18);
        this.nameInput.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, Component.translatable((String)"timeline.advancementoverhaul.editor_desc").getString(), this.contentLeft, (y += 28) - 10, -1431384872, false);
        this.drawGlassInputBg(g, this.contentLeft, y, this.contentWidth, 18);
        this.descInput.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, Component.translatable((String)"timeline.advancementoverhaul.editor_icon").getString(), this.contentLeft, (y += 28) - 10, -1431384872, false);
        this.drawGlassInputBg(g, this.contentLeft, y, this.contentWidth - 24, 18);
        this.iconInput.render(g, mouseX, mouseY, partialTick);
        int iconBtnX = this.contentLeft + this.contentWidth - 22;
        boolean iconSearchHov = GuiUtils.inRect(mouseX, mouseY, iconBtnX, y, 18, 18);
        MilestoneEditorScreen.drawRoundedPill(g, iconBtnX, y, 18, 18, iconSearchHov ? 1082050764 : 1418771656);
        g.drawString(font, "\ud83d\udd0d", iconBtnX + 2, y + 5, -1431384872, false);
        int catY = y += 28;
        g.drawString(font, Component.translatable((String)"timeline.advancementoverhaul.editor_category").getString(), this.contentLeft, catY - 10, -1431384872, false);
        int cx = this.contentLeft;
        for (TimelineCategory cat : TimelineCategory.BUILTIN) {
            String label = cat.icon() + " " + Component.translatable((String)cat.nameKey()).getString();
            int tw = font.width(label) + 14;
            if (cx + tw > this.panelLeft + this.panelWidth - 10 && cx > this.contentLeft) {
                cx = this.contentLeft;
                catY += 22;
            }
            boolean sel = this.selectedCategory.equals(cat.id());
            boolean hov = GuiUtils.inRect(mouseX, mouseY, cx, catY, tw, 20);
            MilestoneEditorScreen.drawRoundedPill(g, cx, catY, tw, 20, sel ? 1553253588 : (hov ? 1082050764 : 1418771656));
            g.drawString(font, label, cx + 7, catY + 6, sel ? -2823952 : (hov ? -3087122 : -1431384872), false);
            cx += tw + 4;
        }
        int trigY = y += 34;
        g.drawString(font, Component.translatable((String)"timeline.advancementoverhaul.editor_trigger").getString(), this.contentLeft, trigY - 10, -1431384872, false);
        boolean trigHov = GuiUtils.inRect(mouseX, mouseY, this.contentLeft, trigY, this.contentWidth, 18);
        this.drawGlassInputBg(g, this.contentLeft, trigY, this.contentWidth, 18);
        g.drawString(font, this.selectedTrigger.getDisplayName(), this.contentLeft + 4, trigY + 5, trigHov ? -3087122 : -1773322, false);
        String arrow = this.triggerPopupOpen ? "\u25b4" : "\u25be";
        g.drawString(font, arrow, this.contentLeft + this.contentWidth - 14, trigY + 5, -8468276, false);
        if (this.triggerPopupOpen) {
            this.renderTriggerPopup(g, font, mouseX, mouseY, trigY);
        }
        g.drawString(font, Component.translatable((String)"timeline.advancementoverhaul.editor_param").getString(), this.contentLeft, (y += 28) - 10, -1431384872, false);
        g.drawString(font, Component.translatable((String)"timeline.advancementoverhaul.editor_threshold").getString(), this.contentLeft + this.contentWidth / 2 + 4, y - 10, -1431384872, false);
        this.drawGlassInputBg(g, this.contentLeft, y, this.contentWidth / 2 - 4, 18);
        this.drawGlassInputBg(g, this.contentLeft + this.contentWidth / 2 + 4, y, this.contentWidth / 2 - 4, 18);
        this.triggerParamInput.render(g, mouseX, mouseY, partialTick);
        this.thresholdInput.render(g, mouseX, mouseY, partialTick);
        int btnY = this.panelTop + this.panelHeight - 30;
        if (this.existing != null && this.onDelete != null) {
            String delText = this.confirmDelete ? Component.translatable((String)"timeline.advancementoverhaul.editor_confirm_delete").getString() : Component.translatable((String)"timeline.advancementoverhaul.editor_delete").getString();
            int delW = font.width(delText) + 20;
            boolean delHov = GuiUtils.inRect(mouseX, mouseY, this.contentLeft, btnY, delW, 22);
            int delColor = this.confirmDelete ? -39322 : -573805974;
            int delHover = this.confirmDelete ? -48060 : -288593302;
            MilestoneEditorScreen.drawShadowButton(g, this.contentLeft, btnY, delW, 22, delColor, delHov, delHover);
            g.drawCenteredString(font, delText, this.contentLeft + delW / 2, btnY + 7, -1379080);
        }
        int saveW = font.width(Component.translatable((String)"timeline.advancementoverhaul.editor_save").getString()) + 20;
        int saveX = this.panelLeft + this.panelWidth - saveW - 12;
        boolean saveHov = GuiUtils.inRect(mouseX, mouseY, saveX, btnY, saveW, 22);
        MilestoneEditorScreen.drawShadowButton(g, saveX, btnY, saveW, 22, -580209476, saveHov, -294996804);
        g.drawCenteredString(font, Component.translatable((String)"timeline.advancementoverhaul.editor_save").getString(), saveX + saveW / 2, btnY + 7, -1379080);
        int cancelW = font.width(Component.translatable((String)"timeline.advancementoverhaul.editor_cancel").getString()) + 16;
        int cancelX = saveX - cancelW - 8;
        boolean cancelHov = GuiUtils.inRect(mouseX, mouseY, cancelX, btnY, cancelW, 22);
        MilestoneEditorScreen.drawShadowButton(g, cancelX, btnY, cancelW, 22, 1821424840, cancelHov, -1936671544);
        g.drawCenteredString(font, Component.translatable((String)"timeline.advancementoverhaul.editor_cancel").getString(), cancelX + cancelW / 2, btnY + 7, cancelHov ? -3087122 : -1431384872);
        if (this.iconSearchMode) {
            this.renderIconSearchDropdown(g, font, mouseX, mouseY);
        }
    }

    private void renderTriggerPopup(GuiGraphics g, Font font, int mouseX, int mouseY, int trigY) {
        MilestoneTrigger[] triggers = new MilestoneTrigger[]{MilestoneTrigger.FIRST_OBTAIN, MilestoneTrigger.FIRST_DEATH, MilestoneTrigger.FIRST_DIMENSION, MilestoneTrigger.FIRST_TAME, MilestoneTrigger.FIRST_BLOCK_PLACE, MilestoneTrigger.FIRST_ENCHANT, MilestoneTrigger.FIRST_LIGHTNING, MilestoneTrigger.FIRST_RAIN_SLEEP, MilestoneTrigger.COUNTER_REACH, MilestoneTrigger.DISTANCE_REACH, MilestoneTrigger.SUNRISE_VIEWED, MilestoneTrigger.SUNSET_VIEWED, MilestoneTrigger.WORLD_JOIN};
        int popupW = this.contentWidth;
        int maxVisible = 7;
        int itemH = 18;
        int pad = 4;
        int popupH = Math.min(triggers.length, maxVisible) * itemH + pad * 2;
        int popupX = this.contentLeft;
        int popupY = trigY + 20;
        if (popupY + popupH > this.height - 4) {
            popupY = trigY - popupH - 2;
        }
        g.fill(popupX, popupY, popupX + popupW, popupY + popupH, -522788630);
        g.renderOutline(popupX, popupY, popupW, popupH, 949272784);
        int totalItems = triggers.length;
        int maxScroll = Math.max(0, totalItems - maxVisible);
        this.triggerPopupScroll = Math.max(0, Math.min(this.triggerPopupScroll, maxScroll));
        int itemY = popupY + pad;
        for (int i = this.triggerPopupScroll; i < Math.min(totalItems, this.triggerPopupScroll + maxVisible); ++i) {
            MilestoneTrigger t = triggers[i];
            String label = t.getDisplayName();
            boolean sel = this.selectedTrigger == t;
            boolean hov = GuiUtils.inRect(mouseX, mouseY, popupX + 2, itemY, popupW - 4, itemH);
            if (sel || hov) {
                g.fill(popupX + 2, itemY, popupX + popupW - 2, itemY + itemH, sel ? 1418771656 : 1082050764);
            }
            g.drawString(font, label, popupX + 8, itemY + 5, sel ? -8468276 : (hov ? -3087122 : -1431384872), false);
            itemY += itemH;
        }
        if (maxScroll > 0) {
            float barH = (float)popupH * (float)maxVisible / (float)totalItems;
            float barY = (float)popupY + (float)this.triggerPopupScroll / (float)maxScroll * ((float)popupH - barH);
            g.fill(popupX + popupW - 3, (int)barY, popupX + popupW - 1, (int)(barY + barH), -8468276);
        }
    }

    private void renderIconSearchDropdown(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (this.filteredItems.isEmpty()) {
            this.updateIconFilter();
        }
        int dropY = this.panelTop + 20 + 56 + 18 + 2;
        int dropH = Math.min(this.filteredItems.size() * 16 + 4, 140);
        int dropX = this.contentLeft;
        if (dropY + dropH > this.panelTop + this.panelHeight) {
            dropY = this.panelTop + 20 + 56 - dropH;
        }
        g.fill(dropX, dropY, dropX + this.contentWidth, dropY + dropH, -522788630);
        g.renderOutline(dropX, dropY, this.contentWidth, dropH, 949272784);
        int itemY = dropY + 2;
        for (int i = 0; i < Math.min(this.filteredItems.size(), 8); ++i) {
            String itemId = this.filteredItems.get(i);
            boolean hov = GuiUtils.inRect(mouseX, mouseY, dropX + 2, itemY, this.contentWidth - 4, 14);
            if (hov) {
                g.fill(dropX + 2, itemY, dropX + this.contentWidth - 2, itemY + 14, 1082050764);
            }
            g.drawString(font, itemId, dropX + 6, itemY + 3, hov ? -3087122 : -1431384872, false);
            itemY += 16;
        }
    }

    private void drawGlassInputBg(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 1620623568);
        g.renderOutline(x - 1, y - 1, w + 2, h + 2, 949272784);
    }

    private static void drawRoundedRect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        r = Math.min(r, Math.min(w, h) / 2);
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        g.fill(x + 1, y + 1, x + r, y + r, color);
        g.fill(x + w - r, y + 1, x + w - 1, y + r, color);
        g.fill(x + 1, y + h - r, x + r, y + h - 1, color);
        g.fill(x + w - r, y + h - r, x + w - 1, y + h - 1, color);
        g.fill(x + r, y, x + w - r, y + 1, color);
        g.fill(x + r, y + h - 1, x + w - r, y + h, color);
    }

    private static void drawShadowButton(GuiGraphics g, int x, int y, int w, int h, int color, boolean hovered, int hoverColor) {
        int c = hovered ? hoverColor : color;
        MilestoneEditorScreen.drawRoundedRect(g, x + 1, y + 2, w, h, 5, 0x38000000);
        MilestoneEditorScreen.drawRoundedRect(g, x, y, w, h, 5, c);
    }

    private static void drawRoundedPill(GuiGraphics g, int px, int py, int w, int h, int color) {
        int r = h / 2;
        g.fill(px + r, py, px + w - r, py + h, color);
        g.fill(px, py + 1, px + r * 2, py + h - 1, color);
        g.fill(px + w - r * 2, py + 1, px + w, py + h - 1, color);
        g.fill(px + r, py, px + w - r, py + 1, color);
        g.fill(px + r, py + h - 1, px + w - r, py + h, color);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        String delText;
        int delW;
        int popupH;
        int popupY;
        int trigY;
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int mx = (int)mouseX;
        int my = (int)mouseY;
        int closeX = this.panelLeft + this.panelWidth - 20;
        if (GuiUtils.inRect(mx, my, closeX, this.panelTop + 6, 16, 16)) {
            this.onClose();
            return true;
        }
        if (!GuiUtils.inRect(mx, my, this.panelLeft - 4, this.panelTop - 4, this.panelWidth + 8, this.panelHeight + 8)) {
            if (this.triggerPopupOpen) {
                trigY = this.triggerY();
                popupY = trigY + 20;
                if (popupY + (popupH = Math.min(13, 7) * 18 + 8) > this.height - 4) {
                    popupY = trigY - popupH - 2;
                }
                if (!GuiUtils.inRect(mx, my, this.contentLeft, popupY, this.contentWidth, popupH)) {
                    this.onClose();
                    return true;
                }
            } else {
                this.onClose();
                return true;
            }
        }
        if (GuiUtils.inRect(mx, my, this.contentLeft, trigY = this.triggerY(), this.contentWidth, 18)) {
            this.triggerPopupOpen = !this.triggerPopupOpen;
            this.triggerPopupScroll = 0;
            return true;
        }
        if (this.triggerPopupOpen) {
            popupY = trigY + 20;
            popupH = Math.min(13, 7) * 18 + 8;
            if (popupY + popupH > this.height - 4) {
                popupY = trigY - popupH - 2;
            }
            if (GuiUtils.inRect(mx, my, this.contentLeft, popupY, this.contentWidth, popupH)) {
                MilestoneTrigger[] triggers = new MilestoneTrigger[]{MilestoneTrigger.FIRST_OBTAIN, MilestoneTrigger.FIRST_DEATH, MilestoneTrigger.FIRST_DIMENSION, MilestoneTrigger.FIRST_TAME, MilestoneTrigger.FIRST_BLOCK_PLACE, MilestoneTrigger.FIRST_ENCHANT, MilestoneTrigger.FIRST_LIGHTNING, MilestoneTrigger.FIRST_RAIN_SLEEP, MilestoneTrigger.COUNTER_REACH, MilestoneTrigger.DISTANCE_REACH, MilestoneTrigger.SUNRISE_VIEWED, MilestoneTrigger.SUNSET_VIEWED, MilestoneTrigger.WORLD_JOIN};
                int maxVisible = 7;
                int itemY = popupY + 4;
                for (int i = this.triggerPopupScroll; i < Math.min(triggers.length, this.triggerPopupScroll + maxVisible); ++i) {
                    if (GuiUtils.inRect(mx, my, this.contentLeft + 2, itemY, this.contentWidth - 4, 18)) {
                        this.selectedTrigger = triggers[i];
                        this.triggerPopupOpen = false;
                        return true;
                    }
                    itemY += 18;
                }
                return true;
            }
            this.triggerPopupOpen = false;
            return true;
        }
        int iconBtnX = this.contentLeft + this.contentWidth - 22;
        int iconY = this.panelTop + 20 + 56;
        if (GuiUtils.inRect(mx, my, iconBtnX, iconY, 18, 18)) {
            boolean bl = this.iconSearchMode = !this.iconSearchMode;
            if (this.iconSearchMode) {
                this.updateIconFilter();
            }
            return true;
        }
        if (this.iconSearchMode) {
            int dropY = iconY + 18 + 2;
            int dropH = Math.min(this.filteredItems.size() * 16 + 4, 140);
            if (dropY + dropH > this.panelTop + this.panelHeight) {
                dropY = iconY - dropH;
            }
            int itemY = dropY + 2;
            for (int i = 0; i < Math.min(this.filteredItems.size(), 8); ++i) {
                if (GuiUtils.inRect(mx, my, this.contentLeft + 2, itemY, this.contentWidth - 4, 14)) {
                    this.iconInput.setValue(this.filteredItems.get(i));
                    this.iconSearchMode = false;
                    return true;
                }
                itemY += 16;
            }
        }
        int catY = this.categoryY();
        int cx = this.contentLeft;
        for (TimelineCategory cat : TimelineCategory.BUILTIN) {
            String label = cat.icon() + " " + Component.translatable((String)cat.nameKey()).getString();
            int tw = Minecraft.getInstance().font.width(label) + 14;
            if (cx + tw > this.panelLeft + this.panelWidth - 10 && cx > this.contentLeft) {
                cx = this.contentLeft;
                catY += 22;
            }
            if (GuiUtils.inRect(mx, my, cx, catY, tw, 20)) {
                this.selectedCategory = cat.id();
                return true;
            }
            cx += tw + 4;
        }
        int btnY = this.panelTop + this.panelHeight - 30;
        Font font = Minecraft.getInstance().font;
        int saveW = font.width(Component.translatable((String)"timeline.advancementoverhaul.editor_save").getString()) + 20;
        int saveX = this.panelLeft + this.panelWidth - saveW - 12;
        if (GuiUtils.inRect(mx, my, saveX, btnY, saveW, 22)) {
            this.doSave();
            return true;
        }
        int cancelW = font.width(Component.translatable((String)"timeline.advancementoverhaul.editor_cancel").getString()) + 16;
        int cancelX = saveX - cancelW - 8;
        if (GuiUtils.inRect(mx, my, cancelX, btnY, cancelW, 22)) {
            this.onClose();
            return true;
        }
        if (this.existing != null && this.onDelete != null && GuiUtils.inRect(mx, my, this.contentLeft, btnY, delW = font.width(delText = this.confirmDelete ? Component.translatable((String)"timeline.advancementoverhaul.editor_confirm_delete").getString() : Component.translatable((String)"timeline.advancementoverhaul.editor_delete").getString()) + 20, 22)) {
            if (this.confirmDelete) {
                this.onDelete.accept(this.existing.id());
                this.onClose();
            } else {
                this.confirmDelete = true;
            }
            return true;
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollXDelta, double scrollYDelta) {
        if (this.triggerPopupOpen) {
            int popupH;
            int trigY = this.triggerY();
            int popupY = trigY + 20;
            if (popupY + (popupH = Math.min(13, 7) * 18 + 8) > this.height - 4) {
                popupY = trigY - popupH - 2;
            }
            if (GuiUtils.inRect((int)mouseX, (int)mouseY, this.contentLeft, popupY, this.contentWidth, popupH)) {
                this.triggerPopupScroll -= (int)(scrollYDelta * 0.5);
                int maxScroll = Math.max(0, 6);
                this.triggerPopupScroll = Math.max(0, Math.min(this.triggerPopupScroll, maxScroll));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollXDelta, scrollYDelta);
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return super.charTyped(codePoint, modifiers);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            if (this.triggerPopupOpen) {
                this.triggerPopupOpen = false;
                return true;
            }
            this.onClose();
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            this.doSave();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void doSave() {
        String name = this.nameInput.getValue().trim();
        String desc = this.descInput.getValue().trim();
        String icon = this.iconInput.getValue().trim();
        if (icon.isEmpty()) {
            icon = "minecraft:paper";
        }
        if (name.isEmpty()) {
            return;
        }
        String id = this.existing != null ? this.existing.id() : "custom_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Object nameKey = this.existing != null ? this.existing.nameKey() : "custom." + id;
        Object descKey = this.existing != null ? this.existing.descriptionKey() : "custom." + id + ".desc";
        long threshold = 1L;
        try {
            String threshStr = this.thresholdInput.getValue().trim();
            if (!threshStr.isEmpty()) {
                threshold = Long.parseLong(threshStr);
            }
        }
        catch (NumberFormatException threshStr) {
            // empty catch block
        }
        TimeMilestone result = new TimeMilestone(id, (String)nameKey, (String)descKey, icon, this.selectedCategory, 0, 0L, false, null, false, null, "custom", name, desc, this.selectedTrigger.name(), this.triggerParamInput.getValue().trim(), threshold);
        if (this.onSave != null) {
            this.onSave.accept(result);
        }
        Minecraft.getInstance().setScreen(this.parent);
    }

    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    public boolean isPauseScreen() {
        return true;
    }
}

