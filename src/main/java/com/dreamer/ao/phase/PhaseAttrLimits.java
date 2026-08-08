package com.dreamer.ao.phase;

/**
 * 阶段属性倍率的全局上下限约束。
 * <p>
 * 所有乘算型属性（玩家属性、怪物倍率）的值都被限制在该区间内，
 * 避免单层或三层叠加后出现数值极端（如 0 或 1000 倍）导致游戏失衡。
 * <p>
 * 该常量在编辑器保存时做校验（超出则截断并提示），并在 UI 中展示说明文字。
 */
public final class PhaseAttrLimits {

    /** 倍率下限（最低可设为该值，例如 0.1 表示最多削弱到 10%） */
    public static final double MIN_MULT = 0.1;

    /** 倍率上限（最高可设为该值，例如 10.0 表示最多增强到 10 倍） */
    public static final double MAX_MULT = 10.0;

    /** 玩家属性倍率允许下限（玩家属性更保守，避免 0 倍即秒杀/无敌） */
    public static final double MIN_PLAYER_MULT = 0.1;

    /** 玩家属性倍率允许上限 */
    public static final double MAX_PLAYER_MULT = 5.0;

    /** 怪物倍率允许下限 */
    public static final double MIN_MOB_MULT = 0.1;

    /** 怪物倍率允许上限 */
    public static final double MAX_MOB_MULT = 20.0;

    private PhaseAttrLimits() {
    }

    /** 将玩家属性倍率钳制到允许范围 */
    public static double clampPlayer(double v) {
        return Math.max(MIN_PLAYER_MULT, Math.min(MAX_PLAYER_MULT, v));
    }

    /** 将怪物倍率钳制到允许范围 */
    public static double clampMob(double v) {
        return Math.max(MIN_MOB_MULT, Math.min(MAX_MOB_MULT, v));
    }

    /** 通用倍率钳制（默认区间） */
    public static double clamp(double v) {
        return Math.max(MIN_MULT, Math.min(MAX_MULT, v));
    }

    /** UI 说明文字（中文） */
    public static String playerLimitHint() {
        return "玩家属性范围 " + (int) (MIN_PLAYER_MULT * 100) + "% ~ " + (int) (MAX_PLAYER_MULT * 100) + "%";
    }

    /** UI 说明文字（中文） */
    public static String mobLimitHint() {
        return "怪物倍率范围 " + (int) (MIN_MOB_MULT * 100) + "% ~ " + (int) (MAX_MOB_MULT * 100) + "%";
    }
}
