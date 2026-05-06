package com.mayo.customizableplayerspawn.terrain;

import com.mayo.customizableplayerspawn.CustomizablePlayerSpawnMod;
import com.mayo.customizableplayerspawn.SpawnProfile;
import com.mayo.customizableplayerspawn.structure.StructurePlacementService;
import com.mayo.customizableplayerspawn.structure.StructureTemplateBounds;
import java.util.Map;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.server.level.ServerLevel;

public final class TerrainPreparer {
    private static final int SUPPORT_VOID_FOUNDATION_TOLERANCE = 3;

    public void beforePlacement(ServerLevel level, SpawnProfile profile, BlockPos structureOrigin, StructureTemplateBounds bounds) {
        if (profile.terrainPreparationMode() == SpawnProfile.TerrainPreparationMode.ISLAND) {
            prepareIslandTerrain(level, profile, structureOrigin, bounds);
        }

        if (profile.terrain().clearStructureVolume()) {
            clearStructureVolume(level, profile, structureOrigin, bounds);
        }
    }

    public void afterPlacement(ServerLevel level, SpawnProfile profile, BlockPos structureOrigin, StructureTemplateBounds bounds) {
        if (profile.terrain().fillSupportVoids()) {
            fillSupportVoids(level, profile, structureOrigin, bounds);
        }
    }

