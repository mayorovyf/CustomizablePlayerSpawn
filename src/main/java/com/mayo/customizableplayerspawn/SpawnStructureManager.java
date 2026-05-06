package com.mayo.customizableplayerspawn;

import com.mayo.customizableplayerspawn.anchor.SpawnAnchorResolver;
import com.mayo.customizableplayerspawn.anchor.SpawnAnchorResult;
import com.mayo.customizableplayerspawn.placement.PlacementEngine;
import com.mayo.customizableplayerspawn.placement.PlacementResult;
import com.mayo.customizableplayerspawn.postprocess.PostPlacementProcessor;
import com.mayo.customizableplayerspawn.structure.StructurePlacementService;
import com.mayo.customizableplayerspawn.structure.StructureTemplateBounds;
import com.mayo.customizableplayerspawn.structure.StructureTemplateProvider;
import com.mayo.customizableplayerspawn.terrain.TerrainPreparer;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class SpawnStructureManager {
    private static final String PLAYER_SPAWN_DATA_KEY = CustomizablePlayerSpawnMod.MODID + ".initial_spawn_applied";
    private static final Vec3i STANDALONE_SPAWN_FOOTPRINT = new Vec3i(1, 2, 1);
    private static final StructureTemplateProvider STRUCTURE_TEMPLATE_PROVIDER = new StructureTemplateProvider();
    private static final PlacementEngine PLACEMENT_ENGINE = new PlacementEngine();
    private static final TerrainPreparer TERRAIN_PREPARER = new TerrainPreparer();
    private static final SpawnAnchorResolver ANCHOR_RESOLVER = new SpawnAnchorResolver();
    private static final PostPlacementProcessor POST_PLACEMENT_PROCESSOR = new PostPlacementProcessor();

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        SpawnProfiles.reload();
        PostPlacementProcessor.clear();
    }

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
    public void onPlayerRespawnPosition(PlayerRespawnPositionEvent event) {
        if (event.isFromEndFight()) {
            return;
        }

        ServerPlayer player = (ServerPlayer) event.getEntity();

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
        event.setDimensionTransition(new DimensionTransition(
                spawn.level(),
                spawn.teleportPosition(),
                Vec3.ZERO,
                spawn.spawnAngle(),
                0.0F,
                DimensionTransition.DO_NOTHING
        ));
        event.setCopyOriginalSpawnPosition(false);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        PostPlacementProcessor.tick();
    }

    private static Optional<SpawnStructureSavedData.ResolvedSpawn> createConfiguredSpawn(MinecraftServer server, SpawnStructureSavedData data) {
        SpawnProfile profile = SpawnProfiles.active();
        ServerLevel targetLevel = server.getLevel(profile.targetDimensionKey());
        if (targetLevel == null) {
            CustomizablePlayerSpawnMod.LOGGER.error("Configured spawn dimension {} does not exist.", profile.targetDimensionKey().location());
            return Optional.empty();
        }

        if (!profile.usesStructurePlacement()) {
            return createSpawnWithoutStructure(server, data, targetLevel, profile);
        }

        Optional<com.mayo.customizableplayerspawn.structure.LoadedStructureTemplate> loadedTemplate = STRUCTURE_TEMPLATE_PROVIDER.resolve(server, profile);
        if (loadedTemplate.isEmpty()) {
            return Optional.empty();
        }

        com.mayo.customizableplayerspawn.structure.LoadedStructureTemplate configuredTemplate = loadedTemplate.get();
        String templateDescription = configuredTemplate.description();
        StructureTemplate template = configuredTemplate.template();
        StructureTemplateBounds templateBounds = configuredTemplate.bounds();
        StructurePlaceSettings settings = StructurePlacementService.createPlacementSettings();
        if (!ANCHOR_RESOLVER.hasTemplateAnchor(profile, template, settings)) {
            CustomizablePlayerSpawnMod.LOGGER.error(
                    "Structure template {} has no usable spawn anchor and anchor.fallback is FAIL.",
                    templateDescription
            );
            return Optional.empty();
        }

        Optional<PlacementResult> placementResult = PLACEMENT_ENGINE.findOrigin(targetLevel, profile, templateBounds);
        if (placementResult.isEmpty()) {
            CustomizablePlayerSpawnMod.LOGGER.error(
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
            CustomizablePlayerSpawnMod.LOGGER.error("Failed to place start structure {} at {}.", templateDescription, structureOrigin);
            return Optional.empty();
        }

        TERRAIN_PREPARER.afterPlacement(targetLevel, profile, structureOrigin, templateBounds);
        POST_PLACEMENT_PROCESSOR.process(targetLevel, profile, template, structureOrigin, settings, templateBounds);

        Optional<SpawnAnchorResult> anchor = ANCHOR_RESOLVER.resolve(targetLevel, profile, template, structureOrigin, settings, templateBounds);
        if (anchor.isEmpty()) {
            CustomizablePlayerSpawnMod.LOGGER.error("Unable to resolve a safe spawn anchor for structure {}.", templateDescription);
            return Optional.empty();
        }

        BlockPos spawnPos = anchor.get().spawnPos();
        float spawnAngle = anchor.get().angle();

        targetLevel.setDefaultSpawnPos(spawnPos, spawnAngle);
        data.setGenerated(targetLevel.dimension(), spawnPos, spawnAngle, structureOrigin);

        CustomizablePlayerSpawnMod.LOGGER.info(
                "Created start structure {} in {} at origin {} with spawn {} using placement={} anchor={}.",
                templateDescription,
                targetLevel.dimension().location(),
                structureOrigin,
                spawnPos,
                placementResult.get().strategy(),
                anchor.get().source()
        );

        return data.resolve(server);
    }

    private static Optional<SpawnStructureSavedData.ResolvedSpawn> createSpawnWithoutStructure(
            MinecraftServer server,
            SpawnStructureSavedData data,
            ServerLevel targetLevel,
            SpawnProfile profile
    ) {
        Optional<PlacementResult> origin = PLACEMENT_ENGINE.findOrigin(targetLevel, profile, StructureTemplateBounds.full(STANDALONE_SPAWN_FOOTPRINT));
        if (origin.isEmpty()) {
            CustomizablePlayerSpawnMod.LOGGER.error(
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

        targetLevel.setDefaultSpawnPos(spawnPos, spawnAngle);
        data.setGenerated(targetLevel.dimension(), spawnPos, spawnAngle, spawnOrigin);

        CustomizablePlayerSpawnMod.LOGGER.info(
                "Configured standalone spawn in {} at {}.",
                targetLevel.dimension().location(),
                spawnPos
        );

        return data.resolve(server);
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

}
