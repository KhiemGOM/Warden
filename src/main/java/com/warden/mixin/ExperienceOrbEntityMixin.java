package com.warden.mixin;

import com.warden.WardenMod;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExperienceOrbEntity.class)
public abstract class ExperienceOrbEntityMixin implements ExperienceOrbEntityAccessor {

    @Unique
    private String warden$source = "unknown";
    @Unique
    private String warden$context = "";

    @Override
    public void warden$setXpSource(String source) {
        this.warden$source = source;
    }

    @Override
    public void warden$setXpContext(String context) {
        this.warden$context = context;
    }

    @Inject(method = "<init>(Lnet/minecraft/world/World;DDDI)V", at = @At("TAIL"))
    private void warden$tagOrbOnCreation(net.minecraft.world.World world, double x, double y, double z, int amount, CallbackInfo ci) {
        this.warden$source = WardenMod.XP_SOURCE.get();
        this.warden$context = WardenMod.XP_CONTEXT.get();
    }

    @Inject(method = "onPlayerCollision", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;addExperience(I)V"))
    private void warden$setXpSourceBeforeAdding(PlayerEntity player, CallbackInfo ci) {
        WardenMod.XP_SOURCE_OVERRIDE.set(warden$source);
        WardenMod.XP_CONTEXT_OVERRIDE.set(warden$context);
    }

    @Inject(method = "onPlayerCollision", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;addExperience(I)V", shift = At.Shift.AFTER))
    private void warden$clearXpSourceAfterAdding(PlayerEntity player, CallbackInfo ci) {
        WardenMod.XP_SOURCE_OVERRIDE.set(null);
        WardenMod.XP_CONTEXT_OVERRIDE.set(null);
    }
}
