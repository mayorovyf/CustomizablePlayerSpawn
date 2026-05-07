package com.mayo.customizableplayerspawn;

import com.mayo.customizableplayerspawn.anchor.SpawnAnchorResolver;
import com.mayo.customizableplayerspawn.anchor.SpawnAnchorResult;
import com.mayo.customizableplayerspawn.placement.PlacementEngine;
import com.mayo.customizableplayerspawn.placement.PlacementResult;
import com.mayo.customizableplayerspawn.postprocess.PostPlacementProcessor;
import com.mayo.customizableplayerspawn.structure.LoadedStructureTemplate;
import com.mayo.customizableplayerspawn.structure.StructurePlacementService;
import com.mayo.customizableplayerspawn.structure.StructureTemplateBounds;
import com.mayo.customizableplayerspawn.structure.StructureTemplateProvider;
import com.mayo.customizableplayerspawn.terrain.TerrainPreparer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

public class SpawnStructureManager {
    private static final Vec3i STANDALONE_SPAWN_FOOTPRINT = new Vec3i(1, 2, 1);
    private static final StructureTemplateProvider STRUCTURE_TEMPLATE_PROVIDER = new StructureTemplateProvider();
    private static final PlacementEngine PLACEMENT_ENGINE = new PlacementEngine();
    private static final TerrainPreparer TERRAIN_PREPARER = new TerrainPreparer();
    private static final SpawnAnchorResolver ANCHOR_RESOLVER = new SpawnAnchorResolver();
    private static final PostPlacementProcessor POST_PLACEMENT_PROCESSOR = new PostPlacementProcessor();

    public void onServerStarting(MinecraftServer server) {
        SpawnProfiles.reload();
        PostPlacementProcessor.clear();
    }

    public void onServerStarted(MinecraftServer server) {
        prepareSharedSpawn(server);
    }

    public void onPlayerLoggedIn(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        SpawnProfile profile = SpawnProfiles.active();
        SpawnStructureSavedData data = SpawnStructureSavedData.get(server);
        if (!profile.spawnPolicy().assignExistingPlayers()
                && data.assignedSpawn(server, player.getUUID()).isEmpty()
                && !data.hasInitialSpawnBeenApplied(player.getUUID())
                && isExistingPlayer(player)) {
            CustomizablePlayerSpawnCommon.LOGGER.info(
                    "Skipping automatic CPS spawn assignment for existing player {} because spawnPolicy.assignExistingPlayers=false.",
                    player.getGameProfile().getName()
            );
            return;
        }

        Optional<SpawnStructureInstance.ResolvedSpawn> resolvedSpawn = resolveOrCreateSpawn(server, data, profile, player);
        if (resolvedSpawn.isEmpty()) {
            return;
        }

        if (profile.spawnPolicy().respawnAtAssignedStructure()) {
            ensureRespawnPosition(player, resolvedSpawn.get(), profile);
        }

        if (data.hasInitialSpawnBeenApplied(player.getUUID())) {
            return;
        }

        applyInitialSpawn(player, resolvedSpawn.get(), data, profile);
    }

    public void onPlayerRespawn(ServerPlayer player, boolean endConquered) {
        if (endConquered) {
            return;
        }

        SpawnProfile profile = SpawnProfiles.active();
        if (!profile.spawnPolicy().respawnAtAssignedStructure()) {
            return;
        }

        if (profile.spawnPolicy().respectBedsAndAnchors() && player.getRespawnPosition() != null) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        Optional<SpawnStructureInstance.ResolvedSpawn> resolvedSpawn = resolveExistingSpawnForPlayer(server, player);
        if (resolvedSpawn.isEmpty()) {
            return;
        }

        SpawnStructureInstance.ResolvedSpawn spawn = resolvedSpawn.get();
        ensureRespawnPosition(player, spawn, profile);
        teleportToResolvedSpawn(player, spawn);
    }

    public void onServerTickEnd() {
        PostPlacementProcessor.tick();
    }

    public Optional<SpawnStructureInstance.ResolvedSpawn> resolveExistingSpawnForPlayer(MinecraftServer server, ServerPlayer player) {
        SpawnStructureSavedData data = SpawnStructureSavedData.get(server);
        Optional<SpawnStructureInstance.ResolvedSpawn> assigned = data.assignedSpawn(server, player.getUUID());
        if (assigned.isPresent()) {
            return assigned;
        }

        return data.sharedSpawn(server);
    }

