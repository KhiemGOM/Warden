package com.serverlimits.mixin;

import com.serverlimits.ServerLimitsMod;
import com.serverlimits.config.ServerLimitsConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.world.World;
import net.minecraft.world.explosion.ExplosionBehavior;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts World#createExplosion at HEAD, caps the power based on entity type,
 * then re-invokes with the capped value and cancels the original call.
 * A ThreadLocal re-entry guard prevents infinite recursion.
 */
@Mixin(World.class)
public abstract class ExplosionMixin {

    private static final ThreadLocal<Boolean> APPLYING = ThreadLocal.withInitial(() -> false);

    @Inject(
        method = "createExplosion(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/damage/DamageSource;Lnet/minecraft/world/explosion/ExplosionBehavior;DDDFZLnet/minecraft/world/World$ExplosionSourceType;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void limitExplosion(
        @Nullable Entity entity,
        @Nullable DamageSource source,
        @Nullable ExplosionBehavior behavior,
        double x, double y, double z,
        float power,
        boolean createFire,
        World.ExplosionSourceType sourceType,
        CallbackInfo ci
    ) {
        if (APPLYING.get()) return;
        if (!ServerLimitsMod.CONFIG.explosionLimitsEnabled) return;

        String sourceKey = resolveSourceKey(entity);
        if (sourceKey == null) return;

        ServerLimitsConfig.ExplosionSourceConfig srcCfg =
                ServerLimitsMod.CONFIG.explosionSources.get(sourceKey);
        if (srcCfg == null || !srcCfg.enabled) return;

        float capped = Math.min(power, srcCfg.maxPower);
        if (capped >= power) return;

        ServerLimitsMod.LOGGER.debug(
            "[ServerLimits] Capped {} explosion power {} -> {}", sourceKey, power, capped);

        APPLYING.set(true);
        try {
            World world = (World)(Object)this;
            world.createExplosion(entity, source, behavior, x, y, z, capped, createFire, sourceType);
            ci.cancel();
        } finally {
            APPLYING.set(false);
        }
    }

    private static @Nullable String resolveSourceKey(@Nullable Entity entity) {
        if (entity == null)                      return "bed";
        if (entity instanceof TntEntity)         return "tnt";
        if (entity instanceof TntMinecartEntity) return "tnt_minecart";
        if (entity instanceof CreeperEntity)     return "creeper";
        String name = entity.getClass().getSimpleName();
        if (name.equals("EndCrystalEntity"))     return "end_crystal";
        if (name.equals("FireballEntity"))       return "ghast";
        return null;
    }
}
