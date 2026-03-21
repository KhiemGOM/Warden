package com.warden.mixin;

import com.warden.WardenMod;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RangedWeaponItem.class)
public abstract class RangedWeaponProjectileMixin {

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
