package com.example.advancementoverhaul.mixin;

import com.example.advancementoverhaul.event.ServerEventHandler;
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
 * Mixin into Inventory.add(ItemStack) to detect item additions in a pure
 * event-driven manner, replacing the old fingerprint-based polling approach.
 * <p>
 * Only fires on the server side (checks instanceof ServerPlayer).
 */
@Mixin(Inventory.class)
public abstract class InventoryMixin {

    @Unique
    private static final ThreadLocal<Integer> advancementoverhaul$capturedCount = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<ItemStack> advancementoverhaul$capturedStack = new ThreadLocal<>();

    @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"))
    private void advancementoverhaul$onAddHead(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        advancementoverhaul$capturedCount.remove();
        advancementoverhaul$capturedStack.remove();
        if (!stack.isEmpty()) {
            advancementoverhaul$capturedCount.set(stack.getCount());
            // Save snapshot before add() clears the stack
            advancementoverhaul$capturedStack.set(stack.copy());
        }
    }

    @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"))
    private void advancementoverhaul$onAddReturn(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Integer prevCount = advancementoverhaul$capturedCount.get();
        ItemStack saved = advancementoverhaul$capturedStack.get();
        advancementoverhaul$capturedCount.remove();
        advancementoverhaul$capturedStack.remove();
        if (prevCount == null || saved == null) return;

        int added = prevCount - stack.getCount();
        if (added <= 0) return;

        Inventory self = (Inventory) (Object) this;
        if (self.player instanceof ServerPlayer serverPlayer) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(saved.getItem());
            saved.setCount(added);
            ServerEventHandler.onInventoryItemAdded(serverPlayer, itemId.toString(), saved, added);
        }
    }
}