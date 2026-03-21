package com.warden.mixin;

import com.warden.WardenMod;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CrossbowItem.class)
public abstract class CrossbowItemMixin {

    @Inject(method = "getPullTime", at = @At("HEAD"), cancellable = true)
    private static void warden$useConfiguredPullTime(ItemStack stack, LivingEntity user, CallbackInfoReturnable<Integer> cir) {
        int configuredTicks = WardenMod.getConfiguredRangedUseTicks(stack, user.getEntityWorld().isClient(), -1);
        if (configuredTicks > 0) {
            cir.setReturnValue(configuredTicks);
        }
    }

    @Inject(method = "createArrowEntity", at = @At("RETURN"))
    private void warden$applyConfiguredProjectileDamage(
            World world,
            LivingEntity shooter,
            ItemStack weaponStack,
            ItemStack projectileStack,
            boolean critical,
            CallbackInfoReturnable<ProjectileEntity> cir
    ) {
        if (cir.getReturnValue() instanceof PersistentProjectileEntity persistentProjectile) {
            WardenMod.applyConfiguredProjectileDamage(persistentProjectile, weaponStack);
        }
    }
}
