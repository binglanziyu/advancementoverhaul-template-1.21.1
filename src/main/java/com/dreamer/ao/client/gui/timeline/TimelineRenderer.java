package com.dreamer.ao.client.gui.timeline;

import com.dreamer.ao.client.gui.GuiUtils;
import com.dreamer.ao.client.gui.timeline.TimelineScreen;
import com.dreamer.ao.milestone.model.TimeMilestone;
import com.dreamer.ao.milestone.model.TimelineCategory;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import com.dreamer.ao.ModInfo;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

final class TimelineRenderer {
    private static final ResourceLocation TEX_VIGNETTE = ModInfo.rl("textures/gui/timeline/vignette");
    private static final ResourceLocation TEX_FROST = ModInfo.rl("textures/gui/timeline/noise_frost");
    private static final ResourceLocation TEX_DROPLET = ModInfo.rl("textures/gui/timeline/droplet");
    private static final ResourceLocation TEX_STREAK = ModInfo.rl("textures/gui/timeline/water_streak");
    private static final ResourceLocation TEX_GLOW_OUTER = ModInfo.rl("textures/gui/milestone_glow_outer.png");
    private static final ResourceLocation TEX_GLOW_INNER = ModInfo.rl("textures/gui/milestone_glow_inner.png");
    private static final ResourceLocation TEX_BTN_HOVER = ModInfo.rl("textures/gui/timeline/button_hover.png");
    private static final ResourceLocation TEX_BTN_NORMAL = ModInfo.rl("textures/gui/timeline/button_normal.png");
    private static final ResourceLocation TEX_BTN_PRESSED = ModInfo.rl("textures/gui/timeline/button_pressed.png");
    private static final int BTN_TEX_W = 160;
    private static final int BTN_TEX_H = 60;
    private static final int BTN_SLICE = 24;
    private static final float[] rainX = new float[40];
    private static final float[] rainY = new float[40];
    private static boolean rainInited = false;
    private static final int[] dropX = new int[15];
    private static final int[] dropY = new int[15];
    private static boolean dropsInited = false;
    private static final float[] streakXf = new float[6];
    private static final float[] streakYf = new float[6];
    private static boolean streaksInited = false;

    private TimelineRenderer() {
    }

    static void renderBackground(GuiGraphics g, int width, int height) {
        g.fill(0, 0, width, height, 0x60000000);
        g.fill(0, 0, width, height, 0x50D0E0E8);
    }

