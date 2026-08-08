package com.dreamer.ao.phase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 阶段效果计算器。
 * <p>
 * 最终效果 = 全局（世界）效果 + 维度效果 + 玩家效果 三层叠加。
 * 叠加规则：
 * <ul>
 *   <li>A 类玩家属性：乘算（各层值相乘，1.0 为无影响）</li>
 *   <li>B 类怪物倍率：乘算</li>
 *   <li>D 类状态效果：合并（同 id 取最高等级/最长时长）</li>
 *   <li>怪物装备规则：合并所有层</li>
 * </ul>
 */
public final class PhaseEffectCalculator {

    private PhaseEffectCalculator() {
    }

    /** 三层合并结果 */
    public record ComputedEffects(
            Map<String, Double> attributes,
            Map<String, Double> mobMults,
            Map<String, PhaseEffectSet.MobEffectSpec> mobEffects,
            List<PhaseEffectSet.MobEquipmentRule> equipmentRules
    ) {
    }

    /**
     * 合并多层效果。参数顺序无所谓（均为乘算/合并），调用方按 全局→维度→玩家 顺序传入即可。
     */
    public static ComputedEffects compute(PhaseEffectSet... layers) {
        Map<String, Double> attrs = new LinkedHashMap<>();
        Map<String, Double> mults = new LinkedHashMap<>();
        Map<String, PhaseEffectSet.MobEffectSpec> effects = new LinkedHashMap<>();
        List<PhaseEffectSet.MobEquipmentRule> equip = new ArrayList<>();

        for (PhaseEffectSet set : layers) {
            if (set == null) {
                continue;
            }
            for (Map.Entry<String, Double> e : set.getAttributes().entrySet()) {
                attrs.merge(e.getKey(), e.getValue(), (a, b) -> a * b);
            }
            for (Map.Entry<String, Double> e : set.getMobMults().entrySet()) {
                mults.merge(e.getKey(), e.getValue(), (a, b) -> a * b);
            }
            for (Map.Entry<String, PhaseEffectSet.MobEffectSpec> e : set.getMobEffects().entrySet()) {
                effects.merge(e.getKey(), e.getValue(), PhaseEffectCalculator::mergeEffect);
            }
            equip.addAll(set.getEquipmentRules());
        }
        return new ComputedEffects(attrs, mults, effects, equip);
    }

    private static PhaseEffectSet.MobEffectSpec mergeEffect(
            PhaseEffectSet.MobEffectSpec a, PhaseEffectSet.MobEffectSpec b) {
        int level = Math.max(a.level(), b.level());
        int seconds = Math.max(a.seconds(), b.seconds());
        return new PhaseEffectSet.MobEffectSpec(a.id(), level, seconds);
    }
}
