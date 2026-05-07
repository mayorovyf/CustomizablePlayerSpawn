package com.mayo.customizableplayerspawn.mixin.forge;

import com.mayo.customizableplayerspawn.protection.StructureProtectionService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireBlock.class)
public abstract class FireBlockForgeMixin {
    @Inject(method = "onPlace", at = @At("HEAD"), cancellable = true)
    private void customizablePlayerSpawn$removeProtectedFire(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState oldState,
            boolean movedByPiston,
            CallbackInfo callbackInfo
    ) {
        if (level instanceof ServerLevel serverLevel && !StructureProtectionService.canFireAffect(serverLevel, pos)) {
            level.removeBlock(pos, false);
            callbackInfo.cancel();
        }
    }

    @Inject(
            method = "tryCatchFire(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;ILnet/minecraft/util/RandomSource;ILnet/minecraft/core/Direction;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void customizablePlayerSpawn$protectTryCatchFire(
            Level level,
            BlockPos pos,
            int chance,
            RandomSource random,
            int age,
            Direction direction,
            CallbackInfo callbackInfo
    ) {
        if (level instanceof ServerLevel serverLevel && !StructureProtectionService.canFireAffect(serverLevel, pos)) {
            callbackInfo.cancel();
        }
    }
}
