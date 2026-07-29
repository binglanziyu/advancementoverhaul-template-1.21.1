package com.example.advancementoverhaul.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * 成就完成旋律（Achievement Completion Chime）。
 * <p>
 * 混合音色编曲：钢琴（音符盒竖琴）构建旋律主体，紫水晶 Chime 点亮高峰，
 * 紫水晶 Resonate 锚定首尾，长笛点缀呼吸。6 tick 紧凑间距让音符交织成旋律，
 * 中段留一次 7 tick 微呼吸，结尾 10 tick 拉开收束。
 * <p>
 * 总时长约 71 tick（约 3.55 秒）。
 */
public final class CompletionChime {

    /** 基础音量倍率，各音色调平后钢琴以此为准 */
    private static final float BASE_VOLUME = 4.0f;

    /** 音色编号 */
    private static final int S_RESONATE = 0;  // 紫水晶共鸣 —— 深沉锚点
    private static final int S_HARP     = 1;  // 音符盒竖琴 —— 温润钢琴
    private static final int S_CHIME    = 2;  // 紫水晶叮咚 —— 空灵高光
    private static final int S_FLUTE    = 3;  // 音符盒长笛 —— 轻盈呼吸

    // 12 个音符，6 tick 基准间距，中段 7 tick 呼吸，结尾 10 tick 收束
    private static final int[] DELAYS = {
        0, 6, 12, 18, 24, 31, 37, 43, 49, 55, 61, 71
    };
    private static final float[] PITCHES = {
        0.50f, 0.84f, 1.00f, 1.50f, 1.19f, 0.89f,
        1.33f, 0.75f, 1.12f, 0.63f, 0.50f, 0.35f
    };
    private static final int[] SOUNDS = {
        S_RESONATE, S_HARP, S_HARP, S_CHIME, S_FLUTE, S_HARP,
        S_CHIME, S_HARP, S_CHIME, S_HARP, S_HARP, S_RESONATE
    };
    /**
     * 每音符独立音量倍率（基于 BASE_VOLUME）。
     * Harp 本身响亮保持 1.0x；Flute 略弱 1.1x；
     * Chime 偏小补到 1.6x；Resonate 最弱补到 2.0x。
     */
    private static final float[] VOLUME_SCALES = {
        2.00f, 1.00f, 1.00f, 1.60f, 1.10f, 1.00f,
        1.60f, 1.00f, 1.60f, 1.00f, 1.00f, 2.00f
    };

    private CompletionChime() {}

    /** 根据音色编号映射到 SoundEvent */
    private static SoundEvent soundFor(int type) {
        return switch (type) {
            case S_HARP     -> SoundEvents.NOTE_BLOCK_HARP.value();
            case S_FLUTE    -> SoundEvents.NOTE_BLOCK_FLUTE.value();
            case S_CHIME    -> SoundEvents.AMETHYST_BLOCK_CHIME;
            case S_RESONATE -> SoundEvents.AMETHYST_BLOCK_RESONATE;
            default         -> SoundEvents.NOTE_BLOCK_HARP.value();
        };
    }

    /** 播放成就完成旋律 */
    public static void play(Minecraft mc) {
        if (mc == null) return;

        // 优先使用自定义音效
        if (com.example.advancementoverhaul.client.ResourceLoader.playCustomChime()) return;

        var mgr = mc.getSoundManager();
        if (mgr == null) return;

        for (int i = 0; i < DELAYS.length; i++) {
            mgr.playDelayed(
                    new SimpleSoundInstance(
                            soundFor(SOUNDS[i]).getLocation(),
                            SoundSource.MASTER,
                            BASE_VOLUME * VOLUME_SCALES[i],
                            PITCHES[i],
                            RandomSource.create(),
                            false, // looping
                            0,     // delay
                            SoundInstance.Attenuation.NONE,
                            0.0, 0.0, 0.0, // x, y, z
                            true  // relative
                    ),
                    DELAYS[i]
            );
        }
    }
}
