package com.warden.mixin;

import com.warden.WardenMod;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blocks item pickup at the collection point when the player is already at the item limit.
 * This is the primary/fast-path defence — fires before the item enters the inventory.
 * The tick-based enforcer in WardenMod acts as fallback for other acquisition paths
 * (crafting, chests, /give, etc.).
 */
@Mixin(ItemEntity.class)
public abstract class ItemPickupMixin {

    @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
    private void onPlayerCollision(PlayerEntity player, CallbackInfo ci) {
        if (!WardenMod.CONFIG.itemLimitsEnabled) return;
        if (!(player instanceof ServerPlayerEntity)) return;

        ItemEntity itemEntity = (ItemEntity) (Object) this;
        ItemStack stack = itemEntity.getStack();
        if (stack.isEmpty()) return;

        String id = Registries.ITEM.getId(stack.getItem()).toString();
        Integer limit = WardenMod.CONFIG.itemLimits.get(id);
        if (limit == null) return;

        PlayerInventory inv = player.getInventory();
        int currentTotal = WardenMod.countItemsInInventory(inv, id);

        if (currentTotal >= limit) {
            ci.cancel();
        }
    }
}
