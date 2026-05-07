package com.mayo.customizableplayerspawn;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public record SpawnStructureInstance(
        String id,
        UUID ownerUuid,
        String dimensionId,
        BlockPos origin,
        BlockPos spawnPos,
        float spawnAngle,
        SpawnStructureBounds bounds,
        long createdAt,
        String profileName
) {
    public static SpawnStructureInstance create(
            String id,
            UUID ownerUuid,
            ResourceKey<Level> dimension,
            BlockPos origin,
            BlockPos spawnPos,
            float spawnAngle,
            SpawnStructureBounds bounds,
            String profileName
    ) {
        return new SpawnStructureInstance(
                id,
                ownerUuid,
                dimension.location().toString(),
                origin.immutable(),
                spawnPos.immutable(),
                spawnAngle,
                bounds,
                System.currentTimeMillis(),
                profileName
        );
    }

    public boolean isInDimension(ResourceKey<Level> dimension) {
        return dimension.location().toString().equals(dimensionId);
    }

    public Optional<ResolvedSpawn> resolve(MinecraftServer server) {
        ResourceLocation dimensionLocation = ResourceLocation.tryParse(dimensionId);
        if (dimensionLocation == null) {
            return Optional.empty();
        }

        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionLocation));
        if (level == null) {
            return Optional.empty();
        }

        return Optional.of(new ResolvedSpawn(this, level, spawnPos, spawnAngle, origin));
    }

    public record ResolvedSpawn(
            SpawnStructureInstance instance,
            ServerLevel level,
            BlockPos spawnPos,
            float spawnAngle,
            BlockPos structureOrigin
    ) {
        public Vec3 teleportPosition() {
            return Vec3.atBottomCenterOf(spawnPos);
        }
    }
}
