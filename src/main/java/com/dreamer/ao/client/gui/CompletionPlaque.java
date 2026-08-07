package com.dreamer.ao.client.gui;

import com.dreamer.ao.LangKeys;
import com.dreamer.ao.client.ResourceLoader;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * 成就完成牌匾 UI。
 * 从屏幕上方优雅滑入，展示成就名称，带小猫爪元素。
 * <p>
 * 通过 {@code RegisterGuiLayersEvent} 注册为 HUD 覆盖层，
 * 无论何种屏幕都会显示。
 * <p>
 * 若同时完成多个成就，排队依次展示，每段持续约 2.5 秒。
 */
public final class CompletionPlaque {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ═══════════════ State ═══════════════

    /** 待展示的牌匾队列 */
    private static final Queue<String> queue = new ArrayDeque<>();

    private static String advancementName = "";
    private static long spawnTime = 0;
    private static boolean active = false;
    private static boolean textureChecked = false;

    // ═══════════════ Timing (ms) ═══════════════

    private static final long SLIDE_DURATION = 400;
    private static final long HOLD_DURATION = 2400;
    private static final long FADE_DURATION = 300;
    private static final long TOTAL_DURATION = SLIDE_DURATION + HOLD_DURATION + FADE_DURATION;

    // ═══════════════ Layout ═══════════════

    private static final int PLAQUE_W = 240;
    private static final int PLAQUE_H = 56;
    private static final int TARGET_Y = 22;

    // ═══════════════ Colors (base, alpha applied per frame) ═══════════════

    private static final int C_BG_OUTER = 0x24163E;     // deep purple outer
    private static final int C_BG_INNER = 0x362058;     // slightly lighter inner
    private static final int C_BORDER = 0xE8C0FF;        // lavender
    private static final int C_GOLD = 0xFFFFD700;         // gold
    private static final int C_TEXT = 0xFFFFF8E7;          // warm white
    private static final int C_PAW = 0xFFFFB6C1;           // light pink
    private static final int C_SPARKLE = 0xFFFFE0B0;       // pale sparkle
    private static final int C_ACCENT = 0xFFD4A0FF;        // accent lavender
    private static final int C_DIVIDER = 0x40D4A0FF;       // low-alpha divider

    private CompletionPlaque() {}

    // ═══════════════ Public API ═══════════════

    /** 截图文件名前缀 */
    private static String pendingScreenshotName = null;

    /** 当前待展示的 lore 文本（用于 Action Bar） */
    private static String pendingLore = null;

    /**
     * 展示牌匾，同时将 lore 文本发送到 Action Bar。
     * 若当前已有牌匾在展示中，则将新名称加入队列等待展示。
     */
    public static void show(String name) {
        show(name, null);
    }

    /**
     * 展示牌匾 + Action Bar 风味文本。
     */
    public static void show(String name, String lore) {
        if (active) {
            queue.add(name);
        } else {
            advancementName = name;
            spawnTime = System.currentTimeMillis();
            active = true;
            scheduleScreenshot(name);
            // Bug 5 修复：仅在真正展示牌匾时显示 Action Bar，排队时延迟到出队
            showLoreActionBar(name, lore);
        }
    }