    static void renderHeader(GuiGraphics g, Font font, int width, boolean editMode, int mouseX, int mouseY, Component title) {
        Double d;
        g.drawString(font, title.getString(), 16, 14, -3087122, false);
        int closeX = width - 24;
        boolean hov = GuiUtils.inRect(mouseX, mouseY, closeX - 4, 2, 22, 30);
        if (hov) {
            TimelineRenderer.drawRoundedRect(g, closeX - 2, 8, 20, 18, 4, 822053000);
        }
        g.drawString(font, "\u2715", closeX, 14, hov ? -21846 : -1431384872, false);
        Object[] objectArray = new Object[1];
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof TimelineScreen) {
            TimelineScreen ts = (TimelineScreen)screen;
            d = ts.zoom;
        } else {
            d = 1.0;
        }
        objectArray[0] = d;
        String zoomText = String.format("%.1f\u00d7", objectArray);
        int zw = font.width(zoomText);
        g.drawString(font, zoomText, closeX - zw - 14, 14, -2138920780, false);
    }

    private static final int COLOR_NORMAL = 0xFF5C7A82;
    private static final int COLOR_HOVER  = 0xFF8AB4C0;
    private static final int COLOR_SELECT = 0xFF0D3338;
    static void renderTabs(GuiGraphics g, Font font, int width, List<TimelineCategory> categories, int selectedIdx, boolean phaseMode, int mouseX, int mouseY) {
        int pillY = 38;
        int pillH = 22;
        int x = 16;
        for (int i = 0; i < categories.size(); ++i) {
            TimelineCategory cat = categories.get(i);
            boolean sel = i == selectedIdx;
            String label = Component.translatable((String)cat.nameKey()).getString();
            int tw = font.width(label) + 24;
            boolean hov = GuiUtils.inRect(mouseX, mouseY, x, pillY, tw, pillH);
            TimelineRenderer.drawMilestoneButton(g, x, pillY, tw, pillH, sel, hov);
            g.drawString(font, label, x + 12, pillY + (pillH - 8) / 2 + 1, sel ? COLOR_SELECT : (hov ? COLOR_HOVER : COLOR_NORMAL), false);
            x += tw + 6;
        }
        String phaseLabel = "\u9636\u6bb5";
        int phaseW = font.width(phaseLabel) + 20;
        int phaseX = width - phaseW - 16;
        boolean phaseHov = GuiUtils.inRect(mouseX, mouseY, phaseX, pillY, phaseW, pillH);
        TimelineRenderer.drawMilestoneButton(g, phaseX, pillY, phaseW, pillH, phaseMode, phaseHov);
        g.drawString(font, phaseLabel, phaseX + 10, pillY + (pillH - 8) / 2 + 1, phaseMode ? COLOR_SELECT : (phaseHov ? COLOR_HOVER : COLOR_NORMAL), false);
    }

    static void renderTimeline(GuiGraphics g, Font font, int cLeft, int cRight, int cTop, int cBottom, int axisY, double scrollX, double zoom, boolean editMode, List<TimeMilestone> milestones, int mouseX, int mouseY, boolean mouseOnAxis, double mouseOnAxisX, int maxDay, long openTime) {
        g.enableScissor(cLeft + 1, cTop, cRight - 1, cBottom);
        double dayPitch = 80.0 * zoom;
        double baseX = (double)(cLeft + 16) - scrollX;
        TimelineRenderer.drawTimelineAxis(g, baseX, baseX + (double)(maxDay + 3) * dayPitch, axisY);
        for (int day = 1; day <= maxDay + 2; ++day) {
            double tickX = baseX + (double)day * dayPitch;
            if (tickX < -100.0 || tickX > (double)(cRight + 100)) continue;
            g.fill((int)tickX, axisY - 10, (int)tickX + 1, axisY + 10, 1217970384);
            if (day % 5 != 0 && day != 1) continue;
            String lbl = Component.translatable((String)"milestone.advancementoverhaul.day_n", (Object[])new Object[]{day}).getString();
            int tw = font.width(lbl);
            g.drawString(font, lbl, (int)(tickX - (double)((float)tw / 2.0f)), axisY - 22, -1147751228, false);
        }
        List<TimelineScreen.NodeInfo> nodes = TimelineRenderer.layoutNodes(milestones, baseX, dayPitch, axisY, maxDay, editMode);
        for (int i = 0; i < nodes.size(); ++i) {
            TimelineRenderer.renderMilestone(g, font, nodes.get(i), mouseX, mouseY, axisY, editMode, openTime, i);
        }
        if (editMode && mouseOnAxis && mouseOnAxisX > (double)cLeft && mouseOnAxisX < (double)cRight) {
            TimelineRenderer.renderHoverRing(g, mouseOnAxisX, axisY);
        }
        if (nodes.isEmpty() && !editMode) {
            String empty = Component.translatable((String)"milestone.advancementoverhaul.timeline_empty").getString();
            int tw = font.width(empty);
            g.drawString(font, empty, (cRight - cLeft) / 2 - tw / 2, axisY + 40, -1431384872, false);
        }
        g.disableScissor();
    }

    private static void drawTimelineAxis(GuiGraphics g, double startX, double endX, int axisY) {
        int totalW = (int)(endX - startX);
        if (totalW <= 0) {
            return;
        }
        int segs = Math.max(totalW / 4, 1);
        for (int i = 0; i < segs; ++i) {
            float p = (float)i / (float)segs;
            int x = (int)(startX + (double)(i * 4));
            int alpha = p < 0.12f ? (int)(208.0f * (p / 0.12f)) : (p > 0.88f ? (int)(208.0f * (1.0f - (p - 0.88f) / 0.12f * 0.35f)) : 208);
            g.fill(x, axisY, x + 4, axisY + 1, alpha << 24 | 0xBED0DA);
        }
        g.fill((int)startX, axisY + 1, (int)startX + Math.min(totalW, 800), axisY + 6, 343853260);
    }

    static List<TimelineScreen.NodeInfo> layoutNodes(List<TimeMilestone> milestones, double baseX, double dayPitch, int axisY, int maxDay, boolean editMode) {
        ArrayList<TimelineScreen.NodeInfo> nodes = new ArrayList<TimelineScreen.NodeInfo>();
        int branchIdx = 0;
        for (TimeMilestone tm : milestones) {
            int ny;
            double nx;
            boolean isConfig = !tm.isCustom();
            boolean unlocked = tm.unlocked();
            if (!editMode && !unlocked) continue;
            if (isConfig) {
                nx = baseX + (double)(unlocked ? tm.unlockDay() : 0) * dayPitch;
                if (!unlocked) {
                    nx = baseX + (double)maxDay * dayPitch + 50.0;
                }
                ny = axisY;
            } else {
                nx = baseX + (double)(unlocked ? tm.unlockDay() : maxDay + 1) * dayPitch;
                ny = axisY + 32 + branchIdx % 3 * 30;
                ++branchIdx;
            }
            nodes.add(new TimelineScreen.NodeInfo(tm, nx, ny, isConfig));
        }
        for (int i = 1; i < nodes.size(); ++i) {
            if (!(Math.abs(((TimelineScreen.NodeInfo)nodes.get((int)i)).x - ((TimelineScreen.NodeInfo)nodes.get((int)(i - 1))).x) < 30.0)) continue;
            TimelineScreen.NodeInfo prev = (TimelineScreen.NodeInfo)nodes.get(i - 1);
            TimelineScreen.NodeInfo curr = (TimelineScreen.NodeInfo)nodes.get(i);
            if (!curr.isConfig || !prev.isConfig) continue;
            curr.labelOffset = prev.labelOffset + 20.0;
        }
        return nodes;
    }

    static void renderMilestone(GuiGraphics g, Font font, TimelineScreen.NodeInfo info, int mouseX, int mouseY, int axisY, boolean editMode, long openTime, int nodeIndex) {
        TimeMilestone tm = info.milestone;
        double cx = info.x;
        double cy = info.y + info.labelOffset;
        boolean isConfig = info.isConfig;
        boolean unlocked = tm.unlocked();
        boolean hovered = TimelineRenderer.dist(mouseX, mouseY, cx, cy) < 20.0;
        String displayName = tm.getDisplayName();
        long now = System.currentTimeMillis();
        long elapsed = now - openTime;
        double appear = TimelineRenderer.clamp01((double)(elapsed - (long)nodeIndex * 80L) / 500.0);
        if ((appear = 1.0 - Math.pow(1.0 - appear, 3.0)) <= 0.0) {
            return;
        }
        float breathe = unlocked ? (float)(Math.sin((double)now / 3000.0 + cx * 0.01) * 0.5 + 0.5) : 0.0f;
        float breatheScale = 0.85f + breathe * 0.15f;
        if (!isConfig && (editMode || unlocked)) {
            int ca = (int)(48.0 * appear);
            int cc = ca << 24 | 0x98C4D0;
            g.fill((int)cx, axisY + 4, (int)cx + 1, (int)cy - 7, cc);
            g.fill((int)(cx - 3.0), axisY - 3, (int)(cx + 4.0), axisY + 4, cc);
        }
        if (unlocked) {
            float a = (float)appear * breatheScale;
            TimelineRenderer.drawCircularGlow(g, cx, cy, 21, 7383224, (int)(60.0f * a));
            TimelineRenderer.drawCircularGlow(g, cx, cy, 15, 7911620, (int)(100.0f * a));
            TimelineRenderer.drawCircularGlow(g, cx, cy, 11, 9028812, (int)(140.0f * a));
            TimelineRenderer.drawCircularGlow(g, cx, cy, 7, 10080988, (int)(200.0f * a));
        } else {
            TimelineRenderer.drawCircularGlow(g, cx, cy, 14, 7383224, (int)(25.0 * appear));
        }
        if (hovered) {
            TimelineRenderer.drawCircularGlow(g, cx, cy, 28, 7911620, (int)(50.0 * appear));
        }
        int coreHex = unlocked ? -578042672 : 682937560;
        int coreA = (int)((double)(unlocked ? 221 : 72) * appear);
        if (hovered) {
            coreA = (int)(255.0 * appear);
        }
        g.fill((int)(cx - 7.0), (int)(cy - 7.0), (int)(cx + 7.0 + 1.0), (int)(cy + 7.0 + 1.0), coreA << 24 | coreHex & 0xFFFFFF);
        if (unlocked) {
            int wa = (int)(255.0 * appear);
            g.fill((int)(cx - 2.0), (int)(cy - 2.0), (int)(cx + 3.0), (int)(cy + 3.0), wa << 24 | 0xE8F4F8);
        }
        if (hovered) {
            int tw = font.width(displayName);
            int lw = tw + 14;
            int lh = 20;
            int lx = (int)(cx - (double)((float)lw / 2.0f));
            int ly = (int)(cy + 7.0 + 6.0);
            g.fill(lx, ly, lx + lw, ly + lh, 1624695018);
            g.renderOutline(lx, ly, lw, lh, 545568968);
            g.fill((int)cx, (int)(cy + 7.0), (int)cx + 1, ly, 746506444);
            g.drawString(font, displayName, lx + 7, ly + (lh - 8) / 2, -3087122, false);
            if (unlocked) {
                String detail = Component.translatable((String)"milestone.advancementoverhaul.day_detail", (Object[])new Object[]{tm.unlockDay()}).getString();
                int dw = font.width(detail);
                g.drawString(font, detail, (int)(cx - (double)((float)dw / 2.0f)), ly + lh + 2, -1431384872, false);
            }
        } else {
            int tw = font.width(displayName);
            int lc = unlocked ? -1431384872 : -1147751228;
            g.drawString(font, displayName, (int)(cx - (double)((float)tw / 2.0f)), (int)(cy + 7.0 + 6.0), lc, false);
        }
        if (editMode && tm.isCustom()) {
            g.drawString(font, "\u270e", (int)(cx - 3.0), (int)(cy - 7.0 - 12.0), -8468276, false);
        }
    }

    static void drawCircularGlow(GuiGraphics g, double centerXd, double centerYd, int radius, int rgb, int baseAlpha) {
        if (baseAlpha <= 0 || radius <= 0) {
            return;
        }
        int cx = (int)centerXd;
        int cy = (int)centerYd;
        for (int r = radius; r >= 1; --r) {
            float t = 1.0f - (float)r / (float)radius;
            int alpha = (int)((float)baseAlpha * t * t);
            if (alpha <= 0) continue;
            int color = alpha << 24 | rgb & 0xFFFFFF;
            int sqR = r * r;
            for (int dy = -r; dy <= r; ++dy) {
                int dx = (int)Math.sqrt(sqR - dy * dy);
                g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
            }
        }
        g.fill(cx, cy, cx + 1, cy + 1, baseAlpha << 24 | rgb & 0xFFFFFF);
    }

    private static void renderHoverRing(GuiGraphics g, double x, int y) {
        TimelineRenderer.drawCircleOutline(g, (int)x, y, 14, -2072065844);
        TimelineRenderer.drawCircleOutline(g, (int)x, y, 6, 1149159628);
        g.fill((int)x - 2, y - 2, (int)x + 3, y + 3, -2072065844);
    }

    static void renderBottomHint(GuiGraphics g, Font font, int width, int height, boolean editMode, int mouseX, int mouseY) {
        int y = height - 28;
        String hint = editMode ? Component.translatable((String)"timeline.advancementoverhaul.edit_hint").getString() : Component.translatable((String)"milestone.advancementoverhaul.timeline_hint").getString();
        g.drawString(font, hint, 16, y + 10 + 1, -1432831800, false);
        String editLabel = "\u7f16\u8f91";
        int editW = font.width(editLabel) + 20;
        int editBtnX = width - editW - 16;
        int editBtnY = y + 3;
        int editBtnH = 22;
        boolean editHov = GuiUtils.inRect(mouseX, mouseY, editBtnX, editBtnY, editW, editBtnH);
        TimelineRenderer.drawMilestoneButton(g, editBtnX, editBtnY, editW, editBtnH, editMode, editHov);
        g.drawString(font, editLabel, editBtnX + 10, editBtnY + (editBtnH - 8) / 2 + 1, editMode ? COLOR_SELECT : (editHov ? COLOR_HOVER : COLOR_NORMAL), false);
    }

    private static void renderDroplets(GuiGraphics g, int width, int height) {
        if (!dropsInited) {
            Random rng = new Random(888L);
            for (int i = 0; i < 15; ++i) {
                TimelineRenderer.dropX[i] = 20 + rng.nextInt(Math.max(width - 40, 1));
                TimelineRenderer.dropY[i] = 30 + rng.nextInt(Math.max(height - 60, 1));
            }
            dropsInited = true;
        }
        for (int i = 0; i < 15; ++i) {
            g.blit(TEX_DROPLET, dropX[i], dropY[i], 0.0f, 0.0f, 8, 8, 8, 8);
        }
    }

    private static void renderWaterStreaks(GuiGraphics g, int width, int height) {
        if (!streaksInited) {
            Random rng = new Random(456L);
            for (int i = 0; i < 6; ++i) {
                TimelineRenderer.streakXf[i] = 30 + rng.nextInt(Math.max(width - 60, 1));
                TimelineRenderer.streakYf[i] = rng.nextFloat() * (float)height * 0.5f;
            }
            streaksInited = true;
        }
        for (int i = 0; i < 6; ++i) {
            int n = i;
            streakYf[n] = streakYf[n] + 0.2f;
            if (streakYf[i] > (float)height) {
                TimelineRenderer.streakYf[i] = -20.0f;
            }
            g.blit(TEX_STREAK, (int)streakXf[i], (int)streakYf[i], 0.0f, 0.0f, 4, 128, 4, 128);
        }
    }

    private static void renderRain(GuiGraphics g, int width, int height) {
        if (!rainInited) {
            Random rng = new Random(42L);
            for (int i = 0; i < 40; ++i) {
                TimelineRenderer.rainX[i] = rng.nextInt(Math.max(width, 1));
                TimelineRenderer.rainY[i] = rng.nextInt(Math.max(height, 1));
            }
            rainInited = true;
        }
        for (int i = 0; i < 40; ++i) {
            int n = i;
            rainY[n] = rainY[n] + 0.9f;
            int n2 = i;
            rainX[n2] = rainX[n2] - 0.5f;
            if (rainY[i] > (float)height) {
                int n3 = i;
                rainY[n3] = rainY[n3] - (float)(height + 20);
            }
            if (rainX[i] < 0.0f) {
                int n4 = i;
                rainX[n4] = rainX[n4] + (float)width;
            }
            int rx = (int)rainX[i];
            int ry = (int)rainY[i];
            g.fill(rx, ry, rx + 1, ry + 3, 345032920);
            g.fill(rx - 1, ry + 3, rx, ry + 4, 177260760);
        }
    }

    static void drawRoundedRect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
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

    static void drawShadowButton(GuiGraphics g, int x, int y, int w, int h, int color, boolean hovered, int hoverColor) {
        int c = hovered ? hoverColor : color;
        TimelineRenderer.drawRoundedRect(g, x + 1, y + 2, w, h, 5, 0x38000000);
        TimelineRenderer.drawRoundedRect(g, x, y, w, h, 5, c);
    }

    static void drawMilestoneButton(GuiGraphics g, int x, int y, int w, int h, boolean selected, boolean hovered) {
        ResourceLocation tex = selected ? TEX_BTN_PRESSED : (hovered ? TEX_BTN_HOVER : TEX_BTN_NORMAL);
        if (w <= BTN_SLICE * 2 || h <= BTN_SLICE * 2) {
            g.blit(tex, x, y, 0, 0, w, h, w, h);
            return;
        }
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        Matrix4f matrix = g.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        float u0 = 0f, v0 = 0f;
        float u1 = (float) BTN_SLICE / BTN_TEX_W;
        float u2 = (float) (BTN_TEX_W - BTN_SLICE) / BTN_TEX_W;
        float u3 = 1f;
        float v1 = (float) BTN_SLICE / BTN_TEX_H;
        float v2 = (float) (BTN_TEX_H - BTN_SLICE) / BTN_TEX_H;
        float v3 = 1f;

        int x0 = x, x1 = x + BTN_SLICE, x2 = x + w - BTN_SLICE, x3 = x + w;
        int y0 = y, y1 = y + BTN_SLICE, y2 = y + h - BTN_SLICE, y3 = y + h;

        // Row 1 (top): TL corner, top edge, TR corner
        quad(builder, matrix, x0, x1, y0, y1, u0, u1, v0, v1);
        quad(builder, matrix, x1, x2, y0, y1, u1, u2, v0, v1);
        quad(builder, matrix, x2, x3, y0, y1, u2, u3, v0, v1);
        // Row 2 (middle): left edge, center, right edge
        quad(builder, matrix, x0, x1, y1, y2, u0, u1, v1, v2);
        quad(builder, matrix, x1, x2, y1, y2, u1, u2, v1, v2);
        quad(builder, matrix, x2, x3, y1, y2, u2, u3, v1, v2);
        // Row 3 (bottom): BL corner, bottom edge, BR corner
        quad(builder, matrix, x0, x1, y2, y3, u0, u1, v2, v3);
        quad(builder, matrix, x1, x2, y2, y3, u1, u2, v2, v3);
        quad(builder, matrix, x2, x3, y2, y3, u2, u3, v2, v3);

        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private static void quad(BufferBuilder builder, Matrix4f matrix, int x1, int x2, int y1, int y2, float u1, float u2, float v1, float v2) {
        builder.addVertex(matrix, (float) x1, (float) y2, 0f).setUv(u1, v2);
        builder.addVertex(matrix, (float) x2, (float) y2, 0f).setUv(u2, v2);
        builder.addVertex(matrix, (float) x2, (float) y1, 0f).setUv(u2, v1);
        builder.addVertex(matrix, (float) x1, (float) y1, 0f).setUv(u1, v1);
    }

    static void drawShadowPill(GuiGraphics g, int x, int y, int w, int h, int color) {
        TimelineRenderer.drawRoundedPill(g, x + 1, y + 2, w, h, 0x38000000);
        TimelineRenderer.drawRoundedPill(g, x, y, w, h, color);
    }

    static void drawRoundedPill(GuiGraphics g, int px, int py, int w, int h, int color) {
        int r = h / 2;
        g.fill(px + r, py, px + w - r, py + h, color);
        g.fill(px, py + 1, px + r * 2, py + h - 1, color);
        g.fill(px + w - r * 2, py + 1, px + w, py + h - 1, color);
        g.fill(px + r, py, px + w - r, py + 1, color);
        g.fill(px + r, py + h - 1, px + w - r, py + h, color);
    }

    static void drawCircleOutline(GuiGraphics g, int cx, int cy, int r, int color) {
        int d = 3 - 2 * r;
        int py = r;
        for (int px = 0; py >= px; ++px) {
            g.fill(cx + px, cy - py, cx + px + 1, cy - py + 1, color);
            g.fill(cx + py, cy - px, cx + py + 1, cy - px + 1, color);
            g.fill(cx - px, cy - py, cx - px + 1, cy - py + 1, color);
            g.fill(cx - py, cy - px, cx - py + 1, cy - px + 1, color);
            g.fill(cx + px, cy + py, cx + px + 1, cy + py + 1, color);
            g.fill(cx + py, cy + px, cx + py + 1, cy + px + 1, color);
            g.fill(cx - px, cy + py, cx - px + 1, cy + py + 1, color);
            g.fill(cx - py, cy + px, cx - py + 1, cy + px + 1, color);
            if (d < 0) {
                d += 4 * px + 6;
                continue;
            }
            d += 4 * (px - py) + 10;
            --py;
        }
    }

    private static double dist(double x1, double y1, double x2, double y2) {
        return Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}

