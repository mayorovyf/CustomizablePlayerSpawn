package com.mayo.customizableplayerspawn.protection;

import com.mayo.customizableplayerspawn.SpawnProfile;
import com.mayo.customizableplayerspawn.SpawnProfiles;
import com.mayo.customizableplayerspawn.SpawnStructureInstance;
import com.mayo.customizableplayerspawn.SpawnStructureSavedData;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class StructureProtectionService {
    private StructureProtectionService() {
    }

    public static boolean canBreakBlock(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SpawnProfile profile = SpawnProfiles.active();
        if (!profile.protection().enabled() || !profile.protection().protectBlocks()) {
            return true;
        }

        return findProtectedInstance(level, pos, profile).map(instance -> canBypass(player, instance, profile)).orElse(true);
    }

    public static boolean canPlaceBlock(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SpawnProfile profile = SpawnProfiles.active();
        if (!profile.protection().enabled() || !profile.protection().protectBlockPlacement()) {
            return true;
        }

        return findProtectedInstance(level, pos, profile).map(instance -> canBypass(player, instance, profile)).orElse(true);
    }

    public static boolean canUseBlock(ServerPlayer player, ServerLevel level, BlockPos pos) {
        SpawnProfile profile = SpawnProfiles.active();
        if (!profile.protection().enabled() || !profile.protection().protectContainers()) {
            return true;
        }

        if (level.getBlockEntity(pos) == null) {
            return true;
        }

        return findProtectedInstance(level, pos, profile).map(instance -> canBypass(player, instance, profile)).orElse(true);
    }

    public static boolean canExplosionAffect(ServerLevel level, BlockPos pos) {
        SpawnProfile profile = SpawnProfiles.active();
        if (!profile.protection().enabled() || !profile.protection().protectExplosions()) {
            return true;
        }

        return findProtectedInstance(level, pos, profile).isEmpty();
    }

    public static boolean canFluidAffect(ServerLevel level, BlockPos pos) {
        SpawnProfile profile = SpawnProfiles.active();
        if (!profile.protection().enabled() || !profile.protection().protectFluids()) {
            return true;
        }

        return findProtectedInstance(level, pos, profile).isEmpty();
    }

    public static boolean canFireAffect(ServerLevel level, BlockPos pos) {
        SpawnProfile profile = SpawnProfiles.active();
        if (!profile.protection().enabled() || !profile.protection().protectFire()) {
            return true;
        }

        return findProtectedInstance(level, pos, profile).isEmpty();
    }

    public static Optional<SpawnStructureInstance> findProtectedInstance(ServerLevel level, BlockPos pos) {
        return findProtectedInstance(level, pos, SpawnProfiles.active());
    }

    private static Optional<SpawnStructureInstance> findProtectedInstance(ServerLevel level, BlockPos pos, SpawnProfile profile) {
        int padding = Math.max(0, profile.protection().padding());
        return SpawnStructureSavedData.get(level.getServer()).protectedInstances(level.dimension(), pos, padding).stream().findFirst();
    }

    private static boolean canBypass(ServerPlayer player, SpawnStructureInstance instance, SpawnProfile profile) {
        if (player == null) {
            return false;
        }

        if (profile.protection().allowOps() && player.hasPermissions(2)) {
            return true;
        }

        return profile.protection().allowOwner()
                && instance.ownerUuid() != null
                && instance.ownerUuid().equals(player.getUUID());
    }
}
