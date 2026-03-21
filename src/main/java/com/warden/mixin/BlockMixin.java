package com.warden.mixin;

import com.warden.WardenMod;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class BlockMixin {

    @Inject(method = "dropExperience", at = @At("HEAD"))
    private void warden$trackBlockMiningXp(ServerWorld world, BlockPos pos, int size, CallbackInfo ci) {
        WardenMod.XP_SOURCE.set("blocksMining");
        WardenMod.XP_CONTEXT.set(Registries.BLOCK.getId((Block)(Object)this).toString());
    }

    @ModifyVariable(method = "dropExperience", at = @At("HEAD"), argsOnly = true)
    private int warden$limitBlockMiningXp(int size) {
        return WardenMod.limitExperienceGain(null, size);
    }

    @Inject(method = "dropExperience", at = @At("TAIL"))
    private void warden$clearBlockMiningXp(ServerWorld world, BlockPos pos, int size, CallbackInfo ci) {
        WardenMod.XP_SOURCE.set("unknown");
        WardenMod.XP_CONTEXT.set("");
    }
}
