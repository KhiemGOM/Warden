package com.warden.mixin;

import com.warden.WardenMod;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin_Xp {

    @Inject(method = "dropExperience", at = @At("HEAD"))
    private void warden$trackMobKillingXp(net.minecraft.server.world.ServerWorld world, net.minecraft.entity.Entity attacker, CallbackInfo ci) {
        WardenMod.XP_SOURCE.set("entitiesKilling");
        WardenMod.XP_CONTEXT.set(net.minecraft.registry.Registries.ENTITY_TYPE.getId(((LivingEntity)(Object)this).getType()).toString());
    }

    @ModifyVariable(method = "dropExperience", at = @At("HEAD"), argsOnly = true)
    private int warden$limitMobKillingXp(int experience) {
        return WardenMod.limitExperienceGain(null, experience);
    }

    @Inject(method = "dropExperience", at = @At("TAIL"))
    private void warden$clearMobKillingXp(net.minecraft.server.world.ServerWorld world, net.minecraft.entity.Entity attacker, CallbackInfo ci) {
        WardenMod.XP_SOURCE.set("unknown");
        WardenMod.XP_CONTEXT.set("");
    }
}
