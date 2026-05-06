package com.mayo.customizableplayerspawn.placement;

import com.mayo.customizableplayerspawn.CustomizablePlayerSpawnMod;
import com.mayo.customizableplayerspawn.SpawnProfile;
import com.mayo.customizableplayerspawn.structure.StructureTemplateBounds;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public final class PlacementEngine {
    private static final int CANDIDATE_FLUID_SCAN_RADIUS = 1;

    public Optional<PlacementResult> findOrigin(ServerLevel level, SpawnProfile profile, StructureTemplateBounds bounds) {
        Set<ResourceLocation> allowedBiomes = profile.allowedBiomeIds();
        if (!profile.allowsAnyBiome() && allowedBiomes.isEmpty()) {
            CustomizablePlayerSpawnMod.LOGGER.error("The allowed biome list contains no valid biome ids.");
            return Optional.empty();
        }

        return switch (profile.placementStrategy()) {
            case AUTO -> throw new IllegalStateException("AUTO placement strategy should be resolved by SpawnProfile");
            case DIRECT -> findDirectOrigin(level, profile, bounds, allowedBiomes);
            case SURFACE -> findSurfaceOrigin(level, profile, bounds, allowedBiomes);
            case FLAT_FOOTPRINT -> findSmartOrigin(level, profile, bounds, allowedBiomes, false);
            case EMBEDDED -> findSmartOrigin(level, profile, bounds, allowedBiomes, true);
            case ISLAND -> findIslandOrigin(level, profile, bounds, allowedBiomes);
        };
    }

    private Optional<PlacementResult> findDirectOrigin(
            ServerLevel level,
            SpawnProfile profile,
            StructureTemplateBounds bounds,
            Set<ResourceLocation> allowedBiomes
    ) {
        BlockPos vanillaSpawn = level.getSharedSpawnPos();
        if (!isBiomeAllowed(level, profile, vanillaSpawn.getX(), vanillaSpawn.getZ(), allowedBiomes)) {
            return Optional.empty();
        }

        int originX = vanillaSpawn.getX() - bounds.centerX();
        int originZ = vanillaSpawn.getZ() - bounds.centerZ();
        int originY = switch (profile.surfaceSearchMode()) {
            case ABSOLUTE_Y -> clampPlacementY(level, profile.placement().absoluteY() + profile.placement().placementY() + profile.placement().surfaceYOffset() - bounds.minY());
            case HEIGHTMAP, SMART -> {
                OptionalInt y = resolveGeneratedHeightmapY(level, vanillaSpawn.getX(), vanillaSpawn.getZ());
                if (y.isEmpty()) {
                    yield clampPlacementY(level, level.getSeaLevel() + profile.placement().placementY() + profile.placement().surfaceYOffset() - bounds.minY());
                }
                yield clampPlacementY(level, y.getAsInt() + profile.placement().placementY() + profile.placement().surfaceYOffset() - bounds.minY());
            }
        };
        BlockPos origin = new BlockPos(originX, originY, originZ);
        if (!fitsBuildHeight(level, origin, bounds.templateSize())) {
            return Optional.empty();
        }

        return Optional.of(new PlacementResult(origin, 0, "DIRECT", "near vanilla spawn"));
    }

    private Optional<PlacementResult> findSurfaceOrigin(
            ServerLevel level,
            SpawnProfile profile,
            StructureTemplateBounds bounds,
            Set<ResourceLocation> allowedBiomes
    ) {
        int searchRadius = profile.placement().searchRadius();
        int searchAttempts = profile.placement().searchAttempts();
        BlockPos vanillaSpawn = level.getSharedSpawnPos();
        int centerChunkX = vanillaSpawn.getX() >> 4;
        int centerChunkZ = vanillaSpawn.getZ() >> 4;
        int chunkRadius = Math.max(0, Mth.ceil((float) searchRadius / 16.0F));
        int attempted = 0;

        for (int radius = 0; radius <= chunkRadius && attempted < searchAttempts; radius++) {
            for (int dz = -radius; dz <= radius && attempted < searchAttempts; dz++) {
                for (int dx = -radius; dx <= radius && attempted < searchAttempts; dx++) {
                    if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }

                    attempted++;
                    int centerX = (centerChunkX + dx) * 16 + 8;
                    int centerZ = (centerChunkZ + dz) * 16 + 8;
                    int originX = centerX - bounds.centerX();
                    int originZ = centerZ - bounds.centerZ();

                    if (!isBiomeAllowed(level, profile, centerX, centerZ, allowedBiomes)) {
                        continue;
                    }

                    Optional<HeightmapFootprint> footprint = analyzeHeightmapFootprint(
                            level,
                            profile,
                            originX + bounds.minX(),
                            originZ + bounds.minZ(),
                            bounds.footprintSize()
                    );
                    if (footprint.isEmpty()) {
                        continue;
                    }

                    int baseY = footprint.get().originY() + profile.placement().placementY() + profile.placement().surfaceYOffset() - bounds.minY();
                    int originY = clampPlacementY(level, baseY);
                    BlockPos origin = new BlockPos(originX, originY, originZ);
                    if (!fitsBuildHeight(level, origin, bounds.templateSize())) {
                        continue;
                    }

                    return Optional.of(new PlacementResult(origin, -footprint.get().surfaceStep(), "SURFACE", "heightmap footprint"));
                }
            }
        }

        return findDirectOrigin(level, profile, bounds, allowedBiomes)
                .map(result -> new PlacementResult(result.origin(), result.score(), "SURFACE", "fallback to direct"));
    }

    private Optional<PlacementResult> findIslandOrigin(
            ServerLevel level,
            SpawnProfile profile,
            StructureTemplateBounds bounds,
            Set<ResourceLocation> allowedBiomes
    ) {
        Optional<PlacementResult> dryOrigin = findIslandOrigin(level, profile, bounds, allowedBiomes, true);
        return dryOrigin.isPresent() ? dryOrigin : findIslandOrigin(level, profile, bounds, allowedBiomes, false);
    }

    private Optional<PlacementResult> findIslandOrigin(
            ServerLevel level,
            SpawnProfile profile,
            StructureTemplateBounds bounds,
            Set<ResourceLocation> allowedBiomes,
            boolean requireDryAnchor
    ) {
        int searchRadius = profile.placement().searchRadius();
        int searchAttempts = profile.placement().searchAttempts();
        BlockPos vanillaSpawn = level.getSharedSpawnPos();
        int centerChunkX = vanillaSpawn.getX() >> 4;
        int centerChunkZ = vanillaSpawn.getZ() >> 4;
        int chunkRadius = Math.max(0, Mth.ceil((float) searchRadius / 16.0F));
        int attempted = 0;

        for (int radius = 0; radius <= chunkRadius && attempted < searchAttempts; radius++) {
            for (int dz = -radius; dz <= radius && attempted < searchAttempts; dz++) {
                for (int dx = -radius; dx <= radius && attempted < searchAttempts; dx++) {
                    if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }

                    attempted++;
                    int centerX = (centerChunkX + dx) * 16 + 8;
                    int centerZ = (centerChunkZ + dz) * 16 + 8;
                    int originX = centerX - bounds.centerX();
                    int originZ = centerZ - bounds.centerZ();

                    if (!isBiomeAllowed(level, profile, centerX, centerZ, allowedBiomes)) {
                        continue;
                    }

                    OptionalInt surfaceY = resolveIslandAnchorSurfaceY(level, centerX, centerZ, requireDryAnchor);
                    if (surfaceY.isEmpty()) {
                        continue;
                    }

                    int originY = clampPlacementY(level, surfaceY.getAsInt() + 1 + profile.placement().placementY() + profile.placement().surfaceYOffset() - bounds.minY());
                    BlockPos origin = new BlockPos(originX, originY, originZ);
                    if (!fitsBuildHeight(level, origin, bounds.templateSize())) {
                        continue;
                    }

                    return Optional.of(new PlacementResult(origin, 0, "ISLAND", "dryRequired=" + requireDryAnchor));
                }
            }
        }

        return Optional.empty();
    }

    private Optional<PlacementResult> findSmartOrigin(
            ServerLevel level,
            SpawnProfile profile,
            StructureTemplateBounds bounds,
            Set<ResourceLocation> allowedBiomes,
            boolean embedded
    ) {
        int searchRadius = profile.placement().searchRadius();
        int searchAttempts = profile.placement().searchAttempts();
        int chunkRadius = Math.max(0, Mth.ceil((float) searchRadius / 16.0F));
        RandomSource random = RandomSource.create(level.getSeed() ^ (embedded ? 0x454D4244L : 0x4350534CL));
        SearchCandidate bestCandidate = null;

        for (int attempt = 0; attempt < searchAttempts; attempt++) {
            int chunkX = chunkRadius == 0 ? 0 : random.nextInt(chunkRadius * 2 + 1) - chunkRadius;
            int chunkZ = chunkRadius == 0 ? 0 : random.nextInt(chunkRadius * 2 + 1) - chunkRadius;
            int centerX = chunkX * 16 + 8;
            int centerZ = chunkZ * 16 + 8;
            int originX = centerX - bounds.centerX();
            int originZ = centerZ - bounds.centerZ();

            if (!isBiomeAllowed(level, profile, centerX, centerZ, allowedBiomes)) {
                continue;
            }

            Optional<SearchCandidate> candidate = evaluateCandidate(level, profile, originX, originZ, centerX, centerZ, bounds, embedded);
            if (candidate.isEmpty()) {
                continue;
            }

            if (bestCandidate == null || candidate.get().score() > bestCandidate.score()) {
                bestCandidate = candidate.get();
            }
        }

        if (bestCandidate == null) {
            return Optional.empty();
        }

        return Optional.of(new PlacementResult(
                bestCandidate.origin(),
                bestCandidate.score(),
                embedded ? "EMBEDDED" : "FLAT_FOOTPRINT",
                "step=%s fluids=%s intrusions=%s".formatted(
                        bestCandidate.surfaceStep(),
                        bestCandidate.nearbyFluidBlocks(),
                        bestCandidate.solidIntrusions()
                )
        ));
    }

    private Optional<SearchCandidate> evaluateCandidate(
            ServerLevel level,
            SpawnProfile profile,
            int originX,
            int originZ,
            int probeX,
            int probeZ,
            StructureTemplateBounds bounds,
            boolean embedded
    ) {
        Optional<ColumnSurface> referenceSurface = profile.surfaceSearchMode() == SpawnProfile.SurfaceSearchMode.SMART
                ? findSurfaceCandidate(level, profile, probeX, probeZ)
                : Optional.empty();
        if (profile.surfaceSearchMode() == SpawnProfile.SurfaceSearchMode.SMART && referenceSurface.isEmpty()) {
            return Optional.empty();
        }

        int originY = switch (profile.surfaceSearchMode()) {
            case SMART -> applyPlacementOffset(level, profile, referenceSurface.get().feetY()) - bounds.minY();
            case ABSOLUTE_Y -> clampPlacementY(level, profile.placement().absoluteY() + profile.placement().placementY() + profile.placement().surfaceYOffset() - bounds.minY());
            case HEIGHTMAP -> {
                OptionalInt height = resolveGeneratedHeightmapY(level, probeX, probeZ);
                if (height.isEmpty()) {
                    yield clampPlacementY(level, level.getSeaLevel() + profile.placement().placementY() + profile.placement().surfaceYOffset() - bounds.minY());
                }
                yield clampPlacementY(level, height.getAsInt() + profile.placement().placementY() + profile.placement().surfaceYOffset() - bounds.minY());
            }
        };
        originY = clampPlacementY(level, originY);
        BlockPos origin = new BlockPos(originX, originY, originZ);

        if (!fitsBuildHeight(level, origin, bounds.templateSize())) {
            return Optional.empty();
        }

        Optional<FootprintAnalysis> footprintAnalysis = analyzeFootprint(level, profile, origin, bounds.templateSize(), embedded);
        if (footprintAnalysis.isEmpty() || !footprintAnalysis.get().valid()) {
            return Optional.empty();
        }

        FootprintAnalysis analysis = footprintAnalysis.get();
        int targetY = referenceSurface.map(surface -> applyPlacementOffset(level, profile, surface.feetY())).orElse(originY);
        int score = -(Math.abs(originY - targetY) * 16 + analysis.surfaceStep() * 24 + analysis.nearbyFluidBlocks() * 8 + analysis.solidIntrusions() * 3);
        return Optional.of(new SearchCandidate(origin, score, analysis.surfaceStep(), analysis.nearbyFluidBlocks(), analysis.solidIntrusions()));
    }

    private Optional<FootprintAnalysis> analyzeFootprint(ServerLevel level, SpawnProfile profile, BlockPos origin, Vec3i footprintSize, boolean embedded) {
        int width = Math.max(1, footprintSize.getX());
        int depth = Math.max(1, footprintSize.getZ());
        int minSurfaceY = Integer.MAX_VALUE;
        int maxSurfaceY = Integer.MIN_VALUE;
        int landColumns = 0;
        int treeOverlap = 0;

        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < depth; dz++) {
                int worldX = origin.getX() + dx;
                int worldZ = origin.getZ() + dz;
                Optional<ColumnSurface> localSurface = findSurfaceCandidate(level, profile, worldX, worldZ);
                if (localSurface.isEmpty()) {
                    if (!embedded) {
                        return Optional.of(FootprintAnalysis.invalid());
                    }
                    continue;
                }

                ColumnSurface surface = localSurface.get();
                minSurfaceY = Math.min(minSurfaceY, surface.feetY());
                maxSurfaceY = Math.max(maxSurfaceY, surface.feetY());
                if (surface.floorState().getFluidState().isEmpty()) {
                    landColumns++;
                }
            }
        }

        int totalColumns = width * depth;
        double landRatio = totalColumns == 0 ? 1.0D : (double) landColumns / (double) totalColumns;
        if (landRatio < profile.placement().minLandRatio()) {
            return Optional.of(FootprintAnalysis.invalid());
        }

        int surfaceStep = maxSurfaceY == Integer.MIN_VALUE ? 0 : maxSurfaceY - minSurfaceY;
        if (!embedded && surfaceStep > profile.placement().maxSurfaceStep()) {
            return Optional.of(FootprintAnalysis.invalid());
        }

        BlockPos maxPos = origin.offset(width - 1, Math.max(0, footprintSize.getY() - 1), depth - 1);
        int solidIntrusions = 0;
        int solidIntrusionLimit = embedded ? Math.max(8, width * depth) : allowedSolidIntrusions(footprintSize);

        for (BlockPos currentPos : BlockPos.betweenClosed(origin, maxPos)) {
            BlockState state = level.getBlockState(currentPos);
            if (state.isAir()) {
                continue;
            }

            if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
                treeOverlap++;
                if (treeOverlap > profile.placement().maxTreeOverlap()) {
                    return Optional.of(FootprintAnalysis.invalid());
                }
            }

            if (isDangerous(state) || (!state.getFluidState().isEmpty() && profile.placement().avoidFluids())) {
                return Optional.of(FootprintAnalysis.invalid());
            }

            if (isSolidIntrusion(level, currentPos, state)) {
                solidIntrusions++;
                if (solidIntrusions > solidIntrusionLimit) {
                    return Optional.of(FootprintAnalysis.invalid());
                }
            }
        }

        int nearbyFluidBlocks = countNearbyFluidBlocks(level, origin, footprintSize);
        return Optional.of(FootprintAnalysis.valid(surfaceStep, nearbyFluidBlocks, solidIntrusions));
    }

    private Optional<ColumnSurface> findSurfaceCandidate(ServerLevel level, SpawnProfile profile, int x, int z) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        int searchTop = Mth.clamp(maxY + profile.placement().smartSearchTopOffset(), minY, maxY);
        int searchBottom = Mth.clamp(minY + profile.placement().smartSearchBottomOffset(), minY, maxY);
        if (searchTop < searchBottom) {
            return Optional.empty();
        }

        for (int y = searchTop; y >= searchBottom; y--) {
            if (y <= minY || y + 1 >= level.getMaxBuildHeight()) {
                continue;
            }

            BlockPos feetPos = new BlockPos(x, y, z);
            BlockPos floorPos = feetPos.below();
            BlockPos headPos = feetPos.above();
            BlockState floorState = level.getBlockState(floorPos);
            BlockState feetState = level.getBlockState(feetPos);
            BlockState headState = level.getBlockState(headPos);

            if (!isValidFloor(level, profile, floorPos, floorState)) {
                continue;
            }

            if (!isPassable(level, feetPos, feetState, profile.placement().allowReplaceableAtFeet())) {
                continue;
            }

            if (!isPassable(level, headPos, headState, profile.placement().allowReplaceableAtHead())) {
                continue;
            }

            return Optional.of(new ColumnSurface(feetPos, floorPos, floorState));
        }

        return Optional.empty();
    }

    private Optional<HeightmapFootprint> analyzeHeightmapFootprint(ServerLevel level, SpawnProfile profile, int originX, int originZ, Vec3i footprintSize) {
        int width = Math.max(1, footprintSize.getX());
        int depth = Math.max(1, footprintSize.getZ());
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        List<Vec3i> samples = createFootprintSamples(width, depth);

        for (Vec3i sample : samples) {
            int x = originX + sample.getX();
            int z = originZ + sample.getZ();
            OptionalInt heightmapY = resolveGeneratedHeightmapY(level, x, z);
            if (heightmapY.isEmpty()) {
                return Optional.empty();
            }

            int y = heightmapY.getAsInt();
            BlockPos floorPos = new BlockPos(x, y - 1, z);
            BlockState floorState = level.getBlockState(floorPos);
            if (!isValidFloor(level, profile, floorPos, floorState)) {
                return Optional.empty();
            }

            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }

        int surfaceStep = maxY - minY;
        if (surfaceStep > profile.placement().maxSurfaceStep()) {
            return Optional.empty();
        }

        return Optional.of(new HeightmapFootprint(minY, surfaceStep));
    }

    private List<Vec3i> createFootprintSamples(int width, int depth) {
        LinkedHashSet<Vec3i> samples = new LinkedHashSet<>();
        int maxX = width - 1;
        int maxZ = depth - 1;
        int midX = maxX / 2;
        int midZ = maxZ / 2;

        addFootprintSample(samples, 0, 0);
        addFootprintSample(samples, maxX, 0);
        addFootprintSample(samples, 0, maxZ);
        addFootprintSample(samples, maxX, maxZ);
        addFootprintSample(samples, midX, midZ);
        addFootprintSample(samples, midX, 0);
        addFootprintSample(samples, midX, maxZ);
        addFootprintSample(samples, 0, midZ);
        addFootprintSample(samples, maxX, midZ);

        int step = 8;
        for (int dx = 0; dx <= maxX; dx += step) {
            for (int dz = 0; dz <= maxZ; dz += step) {
                addFootprintSample(samples, dx, dz);
            }
        }
        addFootprintSample(samples, maxX, maxZ);

        return new ArrayList<>(samples);
    }

    private void addFootprintSample(Set<Vec3i> samples, int dx, int dz) {
        samples.add(new Vec3i(Math.max(0, dx), 0, Math.max(0, dz)));
    }

    private OptionalInt resolveIslandAnchorSurfaceY(ServerLevel level, int x, int z, boolean requireDryAnchor) {
        OptionalInt height = resolveGeneratedHeightmapY(level, x, z);
        int surfaceY = height.orElse(level.getSeaLevel() + 1) - 1;
        surfaceY = Mth.clamp(surfaceY, level.getMinBuildHeight(), level.getMaxBuildHeight() - 2);
        BlockState surfaceState = level.getBlockState(new BlockPos(x, surfaceY, z));

        if (isDangerous(surfaceState)) {
            return OptionalInt.empty();
        }

        if (requireDryAnchor && !surfaceState.getFluidState().isEmpty()) {
            return OptionalInt.empty();
        }

        return OptionalInt.of(surfaceY);
    }

    private OptionalInt resolveGeneratedHeightmapY(ServerLevel level, int blockX, int blockZ) {
        level.getChunk(blockX >> 4, blockZ >> 4);
        int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
        if (height <= level.getMinBuildHeight() + 1) {
            return OptionalInt.empty();
        }

        return OptionalInt.of(height);
    }

    private int applyPlacementOffset(ServerLevel level, SpawnProfile profile, int baseY) {
        return clampPlacementY(level, baseY + profile.placement().placementY() + profile.placement().surfaceYOffset());
    }

    private int clampPlacementY(ServerLevel level, int y) {
        return Mth.clamp(y, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
    }

    private boolean fitsBuildHeight(ServerLevel level, BlockPos origin, Vec3i footprintSize) {
        int originY = origin.getY();
        int topY = originY + Math.max(0, footprintSize.getY() - 1);
        return originY >= level.getMinBuildHeight() && topY <= level.getMaxBuildHeight() - 1;
    }

    private boolean isBiomeAllowed(ServerLevel level, SpawnProfile profile, int x, int z, Set<ResourceLocation> allowedBiomes) {
        if (profile.allowsAnyBiome()) {
            return true;
        }

        BlockPos probePos = new BlockPos(x, level.getSeaLevel(), z);
        return level.getBiome(probePos)
                .unwrapKey()
                .map(key -> allowedBiomes.contains(key.location()))
                .orElse(false);
    }

    private boolean isValidFloor(ServerLevel level, SpawnProfile profile, BlockPos pos, BlockState state) {
        if (!profile.placement().allowFluidsBelow() && !state.getFluidState().isEmpty()) {
            return false;
        }

        if (isDangerous(state)) {
            return false;
        }

        if (profile.forbiddenSurfaceBlockIds().contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) {
            return false;
        }

        return !state.getCollisionShape(level, pos).isEmpty() && state.isFaceSturdy(level, pos, Direction.UP);
    }

    private boolean isPassable(ServerLevel level, BlockPos pos, BlockState state, boolean allowReplaceable) {
        if (state.isAir()) {
            return true;
        }

        if (!state.getFluidState().isEmpty() || isDangerous(state)) {
            return false;
        }

        if (state.canBeReplaced()) {
            return allowReplaceable;
        }

        return state.getCollisionShape(level, pos).isEmpty();
    }

    private boolean isSolidIntrusion(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.canBeReplaced() && !state.getCollisionShape(level, pos).isEmpty();
    }

    private boolean isDangerous(BlockState state) {
        return state.getFluidState().is(FluidTags.LAVA)
                || state.is(Blocks.LAVA)
                || state.is(Blocks.LAVA_CAULDRON)
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.POINTED_DRIPSTONE)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW);
    }

    private int countNearbyFluidBlocks(ServerLevel level, BlockPos origin, Vec3i footprintSize) {
        int minX = origin.getX() - CANDIDATE_FLUID_SCAN_RADIUS;
        int maxX = origin.getX() + Math.max(0, footprintSize.getX() - 1) + CANDIDATE_FLUID_SCAN_RADIUS;
        int minY = Math.max(level.getMinBuildHeight(), origin.getY() - 1);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, origin.getY() + Math.max(1, footprintSize.getY()));
        int minZ = origin.getZ() - CANDIDATE_FLUID_SCAN_RADIUS;
        int maxZ = origin.getZ() + Math.max(0, footprintSize.getZ() - 1) + CANDIDATE_FLUID_SCAN_RADIUS;
        int nearbyFluidBlocks = 0;

        for (BlockPos currentPos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            if (!level.getFluidState(currentPos).isEmpty()) {
                nearbyFluidBlocks++;
            }
        }

        return nearbyFluidBlocks;
    }

    private int allowedSolidIntrusions(Vec3i footprintSize) {
        int volume = Math.max(1, footprintSize.getX() * footprintSize.getY() * footprintSize.getZ());
        return Math.max(4, volume / 20);
    }

    private record ColumnSurface(BlockPos feetPos, BlockPos floorPos, BlockState floorState) {
        private int feetY() {
            return feetPos.getY();
        }
    }

    private record HeightmapFootprint(int originY, int surfaceStep) {
    }

    private record FootprintAnalysis(boolean valid, int surfaceStep, int nearbyFluidBlocks, int solidIntrusions) {
        private static FootprintAnalysis invalid() {
            return new FootprintAnalysis(false, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        private static FootprintAnalysis valid(int surfaceStep, int nearbyFluidBlocks, int solidIntrusions) {
            return new FootprintAnalysis(true, surfaceStep, nearbyFluidBlocks, solidIntrusions);
        }
    }

    private record SearchCandidate(BlockPos origin, int score, int surfaceStep, int nearbyFluidBlocks, int solidIntrusions) {
    }
}
