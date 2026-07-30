/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.Font
 *  net.minecraft.client.gui.GuiGraphics
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.network.chat.Component
 */
package com.example.advancementoverhaul.client.gui;

import com.example.advancementoverhaul.client.gui.GuiUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class NarrativeScreen
extends Screen {
    protected static final int PARCHMENT_TOP = -14803410;
    protected static final int PARCHMENT_BOTTOM = -14013892;
    protected static final int HEADER_H = 32;
    protected static final int PADDING = 12;
    protected int scrollOff;
    protected int maxScroll;

    protected NarrativeScreen(Component title) {
        super(title);
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        this.renderParchmentBg(g);
        this.renderHeader(g, mouseX, mouseY);
        this.renderContent(g, mouseX, mouseY);
        this.renderScrollbar(g);
    }

    protected void renderParchmentBg(GuiGraphics g) {
        g.fillGradient(0, 0, this.width, this.height, -14803410, -14013892);
    }

    protected void renderHeader(GuiGraphics g, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        g.fill(0, 0, this.width, 32, -14408648);
        g.fill(0, 31, this.width, 32, -12961200);
        String title = this.getTitle().getString();
        g.drawString(font, title, 12, 12, -1, false);
        String close = "\u2715";
        int closeX = this.width - 24;
        boolean hov = GuiUtils.inRect(mouseX, mouseY, closeX, 4, 20, 24);
        g.drawString(font, close, closeX + 4, 12, hov ? -1 : -7303000, false);
    }

    protected abstract void renderContent(GuiGraphics var1, int var2, int var3);

    protected void renderScrollbar(GuiGraphics g) {
        if (this.maxScroll <= 0) {
            return;
        }
        int contentTop = 44;
        int contentBottom = this.height - 4;
        int trackH = contentBottom - contentTop;
        int thumbH = Math.max(24, (int)((float)trackH / (float)(trackH + this.maxScroll) * (float)trackH));
        int thumbY = contentTop + (int)((float)this.scrollOff / (float)this.maxScroll * (float)(trackH - thumbH));
        int barX = this.width - 8;
        g.fill(barX, contentTop, barX + 4, contentBottom, 0x20FFFFFF);
        g.fill(barX, thumbY, barX + 4, thumbY + thumbH, 0x60FFFFFF);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int mx = (int)mouseX;
        int my = (int)mouseY;
        if (GuiUtils.inRect(mx, my, this.width - 24, 4, 20, 24)) {
            this.onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scrollOff -= (int)(scrollY * 20.0);
        this.scrollOff = Math.max(0, Math.min(this.scrollOff, this.maxScroll));
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    public boolean isPauseScreen() {
        return true;
    }

    public static interface NarrativePage {
        public void render(GuiGraphics var1, int var2, int var3, int var4, int var5, int var6);

        public int getContentHeight();
    }
}

