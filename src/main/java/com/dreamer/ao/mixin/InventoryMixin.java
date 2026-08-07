package com.dreamer.ao.mixin;

import com.dreamer.ao.achievement.event.ServerEventHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin 注入 {@link Inventory#add(ItemStack)} 以检测物品拾取事件。
 * <p>
 * 替代旧的指纹轮询方案，使用纯事件驱动方式在物品添加到背包时触发
 * {@link com.dreamer.ao.data.DataStore.ConditionType#GET_ITEM} 条件检查。
 *
 * <h2>实现原理</h2>
 * <ol>
 *   <li>{@code @Inject HEAD}：在 add() 执行前捕获原始物品堆（数量 + 快照）</li>
 *   <li>{@code @Inject RETURN}：比较剩余数量，计算实际加入量，触发条件评估</li>
 * </ol>
 *
 * <h2>线程安全</h2>
 * 使用 {@link ThreadLocal} 存储捕获数据（HEAD 和 RETURN 保证在同一线程执行）。
 * try-finally 确保异常路径下 ThreadLocal 也被清理，防止线程池复用时数据残留。
 * <p>
 * 仅在服务端生效（检查 {@code instanceof ServerPlayer}）。
 */
@Mixin(Inventory.class)
public abstract class InventoryMixin {

    @Unique
    private static final ThreadLocal<Integer> advancementoverhaul$capturedCount = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<ItemStack> advancementoverhaul$capturedStack = new ThreadLocal<>();

    /**
     * HEAD 注入：在 add() 执行前捕获物品数量与快照。
     * 先清理残留数据，再保存当前状态。
     */
    @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"))
    private void advancementoverhaul$onAddHead(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        advancementoverhaul$capturedCount.remove();
        advancementoverhaul$capturedStack.remove();
        if (!stack.isEmpty()) {
            advancementoverhaul$capturedCount.set(stack.getCount());
            // 保存快照（add() 会清空传入的 stack）
            advancementoverhaul$capturedStack.set(stack.copy());
        }
    }

    /**
     * RETURN 注入：计算实际添加量并触发条件评估。
     * try-finally 确保 ThreadLocal 在异常路径下也被清理。
     */
    @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"))
    private void advancementoverhaul$onAddReturn(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Integer prevCount = null;
        ItemStack saved = null;
        try {
            prevCount = advancementoverhaul$capturedCount.get();
            saved = advancementoverhaul$capturedStack.get();
            if (prevCount == null || saved == null) return;

            int added = prevCount - stack.getCount();
            if (added <= 0) return;

            Inventory self = (Inventory) (Object) this;
            if (self.player instanceof ServerPlayer serverPlayer) {
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(saved.getItem());
                saved.setCount(added);
                ServerEventHandler.onInventoryItemAdded(serverPlayer, itemId.toString(), saved, added);
            }
        } finally {
            // 确保 ThreadLocal 被清理（即使上方代码抛出异常）
            advancementoverhaul$capturedCount.remove();
            advancementoverhaul$capturedStack.remove();
        }
    }
}