    public boolean teleportToAssignedSpawn(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }

        Optional<SpawnStructureInstance.ResolvedSpawn> spawn = resolveExistingSpawnForPlayer(server, player);
        if (spawn.isEmpty()) {
            return false;
        }

        teleportToResolvedSpawn(player, spawn.get());
        return true;
    }

    private static Optional<SpawnStructureInstance.ResolvedSpawn> resolveOrCreateSpawn(
            MinecraftServer server,
            SpawnStructureSavedData data,
            SpawnProfile profile,
            ServerPlayer player
    ) {
        if (profile.spawnPolicy().mode() == SpawnProfile.SpawnMode.SHARED) {
            Optional<SpawnStructureInstance.ResolvedSpawn> shared = data.sharedSpawn(server);
            if (shared.isPresent()) {
                data.assignPlayer(player.getUUID(), shared.get().instance().id());
                return shared;
            }

            Optional<SpawnStructureInstance.ResolvedSpawn> created = createConfiguredSpawn(server, data, profile, null, SpawnProfile.SpawnMode.SHARED);
            created.ifPresent(spawn -> {
                data.setSharedInstanceId(spawn.instance().id());
                data.assignPlayer(player.getUUID(), spawn.instance().id());
            });
            return created;
        }

        Optional<SpawnStructureInstance.ResolvedSpawn> assigned = data.assignedSpawn(server, player.getUUID());
        if (assigned.isPresent()) {
            return assigned;
        }

        int maxInstances = profile.spawnPolicy().maxInstances();
        if (maxInstances > 0 && data.instanceCount() >= maxInstances) {
            CustomizablePlayerSpawnCommon.LOGGER.error(
                    "Cannot create a spawn structure for {}; spawnPolicy.maxInstances={} is already reached.",
                    player.getGameProfile().getName(),
                    maxInstances
            );
            return Optional.empty();
        }

        Optional<SpawnStructureInstance.ResolvedSpawn> created = createConfiguredSpawn(server, data, profile, player.getUUID(), SpawnProfile.SpawnMode.PER_PLAYER);
        created.ifPresent(spawn -> data.assignPlayer(player.getUUID(), spawn.instance().id()));
        return created;
    }

    private static void prepareSharedSpawn(MinecraftServer server) {
        SpawnProfile profile = SpawnProfiles.active();
        if (profile.spawnPolicy().mode() != SpawnProfile.SpawnMode.SHARED) {
            return;
        }

        SpawnStructureSavedData data = SpawnStructureSavedData.get(server);
        Optional<SpawnStructureInstance.ResolvedSpawn> existingSharedSpawn = data.sharedSpawn(server);
        if (existingSharedSpawn.isPresent()) {
            SpawnStructureInstance.ResolvedSpawn spawn = existingSharedSpawn.get();
            spawn.level().setDefaultSpawnPos(spawn.spawnPos(), spawn.spawnAngle());
            CustomizablePlayerSpawnCommon.LOGGER.info(
                    "Prepared existing shared CPS spawn at {} in {} before player login.",
                    spawn.spawnPos(),
                    spawn.level().dimension().location()
            );
            return;
        }

        Optional<SpawnStructureInstance.ResolvedSpawn> created = createConfiguredSpawn(server, data, profile, null, SpawnProfile.SpawnMode.SHARED);
        created.ifPresent(spawn -> {
            data.setSharedInstanceId(spawn.instance().id());
            CustomizablePlayerSpawnCommon.LOGGER.info(
                    "Prepared new shared CPS spawn at {} in {} before player login.",
                    spawn.spawnPos(),
                    spawn.level().dimension().location()
            );
        });
    }

    private static Optional<SpawnStructureInstance.ResolvedSpawn> createConfiguredSpawn(
            MinecraftServer server,
            SpawnStructureSavedData data,
            SpawnProfile profile,
            UUID ownerUuid,
            SpawnProfile.SpawnMode mode
    ) {
        ServerLevel targetLevel = server.getLevel(profile.targetDimensionKey());
        if (targetLevel == null) {
            CustomizablePlayerSpawnCommon.LOGGER.error("Configured spawn dimension {} does not exist.", profile.targetDimensionKey().location());
            return Optional.empty();
        }

        if (!profile.usesStructurePlacement()) {
            return createSpawnWithoutStructure(server, data, targetLevel, profile, ownerUuid, mode);
        }

        Optional<LoadedStructureTemplate> loadedTemplate = STRUCTURE_TEMPLATE_PROVIDER.resolve(server, profile);
        if (loadedTemplate.isEmpty()) {
            return Optional.empty();
        }

        LoadedStructureTemplate configuredTemplate = loadedTemplate.get();
        String templateDescription = configuredTemplate.description();
        StructureTemplate template = configuredTemplate.template();
        StructureTemplateBounds templateBounds = configuredTemplate.bounds();
        StructurePlaceSettings settings = StructurePlacementService.createPlacementSettings();
        if (!ANCHOR_RESOLVER.hasTemplateAnchor(profile, template, settings)) {
            CustomizablePlayerSpawnCommon.LOGGER.error(
                    "Structure template {} has no usable spawn anchor and anchor.fallback is FAIL.",
                    templateDescription
            );
            return Optional.empty();
        }

        List<SpawnStructureInstance> existingInstances = mode == SpawnProfile.SpawnMode.PER_PLAYER
                ? data.instancesInDimension(targetLevel.dimension())
                : List.of();
        Optional<PlacementResult> placementResult = PLACEMENT_ENGINE.findOrigin(targetLevel, profile, templateBounds, existingInstances);
        if (placementResult.isEmpty()) {
            CustomizablePlayerSpawnCommon.LOGGER.error(
                    "Unable to find a valid position for {} in dimension {} after {} attempts.",
                    templateDescription,
                    targetLevel.dimension().location(),
                    profile.placement().searchAttempts()
            );
            return Optional.empty();
        }

        BlockPos structureOrigin = placementResult.get().origin();
        Vec3i structureSize = template.getSize();
        preloadStructureArea(targetLevel, structureOrigin, structureSize, profile.terrain().terrainPadding() + profile.terrain().clearVolumePadding());
        TERRAIN_PREPARER.beforePlacement(targetLevel, profile, structureOrigin, templateBounds);

        boolean placed = StructurePlacementService.place(profile, template, targetLevel, structureOrigin, settings);
        if (!placed) {
            CustomizablePlayerSpawnCommon.LOGGER.error("Failed to place start structure {} at {}.", templateDescription, structureOrigin);
            return Optional.empty();
        }

        TERRAIN_PREPARER.afterPlacement(targetLevel, profile, structureOrigin, templateBounds);
        POST_PLACEMENT_PROCESSOR.process(targetLevel, profile, template, structureOrigin, settings, templateBounds);

        Optional<SpawnAnchorResult> anchor = ANCHOR_RESOLVER.resolve(targetLevel, profile, template, structureOrigin, settings, templateBounds);
        if (anchor.isEmpty()) {
            CustomizablePlayerSpawnCommon.LOGGER.error("Unable to resolve a safe spawn anchor for structure {}.", templateDescription);
            return Optional.empty();
        }

        BlockPos spawnPos = anchor.get().spawnPos();
        float spawnAngle = anchor.get().angle();
        SpawnStructureInstance instance = SpawnStructureInstance.create(
                data.nextInstanceId(ownerUuid, mode),
                ownerUuid,
                targetLevel.dimension(),
                structureOrigin,
                spawnPos,
                spawnAngle,
                placementResult.get().bounds(),
                profile.id()
        );
        data.addInstance(instance);

        if (mode == SpawnProfile.SpawnMode.SHARED) {
            targetLevel.setDefaultSpawnPos(spawnPos, spawnAngle);
        }

        CustomizablePlayerSpawnCommon.LOGGER.info(
                "Created {} start structure {} in {} at origin {} with spawn {} using placement={} anchor={} instance={}.",
                mode.name().toLowerCase(),
                templateDescription,
                targetLevel.dimension().location(),
                structureOrigin,
                spawnPos,
                placementResult.get().strategy(),
                anchor.get().source(),
                instance.id()
        );

        return instance.resolve(server);
    }

    private static Optional<SpawnStructureInstance.ResolvedSpawn> createSpawnWithoutStructure(
            MinecraftServer server,
            SpawnStructureSavedData data,
            ServerLevel targetLevel,
            SpawnProfile profile,
            UUID ownerUuid,
            SpawnProfile.SpawnMode mode
    ) {
        StructureTemplateBounds standaloneBounds = StructureTemplateBounds.full(STANDALONE_SPAWN_FOOTPRINT);
        List<SpawnStructureInstance> existingInstances = mode == SpawnProfile.SpawnMode.PER_PLAYER
                ? data.instancesInDimension(targetLevel.dimension())
                : List.of();
        Optional<PlacementResult> origin = PLACEMENT_ENGINE.findOrigin(targetLevel, profile, standaloneBounds, existingInstances);
        if (origin.isEmpty()) {
            CustomizablePlayerSpawnCommon.LOGGER.error(
                    "Unable to find a valid standalone spawn position in dimension {} after {} attempts.",
                    targetLevel.dimension().location(),
                    profile.placement().searchAttempts()
            );
            return Optional.empty();
        }

        BlockPos spawnOrigin = origin.get().origin();
        BlockPos spawnPos = spawnOrigin.offset(
                profile.anchor().spawnOffsetX(),
                profile.anchor().spawnOffsetY(),
                profile.anchor().spawnOffsetZ()
        );
        float spawnAngle = profile.spawnAngle();
        SpawnStructureInstance instance = SpawnStructureInstance.create(
                data.nextInstanceId(ownerUuid, mode),
                ownerUuid,
                targetLevel.dimension(),
                spawnOrigin,
                spawnPos,
                spawnAngle,
                origin.get().bounds(),
                profile.id()
        );
        data.addInstance(instance);

        if (mode == SpawnProfile.SpawnMode.SHARED) {
            targetLevel.setDefaultSpawnPos(spawnPos, spawnAngle);
        }

        CustomizablePlayerSpawnCommon.LOGGER.info(
                "Configured {} standalone spawn in {} at {} instance={}.",
                mode.name().toLowerCase(),
                targetLevel.dimension().location(),
                spawnPos,
                instance.id()
        );

        return instance.resolve(server);
    }

    private static void preloadStructureArea(ServerLevel level, BlockPos origin, Vec3i size, int padding) {
        int minChunkX = (origin.getX() - padding) >> 4;
        int maxChunkX = (origin.getX() + Math.max(0, size.getX() - 1) + padding) >> 4;
        int minChunkZ = (origin.getZ() - padding) >> 4;
        int maxChunkZ = (origin.getZ() + Math.max(0, size.getZ() - 1) + padding) >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                level.getChunk(chunkX, chunkZ);
            }
        }
    }

    private static void applyInitialSpawn(
            ServerPlayer player,
            SpawnStructureInstance.ResolvedSpawn spawn,
            SpawnStructureSavedData data,
            SpawnProfile profile
    ) {
        teleportToResolvedSpawn(player, spawn);
        if (profile.spawnPolicy().respawnAtAssignedStructure()) {
            ensureRespawnPosition(player, spawn, profile);
        }

        data.markInitialSpawnApplied(player.getUUID());

        CustomizablePlayerSpawnCommon.LOGGER.info(
                "Applied initial custom spawn for {} at {} in {} instance={}.",
                player.getGameProfile().getName(),
                spawn.spawnPos(),
                spawn.level().dimension().location(),
                spawn.instance().id()
        );
    }

    private static void teleportToResolvedSpawn(ServerPlayer player, SpawnStructureInstance.ResolvedSpawn spawn) {
        Vec3 position = spawn.teleportPosition();
        player.teleportTo(spawn.level(), position.x, position.y, position.z, spawn.spawnAngle(), 0.0F);
    }

    private static void ensureRespawnPosition(ServerPlayer player, SpawnStructureInstance.ResolvedSpawn spawn, SpawnProfile profile) {
        if (profile.spawnPolicy().respectBedsAndAnchors() && player.getRespawnPosition() != null) {
            return;
        }

        player.setRespawnPosition(spawn.level().dimension(), spawn.spawnPos(), spawn.spawnAngle(), true, false);
    }

    private static boolean isExistingPlayer(ServerPlayer player) {
        return player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) > 0;
    }
}
