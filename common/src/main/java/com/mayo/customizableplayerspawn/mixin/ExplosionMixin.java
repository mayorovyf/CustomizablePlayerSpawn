package com.mayo.customizableplayerspawn.mixin;

import com.mayo.customizableplayerspawn.protection.StructureProtectionService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Explosion.class)
public abstract class ExplosionMixin {
    @Shadow
    @Final
    private Level level;

    @Inject(method = "finalizeExplosion", at = @At("HEAD"))
    private void customizablePlayerSpawn$protectExplosion(boolean spawnParticles, CallbackInfo callbackInfo) {
        if (level instanceof ServerLevel serverLevel) {
            ((Explosion) (Object) this).getToBlow().removeIf(pos -> !StructureProtectionService.canExplosionAffect(serverLevel, pos));
        }
    }
}
