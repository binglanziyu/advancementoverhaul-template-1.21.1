package com.dreamer.ao.phase;

import com.dreamer.ao.ModInfo;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 阶段效果应用器。
 * <p>
 * 将 {@link PhaseEffectCalculator.ComputedEffects} 真实作用到游戏：
 * <ul>
 *   <li>A 类玩家属性：通过 {@link AttributeModifier}（固定 id，apply 前先 remove 同 id 防重复）</li>
 *   <li>B 类怪物倍率 + 装备：订阅 {@link EntityJoinLevelEvent}，对生成怪物按维度作用域施加</li>
 *   <li>D 类状态效果：用 {@link net.minecraft.world.effect.MobEffectInstance} 刷新（先清后加）</li>
 * </ul>
 */
public final class PhaseEffectApplier {

    private static final Logger LOGGER = LoggerFactory.getLogger(PhaseEffectApplier.class);

    /** 稳定 modifier id，避免重复叠加 */
    private static final ResourceLocation PHASE_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(ModInfo.MOD_ID, "phase_player");
    private static final String MOB_MODIFIER_PREFIX = "phase_mob_";

    /** A 类属性 key -> Attribute 映射 */
    private static final Map<String, Holder<Attribute>> ATTR_MAP = Map.of(
            "max_health", Attributes.MAX_HEALTH,
            "armor", Attributes.ARMOR,
            "armor_toughness", Attributes.ARMOR_TOUGHNESS,
            "knockback_resistance", Attributes.KNOCKBACK_RESISTANCE,
            "movement_speed", Attributes.MOVEMENT_SPEED,
            "attack_damage", Attributes.ATTACK_DAMAGE,
            "attack_speed", Attributes.ATTACK_SPEED,
            "luck", Attributes.LUCK,
            "scale", Attributes.SCALE,
            "damage_taken", net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.wrapAsHolder(AoAttributes.DAMAGE_TAKEN.get())
    );

    private static PhaseEffectApplier INSTANCE;
    /** 维度 id -> 该维度生效的怪物倍率 + 装备规则（由解锁阶段计算填充） */
    private volatile PhaseEffectCalculator.ComputedEffects dimensionEffects = empty();

    private PhaseEffectApplier() {
    }

    public static PhaseEffectApplier get() {
        if (INSTANCE == null) {
            INSTANCE = new PhaseEffectApplier();
            NeoForge.EVENT_BUS.register(INSTANCE);
        }
        return INSTANCE;
    }

    private static PhaseEffectCalculator.ComputedEffects empty() {
        return new PhaseEffectCalculator.ComputedEffects(
                Map.of(), Map.of(), Map.of(), List.of());
    }

    /** 由阶段系统更新维度级怪物效果（全局+维度两层合并） */
    public void setDimensionEffects(PhaseEffectCalculator.ComputedEffects effects) {
        this.dimensionEffects = effects != null ? effects : empty();
    }

    /** 应用到玩家（属性 + 状态效果） */
    public void applyToPlayer(Player player, PhaseEffectCalculator.ComputedEffects effects) {
        if (player == null || effects == null) {
            return;
        }
        // A 类属性
        for (Map.Entry<String, Holder<Attribute>> entry : ATTR_MAP.entrySet()) {
            AttributeInstance inst = player.getAttribute(entry.getValue());
            if (inst == null) {
                continue;
            }
            // 先移除旧的阶段修饰符，保证幂等（未配置的属性也会被还原）
            inst.removeModifier(PHASE_MODIFIER_ID);
            Double value = effects.attributes().get(entry.getKey());
            if (value == null || value == 1.0) {
                continue; // 1.0 = 无影响
            }
            // 乘算：以基础值为基准的百分比乘算
            inst.addTransientModifier(new AttributeModifier(
                    PHASE_MODIFIER_ID, value - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        // D 类状态效果：先清后加
        for (var spec : effects.mobEffects().values()) {
            applyMobEffect(player, spec);
        }
    }

    private void applyMobEffect(LivingEntity entity, PhaseEffectSet.MobEffectSpec spec) {
        try {
            ResourceLocation rl = ResourceLocation.parse(spec.id());
            Holder<net.minecraft.world.effect.MobEffect> mobEffect =
                    BuiltInRegistries.MOB_EFFECT.getHolder(rl).orElse(null);
            if (mobEffect == null) {
                LOGGER.warn("阶段状态效果不存在: {}", spec.id());
                return;
            }
            entity.removeEffect(mobEffect);
            entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    mobEffect, spec.seconds() * 20, spec.level(), false, true));
        } catch (Exception ex) {
            LOGGER.warn("阶段状态效果施加失败: {}", spec.id(), ex);
        }
    }

    /** 生物加入世界事件：应用维度级怪物倍率与装备 */
    @net.neoforged.bus.api.SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        // 仅对怪物类（非玩家）应用维度怪物效果
        if (living instanceof Player) {
            return;
        }
        PhaseEffectCalculator.ComputedEffects eff = dimensionEffects;
        if (eff == null) {
            return;
        }
        // 怪物属性倍率
        applyMobMult(living, eff.mobMults());
        // 怪物装备
        MobEquipmentRoller.tryEquip(living, eff.equipmentRules());
    }

    private void applyMobMult(LivingEntity mob, Map<String, Double> mults) {
        if (mults == null || mults.isEmpty()) {
            return;
        }
        applyOne(mob, Attributes.MAX_HEALTH, mults.get("mob_health_mult"), "health");
        applyOne(mob, Attributes.ATTACK_DAMAGE, mults.get("mob_damage_mult"), "damage");
        applyOne(mob, Attributes.MOVEMENT_SPEED, mults.get("mob_speed_mult"), "speed");
        applyOne(mob, Attributes.ARMOR, mults.get("mob_armor_mult"), "armor");
    }

    private void applyOne(LivingEntity mob, Holder<Attribute> attr, Double mult, String key) {
        if (mult == null || mult == 1.0) {
            return;
        }
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst == null) {
            return;
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                ModInfo.MOD_ID, MOB_MODIFIER_PREFIX + key);
        inst.removeModifier(id);
        inst.addTransientModifier(new AttributeModifier(
                id, mult - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        // 生命上限变化后补满，避免新生成怪物血量低于上限
        if (attr == Attributes.MAX_HEALTH) {
            mob.setHealth(mob.getMaxHealth());
        }
    }
}
