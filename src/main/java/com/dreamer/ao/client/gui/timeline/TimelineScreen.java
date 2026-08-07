package com.dreamer.ao.client.gui.timeline;

import com.dreamer.ao.client.gui.GuiUtils;
import com.dreamer.ao.client.gui.timeline.MilestoneEditorScreen;
import com.dreamer.ao.client.gui.timeline.TimelineRenderer;
import com.dreamer.ao.network.payload.TimelineRequestPayload;
import com.dreamer.ao.milestone.model.MilestoneDefinition;
import com.dreamer.ao.milestone.model.TimeMilestone;
import com.dreamer.ao.milestone.model.TimelineCategory;
import com.dreamer.ao.milestone.store.TimelineDefinitionLoader;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

public class TimelineScreen
extends Screen {
    int contentTop;
    int contentBottom;
    int contentLeft;
    int contentRight;
    int axisY;
    double scrollX;
    double targetScrollX;
    double maxScrollX;
    double zoom = 1.0;
    double dragStartX;
    double dragStartScrollX;
    boolean dragging;
    long openTime;
    boolean enterAnimationDone;
    boolean editMode;
    int selectedCategoryIdx;
    boolean phaseMode;
    final List<String> categoryIds = new ArrayList<String>();
    final List<TimelineCategory> categories = new ArrayList<TimelineCategory>();
    final List<TimeMilestone> allMilestones = new ArrayList<TimeMilestone>();
    final List<TimeMilestone> displayMilestones = new ArrayList<TimeMilestone>();
    final Map<String, TimeMilestone> milestoneIndex = new LinkedHashMap<String, TimeMilestone>();
    int dataVersion = -1;
    double mouseOnAxisX = -1.0;
    boolean mouseOnAxis;
    final List<TimeMilestone> pendingCustomMilestones = new ArrayList<TimeMilestone>();

    public TimelineScreen() {
        super(Component.literal("\u65c5\u9014"));
    }

    protected void init() {
        super.init();
        this.openTime = System.currentTimeMillis();
        this.enterAnimationDone = false;
        this.editMode = false;
        this.dragging = false;
        this.dragStartX = 0.0;
        this.dragStartScrollX = 0.0;
        this.mouseOnAxis = false;
        this.mouseOnAxisX = -1.0;
        this.loadCategories();
        PacketDistributor.sendToServer(new TimelineRequestPayload());
        this.loadLocalDefinitions();
        this.updateLayout();
        this.targetScrollX = this.computeTargetScrollX();
        this.scrollX = 0.0;
    }

    private void loadCategories() {
        this.categories.clear();
        this.categoryIds.clear();
        for (TimelineCategory cat : TimelineCategory.BUILTIN) {
            this.categories.add(cat);
            this.categoryIds.add(cat.id());
        }
    }

    private void loadLocalDefinitions() {
        TimelineDefinitionLoader loader = TimelineDefinitionLoader.getInstance();
        if (loader.getAllMilestones().isEmpty()) {
            loader.init(FMLPaths.CONFIGDIR.get());
        }
        this.allMilestones.clear();
        this.milestoneIndex.clear();
        for (MilestoneDefinition def : loader.getAllMilestones()) {
            TimeMilestone tm = def.toPendingMilestone();
            this.allMilestones.add(tm);
            this.milestoneIndex.put(tm.id(), tm);
        }
        for (TimeMilestone ct : loader.getCustomMilestones()) {
            this.allMilestones.add(ct);
            this.milestoneIndex.put(ct.id(), ct);
        }
        this.filterByCategory();
    }

    void updateLayout() {
        this.contentTop = 72;
        this.contentBottom = this.height - 28;
        this.contentLeft = 0;
        this.contentRight = this.width;
        this.axisY = this.contentTop + (this.contentBottom - this.contentTop) / 2;
    }

    public void updateTimelineData(String json) {
        try {
            JsonArray arr = JsonParser.parseString((String)json).getAsJsonArray();
            this.allMilestones.clear();
            this.milestoneIndex.clear();
            for (int i = 0; i < arr.size(); ++i) {
                TimeMilestone tm = TimeMilestone.fromJson(arr.get(i).getAsJsonObject());
                this.allMilestones.add(tm);
                this.milestoneIndex.put(tm.id(), tm);
            }
            ++this.dataVersion;
            this.filterByCategory();
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    void filterByCategory() {
        this.displayMilestones.clear();
        if (this.categoryIds.isEmpty()) {
            return;
        }
        String selCat = this.categoryIds.get(this.selectedCategoryIdx);
        for (TimeMilestone tm : this.allMilestones) {
            if (!tm.category().equals(selCat) || !this.editMode && !tm.unlocked()) continue;
            this.displayMilestones.add(tm);
        }
        this.displayMilestones.sort(Comparator.comparingInt(a -> a.unlocked() ? a.unlockDay() : Integer.MAX_VALUE));
        this.updateMaxScroll();
        if (!this.enterAnimationDone) {
            this.targetScrollX = this.computeTargetScrollX();
        }
    }

    double computeTargetScrollX() {
        int maxDay = this.computeMaxDay();
        double totalWidth = (double)((maxDay + 5) * 80) * this.zoom + 32.0;
        double viewWidth = this.contentRight - this.contentLeft;
        return Math.max(0.0, totalWidth - viewWidth);
    }

    private void updateMaxScroll() {
        int maxDay = Math.max(10, this.computeMaxDay() + 3);
        double totalWidth = (double)(maxDay * 80) * this.zoom + 32.0;
        double viewWidth = this.contentRight - this.contentLeft;
        this.maxScrollX = Math.max(0.0, totalWidth - viewWidth);
        this.scrollX = Math.max(0.0, Math.min(this.scrollX, this.maxScrollX));
    }

    int computeMaxDay() {
        int maxDay = 10;
        for (TimeMilestone tm : this.displayMilestones) {
            if (!tm.unlocked() || tm.unlockDay() <= maxDay) continue;
            maxDay = tm.unlockDay();
        }
        return maxDay;
    }

    public void addCustomMilestone(TimeMilestone milestone) {
        this.allMilestones.add(milestone);
        this.milestoneIndex.put(milestone.id(), milestone);
        this.pendingCustomMilestones.add(milestone);
        TimelineDefinitionLoader.getInstance().addCustomMilestone(milestone);
        this.filterByCategory();
    }

    public void removeCustomMilestone(String id) {
        this.allMilestones.removeIf(m -> m.id().equals(id));
        this.milestoneIndex.remove(id);
        this.pendingCustomMilestones.removeIf(m -> m.id().equals(id));
        TimelineDefinitionLoader.getInstance().removeCustomMilestone(id);
        this.filterByCategory();
    }

    public void updateCustomMilestone(TimeMilestone updated) {
        int i;
        for (i = 0; i < this.allMilestones.size(); ++i) {
            if (!this.allMilestones.get(i).id().equals(updated.id())) continue;
            this.allMilestones.set(i, updated);
            this.milestoneIndex.put(updated.id(), updated);
            break;
        }
        for (i = 0; i < this.pendingCustomMilestones.size(); ++i) {
            if (!this.pendingCustomMilestones.get(i).id().equals(updated.id())) continue;
            this.pendingCustomMilestones.set(i, updated);
            break;
        }
        TimelineDefinitionLoader.getInstance().updateCustomMilestone(updated);
        this.filterByCategory();
    }

    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long elapsed = System.currentTimeMillis() - this.openTime;
        if (!this.enterAnimationDone) {
            double progress = Math.min(1.0, (double)elapsed / 1200.0);
            progress = 1.0 - Math.pow(1.0 - progress, 3.0);
            this.scrollX = this.targetScrollX * progress;
            if (progress >= 1.0) {
                this.enterAnimationDone = true;
                this.scrollX = this.targetScrollX;
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
        this.updateLayout();
        this.mouseOnAxis = Math.abs(mouseY - this.axisY) < 18 && mouseY > this.contentTop && mouseY < this.contentBottom;
        this.mouseOnAxisX = this.mouseOnAxis ? (double)mouseX : -1.0;
        Font font = Minecraft.getInstance().font;
        int maxDay = Math.max(10, this.computeMaxDay() + 3);
        TimelineRenderer.renderBackground(g, this.width, this.height);
        TimelineRenderer.renderTabs(g, font, this.width, this.categories, this.selectedCategoryIdx, this.phaseMode, mouseX, mouseY);
        TimelineRenderer.renderTimeline(g, font, this.contentLeft, this.contentRight, this.contentTop, this.contentBottom, this.axisY, this.scrollX, this.zoom, this.editMode, this.displayMilestones, mouseX, mouseY, this.mouseOnAxis, this.mouseOnAxisX, maxDay, this.openTime);
        TimelineRenderer.renderHeader(g, font, this.width, this.editMode, mouseX, mouseY, Component.literal("\u65c5\u9014"));
        TimelineRenderer.renderBottomHint(g, font, this.width, this.height, this.editMode, mouseX, mouseY);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        NodeInfo clicked;
        int editBtnY;
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int mx = (int)mouseX;
        int my = (int)mouseY;
        if (GuiUtils.inRect(mx, my, this.width - 28, 2, 22, 30)) {
            this.onClose();
            return true;
        }
        Font font = Minecraft.getInstance().font;
        int editBtnY2 = this.height - 28 + 3;
        String editLabel = "\u7f16\u8f91";
        int editW = font.width(editLabel) + 20;
        int editBtnX = this.width - editW - 16;
        if (GuiUtils.inRect(mx, my, editBtnX, editBtnY2, editW, 22)) {
            this.toggleEditMode();
            return true;
        }
        int tabX = 16;
        int tabY = 34;
        for (int i = 0; i < this.categories.size(); ++i) {
            TimelineCategory cat = this.categories.get(i);
            String label = Component.translatable((String)cat.nameKey()).getString();
            int tw = font.width(label) + 24;
            if (GuiUtils.inRect(mx, my, tabX, tabY, tw, 30)) {
                if (this.selectedCategoryIdx != i) {
                    this.selectedCategoryIdx = i;
                    this.scrollX = 0.0;
                    this.targetScrollX = this.computeTargetScrollX();
                    this.filterByCategory();
                    GuiUtils.playClickSound();
                }
                return true;
            }
            tabX += tw;
        }
        String phaseLabel = "\u9636\u6bb5";
        int phaseW = font.width(phaseLabel) + 20;
        int phaseX = this.width - phaseW - 16;
        if (GuiUtils.inRect(mx, my, phaseX, tabY, phaseW, 30)) {
            GuiUtils.playClickSound();
            this.minecraft.setScreen(new PhasePanelScreen(this));
            return true;
        }
        if (button == 1 && this.editMode && this.mouseOnAxis) {
            this.openMilestoneEditor(null, mouseX);
            return true;
        }
        if (button == 0 && (clicked = this.findClickedNode(mx, my)) != null) {
            if (this.editMode && clicked.milestone.isCustom()) {
                this.openMilestoneEditor(clicked.milestone, clicked.x);
            }
            return true;
        }
        if (button == 0 && this.mouseOnAxis) {
            this.dragStartX = mouseX;
            this.dragStartScrollX = this.scrollX;
            this.dragging = true;
        }
        return true;
    }

    private NodeInfo findClickedNode(int mx, int my) {
        double dayPitch = 80.0 * this.zoom;
        double baseX = (double)(this.contentLeft + 16) - this.scrollX;
        int maxDay = Math.max(10, this.computeMaxDay() + 3);
        int branchIdx = 0;
        for (TimeMilestone tm : this.displayMilestones) {
            double cy;
            double cx;
            if (!tm.isCustom()) {
                cx = baseX + (double)(tm.unlocked() ? tm.unlockDay() : maxDay) * dayPitch;
                cy = this.axisY;
            } else {
                cx = baseX + (double)(tm.unlocked() ? tm.unlockDay() : maxDay + 1) * dayPitch;
                cy = this.axisY + 32 + branchIdx % 3 * 30;
                ++branchIdx;
            }
            if (!(TimelineScreen.distance(mx, my, cx, cy) < 20.0)) continue;
            return new NodeInfo(tm, cx, cy, !tm.isCustom());
        }
        return null;
    }

    private void openMilestoneEditor(TimeMilestone existing, double xPos) {
        Minecraft.getInstance().setScreen((Screen)new MilestoneEditorScreen(this, existing, saved -> {
            if (existing == null) {
                this.addCustomMilestone((TimeMilestone)saved);
            } else {
                this.updateCustomMilestone((TimeMilestone)saved);
            }
        }, id -> {
            if (id != null) {
                this.removeCustomMilestone((String)id);
            }
        }));
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.dragging && button == 0) {
            this.scrollX = this.dragStartScrollX - (mouseX - this.dragStartX);
            this.scrollX = Math.max(0.0, Math.min(this.scrollX, this.maxScrollX));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollXDelta, double scrollYDelta) {
        if (TimelineScreen.hasControlDown()) {
            double oldZoom = this.zoom;
            this.zoom += scrollYDelta > 0.0 ? 0.1 : -0.1;
            this.zoom = Math.max(0.25, Math.min(2.5, this.zoom));
            if (this.zoom != oldZoom) {
                double anchorX = this.scrollX + mouseX;
                double ratio = this.zoom / oldZoom;
                this.scrollX = anchorX * ratio - mouseX;
                this.updateMaxScroll();
            }
        } else {
            this.scrollX -= scrollYDelta * 20.0;
            this.scrollX = Math.max(0.0, Math.min(this.scrollX, this.maxScrollX));
        }
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.onClose();
            return true;
        }
        if (keyCode == 258) {
            this.toggleEditMode();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void toggleEditMode() {
        this.editMode = !this.editMode;
        this.scrollX = 0.0;
        this.targetScrollX = this.computeTargetScrollX();
        this.filterByCategory();
    }

    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    public boolean isPauseScreen() {
        return true;
    }

    private static double distance(double x1, double y1, double x2, double y2) {
        return Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
    }

    static class NodeInfo {
        final TimeMilestone milestone;
        final double x;
        final double y;
        final boolean isConfig;
        double labelOffset;

        NodeInfo(TimeMilestone m, double x, double y, boolean isConfig) {
            this.milestone = m;
            this.x = x;
            this.y = y;
            this.isConfig = isConfig;
            this.labelOffset = 0.0;
        }
    }
}

