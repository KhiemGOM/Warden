package com.warden.mixin;

import com.warden.WardenMod;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PersistentProjectileEntity.class)
public abstract class PersistentProjectileEntityMixin {

    @Unique
    private boolean warden$damageApplied;

    @Inject(method = "tick", at = @At("HEAD"))
    private void warden$applyProjectileDamage(CallbackInfo ci) {
        if (!warden$damageApplied) {
            warden$damageApplied = WardenMod.enforceProjectileDamage((PersistentProjectileEntity) (Object) this);
        }
    }
}
