package com.dreamer.ao.data;

import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 阶段系统属性上限常量。
 * <p>
 * 所有效果倍率计算后的最终值会被 clamp 到这些上限，防止超限造成不可预估的后果。
 * 上限为绝对值，不是相对倍率。
 */
public final class PhaseConstants {

    private PhaseConstants() {}

    /** 最大生命值上限（血条显示极限） */
    public static final double MAX_HEALTH_CAP = 1024.0;

    /** 攻击伤害上限 */
    public static final double ATTACK_DAMAGE_CAP = 50.0;

    /** 护甲值上限 */
    public static final double ARMOR_CAP = 30.0;

    /** 护甲韧性上限 */
    public static final double ARMOR_TOUGHNESS_CAP = 20.0;

    /** 移动速度上限 */
    public static final double MOVEMENT_SPEED_CAP = 1.3;

    /** 攻击击退上限 */
    public static final double ATTACK_KNOCKBACK_CAP = 10.0;

    /** 击退抗性上限（1.0 = 完全免疫） */
    public static final double KNOCKBACK_RESISTANCE_CAP = 0.95;

    /** 攻击速度上限 */
    public static final double ATTACK_SPEED_CAP = 6.0;

    /** 增援概率上限 */
    public static final double SPAWN_REINFORCEMENTS_CAP = 0.5;

    /**
     * 根据属性获取对应的上限值。
     */
    public static double getCapForAttribute(String attributeId) {
        return switch (attributeId) {
            case "generic.max_health"         -> MAX_HEALTH_CAP;
            case "generic.attack_damage"      -> ATTACK_DAMAGE_CAP;
            case "generic.armor"              -> ARMOR_CAP;
            case "generic.armor_toughness"    -> ARMOR_TOUGHNESS_CAP;
            case "generic.movement_speed"     -> MOVEMENT_SPEED_CAP;
            case "generic.attack_knockback"   -> ATTACK_KNOCKBACK_CAP;
            case "generic.knockback_resistance" -> KNOCKBACK_RESISTANCE_CAP;
            case "generic.attack_speed"       -> ATTACK_SPEED_CAP;
            case "zombie.spawn_reinforcements" -> SPAWN_REINFORCEMENTS_CAP;
            default -> Double.MAX_VALUE;
        };
    }
}
