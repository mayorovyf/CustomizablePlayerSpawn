package com.mayo.customizableplayerspawn.mixin;

import com.mayo.customizableplayerspawn.protection.StructureProtectionService;
import net.minecraft.core.BlockPos;
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
public abstract class FireBlockMixin {
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

    @Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
    private void customizablePlayerSpawn$protectBurnOut(
            Level level,
            BlockPos pos,
            int chance,
            RandomSource random,
            int age,
            CallbackInfo callbackInfo
    ) {
        if (level instanceof ServerLevel serverLevel && !StructureProtectionService.canFireAffect(serverLevel, pos)) {
            callbackInfo.cancel();
        }
    }
}
