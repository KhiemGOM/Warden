package com.warden.mixin;

import com.warden.WardenMod;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BowItem.class)
public abstract class BowItemMixin {

    @Redirect(
            method = "onStoppedUsing",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/BowItem;getPullProgress(I)F")
    )
    private float warden$useConfiguredPullProgress(int useTicks, ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        return WardenMod.getConfiguredBowPullProgress(stack, world.isClient(), useTicks);
    }
}
