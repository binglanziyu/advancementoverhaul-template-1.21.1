package com.dreamer.ao.phase;

import com.dreamer.ao.ModInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 本模组的自定义 Attribute 注册。
 * <p>
 * 原版 1.21.1 没有"受到伤害"相关的 Attribute，这里注册一个 {@code ao:damage_taken}：
 * <ul>
 *   <li>类型 Ranged，基础值 1.0，表示受到伤害的倍率；</li>
 *   <li>在 {@link LivingIncomingDamageEvent} 中用该 attribute 的当前值乘算传入伤害；</li>
 *   <li>通过 {@link EntityAttributeModificationEvent} 给玩家附加该属性（玩家默认无此属性）。</li>
 * </ul>
 * 这样"受到伤害增加"就可以像其他玩家属性一样，经由 {@link PhaseEffectApplier} 的 ATTR_MAP 乘算施加。
 */
public final class AoAttributes {

    private AoAttributes() {
    }

    /** 自定义属性注册器 */
    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, ModInfo.MOD_ID);

    /** 受到伤害倍率属性：1.0 = 正常，>1 受伤增加，<1 受伤减少 */
    public static final Supplier<Attribute> DAMAGE_TAKEN = ATTRIBUTES.register(
            "damage_taken",
            () -> new RangedAttribute("attribute.name.ao.damage_taken", 1.0, 0.0, 100.0)
                    .setSyncable(true));

    /** 稳定 modifier id，与 PhaseEffectApplier 保持一致 */
    public static final ResourceLocation PHASE_DAMAGE_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(ModInfo.MOD_ID, "phase_player");

    /** 取得该自定义属性的 Holder（用于 entity.add / getAttribute） */
    private static Holder<Attribute> holder() {
        return BuiltInRegistries.ATTRIBUTE.wrapAsHolder(DAMAGE_TAKEN.get());
    }

    /**
     * 注册自定义属性与事件处理器。
     * <ul>
     *   <li>{@link DeferredRegister} 与 {@link EntityAttributeModificationEvent}（ModBus 事件）
     *       必须在 mod 总线注册；</li>
     *   <li>{@link LivingIncomingDamageEvent} 是普通游戏事件，注册到 NeoForge 公共总线。</li>
     * </ul>
     * 注意：绝不能把整个类注册到 NeoForge 总线，否则 ModBus 事件也会被挂到公共总线而抛
     * {@code IllegalArgumentException: IModBusEvent events are not allowed on the common NeoForge bus}。
     */
    public static void register(net.neoforged.bus.api.IEventBus modBus) {
        ATTRIBUTES.register(modBus);
        // 仅把游戏事件注册到 NeoForge 公共总线，ModBus 事件由调用方通过 modBus 注册
        NeoForge.EVENT_BUS.addListener(AoAttributes::onLivingIncomingDamage);
    }

    /** 给玩家附加自定义属性（玩家默认无此 attribute） */
    @SubscribeEvent
    public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
        if (event.getTypes().contains(net.minecraft.world.entity.EntityType.PLAYER)) {
            event.add(net.minecraft.world.entity.EntityType.PLAYER, holder());
        }
    }

    /** 受伤事件：用自定义属性乘算伤害（仅对玩家，怪物受伤由怪物倍率另行处理） */
    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            var inst = player.getAttribute(holder());
            if (inst != null) {
                double mult = inst.getValue();
                if (mult != 1.0) {
                    event.setAmount((float) (event.getAmount() * mult));
                }
            }
        }
    }
}