    private void prepareIslandTerrain(ServerLevel level, SpawnProfile profile, BlockPos structureOrigin, StructureTemplateBounds bounds) {
        int padding = profile.terrain().terrainPadding();
        int structureMinX = structureOrigin.getX() + bounds.minX();
        int structureMaxX = structureOrigin.getX() + bounds.maxX();
        int structureMinZ = structureOrigin.getZ() + bounds.minZ();
        int structureMaxZ = structureOrigin.getZ() + bounds.maxZ();
        int minX = structureMinX - padding;
        int maxX = structureMaxX + padding;
        int minZ = structureMinZ - padding;
        int maxZ = structureMaxZ + padding;
        int structureSurfaceY = structureOrigin.getY() + bounds.minY() - 1;
        int clearTopY = Math.min(
                level.getMaxBuildHeight() - 1,
                structureOrigin.getY() + bounds.maxY() + profile.terrain().clearVolumePadding() + 3
        );
        int preparedColumns = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                int outsideDistance = bounds.distanceToOccupiedColumn(x - structureOrigin.getX(), z - structureOrigin.getZ());
                outsideDistance = Math.max(0, outsideDistance - 2);
                OptionalInt targetSurfaceY = islandColumnSurfaceY(level, profile, x, z, outsideDistance, padding, structureSurfaceY);
                if (targetSurfaceY.isEmpty()) {
                    continue;
                }

                prepareIslandColumn(level, profile, x, z, targetSurfaceY.getAsInt(), clearTopY);
                preparedColumns++;
            }
        }

        CustomizablePlayerSpawnMod.LOGGER.info(
                "Prepared ISLAND terrain for start structure at {} with {} columns.",
                structureOrigin,
                preparedColumns
        );
    }

    private OptionalInt islandColumnSurfaceY(
            ServerLevel level,
            SpawnProfile profile,
            int x,
            int z,
            int outsideDistance,
            int padding,
            int structureSurfaceY
    ) {
        if (outsideDistance == 0) {
            return OptionalInt.of(structureSurfaceY + interiorMicroRelief(level, profile, x, z));
        }

        if (outsideDistance > padding) {
            return OptionalInt.empty();
        }

        int edgeErosion = profile.terrain().edgeNoise() ? Mth.floor(deterministicNoise(level.getSeed(), x, z, 17) * 4.0D) : 0;
        if (outsideDistance > Math.max(1, padding - edgeErosion)) {
            return OptionalInt.empty();
        }

        int falloff = Math.max(1, profile.terrain().islandEdgeFalloff());
        int maxDrop = profile.terrain().islandMaxDrop();
        double progress = Mth.clamp((double) outsideDistance / (double) falloff, 0.0D, 1.0D);
        int drop = Mth.ceil(Math.pow(progress, 1.45D) * maxDrop);
        int noiseOffset = profile.terrain().edgeNoise()
                ? Mth.floor(deterministicNoise(level.getSeed(), x, z, 31) * 3.0D) - 1
                : 0;
        int targetY = structureSurfaceY - drop + noiseOffset;
        return OptionalInt.of(Mth.clamp(targetY, level.getMinBuildHeight(), level.getMaxBuildHeight() - 2));
    }

    private int interiorMicroRelief(ServerLevel level, SpawnProfile profile, int x, int z) {
        if (!profile.terrain().edgeNoise()) {
            return 0;
        }

        double noise = deterministicNoise(level.getSeed(), x, z, 53);
        if (noise < 0.18D) {
            return -1;
        }

        if (noise > 0.93D) {
            return 1;
        }

        return 0;
    }

    private void prepareIslandColumn(ServerLevel level, SpawnProfile profile, int x, int z, int targetSurfaceY, int clearTopY) {
        int minBuildY = level.getMinBuildHeight();
        int currentSurfaceY = Mth.clamp(
                level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1,
                minBuildY,
                level.getMaxBuildHeight() - 1
        );
        int fillStartY = Math.max(minBuildY, Math.min(currentSurfaceY, targetSurfaceY) - 4);
        int flags = StructurePlacementService.worldEditFlags(profile);

        BlockState localTopBlock = resolveLocalTerrainTopBlock(level, profile, x, z);
        for (int y = fillStartY; y <= targetSurfaceY; y++) {
            level.setBlock(new BlockPos(x, y, z), islandBlockForDepth(profile, targetSurfaceY - y, localTopBlock), flags);
        }

        for (int y = targetSurfaceY + 1; y <= clearTopY; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
            }
        }
    }

    private BlockState islandBlockForDepth(SpawnProfile profile, int depth, BlockState localTopBlock) {
        if (depth == 0) {
            return localTopBlock;
        }

        if (isSandLike(localTopBlock)) {
            return depth <= 3 ? localTopBlock : Blocks.SANDSTONE.defaultBlockState();
        }

        if (depth <= 4) {
            return profile.terrainFillBlockState();
        }

        return profile.terrainCoreBlockState();
    }

    private BlockState resolveLocalTerrainTopBlock(ServerLevel level, SpawnProfile profile, int x, int z) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        if (surfaceY > level.getMinBuildHeight()) {
            BlockState localState = level.getBlockState(new BlockPos(x, surfaceY, z));
            if (isNaturalTerrainTop(localState)) {
                return localState;
            }
        }

        return profile.terrainTopBlockState();
    }

    private void clearStructureVolume(ServerLevel level, SpawnProfile profile, BlockPos structureOrigin, StructureTemplateBounds bounds) {
        int padding = profile.terrain().clearVolumePadding();
        int maxY = Math.min(level.getMaxBuildHeight() - 1, structureOrigin.getY() + bounds.maxY() + padding);
        int flags = StructurePlacementService.worldEditFlags(profile);
        int clearedBlocks = 0;

        for (Map.Entry<Long, Integer> entry : bounds.supportBottomYByColumn().entrySet()) {
            long column = entry.getKey();
            int x = structureOrigin.getX() + StructureTemplateBounds.columnX(column);
            int z = structureOrigin.getZ() + StructureTemplateBounds.columnZ(column);
            int minY = Math.max(level.getMinBuildHeight(), structureOrigin.getY() + entry.getValue());
            for (int y = minY; y <= maxY; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(pos);
                if (!state.isAir() && shouldClearPlacementBlock(level, pos, state)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
                    clearedBlocks++;
                }
            }
        }

        CustomizablePlayerSpawnMod.LOGGER.info("Cleared {} blocks from start structure placement volume at {}.", clearedBlocks, structureOrigin);
    }

    private boolean shouldClearPlacementBlock(ServerLevel level, BlockPos pos, BlockState state) {
        return state.is(BlockTags.LEAVES)
                || state.canBeReplaced()
                || !state.getFluidState().isEmpty()
                || !state.getCollisionShape(level, pos).isEmpty();
    }

    private void fillSupportVoids(ServerLevel level, SpawnProfile profile, BlockPos structureOrigin, StructureTemplateBounds bounds) {
        int maxDepth = profile.terrain().supportVoidMaxFillDepth();
        if (maxDepth <= 0 || bounds.supportBottomYByColumn().isEmpty()) {
            return;
        }

        int flags = StructurePlacementService.worldEditFlags(profile);
        int filledBlocks = 0;
        int skippedElevatedColumns = 0;
        int maxSupportedTemplateY = bounds.minY() + SUPPORT_VOID_FOUNDATION_TOLERANCE;
        for (Map.Entry<Long, Integer> entry : bounds.supportBottomYByColumn().entrySet()) {
            if (entry.getValue() > maxSupportedTemplateY) {
                skippedElevatedColumns++;
                continue;
            }

            long column = entry.getKey();
            int x = structureOrigin.getX() + StructureTemplateBounds.columnX(column);
            int z = structureOrigin.getZ() + StructureTemplateBounds.columnZ(column);
            int bottomY = structureOrigin.getY() + entry.getValue();
            BlockState fillState = supportFillState(level, profile, x, z, bottomY);

            for (int depth = 1; depth <= maxDepth; depth++) {
                int y = bottomY - depth;
                if (y < level.getMinBuildHeight()) {
                    break;
                }

                BlockPos fillPos = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(fillPos);
                if (!canFillSupportVoid(level, fillPos, state)) {
                    break;
                }

                level.setBlock(fillPos, fillState, flags);
                filledBlocks++;
            }
        }

        CustomizablePlayerSpawnMod.LOGGER.info(
                "Filled {} support void blocks under start structure at {}; skipped {} elevated columns.",
                filledBlocks,
                structureOrigin,
                skippedElevatedColumns
        );
    }

    private boolean canFillSupportVoid(ServerLevel level, BlockPos pos, BlockState state) {
        return state.isAir()
                || state.canBeReplaced()
                || !state.getFluidState().isEmpty()
                || state.getCollisionShape(level, pos).isEmpty();
    }

    private BlockState supportFillState(ServerLevel level, SpawnProfile profile, int x, int z, int bottomY) {
        int minY = Math.max(level.getMinBuildHeight(), bottomY - profile.terrain().supportVoidMaxFillDepth() - 2);
        for (int y = bottomY - 1; y >= minY; y--) {
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (canFillSupportVoid(level, new BlockPos(x, y, z), state)) {
                continue;
            }

            if (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.STONE) || state.is(Blocks.MOSS_BLOCK)) {
                return state;
            }

            if (isNaturalTerrainTop(state)) {
                return profile.terrainFillBlockState();
            }

            return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        }

        return profile.terrainFillBlockState();
    }

    private boolean isNaturalTerrainTop(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.MUD)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND)
                || state.is(Blocks.STONE)
                || state.is(Blocks.MOSS_BLOCK)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.TERRACOTTA);
    }

    private boolean isSandLike(BlockState state) {
        return state.is(Blocks.SAND) || state.is(Blocks.RED_SAND);
    }

    private double deterministicNoise(long seed, int x, int z, int salt) {
        long value = seed;
        value ^= (long) x * 0x9E3779B97F4A7C15L;
        value ^= (long) z * 0xC2B2AE3D27D4EB4FL;
        value ^= (long) salt * 0x165667B19E3779F9L;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value = value ^ (value >>> 31);
        return (double) (value & 0x1FFFFFL) / (double) 0x200000L;
    }
}
