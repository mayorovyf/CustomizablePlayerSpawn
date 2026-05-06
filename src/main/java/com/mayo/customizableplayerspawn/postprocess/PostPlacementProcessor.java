package com.mayo.customizableplayerspawn.postprocess;

import com.mayo.customizableplayerspawn.CustomizablePlayerSpawnMod;
import com.mayo.customizableplayerspawn.SpawnProfile;
import com.mayo.customizableplayerspawn.structure.StructurePlacementService;
import com.mayo.customizableplayerspawn.structure.StructureTemplateBounds;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class PostPlacementProcessor {
    private static final String STRUCTURE_BLOCK_MODE_TAG = "mode";
    private static final String STRUCTURE_BLOCK_METADATA_TAG = "metadata";
    private static final String STRUCTURE_BLOCK_DATA_MODE = "DATA";
    private static final int LIGHT_REFRESH_PADDING = 2;
    private static final ArrayList<PendingClientChunkRefresh> PENDING_CLIENT_CHUNK_REFRESHES = new ArrayList<>();

    public void process(
            ServerLevel level,
            SpawnProfile profile,
            StructureTemplate template,
            BlockPos structureOrigin,
            StructurePlaceSettings settings,
            StructureTemplateBounds bounds
    ) {
        if (profile.postProcess().processDataMarkers()) {
            processKnownDataMarkers(level, template, structureOrigin, settings);
        }

        if (profile.postProcess().stabilizeBlocks() || profile.postProcess().updateNeighborShapes()) {
            stabilizeBlocks(level, profile, structureOrigin, bounds);
        }

        if (profile.postProcess().refreshLighting()) {
            refreshStructureLighting(level, profile, structureOrigin, bounds);
        }
    }

    public static void tick() {
        if (PENDING_CLIENT_CHUNK_REFRESHES.isEmpty()) {
            return;
        }

        Iterator<PendingClientChunkRefresh> iterator = PENDING_CLIENT_CHUNK_REFRESHES.iterator();
        while (iterator.hasNext()) {
            PendingClientChunkRefresh refresh = iterator.next();
            refresh.ticksRemaining--;
            if (refresh.ticksRemaining > 0) {
                continue;
            }

            resendChunksWithLight(refresh.level, refresh.chunkPositions);
            iterator.remove();
        }
    }

    public static void clear() {
        PENDING_CLIENT_CHUNK_REFRESHES.clear();
    }

    private void processKnownDataMarkers(
            ServerLevel level,
            StructureTemplate template,
            BlockPos structureOrigin,
            StructurePlaceSettings settings
    ) {
        for (StructureTemplate.StructureBlockInfo blockInfo : template.filterBlocks(structureOrigin, settings, Blocks.STRUCTURE_BLOCK)) {
            CompoundTag tag = blockInfo.nbt();
            if (tag == null || !STRUCTURE_BLOCK_DATA_MODE.equals(tag.getString(STRUCTURE_BLOCK_MODE_TAG))) {
                continue;
            }

            handleDataMarker(level, blockInfo.pos(), tag.getString(STRUCTURE_BLOCK_METADATA_TAG), settings);
        }
    }

    private void handleDataMarker(ServerLevel level, BlockPos pos, String metadata, StructurePlaceSettings settings) {
        if (metadata.startsWith("Elytra")) {
            ItemFrame itemFrame = new ItemFrame(level, pos, settings.getRotation().rotate(Direction.SOUTH));
            itemFrame.setItem(new ItemStack(Items.ELYTRA), false);
            level.addFreshEntity(itemFrame);
        }
    }

    private void stabilizeBlocks(ServerLevel level, SpawnProfile profile, BlockPos structureOrigin, StructureTemplateBounds bounds) {
        int flags = StructurePlacementService.worldEditFlags(profile);
        int minX = structureOrigin.getX() + bounds.minX() - 1;
        int maxX = structureOrigin.getX() + bounds.maxX() + 1;
        int minY = Math.max(level.getMinBuildHeight(), structureOrigin.getY() + bounds.minY() - 1);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, structureOrigin.getY() + bounds.maxY() + 1);
        int minZ = structureOrigin.getZ() + bounds.minZ() - 1;
        int maxZ = structureOrigin.getZ() + bounds.maxZ() + 1;
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        int touchedBlocks = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutablePos.set(x, y, z);
                    if (level.getBlockState(mutablePos).isAir()) {
                        continue;
                    }

                    if (profile.postProcess().updateNeighborShapes()) {
                        level.updateNeighborsAt(mutablePos, level.getBlockState(mutablePos).getBlock());
                    } else {
                        level.setBlock(mutablePos, level.getBlockState(mutablePos), flags);
                    }
                    touchedBlocks++;
                }
            }
        }

        CustomizablePlayerSpawnMod.LOGGER.info("Stabilized {} blocks around start structure at {}.", touchedBlocks, structureOrigin);
    }

    private void refreshStructureLighting(ServerLevel level, SpawnProfile profile, BlockPos structureOrigin, StructureTemplateBounds bounds) {
        int minX = structureOrigin.getX() + bounds.minX() - LIGHT_REFRESH_PADDING;
        int maxX = structureOrigin.getX() + bounds.maxX() + LIGHT_REFRESH_PADDING;
        int minY = Math.max(level.getMinBuildHeight(), structureOrigin.getY() + bounds.minY() - LIGHT_REFRESH_PADDING);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, structureOrigin.getY() + bounds.maxY() + LIGHT_REFRESH_PADDING);
        int minZ = structureOrigin.getZ() + bounds.minZ() - LIGHT_REFRESH_PADDING;
        int maxZ = structureOrigin.getZ() + bounds.maxZ() + LIGHT_REFRESH_PADDING;
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        int checkedBlocks = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    level.getLightEngine().checkBlock(mutablePos.set(x, y, z));
                    checkedBlocks++;
                }
            }
        }

        level.getChunkSource().getLightEngine().tryScheduleUpdate();
        Set<Long> chunkPositions = chunkPositionsForBox(minX, maxX, minZ, maxZ);
        scheduleClientChunkLightRefresh(level, profile, chunkPositions);
        CustomizablePlayerSpawnMod.LOGGER.info(
                "Queued light refresh for {} blocks across {} chunks around start structure at {}.",
                checkedBlocks,
                chunkPositions.size(),
                structureOrigin
        );
    }

    private Set<Long> chunkPositionsForBox(int minX, int maxX, int minZ, int maxZ) {
        Set<Long> chunkPositions = new LinkedHashSet<>();
        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                chunkPositions.add(ChunkPos.asLong(chunkX, chunkZ));
            }
        }

        return chunkPositions;
    }

    private void scheduleClientChunkLightRefresh(ServerLevel level, SpawnProfile profile, Set<Long> chunkPositions) {
        if (chunkPositions.isEmpty()) {
            return;
        }

        for (int delay : profile.postProcess().refreshLightingDelays()) {
            PENDING_CLIENT_CHUNK_REFRESHES.add(new PendingClientChunkRefresh(level, chunkPositions, delay));
        }
    }

    private static void resendChunksWithLight(ServerLevel level, Set<Long> chunkPositions) {
        if (level.players().isEmpty()) {
            return;
        }

        int sentPackets = 0;
        for (long chunkPosition : chunkPositions) {
            int chunkX = ChunkPos.getX(chunkPosition);
            int chunkZ = ChunkPos.getZ(chunkPosition);
            LevelChunk chunk = level.getChunk(chunkX, chunkZ);
            ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null);
            for (ServerPlayer player : level.players()) {
                player.connection.send(packet);
                sentPackets++;
            }
        }

        CustomizablePlayerSpawnMod.LOGGER.info(
                "Resent {} start structure chunks with light data to {} players using {} packets.",
                chunkPositions.size(),
                level.players().size(),
                sentPackets
        );
    }

    private static final class PendingClientChunkRefresh {
        private final ServerLevel level;
        private final Set<Long> chunkPositions;
        private int ticksRemaining;

        private PendingClientChunkRefresh(ServerLevel level, Set<Long> chunkPositions, int ticksRemaining) {
            this.level = level;
            this.chunkPositions = new LinkedHashSet<>(chunkPositions);
            this.ticksRemaining = ticksRemaining;
        }
    }
}
