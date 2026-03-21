package com.warden.mixin;

import com.warden.WardenMod;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TridentItem;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(TridentItem.class)
public abstract class TridentItemMixin {

    @ModifyConstant(method = "onStoppedUsing", constant = @Constant(intValue = 10))
    private int warden$useConfiguredMinDrawDuration(int vanillaTicks, ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        return WardenMod.getConfiguredTridentUseTicks(stack, world.isClient(), vanillaTicks);
    }
}
