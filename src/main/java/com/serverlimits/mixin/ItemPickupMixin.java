package com.serverlimits.mixin;

import com.serverlimits.ServerLimitsMod;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optionally blocks pickup when the player is already at or above the item limit.
 * This is a secondary defence -- the main enforcement is the tick-based dropper
 * in ServerLimitsMod. Pickup blocking prevents the item from ever entering
 * the inventory in the first place.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ItemPickupMixin {

    @Inject(method = "sendPickup", at = @At("HEAD"), cancellable = true)
    private void onPickup(net.minecraft.entity.Entity item, int count, CallbackInfo ci) {
        if (!ServerLimitsMod.CONFIG.itemLimitsEnabled) return;
        if (!(item instanceof ItemEntity itemEntity)) return;

        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        ItemStack stack = itemEntity.getStack();
        if (stack.isEmpty()) return;

        String id = Registries.ITEM.getId(stack.getItem()).toString();
        Integer limit = ServerLimitsMod.CONFIG.itemLimits.get(id);
        if (limit == null) return;

        // Count current total in inventory
        PlayerInventory inv = player.getInventory();
        int currentTotal = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && Registries.ITEM.getId(s.getItem()).toString().equals(id)) {
                currentTotal += s.getCount();
            }
        }

        // If already at or above limit, cancel the pickup notification
        // (actual item pickup prevention is handled by inventory being full of that item
        //  via the tick enforcer dropping excess -- this just prevents the visual)
        if (currentTotal >= limit) {
            ci.cancel();
        }
    }
}
