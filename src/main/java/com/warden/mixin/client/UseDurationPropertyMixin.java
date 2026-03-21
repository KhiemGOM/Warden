package com.warden.mixin.client;

import com.warden.WardenMod;
import net.minecraft.client.render.item.property.numeric.UseDurationProperty;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TridentItem;
import net.minecraft.util.HeldItemContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(UseDurationProperty.class)
public abstract class UseDurationPropertyMixin {

    @Inject(method = "getValue", at = @At("RETURN"), cancellable = true)
    private void warden$scaleHeldUseDuration(
            ItemStack stack,
            net.minecraft.client.world.ClientWorld world,
            HeldItemContext context,
            int seed,
            CallbackInfoReturnable<Float> cir
    ) {
        if (context == null) {
            return;
        }

        LivingEntity entity = context.getEntity();
        if (entity == null || entity.getActiveItem() != stack) {
            return;
        }

        float value = cir.getReturnValueF();
        if (stack.getItem() instanceof BowItem) {
            int configured = Math.max(1, WardenMod.getConfiguredRangedUseTicks(stack, true, 20));
            cir.setReturnValue(value * (20.0f / configured));
            return;
        }

        if (stack.getItem() instanceof TridentItem) {
            int configured = Math.max(1, WardenMod.getConfiguredRangedUseTicks(stack, true, 10));
            cir.setReturnValue(value * (10.0f / configured));
        }
    }
}
