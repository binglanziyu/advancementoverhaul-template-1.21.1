package com.dreamer.ao.phase;

import com.dreamer.ao.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 怪物概率穿戴装备。
 * <p>
 * 在生物生成事件中，根据 {@link PhaseEffectSet.MobEquipmentRule} 按概率给怪物套装备。
 * 规则特性：
 * <ul>
 *   <li>整条规则按 {@code chance} 触发，单部位可有多个条目，每条目按自身 {@code chance} 触发；</li>
 *   <li>条目可携带附魔（附魔 id -> 等级），由 {@link EnchantmentHelper} 写入物品；</li>
 *   <li>同实体命中多条规则时，所有命中规则合并到一次 roll（部位并集、条目叠加）；</li>
 *   <li>默认仅补<b>空槽</b>（与其他 mod/原版共存）；开启 {@code overwriteOthers} 时先清空再套。</li>
 * </ul>
 * 槽位名（head/chest/legs/feet/mainhand）映射到 {@link EquipmentSlot}。
 */
public final class MobEquipmentRoller {

    private static final Logger LOGGER = LoggerFactory.getLogger(MobEquipmentRoller.class);
    private static final Random RNG = new Random();

    private MobEquipmentRoller() {
    }

    /** 幂等标记键：写入实体持久化 NBT，避免区块重载/维度切换时重复 roll 装备 */
    private static final String EQUIPPED_MARK = "adv_phase_equipped";

    /**
     * 对生成的生物尝试按规则套装备（仅一次判定）。
     * <p>
     * 多条规则对同一实体并集处理：先按整条 chance 筛选命中规则，再对命中规则所有部位做 roll。
     */
    public static void tryEquip(LivingEntity entity, List<PhaseEffectSet.MobEquipmentRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        // 幂等：已处理过的实体直接跳过（EntityJoinLevelEvent 在区块重载/维度切换时也会触发）
        var persistent = entity.getPersistentData();
        if (persistent.getBoolean(EQUIPPED_MARK)) {
            return;
        }

        boolean overwrite = Config.OVERWRITE_OTHERS.get();

        // 收集命中规则（整条 chance 通过 + 实体类型匹配）
        for (PhaseEffectSet.MobEquipmentRule rule : rules) {
            if (rule.getChance() <= 0) {
                continue;
            }
            if (rule.getEntityFilter() != null) {
                ResourceLocation filter = ResourceLocation.parse(rule.getEntityFilter());
                if (!entity.getType().equals(BuiltInRegistries.ENTITY_TYPE.get(filter))) {
                    continue;
                }
            }
            if (RNG.nextDouble() > rule.getChance()) {
                continue;
            }
            // 命中：先标记（确保整个并集只做一次），再 roll 该规则各部位
            persistent.putBoolean(EQUIPPED_MARK, true);
            if (overwrite) {
                clearSlots(entity);
            }
            for (Map.Entry<String, List<PhaseEffectSet.EquipmentEntry>> slotEntry : rule.getSlots().entrySet()) {
                EquipmentSlot es = parseSlot(slotEntry.getKey());
                if (es == null) {
                    continue;
                }
                // 默认只补空槽：若槽位已占用且非覆盖模式，跳过该部位
                if (!overwrite && !entity.getItemBySlot(es).isEmpty()) {
                    continue;
                }
                rollSlot(entity, es, slotEntry.getValue());
            }
        }
    }

    /** 对单个部位 roll：多条目各自按概率触发，命中则套该条目物品+附魔 */
    private static void rollSlot(LivingEntity entity, EquipmentSlot es, List<PhaseEffectSet.EquipmentEntry> entries) {
        for (PhaseEffectSet.EquipmentEntry entry : entries) {
            if (entry.getChance() <= 0) {
                continue;
            }
            if (RNG.nextDouble() > entry.getChance()) {
                continue;
            }
            ItemStack stack = resolveItem(entry.getItem());
            if (stack.isEmpty()) {
                continue;
            }
            applyEnchants(entity, stack, entry.getEnchants());
            entity.setItemSlot(es, stack);
            // 单部位填了第一个命中条目即可（避免同部位多件互相覆盖）
            return;
        }
    }

    private static void clearSlots(LivingEntity entity) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            entity.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    private static void applyEnchants(LivingEntity entity, ItemStack stack, Map<String, Integer> enchants) {
        if (enchants == null || enchants.isEmpty()) {
            return;
        }
        var reg = entity.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        for (Map.Entry<String, Integer> e : enchants.entrySet()) {
            try {
                ResourceLocation rl = ResourceLocation.parse(e.getKey());
                Holder<Enchantment> ench = reg.getHolder(ResourceKey.create(Registries.ENCHANTMENT, rl)).orElse(null);
                if (ench != null) {
                    stack.enchant(ench, e.getValue());
                }
            } catch (Exception ex) {
                LOGGER.warn("阶段装备附魔解析失败: {}", e.getKey(), ex);
            }
        }
    }

    private static EquipmentSlot parseSlot(String name) {
        return switch (name.toLowerCase()) {
            case "head", "helmet" -> EquipmentSlot.HEAD;
            case "chest", "chestplate" -> EquipmentSlot.CHEST;
            case "legs", "leggings" -> EquipmentSlot.LEGS;
            case "feet", "boots" -> EquipmentSlot.FEET;
            case "mainhand", "hand" -> EquipmentSlot.MAINHAND;
            case "offhand" -> EquipmentSlot.OFFHAND;
            default -> null;
        };
    }

    private static ItemStack resolveItem(String itemId) {
        try {
            ResourceLocation rl = ResourceLocation.parse(itemId);
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(item);
        } catch (Exception e) {
            LOGGER.warn("阶段装备解析失败: {}", itemId, e);
            return ItemStack.EMPTY;
        }
    }
}
