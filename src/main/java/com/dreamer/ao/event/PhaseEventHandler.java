package com.dreamer.ao.event;

import com.dreamer.ao.data.PhaseConfigLoader;
import com.dreamer.ao.data.PhaseConstants;
import com.dreamer.ao.data.PhaseStore;
import com.dreamer.ao.data.model.PhaseDefinition;
import com.dreamer.ao.data.model.PhaseEquipment;
import com.dreamer.ao.data.model.PhaseEquipmentSlot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 阶段系统事件处理器。
 * <p>
 * 监听 7 个事件，根据当前阶段配置修改实体属性和装备。
 * 效果叠加模型：最终倍率 = 全局倍率 + 维度倍率 + 玩家倍率。
 * 所有属性值 clamp 到 {@link PhaseConstants} 中的绝对上限。
 */
public class PhaseEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PhaseEventHandler.class);

    /** 阶段属性修饰符 UUID（固定，避免重复添加） */
    private static final UUID PHASE_MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

    /** 已应用阶段属性的实体追踪（防止重复计算） */
    private static final Map<UUID, String> appliedPhaseCache = new HashMap<>();

    // ═══════════════ 1. 怪物生成时 — 设置属性 + 装备 ═══════════════

    @SubscribeEvent
    public static void onMobSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (mob.level().isClientSide()) return;

        String dimKey = mob.level().dimension().location().toString();
        PhaseStore store = PhaseStore.getInstance();
        double mult = store.getCombinedMultiplier(dimKey, "");
        if (mult == 1.0) return; // 无阶段效果

        applyPhaseAttributes(mob, dimKey, null);
        applyPhaseEquipment(mob, dimKey);
    }

    // ═══════════════ 2. 实体进入世界 — 补正属性 ═══════════════

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (mob.level().isClientSide()) return;

        String dimKey = mob.level().dimension().location().toString();
        applyPhaseAttributes(mob, dimKey, null);
    }

    // ═══════════════ 3. 伤害事件 — 修改伤害值 ═══════════════

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide()) return;

        // 怪物对玩家造成的伤害
        if (event.getSource().getEntity() instanceof Mob attacker
                && event.getEntity() instanceof Player) {
            String dimKey = attacker.level().dimension().location().toString();
            PhaseStore store = PhaseStore.getInstance();
            double mult = store.getCombinedMultiplier(dimKey, "generic.attack_damage");
            if (mult != 1.0) {
                float original = event.getOriginalDamage();
                float modified = (float) Math.min(original * mult, PhaseConstants.ATTACK_DAMAGE_CAP);
                event.setModifiedDamage(modified);
            }
        }

        // 玩家对怪物造成的伤害（反向：怪物护甲减免）
        if (event.getEntity() instanceof Mob mob
                && event.getSource().getEntity() instanceof Player) {
            String dimKey = mob.level().dimension().location().toString();
            PhaseStore store = PhaseStore.getInstance();
            // 怪物护甲影响：伤害减少
            double armorMult = store.getCombinedMultiplier(dimKey, "generic.armor");
            if (armorMult > 1.0) {
                float original = event.getModifiedDamage();
                // 护甲减免近似：每点护甲减少 4% 伤害
                double extraArmor = (armorMult - 1.0) * 10; // 假设基础护甲约 10
                double reduction = Math.min(0.8, extraArmor * 0.04);
                float modified = (float) (original * (1.0 - reduction));
                event.setModifiedDamage(Math.max(0, modified));
            }
        }
    }

    // ═══════════════ 4. 回血事件 — 修改回血量 ═══════════════

    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        if (event.getEntity() instanceof Mob mob) {
            String dimKey = mob.level().dimension().location().toString();
            PhaseStore store = PhaseStore.getInstance();
            // 血量倍率越高，回血量也按比例增加
            double healthMult = store.getCombinedMultiplier(dimKey, "generic.max_health");
            if (healthMult > 1.0) {
                float original = event.getAmount();
                float modified = (float) Math.min(original * healthMult, 100);
                event.setAmount(modified);
            }
        }
    }

    // ═══════════════ 5. 最大血量变更 — 修改 ═══════════════

    @SubscribeEvent
    public static void onMaxHealthChange(LivingEvent.MaxHealthChange event) {
        if (event.getEntity().level().isClientSide()) return;

        if (event.getEntity() instanceof Mob mob) {
            String dimKey = mob.level().dimension().location().toString();
            PhaseStore store = PhaseStore.getInstance();
            double mult = store.getCombinedMultiplier(dimKey, "generic.max_health");
            if (mult != 1.0) {
                float original = event.getNewMaxHealth();
                float modified = (float) Math.min(original * mult, (float) PhaseConstants.MAX_HEALTH_CAP);
                event.setNewMaxHealth(modified);
            }
        }
    }

    // ═══════════════ 6. Tick — 持续效果 ═══════════════

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        // 每 100 tick (5秒) 检查一次，避免性能开销
        if (event.getEntity().tickCount % 100 != 0) return;
        // 当前暂无持续效果需求，预留接口
    }

    // ═══════════════ 7. 暴击事件 — 修改暴击伤害 ═══════════════

    @SubscribeEvent
    public static void onCriticalHit(CriticalHitEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        if (event.getTarget() instanceof Mob mob) {
            String dimKey = mob.level().dimension().location().toString();
            PhaseStore store = PhaseStore.getInstance();
            double dmgMult = store.getCombinedMultiplier(dimKey, "generic.attack_damage");
            if (dmgMult > 1.0) {
                // 暴击伤害额外按攻击倍率增加
                float original = event.getDamageModifier();
                float modified = (float) Math.min(original * (1.0 + (dmgMult - 1.0) * 0.3), 5.0f);
                event.setDamageModifier(modified);
            }
        }
    }

    // ═══════════════ 属性应用工具方法 ═══════════════

    /**
     * 对实体应用阶段属性修饰符。
     * 通过 AttributeModifier 以 MULTIPLY_BASE 模式叠加。
     */
    private static void applyPhaseAttributes(Mob mob, String dimKey, UUID playerUuid) {
        PhaseStore store = PhaseStore.getInstance();

        Map<String, net.minecraft.world.entity.ai.attributes.Attribute> attrMap = Map.of(
                "generic.max_health", Attributes.MAX_HEALTH,
                "generic.attack_damage", Attributes.ATTACK_DAMAGE,
                "generic.armor", Attributes.ARMOR,
                "generic.armor_toughness", Attributes.ARMOR_TOUGHNESS,
                "generic.movement_speed", Attributes.MOVEMENT_SPEED,
                "generic.attack_knockback", Attributes.ATTACK_KNOCKBACK,
                "generic.knockback_resistance", Attributes.KNOCKBACK_RESISTANCE,
                "generic.attack_speed", Attributes.ATTACK_SPEED
        );

        for (var entry : attrMap.entrySet()) {
            String attrId = entry.getKey();
            net.minecraft.world.entity.ai.attributes.Attribute attr = entry.getValue();

            double mult = playerUuid != null
                    ? store.getCombinedMultiplierForPlayer(playerUuid, dimKey, attrId)
                    : store.getCombinedMultiplier(dimKey, attrId);

            if (mult == 1.0) continue;

            AttributeInstance instance = mob.getAttribute(attr);
            if (instance == null) continue;

            // 移除旧的阶段修饰符
            instance.removeModifier(PHASE_MODIFIER_UUID);

            // 计算最终值并 clamp
            double base = instance.getBaseValue();
            double newValue = base * mult;
            double cap = PhaseConstants.getCapForAttribute(attrId);
            newValue = Math.min(newValue, cap);
            double modifier = newValue - base;

            if (modifier != 0) {
                instance.addTransientModifier(new AttributeModifier(
                        PHASE_MODIFIER_UUID, "ao_phase", modifier,
                        AttributeModifier.Operation.ADDITION));
            }
        }

        // 特殊处理：SPAWN_REINFORCEMENTS（仅僵尸/骷髅等）
        if (mob.getAttribute(Attributes.SPAWN_REINFORCEMENTS) != null) {
            double mult = store.getCombinedMultiplier(dimKey, "zombie.spawn_reinforcements");
            if (mult != 1.0) {
                AttributeInstance instance = mob.getAttribute(Attributes.SPAWN_REINFORCEMENTS);
                if (instance != null) {
                    instance.removeModifier(PHASE_MODIFIER_UUID);
                    double newValue = Math.min(instance.getBaseValue() * mult,
                            PhaseConstants.SPAWN_REINFORCEMENTS_CAP);
                    double modifier = newValue - instance.getBaseValue();
                    if (modifier != 0) {
                        instance.addTransientModifier(new AttributeModifier(
                                PHASE_MODIFIER_UUID, "ao_phase_reinforcements", modifier,
                                AttributeModifier.Operation.ADDITION));
                    }
                }
            }
        }
    }

    // ═══════════════ 装备应用工具方法 ═══════════════

    /**
     * 对怪物应用阶段装备配置。
     * 遍历所有装备槽位，从阶段配置中加权随机选择装备。
     */
    private static void applyPhaseEquipment(Mob mob, String dimKey) {
        PhaseStore store = PhaseStore.getInstance();

        // 收集全局 + 维度的装备配置（维度优先）
        PhaseDefinition globalPhase = store.getGlobalPhase();
        PhaseDefinition dimPhase = store.getDimensionPhase(dimKey);

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            PhaseEquipmentSlot slotConfig = null;

            // 维度阶段优先
            if (dimPhase != null) {
                slotConfig = dimPhase.getEquipmentSlot(slot);
            }
            // 全局阶段补充
            if (slotConfig == null && globalPhase != null) {
                slotConfig = globalPhase.getEquipmentSlot(slot);
            }

            if (slotConfig == null || slotConfig.isEmpty()) continue;

            PhaseEquipment chosen = slotConfig.roll();
            if (chosen == null) continue;

            // 按概率判定是否穿戴
            if (ThreadLocalRandom.current().nextFloat() < chosen.probability()) {
                mob.setItemSlot(slot, chosen.item().copy());
                mob.setDropChance(slot, chosen.dropChance());
            }
        }
    }
}