    /** 在 Action Bar 显示风味文本，若无颜色代码则随机美化 */
    private static void showLoreActionBar(String name, String lore) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (lore == null || lore.isEmpty()) {
            mc.player.displayClientMessage(Component.literal("\u2726 " + name), true);
            return;
        }
        mc.player.displayClientMessage(applyLoreStyle(lore), true);
    }

    /** 对无 § 颜色代码的 lore 应用随机美学风格，返回正确的 Component */
    static MutableComponent applyLoreStyle(String lore) {
        if (lore.contains("\u00a7")) return Component.literal(lore); // 已有样式代码，保持不变

        // 基于文本内容 hash 选择稳定风格
        int hash = Math.abs(lore.hashCode());
        int styleIdx = hash % 8;

        return switch (styleIdx) {
            case 0 -> Component.literal("\u2726 " + lore).setStyle(Style.EMPTY
                    .withColor(ChatFormatting.GOLD).withItalic(true));               // Golden Quill
            case 1 -> Component.literal("\u2728 " + lore).setStyle(Style.EMPTY
                    .withColor(ChatFormatting.AQUA).withItalic(true));               // Celestial
            case 2 -> Component.literal("\u2663 " + lore).setStyle(Style.EMPTY
                    .withColor(ChatFormatting.GREEN));                               // Verdant
            case 3 -> Component.literal("\u2736 " + lore).setStyle(Style.EMPTY
                    .withColor(ChatFormatting.LIGHT_PURPLE).withItalic(true));       // Arcane
            case 4 -> Component.literal("\u2620 " + lore).setStyle(Style.EMPTY
                    .withColor(ChatFormatting.GOLD));                                // Scorched
            case 5 -> Component.literal("\u2744 " + lore).setStyle(Style.EMPTY
                    .withColor(ChatFormatting.AQUA));                                // Frost
            case 6 -> Component.literal("\u273f " + lore).setStyle(Style.EMPTY
                    .withColor(ChatFormatting.GRAY).withItalic(true));               // Whisper
            default -> Component.literal("\u2606 " + lore).setStyle(Style.EMPTY
                    .withColor(ChatFormatting.YELLOW));                              // Starlight
        };
    }

    /** 安排截图（在牌匾显示后延迟一帧执行，确保画面稳定） */
    private static void scheduleScreenshot(String name) {
        pendingScreenshotName = name;
    }

    /** 每帧由 GUI Layer 回调渲染。 */
    public static void render(GuiGraphics g, float partialTick) {
        if (!active) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || mc.font == null) return;

        // 首次渲染时尝试加载自定义纹理（必须在渲染线程）
        if (!textureChecked) {
            textureChecked = true;
            ResourceLoader.loadPlaqueTexture(mc, FMLPaths.CONFIGDIR.get());
        }

        long elapsed = System.currentTimeMillis() - spawnTime;

        // 当前展示完成，出队下一个（若有）
        if (elapsed > TOTAL_DURATION) {
            String next = queue.poll();
            if (next != null) {
                advancementName = next;
                spawnTime = System.currentTimeMillis();
                elapsed = 0;
            } else {
                active = false;
                return;
            }
        }

        int screenW = mc.getWindow().getGuiScaledWidth();
        Font font = mc.font;

        // 截图捕获（延迟至牌匾显示后执行）
        if (pendingScreenshotName != null && elapsed > 100) {
            takeScreenshot(mc, pendingScreenshotName);
            pendingScreenshotName = null;
        }

        // --- Animation ---
        int alpha;
        int y;

        if (elapsed < SLIDE_DURATION) {
            float t = (float) elapsed / SLIDE_DURATION;
            float phase = easeOutBack(t);
            alpha = 255;
            y = TARGET_Y + (int) ((1f - phase) * -120);
        } else if (elapsed < SLIDE_DURATION + HOLD_DURATION) {
            alpha = 255;
            y = TARGET_Y;
        } else {
            float t = (float) (elapsed - SLIDE_DURATION - HOLD_DURATION) / (float) FADE_DURATION;
            alpha = (int) (255 * (1f - easeInQuad(t)));
            y = TARGET_Y;
        }

        if (alpha <= 0) return;
        int x = (screenW - PLAQUE_W) / 2;
        int a = alpha;

        // --- Render ---
        renderPlaque(g, font, x, y, a);
    }

    // ═══════════════ Main render ═══════════════

    private static void renderPlaque(GuiGraphics g, Font font, int x, int y, int a) {
        // Shadow
        GuiUtils.drawCardShadow(g, x, y, PLAQUE_W, PLAQUE_H);

        // 优先使用自定义纹理
        if (ResourceLoader.isPlaqueAvailable()) {
            g.setColor(1f, 1f, 1f, a / 255f);
            g.blit(ResourceLoader.getPlaqueTexture(), x, y, 0, 0, PLAQUE_W, PLAQUE_H, PLAQUE_W, PLAQUE_H);
            g.setColor(1f, 1f, 1f, 1f);
        } else {
            // 默认代码绘制
            renderDefaultPlaque(g, font, x, y, a);
        }

        // ── 文字叠加（自定义纹理上也显示）──
        String title = Component.translatable(LangKeys.COMPLETION_TITLE).getString();
        int titleW = font.width(title);
        g.drawString(font, title, x + (PLAQUE_W - titleW) / 2, y + 10,
                (a << 24) | C_GOLD, false);

        String displayName = GuiUtils.truncate(font, advancementName, PLAQUE_W - 40);
        int nameW = font.width(displayName);
        g.drawString(font, displayName, x + (PLAQUE_W - nameW) / 2, y + 29,
                (a << 24) | C_TEXT, false);
    }

    /** 默认代码绘制的牌匾背景——优化版猫爪布局。 */
    private static void renderDefaultPlaque(GuiGraphics g, Font font, int x, int y, int a) {

        // Outer background
        g.fill(x, y, x + PLAQUE_W, y + PLAQUE_H, (a << 24) | C_BG_OUTER);

        // Inner glow region — 左右留白更多，让爪印在深色区域更突出
        int innerMargin = 6;
        g.fill(x + innerMargin, y + innerMargin,
                x + PLAQUE_W - innerMargin, y + PLAQUE_H - innerMargin,
                ((int)(a * 0.30f) << 24) | C_BG_INNER);

        // Border
        int borderA = (int)(a * 0.75f);
        g.renderOutline(x, y, PLAQUE_W, PLAQUE_H,
                (borderA << 24) | C_BORDER);

        // ── Top accent bar ──
        int accentA = (int)(a * 0.9f);
        g.fill(x + 10, y + 2, x + PLAQUE_W - 10, y + 4,
                (accentA << 24) | C_ACCENT);

        // ── Top sparkles（下移，避开 accent bar）──
        drawSparkle(g, x + 13, y + 8, 2.0f, (a << 24) | C_SPARKLE);
        drawSparkle(g, x + PLAQUE_W - 13, y + 8, 2.0f, (a << 24) | C_SPARKLE);

        // ── Top paw decorations（下移到文字下方，size 缩小使 toes 不溢出到 accent bar）──
        drawSmallCatPaw(g, x + 16, y + 13, 4, (a << 24) | C_PAW);
        drawSmallCatPaw(g, x + PLAQUE_W - 16, y + 13, 4, (a << 24) | C_PAW, true);

        // ── Divider ──
        int divX1 = x + 30;
        int divX2 = x + PLAQUE_W - 30;
        int divY = y + 21;
        g.fill(divX1, divY, divX2, divY + 1, (a << 24) | C_DIVIDER);

        // ── Center paw decorations：移到分隔线下方，清晰可见 ──
        int pawMidY = y + 26;
        drawTinyPaw(g, x + 44, pawMidY, 3, ((int)(a * 0.85f) << 24) | C_PAW);
        drawTinyPaw(g, x + PLAQUE_W - 47, pawMidY, 3, ((int)(a * 0.85f) << 24) | C_PAW);

        // ── Bottom paw trail（上移 + 放大，更醒目）──
        int trailY = y + 40;
        drawPawTrail(g, x + 18, trailY, 4, (a << 24) | C_PAW, 1);
        drawPawTrail(g, x + PLAQUE_W - 18, trailY, 4, (a << 24) | C_PAW, -1);

        // ── Bottom corner sparkles（放大 + 提高透明度）──
        drawSparkle(g, x + 13, y + 49, 2.0f, ((int)(a * 0.75f) << 24) | C_SPARKLE);
        drawSparkle(g, x + PLAQUE_W - 13, y + 49, 2.0f, ((int)(a * 0.75f) << 24) | C_SPARKLE);
    }

    // ═══════════════ Cat paw drawing ═══════════════

    /**
     * 绘制完整小猫爪印（主肉垫 + 4 个趾垫扇形排列）。
     *
     * @param mirror true 时镜像翻转
     */
    private static void drawSmallCatPaw(GuiGraphics g, int cx, int cy, int size,
                                         int color, boolean mirror) {
        int d = mirror ? -1 : 1;

        // 主肉垫：圆角矩形，更宽更扁像真实猫爪
        int pw = (int) (size * 2.0f);
        int ph = (int) (size * 1.1f);
        GuiUtils.fillRoundedCard(g, cx - pw / 2, cy + 1, pw, ph, color);

        // 4 个趾垫：弧形排列，趾垫稍大更可爱
        int tr = Math.max(1, (int) (size * 0.40f));
        int spread = (int) (size * 1.1f);
        int outerY = cy - size + 1;
        int innerY = cy - size - 1;

        GuiUtils.fillCircle(g, cx - spread * d, outerY, tr, color);
        GuiUtils.fillCircle(g, cx - spread / 3 * d, innerY, tr, color);
        GuiUtils.fillCircle(g, cx + spread / 3 * d, innerY, tr, color);
        GuiUtils.fillCircle(g, cx + spread * d, outerY, tr, color);
    }

    private static void drawSmallCatPaw(GuiGraphics g, int cx, int cy,
                                         int size, int color) {
        drawSmallCatPaw(g, cx, cy, size, color, false);
    }

    /** 绘制迷你猫爪（仅主垫+2 趾），稍大更清晰。 */
    private static void drawTinyPaw(GuiGraphics g, int cx, int cy, int size, int color) {
        int pw = (int) (size * 1.8f);
        int ph = (int) (size * 1.0f);
        GuiUtils.fillRoundedCard(g, cx - pw / 2, cy, pw, ph, color);
        int tr = Math.max(1, (int)(size * 0.45f));
        GuiUtils.fillCircle(g, cx - size / 2, cy - size / 2, tr, color);
        GuiUtils.fillCircle(g, cx + size / 2, cy - size / 2, tr, color);
    }

    /** 绘制一对迷你爪印组成的脚印轨迹，每个爪印都有主垫+两趾的完整形状。 */
    private static void drawPawTrail(GuiGraphics g, int startX, int cy, int size,
                                      int color, int dir) {
        for (int i = 0; i < 2; i++) {
            int cx = startX + i * size * 4 * dir;
            int toeR = Math.max(1, (int)(size * 0.45f));
            int padW = (int)(size * 1.6f);
            int padH = (int)(size * 1.0f);
            // 主肉垫
            GuiUtils.fillRoundedCard(g, cx - padW / 2, cy, padW, padH, color);
            // 两趾垫
            GuiUtils.fillCircle(g, cx - size / 2 * dir, cy - size / 2, toeR, color);
            GuiUtils.fillCircle(g, cx + size / 2 * dir, cy - size / 2, toeR, color);
        }
    }

    // ═══════════════ Sparkle drawing ═══════════════

    /** 绘制四角星闪烁（水平+垂直+两条对角线，更像星形光点）。 */
    private static void drawSparkle(GuiGraphics g, int cx, int cy, float size, int color) {
        int s = (int) size;
        int hs = Math.max(1, s / 2);
        // 水平线
        g.fill(cx - s, cy, cx + s + 1, cy + 1, color);
        // 垂直线
        g.fill(cx, cy - s, cx + 1, cy + s + 1, color);
        // 对角线（短一些，更有层次）
        g.fill(cx - hs, cy - hs, cx - hs + 1, cy - hs + 1, color);
        g.fill(cx + hs, cy - hs, cx + hs + 1, cy - hs + 1, color);
        g.fill(cx - hs, cy + hs, cx - hs + 1, cy + hs + 1, color);
        g.fill(cx + hs, cy + hs, cx + hs + 1, cy + hs + 1, color);
    }

    // ═══════════════ Easing functions ═══════════════

    /** 回弹缓出：c ≈ 1.70158 */
    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    private static float easeInQuad(float t) {
        return t * t;
    }

    // ═══════════════ Screenshot ═══════════════

    private static void takeScreenshot(Minecraft mc, String name) {
        try {
            Path dir = mc.gameDirectory.toPath().resolve("screenshots").resolve("advancements");
            Files.createDirectories(dir);
            String safeName = name.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fff _\\-]", "_").replaceAll("\\s+", "_");
            String timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = safeName + "_" + timestamp + ".png";
            Path filePath = dir.resolve(filename);

            net.minecraft.client.Screenshot.grab(
                    mc.gameDirectory,
                    mc.getMainRenderTarget(),
                    msg -> {
                        // 复制到我们的目录
                        Path defaultFile = mc.gameDirectory.toPath().resolve("screenshots").resolve(filename);
                        Path defaultPng = mc.gameDirectory.toPath().resolve("screenshots")
                                .resolve(filename.replace(".png", "") + ".png");
                        try {
                            // 尝试移动文件
                            java.io.File src = defaultFile.toFile();
                            if (!src.exists()) src = defaultPng.toFile();
                            if (src.exists()) {
                                Files.move(src.toPath(), filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            }
                        } catch (Exception e) {
                            LOGGER.debug("Failed to move screenshot file: {}", e.getMessage());
                        }
                    }
            );
        } catch (Exception e) {
            LOGGER.debug("Screenshot capture failed: {}", e.getMessage());
        }
    }
}
