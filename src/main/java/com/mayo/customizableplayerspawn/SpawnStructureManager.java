package com.mayo.customizableplayerspawn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class SpawnStructureManager {
    private static final String PLAYER_SPAWN_DATA_KEY = CustomizablePlayerSpawnMod.MODID + ".initial_spawn_applied";
    private static final float DEFAULT_SPAWN_ANGLE = 0.0F;
    private static final String STRUCTURE_BLOCK_MODE_TAG = "mode";
    private static final String STRUCTURE_BLOCK_METADATA_TAG = "metadata";
    private static final String STRUCTURE_BLOCK_DATA_MODE = "DATA";
    private static final Vec3i STANDALONE_SPAWN_FOOTPRINT = new Vec3i(1, 2, 1);
    private static final int CANDIDATE_FLUID_SCAN_RADIUS = 1;

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        SpawnStructureSavedData data = SpawnStructureSavedData.get(server);
        Optional<SpawnStructureSavedData.ResolvedSpawn> resolvedSpawn = data.resolve(server);
        if (resolvedSpawn.isEmpty()) {
            resolvedSpawn = createConfiguredSpawn(server, data);
        }

        if (resolvedSpawn.isEmpty()) {
            return;
        }

        ensureRespawnPosition(player, resolvedSpawn.get());

        if (hasInitialSpawnBeenApplied(player)) {
            return;
        }

        applyInitialSpawn(player, resolvedSpawn.get());
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (player.getRespawnPosition() != null) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        Optional<SpawnStructureSavedData.ResolvedSpawn> resolvedSpawn = SpawnStructureSavedData.get(server).resolve(server);
        if (resolvedSpawn.isEmpty()) {
            return;
        }

        SpawnStructureSavedData.ResolvedSpawn spawn = resolvedSpawn.get();
        ensureRespawnPosition(player, spawn);
        teleportToResolvedSpawn(player, spawn);
    }

    private static Optional<SpawnStructureSavedData.ResolvedSpawn> createConfiguredSpawn(MinecraftServer server, SpawnStructureSavedData data) {
        ServerLevel targetLevel = server.getLevel(Config.targetDimensionKey());
        if (targetLevel == null) {
            CustomizablePlayerSpawnMod.LOGGER.error("Configured spawn dimension {} does not exist.", Config.targetDimensionKey().location());
            return Optional.empty();
        }

        if (!Config.usesStructurePlacement()) {
            return createSpawnWithoutStructure(server, data, targetLevel);
        }

        Optional<LoadedStructureTemplate> loadedTemplate = resolveConfiguredStructureTemplate(server);
        if (loadedTemplate.isEmpty()) {
            return Optional.empty();
        }

        LoadedStructureTemplate configuredTemplate = loadedTemplate.get();
        String templateDescription = configuredTemplate.description();
        StructureTemplate template = configuredTemplate.template();
        StructurePlaceSettings settings = createPlacementSettings();
        Optional<ConfiguredMarkerBlock> markerBlock = resolveConfiguredMarkerBlock();
        if (!hasSpawnAnchor(template, settings, markerBlock)) {
            logMissingSpawnAnchor(templateDescription, markerBlock);
            return Optional.empty();
        }

        Optional<BlockPos> origin = findStructureOrigin(targetLevel, template.getSize());
        if (origin.isEmpty()) {
            CustomizablePlayerSpawnMod.LOGGER.error(
                    "Unable to find a valid position for {} in dimension {} after {} attempts.",
                    templateDescription,
                    targetLevel.dimension().location(),
                    Config.SEARCH_ATTEMPTS.get()
            );
            return Optional.empty();
        }

        BlockPos structureOrigin = origin.get();
        preloadStructureArea(targetLevel, structureOrigin, template.getSize());

        boolean placed = template.placeInWorld(targetLevel, structureOrigin, structureOrigin, settings, targetLevel.getRandom(), Block.UPDATE_ALL);
        if (!placed) {
            CustomizablePlayerSpawnMod.LOGGER.error("Failed to place start structure {} at {}.", templateDescription, structureOrigin);
            return Optional.empty();
        }

        processKnownDataMarkers(targetLevel, template, structureOrigin, settings);

        Optional<BlockPos> markerPos = resolvePlacedSpawnMarker(targetLevel, template, structureOrigin, settings, markerBlock);
        if (markerPos.isEmpty()) {
            logMissingSpawnAnchor(templateDescription, markerBlock);
            return Optional.empty();
        }

        BlockPos transformedOffset = StructureTemplate.calculateRelativePosition(
                settings,
                new BlockPos(Config.SPAWN_OFFSET_X.get(), Config.SPAWN_OFFSET_Y.get(), Config.SPAWN_OFFSET_Z.get())
        );
        BlockPos spawnPos = markerPos.get().offset(transformedOffset.getX(), transformedOffset.getY(), transformedOffset.getZ());
        float spawnAngle = Config.spawnAngle();

        targetLevel.setDefaultSpawnPos(spawnPos, spawnAngle);
        data.setGenerated(targetLevel.dimension(), spawnPos, spawnAngle, structureOrigin);

        CustomizablePlayerSpawnMod.LOGGER.info(
                "Created start structure {} in {} at origin {} with spawn {}.",
                templateDescription,
                targetLevel.dimension().location(),
                structureOrigin,
                spawnPos
        );

        return data.resolve(server);
    }

    private static Optional<SpawnStructureSavedData.ResolvedSpawn> createSpawnWithoutStructure(
            MinecraftServer server,
            SpawnStructureSavedData data,
            ServerLevel targetLevel
    ) {
        Optional<BlockPos> origin = findStructureOrigin(targetLevel, STANDALONE_SPAWN_FOOTPRINT);
        if (origin.isEmpty()) {
            CustomizablePlayerSpawnMod.LOGGER.error(
                    "Unable to find a valid standalone spawn position in dimension {} after {} attempts.",
                    targetLevel.dimension().location(),
                    Config.SEARCH_ATTEMPTS.get()
            );
            return Optional.empty();
        }

        BlockPos spawnOrigin = origin.get();
        BlockPos spawnPos = spawnOrigin.offset(
                Config.SPAWN_OFFSET_X.get(),
                Config.SPAWN_OFFSET_Y.get(),
                Config.SPAWN_OFFSET_Z.get()
        );
        float spawnAngle = Config.spawnAngle();

        targetLevel.setDefaultSpawnPos(spawnPos, spawnAngle);
        data.setGenerated(targetLevel.dimension(), spawnPos, spawnAngle, spawnOrigin);

        CustomizablePlayerSpawnMod.LOGGER.info(
                "Configured standalone spawn in {} at {}.",
                targetLevel.dimension().location(),
                spawnPos
        );

        return data.resolve(server);
    }

    private static Optional<LoadedStructureTemplate> resolveConfiguredStructureTemplate(MinecraftServer server) {
        if (Config.hasExternalStructureFile()) {
            Optional<Path> externalPath = Config.externalStructureTemplatePath();
            if (externalPath.isEmpty()) {
                CustomizablePlayerSpawnMod.LOGGER.error(
                        "Configured externalStructureFile {} is not a valid relative .nbt path inside {}.",
                        Config.externalStructureFileName(),
                        Config.externalStructureBaseDirectory()
                );
                return Optional.empty();
            }

            return loadExternalStructureTemplate(server, externalPath.get());
        }

        if (!Config.hasStructureTemplate()) {
            return Optional.empty();
        }

        ResourceLocation templateId = Config.structureTemplateId();
        Optional<StructureTemplate> optionalTemplate = server.getStructureManager().get(templateId);
        if (optionalTemplate.isEmpty()) {
            CustomizablePlayerSpawnMod.LOGGER.error(
                    "Structure template {} was not found. Put the .nbt into generated/<namespace>/structures, data/<namespace>/structure, or config/{}/structures.",
                    templateId,
                    CustomizablePlayerSpawnMod.MODID
            );
            return Optional.empty();
        }

        return Optional.of(new LoadedStructureTemplate(templateId.toString(), optionalTemplate.get()));
    }

    private static Optional<LoadedStructureTemplate> loadExternalStructureTemplate(MinecraftServer server, Path configuredPath) {
        Path absoluteConfiguredPath = configuredPath.toAbsolutePath().normalize();
        Path absoluteBaseDirectory = Config.externalStructureBaseDirectory().toAbsolutePath().normalize();
        if (!absoluteConfiguredPath.startsWith(absoluteBaseDirectory)) {
            CustomizablePlayerSpawnMod.LOGGER.error(
                    "Configured external structure path {} escapes the allowed directory {}.",
                    absoluteConfiguredPath,
                    absoluteBaseDirectory
            );
            return Optional.empty();
        }

        if (!Files.isRegularFile(absoluteConfiguredPath)) {
            CustomizablePlayerSpawnMod.LOGGER.error(
                    "Configured external structure file {} was not found.",
                    absoluteConfiguredPath
            );
            return Optional.empty();
        }

        try {
            StructureTemplate template = server.getStructureManager().readStructure(
                    NbtIo.readCompressed(absoluteConfiguredPath.toFile())
            );
            return Optional.of(new LoadedStructureTemplate(absoluteConfiguredPath.toString(), template));
        } catch (IOException exception) {
            CustomizablePlayerSpawnMod.LOGGER.error(
                    "Couldn't load external structure file {}.",
                    absoluteConfiguredPath,
                    exception
            );
            return Optional.empty();
        }
    }

    private static StructurePlaceSettings createPlacementSettings() {
        return new StructurePlaceSettings().addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
    }

    private static boolean hasSpawnAnchor(
            StructureTemplate template,
            StructurePlaceSettings settings,
            Optional<ConfiguredMarkerBlock> markerBlock
    ) {
        return !findConfiguredMarkerBlocks(template, BlockPos.ZERO, settings, markerBlock).isEmpty()
                || findConfiguredDataMarker(template, BlockPos.ZERO, settings).isPresent();
    }

    private static Optional<BlockPos> resolvePlacedSpawnMarker(
            ServerLevel level,
            StructureTemplate template,
            BlockPos structureOrigin,
            StructurePlaceSettings settings,
            Optional<ConfiguredMarkerBlock> markerBlock
    ) {
        List<StructureTemplate.StructureBlockInfo> placedMarkers = findConfiguredMarkerBlocks(template, structureOrigin, settings, markerBlock);
        if (!placedMarkers.isEmpty()) {
            BlockPos markerPos = placedMarkers.get(0).pos();
            if (Config.REMOVE_SPAWN_MARKER_BLOCK.get()) {
                for (StructureTemplate.StructureBlockInfo marker : placedMarkers) {
                    level.setBlock(marker.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            return Optional.of(markerPos);
        }

        return findConfiguredDataMarker(template, structureOrigin, settings).map(StructureTemplate.StructureBlockInfo::pos);
    }

    private static Optional<StructureTemplate.StructureBlockInfo> findConfiguredDataMarker(
            StructureTemplate template,
            BlockPos structureOrigin,
            StructurePlaceSettings settings
    ) {
        String dataMarkerName = Config.dataMarkerName();
        if (dataMarkerName.isEmpty()) {
            return Optional.empty();
        }

        return template.filterBlocks(structureOrigin, settings, Blocks.STRUCTURE_BLOCK)
                .stream()
                .filter(blockInfo -> isMatchingDataMarker(blockInfo.nbt(), dataMarkerName))
                .findFirst();
    }

    private static List<StructureTemplate.StructureBlockInfo> findConfiguredMarkerBlocks(
            StructureTemplate template,
            BlockPos structureOrigin,
            StructurePlaceSettings settings,
            Optional<ConfiguredMarkerBlock> markerBlock
    ) {
        return markerBlock.map(configuredMarkerBlock -> template.filterBlocks(structureOrigin, settings, configuredMarkerBlock.block()))
                .orElseGet(List::of);
    }

    private static void processKnownDataMarkers(
            ServerLevel level,
            StructureTemplate template,
            BlockPos structureOrigin,
            StructurePlaceSettings settings
    ) {
        for (StructureTemplate.StructureBlockInfo blockInfo : template.filterBlocks(structureOrigin, settings, Blocks.STRUCTURE_BLOCK)) {
            CompoundTag tag = blockInfo.nbt();
            if (!isDataMarker(tag)) {
                continue;
            }

            handleDataMarker(level, blockInfo.pos(), tag.getString(STRUCTURE_BLOCK_METADATA_TAG), settings);
        }
    }

    private static void handleDataMarker(
            ServerLevel level,
            BlockPos pos,
            String metadata,
            StructurePlaceSettings settings
    ) {
        if (metadata.startsWith("Elytra")) {
            ItemFrame itemFrame = new ItemFrame(level, pos, settings.getRotation().rotate(Direction.SOUTH));
            itemFrame.setItem(new ItemStack(Items.ELYTRA), false);
            level.addFreshEntity(itemFrame);
        }
    }

    private static boolean isMatchingDataMarker(CompoundTag tag, String markerName) {
        return isDataMarker(tag) && markerName.equals(tag.getString(STRUCTURE_BLOCK_METADATA_TAG));
    }

    private static boolean isDataMarker(CompoundTag tag) {
        return tag != null && STRUCTURE_BLOCK_DATA_MODE.equals(tag.getString(STRUCTURE_BLOCK_MODE_TAG));
    }

    private static Optional<ConfiguredMarkerBlock> resolveConfiguredMarkerBlock() {
        String markerBlockName = Config.spawnMarkerBlockName();
        if (markerBlockName.isEmpty()) {
            return Optional.empty();
        }

        ResourceLocation markerBlockId = ResourceLocation.tryParse(markerBlockName);
        if (markerBlockId == null) {
            CustomizablePlayerSpawnMod.LOGGER.error("Configured spawnMarkerBlock {} is not a valid resource location.", markerBlockName);
            return Optional.empty();
        }

        Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(markerBlockId);
        if (block.isEmpty() || block.get() == Blocks.AIR) {
            CustomizablePlayerSpawnMod.LOGGER.error("Configured spawnMarkerBlock {} does not exist as a loaded block.", markerBlockId);
            return Optional.empty();
        }

        return Optional.of(new ConfiguredMarkerBlock(markerBlockId, block.get()));
    }

    private static void logMissingSpawnAnchor(String templateDescription, Optional<ConfiguredMarkerBlock> markerBlock) {
        String markerBlockDescription = markerBlock.map(configuredMarkerBlock -> configuredMarkerBlock.id().toString()).orElse("<disabled>");
        String dataMarkerName = Config.dataMarkerName();
        if (dataMarkerName.isEmpty()) {
            CustomizablePlayerSpawnMod.LOGGER.error(
                    "Structure template {} has no configured marker block {} and dataMarker is empty.",
                    templateDescription,
                    markerBlockDescription
            );
            return;
        }

        CustomizablePlayerSpawnMod.LOGGER.error(
                "Structure template {} has no configured marker block {} and no DATA structure block with metadata {}.",
                templateDescription,
                markerBlockDescription,
                dataMarkerName
        );
    }

    private static Optional<BlockPos> findStructureOrigin(ServerLevel level, Vec3i footprintSize) {
        Set<ResourceLocation> allowedBiomes = Config.allowedBiomeIds();
        if (!Config.allowsAnyBiome() && allowedBiomes.isEmpty()) {
            CustomizablePlayerSpawnMod.LOGGER.error("The allowed biome list contains no valid biome ids.");
            return Optional.empty();
        }

        Config.SurfaceSearchMode searchMode = Config.surfaceSearchMode();
        if (searchMode == Config.SurfaceSearchMode.HEIGHTMAP) {
            return findLegacyStructureOrigin(level, allowedBiomes);
        }

        int searchRadius = Config.SEARCH_RADIUS.get();
        int searchAttempts = Config.SEARCH_ATTEMPTS.get();
        int chunkRadius = Math.max(0, Mth.ceil((float) searchRadius / 16.0F));
        RandomSource random = RandomSource.create(level.getSeed() ^ 0x4350534CL);
        SearchCandidate bestCandidate = null;

        for (int attempt = 0; attempt < searchAttempts; attempt++) {
            int chunkX = chunkRadius == 0 ? 0 : random.nextInt(chunkRadius * 2 + 1) - chunkRadius;
            int chunkZ = chunkRadius == 0 ? 0 : random.nextInt(chunkRadius * 2 + 1) - chunkRadius;
            int blockX = chunkX * 16 + 8;
            int blockZ = chunkZ * 16 + 8;

            if (!isBiomeAllowed(level, blockX, blockZ, allowedBiomes)) {
                debugCandidate(blockX, blockZ, "rejected: biome not allowed");
                continue;
            }

            Optional<SearchCandidate> candidate = evaluateCandidate(level, blockX, blockZ, footprintSize, searchMode);
            if (candidate.isEmpty()) {
                continue;
            }

            SearchCandidate acceptedCandidate = candidate.get();
            debugCandidate(
                    blockX,
                    blockZ,
                    "accepted at y=%s floor=%s step=%s solidIntrusions=%s nearbyFluids=%s score=%s".formatted(
                            acceptedCandidate.origin().getY(),
                            acceptedCandidate.floorBlockId(),
                            acceptedCandidate.surfaceStep(),
                            acceptedCandidate.solidIntrusions(),
                            acceptedCandidate.nearbyFluidBlocks(),
                            acceptedCandidate.score()
                    )
            );
            if (bestCandidate == null || acceptedCandidate.score() > bestCandidate.score()) {
                bestCandidate = acceptedCandidate;
            }
        }

        if (bestCandidate != null) {
            debugCandidate(
                    bestCandidate.origin().getX(),
                    bestCandidate.origin().getZ(),
                    "selected best candidate at y=%s score=%s".formatted(bestCandidate.origin().getY(), bestCandidate.score())
            );
            return Optional.of(bestCandidate.origin());
        }

        return Optional.empty();
    }

    private static Optional<BlockPos> findLegacyStructureOrigin(ServerLevel level, Set<ResourceLocation> allowedBiomes) {
        int searchRadius = Config.SEARCH_RADIUS.get();
        int searchAttempts = Config.SEARCH_ATTEMPTS.get();
        int chunkRadius = Math.max(0, Mth.ceil((float) searchRadius / 16.0F));
        RandomSource random = RandomSource.create(level.getSeed() ^ 0x4350534CL);

        for (int attempt = 0; attempt < searchAttempts; attempt++) {
            int chunkX = chunkRadius == 0 ? 0 : random.nextInt(chunkRadius * 2 + 1) - chunkRadius;
            int chunkZ = chunkRadius == 0 ? 0 : random.nextInt(chunkRadius * 2 + 1) - chunkRadius;
            int blockX = chunkX * 16 + 8;
            int blockZ = chunkZ * 16 + 8;

            if (!isBiomeAllowed(level, blockX, blockZ, allowedBiomes)) {
                continue;
            }

            int baseY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ)
                    + Config.PLACEMENT_Y.get()
                    + Config.SURFACE_Y_OFFSET.get();
            int blockY = Mth.clamp(baseY, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
            return Optional.of(new BlockPos(blockX, blockY, blockZ));
        }

        return Optional.empty();
    }

    private static Optional<SearchCandidate> evaluateCandidate(
            ServerLevel level,
            int x,
            int z,
            Vec3i footprintSize,
            Config.SurfaceSearchMode searchMode
    ) {
        Optional<ColumnSurface> referenceSurface = searchMode == Config.SurfaceSearchMode.SMART
                ? findSurfaceCandidate(level, x, z)
                : Optional.empty();
        if (searchMode == Config.SurfaceSearchMode.SMART && referenceSurface.isEmpty()) {
            debugCandidate(x, z, "rejected: no valid walkable surface found in column");
            return Optional.empty();
        }

        int originY = switch (searchMode) {
            case SMART -> applyPlacementOffset(level, referenceSurface.get().feetY());
            case ABSOLUTE_Y -> clampPlacementY(level, Config.ABSOLUTE_Y.get() + Config.PLACEMENT_Y.get() + Config.SURFACE_Y_OFFSET.get());
            case HEIGHTMAP -> throw new IllegalStateException("HEIGHTMAP candidates should be handled by legacy search");
        };
        BlockPos origin = new BlockPos(x, originY, z);

        if (!fitsBuildHeight(level, origin, footprintSize)) {
            debugCandidate(x, z, "rejected: placement volume is outside build height at y=" + originY);
            return Optional.empty();
        }

        Optional<FootprintAnalysis> footprintAnalysis = analyzeFootprint(level, origin, footprintSize);
        if (footprintAnalysis.isEmpty()) {
            debugCandidate(x, z, "rejected: footprint analysis failed unexpectedly");
            return Optional.empty();
        }

        FootprintAnalysis analysis = footprintAnalysis.get();
        if (!analysis.valid()) {
            debugCandidate(x, z, "rejected: " + analysis.reason());
            return Optional.empty();
        }

        int targetY = resolveTargetY(level, x, z, searchMode, referenceSurface);
        int score = calculateCandidateScore(originY, targetY, analysis.surfaceStep(), analysis.nearbyFluidBlocks(), analysis.solidIntrusions());
        String floorBlockId = blockId(analysis.referenceFloor());
        return Optional.of(new SearchCandidate(origin, score, analysis.surfaceStep(), analysis.nearbyFluidBlocks(), analysis.solidIntrusions(), floorBlockId));
    }

    private static Optional<ColumnSurface> findSurfaceCandidate(ServerLevel level, int x, int z) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        int searchTop = Mth.clamp(maxY + Config.SMART_SEARCH_TOP_OFFSET.get(), minY, maxY);
        int searchBottom = Mth.clamp(minY + Config.SMART_SEARCH_BOTTOM_OFFSET.get(), minY, maxY);
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

            if (!isValidFloor(level, floorPos, floorState)) {
                continue;
            }

            if (!isPassable(level, feetPos, feetState, Config.ALLOW_REPLACEABLE_AT_FEET.get())) {
                continue;
            }

            if (!isPassable(level, headPos, headState, Config.ALLOW_REPLACEABLE_AT_HEAD.get())) {
                continue;
            }

            return Optional.of(new ColumnSurface(feetPos, floorPos, floorState));
        }

        return Optional.empty();
    }

    private static Optional<FootprintAnalysis> analyzeFootprint(ServerLevel level, BlockPos origin, Vec3i footprintSize) {
        int width = Math.max(1, footprintSize.getX());
        int depth = Math.max(1, footprintSize.getZ());
        int minSurfaceY = Integer.MAX_VALUE;
        int maxSurfaceY = Integer.MIN_VALUE;
        BlockState referenceFloor = level.getBlockState(origin.below());

        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < depth; dz++) {
                int worldX = origin.getX() + dx;
                int worldZ = origin.getZ() + dz;
                Optional<ColumnSurface> localSurface = findSurfaceCandidate(level, worldX, worldZ);
                if (localSurface.isEmpty()) {
                    return Optional.of(FootprintAnalysis.invalid("footprint column x=%s z=%s has no valid surface".formatted(worldX, worldZ)));
                }

                ColumnSurface surface = localSurface.get();
                minSurfaceY = Math.min(minSurfaceY, surface.feetY());
                maxSurfaceY = Math.max(maxSurfaceY, surface.feetY());

                BlockPos supportPos = new BlockPos(worldX, origin.getY() - 1, worldZ);
                BlockState supportState = level.getBlockState(supportPos);
                if (!isValidFloor(level, supportPos, supportState)) {
                    return Optional.of(FootprintAnalysis.invalid(
                            "unsafe floor block %s at x=%s y=%s z=%s".formatted(blockId(supportState), worldX, supportPos.getY(), worldZ)
                    ));
                }
            }
        }

        int surfaceStep = maxSurfaceY - minSurfaceY;
        if (surfaceStep > Config.MAX_SURFACE_STEP.get()) {
            return Optional.of(FootprintAnalysis.invalid(
                    "surface step %s exceeds maxSurfaceStep=%s".formatted(surfaceStep, Config.MAX_SURFACE_STEP.get())
            ));
        }

        BlockPos maxPos = origin.offset(width - 1, Math.max(0, footprintSize.getY() - 1), depth - 1);
        int solidIntrusions = 0;
        int solidIntrusionLimit = allowedSolidIntrusions(footprintSize);

        for (BlockPos currentPos : BlockPos.betweenClosed(origin, maxPos)) {
            BlockState state = level.getBlockState(currentPos);
            if (state.isAir()) {
                continue;
            }

            if (isDangerous(state) || !state.getFluidState().isEmpty()) {
                return Optional.of(FootprintAnalysis.invalid(
                        "critical block %s inside structure volume at %s".formatted(blockId(state), currentPos)
                ));
            }

            if (isSolidIntrusion(level, currentPos, state)) {
                solidIntrusions++;
                if (solidIntrusions > solidIntrusionLimit) {
                    return Optional.of(FootprintAnalysis.invalid(
                            "structure volume intersects too many solid blocks (%s > %s)".formatted(solidIntrusions, solidIntrusionLimit)
                    ));
                }
            }
        }

        int nearbyFluidBlocks = countNearbyFluidBlocks(level, origin, footprintSize);
        return Optional.of(FootprintAnalysis.valid(referenceFloor, surfaceStep, nearbyFluidBlocks, solidIntrusions));
    }

    private static int countNearbyFluidBlocks(ServerLevel level, BlockPos origin, Vec3i footprintSize) {
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

    private static int allowedSolidIntrusions(Vec3i footprintSize) {
        int volume = Math.max(1, footprintSize.getX() * footprintSize.getY() * footprintSize.getZ());
        return Math.max(4, volume / 20);
    }

    private static boolean fitsBuildHeight(ServerLevel level, BlockPos origin, Vec3i footprintSize) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        int originY = origin.getY();
        int topY = originY + Math.max(0, footprintSize.getY() - 1);
        return originY >= minY && topY <= maxY;
    }

    private static int applyPlacementOffset(ServerLevel level, int baseY) {
        return clampPlacementY(level, baseY + Config.PLACEMENT_Y.get() + Config.SURFACE_Y_OFFSET.get());
    }

    private static int clampPlacementY(ServerLevel level, int y) {
        return Mth.clamp(y, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
    }

    private static int resolveTargetY(
            ServerLevel level,
            int x,
            int z,
            Config.SurfaceSearchMode searchMode,
            Optional<ColumnSurface> referenceSurface
    ) {
        return switch (searchMode) {
            case ABSOLUTE_Y -> clampPlacementY(level, Config.ABSOLUTE_Y.get() + Config.PLACEMENT_Y.get() + Config.SURFACE_Y_OFFSET.get());
            case SMART -> referenceSurface
                    .map(surface -> applyPlacementOffset(level, surface.feetY()))
                    .orElseGet(() -> clampPlacementY(
                            level,
                            level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
                                    + Config.PLACEMENT_Y.get()
                                    + Config.SURFACE_Y_OFFSET.get()
                    ));
            case HEIGHTMAP -> clampPlacementY(
                    level,
                    level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
                            + Config.PLACEMENT_Y.get()
                            + Config.SURFACE_Y_OFFSET.get()
            );
        };
    }

    private static int calculateCandidateScore(int originY, int targetY, int surfaceStep, int nearbyFluidBlocks, int solidIntrusions) {
        return -(Math.abs(originY - targetY) * 16 + surfaceStep * 24 + nearbyFluidBlocks * 8 + solidIntrusions * 3);
    }

    private static boolean isBiomeAllowed(ServerLevel level, int x, int z, Set<ResourceLocation> allowedBiomes) {
        if (Config.allowsAnyBiome()) {
            return true;
        }

        BlockPos probePos = new BlockPos(x, level.getSeaLevel(), z);
        return level.getBiome(probePos)
                .unwrapKey()
                .map(key -> allowedBiomes.contains(key.location()))
                .orElse(false);
    }

    private static void preloadStructureArea(ServerLevel level, BlockPos origin, Vec3i size) {
        int minChunkX = origin.getX() >> 4;
        int maxChunkX = (origin.getX() + Math.max(0, size.getX() - 1)) >> 4;
        int minChunkZ = origin.getZ() >> 4;
        int maxChunkZ = (origin.getZ() + Math.max(0, size.getZ() - 1)) >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
    }

    private static void applyInitialSpawn(ServerPlayer player, SpawnStructureSavedData.ResolvedSpawn spawn) {
        teleportToResolvedSpawn(player, spawn);
        ensureRespawnPosition(player, spawn);

        markInitialSpawnApplied(player);

        CustomizablePlayerSpawnMod.LOGGER.info(
                "Applied initial custom spawn for {} at {} in {}.",
                player.getGameProfile().getName(),
                spawn.spawnPos(),
                spawn.level().dimension().location()
        );
    }

    private static void teleportToResolvedSpawn(ServerPlayer player, SpawnStructureSavedData.ResolvedSpawn spawn) {
        Vec3 position = spawn.teleportPosition();
        player.teleportTo(spawn.level(), position.x, position.y, position.z, spawn.spawnAngle(), 0.0F);
    }

    private static void ensureRespawnPosition(ServerPlayer player, SpawnStructureSavedData.ResolvedSpawn spawn) {
        if (player.getRespawnPosition() == null) {
            player.setRespawnPosition(spawn.level().dimension(), spawn.spawnPos(), spawn.spawnAngle(), true, false);
        }
    }

    private static boolean hasInitialSpawnBeenApplied(ServerPlayer player) {
        return getPersistedPlayerData(player).getBoolean(PLAYER_SPAWN_DATA_KEY);
    }

    private static void markInitialSpawnApplied(ServerPlayer player) {
        getPersistedPlayerData(player).putBoolean(PLAYER_SPAWN_DATA_KEY, true);
    }

    private static CompoundTag getPersistedPlayerData(ServerPlayer player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        return persisted;
    }

    private static boolean isValidFloor(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return false;
        }

        if (!Config.ALLOW_FLUIDS_BELOW.get() && !state.getFluidState().isEmpty()) {
            return false;
        }

        if (state.is(BlockTags.LEAVES) || state.canBeReplaced() || isDangerous(state)) {
            return false;
        }

        if (Config.forbiddenSurfaceBlockIds().contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) {
            return false;
        }

        return !state.getCollisionShape(level, pos).isEmpty() && state.isFaceSturdy(level, pos, Direction.UP);
    }

    private static boolean isPassable(ServerLevel level, BlockPos pos, BlockState state, boolean allowReplaceable) {
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

    private static boolean isSolidIntrusion(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.canBeReplaced() && !state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean isDangerous(BlockState state) {
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

    private static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private static void debugCandidate(int x, int z, String message) {
        CustomizablePlayerSpawnMod.LOGGER.debug("Candidate x={} z={} {}", x, z, message);
    }

    private record LoadedStructureTemplate(String description, StructureTemplate template) {
    }

    private record ConfiguredMarkerBlock(ResourceLocation id, Block block) {
    }

    private record ColumnSurface(BlockPos feetPos, BlockPos floorPos, BlockState floorState) {
        private int feetY() {
            return feetPos.getY();
        }
    }

    private record FootprintAnalysis(
            boolean valid,
            String reason,
            BlockState referenceFloor,
            int surfaceStep,
            int nearbyFluidBlocks,
            int solidIntrusions
    ) {
        private static FootprintAnalysis invalid(String reason) {
            return new FootprintAnalysis(false, reason, Blocks.AIR.defaultBlockState(), Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        private static FootprintAnalysis valid(BlockState referenceFloor, int surfaceStep, int nearbyFluidBlocks, int solidIntrusions) {
            return new FootprintAnalysis(true, "", referenceFloor, surfaceStep, nearbyFluidBlocks, solidIntrusions);
        }
    }

    private record SearchCandidate(
            BlockPos origin,
            int score,
            int surfaceStep,
            int nearbyFluidBlocks,
            int solidIntrusions,
            String floorBlockId
    ) {
    }
}
