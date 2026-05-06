package com.mayo.customizableplayerspawn.anchor;

import com.mayo.customizableplayerspawn.CustomizablePlayerSpawnMod;
import com.mayo.customizableplayerspawn.SpawnProfile;
import com.mayo.customizableplayerspawn.structure.StructurePlacementService;
import com.mayo.customizableplayerspawn.structure.StructureTemplateBounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class SpawnAnchorResolver {
    private static final String STRUCTURE_BLOCK_MODE_TAG = "mode";
    private static final String STRUCTURE_BLOCK_METADATA_TAG = "metadata";
    private static final String STRUCTURE_BLOCK_DATA_MODE = "DATA";

    private final SpawnPointValidator validator = new SpawnPointValidator();

    public Optional<SpawnAnchorResult> resolve(
            ServerLevel level,
            SpawnProfile profile,
            StructureTemplate template,
            BlockPos structureOrigin,
            StructurePlaceSettings settings,
            StructureTemplateBounds bounds
    ) {
        List<AnchorCandidate> candidates = candidates(profile, template, structureOrigin, settings, bounds);
        BlockPos transformedOffset = StructureTemplate.calculateRelativePosition(
                settings,
                new BlockPos(profile.anchor().spawnOffsetX(), profile.anchor().spawnOffsetY(), profile.anchor().spawnOffsetZ())
        );

        for (AnchorCandidate candidate : candidates) {
            BlockPos spawnPos = candidate.pos().offset(transformedOffset.getX(), transformedOffset.getY(), transformedOffset.getZ());
            if (!validator.isSafe(level, profile, spawnPos)) {
                Optional<BlockPos> safeNearby = safeNearby(level, profile, spawnPos);
                if (safeNearby.isEmpty()) {
                    CustomizablePlayerSpawnMod.LOGGER.warn("Spawn anchor {} at {} is unsafe and no nearby safe fallback was found.", candidate.source(), spawnPos);
                    continue;
                }

                removeMarkerBlocksIfNeeded(level, profile, candidate);
                return Optional.of(new SpawnAnchorResult(candidate.pos(), safeNearby.get(), profile.spawnAngle(), candidate.source() + ":safe_nearby"));
            }

            removeMarkerBlocksIfNeeded(level, profile, candidate);
            return Optional.of(new SpawnAnchorResult(candidate.pos(), spawnPos, profile.spawnAngle(), candidate.source()));
        }

        return fallback(level, profile, structureOrigin, bounds, transformedOffset);
    }

    public boolean hasTemplateAnchor(SpawnProfile profile, StructureTemplate template, StructurePlaceSettings settings) {
        return !markerBlocks(profile, template, BlockPos.ZERO, settings).isEmpty()
                || dataMarker(profile, template, BlockPos.ZERO, settings).isPresent()
                || profile.anchor().mode() == SpawnProfile.AnchorMode.RELATIVE
                || profile.anchor().mode() == SpawnProfile.AnchorMode.CENTER
                || profile.anchor().fallback() != SpawnProfile.AnchorFallback.FAIL;
    }

    private List<AnchorCandidate> candidates(
            SpawnProfile profile,
            StructureTemplate template,
            BlockPos structureOrigin,
            StructurePlaceSettings settings,
            StructureTemplateBounds bounds
    ) {
        List<AnchorCandidate> candidates = new ArrayList<>();
        SpawnProfile.AnchorMode mode = profile.anchor().mode();

        if (mode == SpawnProfile.AnchorMode.AUTO || mode == SpawnProfile.AnchorMode.MARKER_BLOCK) {
            for (StructureTemplate.StructureBlockInfo marker : markerBlocks(profile, template, structureOrigin, settings)) {
                candidates.add(new AnchorCandidate(marker.pos(), "marker_block", List.of(marker.pos())));
            }
        }

        if (mode == SpawnProfile.AnchorMode.AUTO || mode == SpawnProfile.AnchorMode.DATA_MARKER) {
            dataMarker(profile, template, structureOrigin, settings)
                    .ifPresent(marker -> candidates.add(new AnchorCandidate(marker.pos(), "data_marker", List.of())));
        }

        if (mode == SpawnProfile.AnchorMode.RELATIVE) {
            candidates.add(new AnchorCandidate(
                    structureOrigin.offset(profile.anchor().relativeX(), profile.anchor().relativeY(), profile.anchor().relativeZ()),
                    "relative",
                    List.of()
            ));
        }

        if (mode == SpawnProfile.AnchorMode.CENTER) {
            candidates.add(new AnchorCandidate(
                    structureOrigin.offset(bounds.centerX(), bounds.minY(), bounds.centerZ()),
                    "center",
                    List.of()
            ));
        }

        return candidates;
    }

    private Optional<SpawnAnchorResult> fallback(
            ServerLevel level,
            SpawnProfile profile,
            BlockPos structureOrigin,
            StructureTemplateBounds bounds,
            BlockPos transformedOffset
    ) {
        if (profile.anchor().fallback() == SpawnProfile.AnchorFallback.FAIL) {
            return Optional.empty();
        }

        BlockPos fallbackMarker = structureOrigin.offset(bounds.centerX(), bounds.minY(), bounds.centerZ());
        BlockPos fallbackSpawn = fallbackMarker.offset(transformedOffset.getX(), transformedOffset.getY(), transformedOffset.getZ());
        if (profile.anchor().fallback() == SpawnProfile.AnchorFallback.SAFE_NEARBY) {
            fallbackSpawn = safeNearby(level, profile, fallbackSpawn).orElse(fallbackSpawn);
        }

        if (!validator.isSafe(level, profile, fallbackSpawn)) {
            return Optional.empty();
        }

        return Optional.of(new SpawnAnchorResult(fallbackMarker, fallbackSpawn, profile.spawnAngle(), profile.anchor().fallback().name().toLowerCase()));
    }

    private Optional<BlockPos> safeNearby(ServerLevel level, SpawnProfile profile, BlockPos origin) {
        int radius = 6;
        for (int dy = -2; dy <= 4; dy++) {
            for (int distance = 0; distance <= radius; distance++) {
                for (int dx = -distance; dx <= distance; dx++) {
                    for (int dz = -distance; dz <= distance; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != distance) {
                            continue;
                        }

                        BlockPos candidate = origin.offset(dx, dy, dz);
                        if (validator.isSafe(level, profile, candidate)) {
                            return Optional.of(candidate);
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    private List<StructureTemplate.StructureBlockInfo> markerBlocks(
            SpawnProfile profile,
            StructureTemplate template,
            BlockPos structureOrigin,
            StructurePlaceSettings settings
    ) {
        Optional<Block> block = markerBlock(profile);
        return block.map(value -> template.filterBlocks(structureOrigin, settings, value)).orElseGet(List::of);
    }

    private Optional<StructureTemplate.StructureBlockInfo> dataMarker(
            SpawnProfile profile,
            StructureTemplate template,
            BlockPos structureOrigin,
            StructurePlaceSettings settings
    ) {
        String dataMarkerName = profile.dataMarkerName();
        if (dataMarkerName.isEmpty()) {
            return Optional.empty();
        }

        return template.filterBlocks(structureOrigin, settings, Blocks.STRUCTURE_BLOCK)
                .stream()
                .filter(blockInfo -> isMatchingDataMarker(blockInfo.nbt(), dataMarkerName))
                .findFirst();
    }

    private Optional<Block> markerBlock(SpawnProfile profile) {
        String markerBlockName = profile.spawnMarkerBlockName();
        if (markerBlockName.isEmpty()) {
            return Optional.empty();
        }

        ResourceLocation markerBlockId = ResourceLocation.tryParse(markerBlockName);
        if (markerBlockId == null) {
            CustomizablePlayerSpawnMod.LOGGER.error("Configured anchor.markerBlock {} is not a valid resource location.", markerBlockName);
            return Optional.empty();
        }

        Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(markerBlockId);
        if (block.isEmpty() || block.get() == Blocks.AIR) {
            CustomizablePlayerSpawnMod.LOGGER.error("Configured anchor.markerBlock {} does not exist as a loaded block.", markerBlockId);
            return Optional.empty();
        }

        return block;
    }

    private void removeMarkerBlocksIfNeeded(ServerLevel level, SpawnProfile profile, AnchorCandidate candidate) {
        if (!profile.anchor().removeMarkerBlock()) {
            return;
        }

        int flags = StructurePlacementService.worldEditFlags(profile);
        for (BlockPos markerPos : candidate.markerBlocksToRemove()) {
            level.setBlock(markerPos, Blocks.AIR.defaultBlockState(), flags);
        }
    }

    private boolean isMatchingDataMarker(CompoundTag tag, String markerName) {
        return tag != null
                && STRUCTURE_BLOCK_DATA_MODE.equals(tag.getString(STRUCTURE_BLOCK_MODE_TAG))
                && markerName.equals(tag.getString(STRUCTURE_BLOCK_METADATA_TAG));
    }

    private record AnchorCandidate(BlockPos pos, String source, List<BlockPos> markerBlocksToRemove) {
    }
}
