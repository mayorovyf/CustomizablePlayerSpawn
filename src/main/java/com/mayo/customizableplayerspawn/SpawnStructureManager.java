package com.mayo.customizableplayerspawn;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;

public class SpawnStructureManager {
    private static final String PLAYER_SPAWN_DATA_KEY = CustomizablePlayerSpawnMod.MODID + ".initial_spawn_applied";
    private static final float DEFAULT_SPAWN_ANGLE = 0.0F;
    private static final String STRUCTURE_BLOCK_MODE_TAG = "mode";
    private static final String STRUCTURE_BLOCK_METADATA_TAG = "metadata";
    private static final String STRUCTURE_BLOCK_DATA_MODE = "DATA";

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

        if (hasInitialSpawnBeenApplied(player)) {
            return;
        }

        applyInitialSpawn(player, resolvedSpawn.get());
    }

    @SubscribeEvent
    public void onPlayerRespawnPosition(PlayerRespawnPositionEvent event) {
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

        Optional<BlockPos> origin = findStructureOrigin(targetLevel);
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
        Optional<BlockPos> origin = findStructureOrigin(targetLevel);
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
                    NbtIo.readCompressed(absoluteConfiguredPath, NbtAccounter.unlimitedHeap())
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

    private static Optional<BlockPos> findStructureOrigin(ServerLevel level) {
        Set<ResourceLocation> allowedBiomes = Config.allowedBiomeIds();
        if (!Config.allowsAnyBiome() && allowedBiomes.isEmpty()) {
            CustomizablePlayerSpawnMod.LOGGER.error("The allowed biome list contains no valid biome ids.");
            return Optional.empty();
        }

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
        Vec3 position = spawn.teleportPosition();
        player.teleportTo(spawn.level(), position.x, position.y, position.z, spawn.spawnAngle(), 0.0F);
        player.setRespawnPosition(spawn.level().dimension(), spawn.spawnPos(), spawn.spawnAngle(), true, false);

        markInitialSpawnApplied(player);

        CustomizablePlayerSpawnMod.LOGGER.info(
                "Applied initial custom spawn for {} at {} in {}.",
                player.getGameProfile().getName(),
                spawn.spawnPos(),
                spawn.level().dimension().location()
        );
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

    private record LoadedStructureTemplate(String description, StructureTemplate template) {
    }

    private record ConfiguredMarkerBlock(ResourceLocation id, Block block) {
    }
}
