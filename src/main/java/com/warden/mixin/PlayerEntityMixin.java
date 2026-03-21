package com.warden.mixin;

import com.warden.WardenMod;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    @Shadow public abstract void addExperience(int experience);

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void warden$blockWeaponAttackOnCooldown(Entity target, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayerEntity player && WardenMod.isWeaponAttackBlockedByCooldown(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "attack", at = @At("TAIL"))
    private void warden$applyWeaponCooldown(Entity target, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayerEntity player) {
            WardenMod.applyWeaponAttackCooldown(player);
        }
    }

    @ModifyVariable(method = "addExperience", at = @At("HEAD"), argsOnly = true)
    private int warden$limitXpGain(int experience) {
        if ((Object) this instanceof ServerPlayerEntity player) {
            return WardenMod.limitExperienceGain(player, experience);
        }
        return experience;
    }
}
