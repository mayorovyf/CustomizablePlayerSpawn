package com.mayo.customizableplayerspawn;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public record SpawnProfile(
        int schemaVersion,
        String id,
        boolean enabled,
        int priority,
        String targetDimension,
        List<String> allowedBiomes,
        StructureSettings structure,
        AnchorSettings anchor,
        PlacementSettings placement,
        TerrainSettings terrain,
        PostProcessSettings postProcess,
        SpawnPolicySettings spawnPolicy,
        ProtectionSettings protection
) {
    public static SpawnProfile legacy() {
        return new SpawnProfile(
                1,
                "legacy",
                true,
                0,
                "minecraft:the_end",
                List.of(),
                new StructureSettings("minecraft:end_city/ship", ""),
                new AnchorSettings(
                        AnchorMode.AUTO,
                        CustomizablePlayerSpawnCommon.MODID + ":player_spawn_marker",
                        true,
                        "Elytra",
                        0,
                        -1,
                        1,
                        180.0F,
                        AnchorFallback.SAFE_NEARBY,
                        0,
                        0,
                        0
                ),
                new PlacementSettings(
                        PlacementStrategy.AUTO,
                        0,
                        1,
                        SurfaceSearchMode.HEIGHTMAP,
                        96,
                        0,
                        0,
                        0,
                        0,
                        3,
                        false,
                        true,
                        true,
                        List.of("minecraft:lava", "minecraft:magma_block"),
                        0.85D,
                        0,
                        true
                ),
                new TerrainSettings(
                        TerrainPreparationMode.NONE,
                        10,
                        "minecraft:grass_block",
                        "minecraft:dirt",
                        "minecraft:stone",
                        true,
                        true,
                        2,
                        true,
                        8,
                        8,
                        6
                ),
                new PostProcessSettings(true, List.of(5, 20, 60), true, true, true, true),
                new SpawnPolicySettings(
                        SpawnMode.SHARED,
                        true,
                        true,
                        true,
                        0,
                        512
                ),
                new ProtectionSettings(
                        false,
                        true,
                        true,
                        false,
                        true,
                        true,
                        true,
                        true,
                        false,
                        0
                )
        );
    }

    public ResourceKey<Level> targetDimensionKey() {
        return ResourceKey.create(Registries.DIMENSION, parseResourceLocation(targetDimension, Level.OVERWORLD.location()));
    }

    public ResourceLocation structureTemplateId() {
        return parseResourceLocation(
                structure.template(),
                ResourceLocation.tryParse(CustomizablePlayerSpawnCommon.MODID + ":start_spawn")
        );
    }

    public boolean hasStructureTemplate() {
        return !structure.template().trim().isEmpty();
    }

    public boolean hasExternalStructureFile() {
        return !structure.externalFile().trim().isEmpty();
    }

    public boolean usesStructurePlacement() {
        return hasExternalStructureFile() || hasStructureTemplate();
    }

    public String externalStructureFileName() {
        return structure.externalFile().trim();
    }

    public Path externalStructureBaseDirectory() {
        return CustomizablePlayerSpawnCommon.platform().configDir().resolve(CustomizablePlayerSpawnCommon.MODID).resolve("structures");
    }

    public Optional<Path> externalStructureTemplatePath() {
        return resolveExternalStructurePath(structure.externalFile());
    }

    public String spawnMarkerBlockName() {
        return anchor.markerBlock().trim();
    }

    public String dataMarkerName() {
        return anchor.dataMarker().trim();
    }

    public float spawnAngle() {
        return anchor.spawnAngle();
    }

    public Set<ResourceLocation> allowedBiomeIds() {
        return resourceLocationSet(allowedBiomes);
    }

    public boolean allowsAnyBiome() {
        return allowedBiomes.isEmpty();
    }

    public Set<ResourceLocation> forbiddenSurfaceBlockIds() {
        return resourceLocationSet(placement.forbiddenSurfaceBlocks());
    }

    public SurfaceSearchMode surfaceSearchMode() {
        return placement.surfaceSearchMode();
    }

    public TerrainPreparationMode terrainPreparationMode() {
        return terrain.mode();
    }

    public PlacementStrategy placementStrategy() {
        if (placement.strategy() == PlacementStrategy.AUTO) {
            if (terrain.mode() == TerrainPreparationMode.ISLAND) {
                return PlacementStrategy.ISLAND;
            }

            return switch (placement.surfaceSearchMode()) {
                case HEIGHTMAP -> PlacementStrategy.SURFACE;
                case SMART -> PlacementStrategy.FLAT_FOOTPRINT;
                case ABSOLUTE_Y -> PlacementStrategy.DIRECT;
            };
        }

        return placement.strategy();
    }

    public BlockState terrainTopBlockState() {
        return blockStateByName(terrain.topBlock(), Blocks.GRASS_BLOCK);
    }

    public BlockState terrainFillBlockState() {
        return blockStateByName(terrain.fillBlock(), Blocks.DIRT);
    }

    public BlockState terrainCoreBlockState() {
        return blockStateByName(terrain.coreBlock(), Blocks.STONE);
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    private static Set<ResourceLocation> resourceLocationSet(List<String> values) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (String entry : values) {
            ResourceLocation id = ResourceLocation.tryParse(entry);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static ResourceLocation parseResourceLocation(String value, ResourceLocation fallback) {
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        return parsed != null ? parsed : fallback;
    }

    private Optional<Path> resolveExternalStructurePath(String value) {
        String normalizedFileName = normalizeExternalStructureFileName(value);
        if (normalizedFileName.isEmpty()) {
            return Optional.empty();
        }

        try {
            Path relativePath = Paths.get(normalizedFileName).normalize();
            if (relativePath.isAbsolute() || relativePath.startsWith("..")) {
                return Optional.empty();
            }

            return Optional.of(externalStructureBaseDirectory().resolve(relativePath).normalize());
        } catch (InvalidPathException exception) {
            return Optional.empty();
        }
    }

    private static String normalizeExternalStructureFileName(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        return trimmed.endsWith(".nbt") ? trimmed : trimmed + ".nbt";
    }

    private static BlockState blockStateByName(String name, Block fallback) {
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null) {
            return fallback.defaultBlockState();
        }

        return BuiltInRegistries.BLOCK.getOptional(id)
                .map(Block::defaultBlockState)
                .orElseGet(fallback::defaultBlockState);
    }

    public enum SurfaceSearchMode {
        HEIGHTMAP,
        SMART,
        ABSOLUTE_Y;

        public static SurfaceSearchMode byName(String name) {
            return tryParse(name).orElse(HEIGHTMAP);
        }

        public static Optional<SurfaceSearchMode> tryParse(String name) {
            if (name == null) {
                return Optional.empty();
            }

            for (SurfaceSearchMode mode : values()) {
                if (mode.name().equalsIgnoreCase(name.trim())) {
                    return Optional.of(mode);
                }
            }

            return Optional.empty();
        }
    }

    public enum TerrainPreparationMode {
        NONE,
        ISLAND;

        public static TerrainPreparationMode byName(String name) {
            return tryParse(name).orElse(NONE);
        }

        public static Optional<TerrainPreparationMode> tryParse(String name) {
            if (name == null) {
                return Optional.empty();
            }

            for (TerrainPreparationMode mode : values()) {
                if (mode.name().equalsIgnoreCase(name.trim())) {
                    return Optional.of(mode);
                }
            }

            return Optional.empty();
        }
    }

    public enum PlacementStrategy {
        AUTO,
        DIRECT,
        SURFACE,
        FLAT_FOOTPRINT,
        EMBEDDED,
        ISLAND;

        public static PlacementStrategy byName(String name) {
            return tryParse(name).orElse(AUTO);
        }

        public static Optional<PlacementStrategy> tryParse(String name) {
            if (name == null) {
                return Optional.empty();
            }

            for (PlacementStrategy strategy : values()) {
                if (strategy.name().equalsIgnoreCase(name.trim())) {
                    return Optional.of(strategy);
                }
            }

            return Optional.empty();
        }
    }

    public enum AnchorMode {
        AUTO,
        MARKER_BLOCK,
        DATA_MARKER,
        RELATIVE,
        CENTER;

        public static AnchorMode byName(String name) {
            return tryParse(name).orElse(AUTO);
        }

        public static Optional<AnchorMode> tryParse(String name) {
            if (name == null) {
                return Optional.empty();
            }

            for (AnchorMode mode : values()) {
                if (mode.name().equalsIgnoreCase(name.trim())) {
                    return Optional.of(mode);
                }
            }

            return Optional.empty();
        }
    }

    public enum AnchorFallback {
        FAIL,
        CENTER,
        SAFE_NEARBY;

        public static AnchorFallback byName(String name) {
            return tryParse(name).orElse(SAFE_NEARBY);
        }

        public static Optional<AnchorFallback> tryParse(String name) {
            if (name == null) {
                return Optional.empty();
            }

            for (AnchorFallback fallback : values()) {
                if (fallback.name().equalsIgnoreCase(name.trim())) {
                    return Optional.of(fallback);
                }
            }

            return Optional.empty();
        }
    }

    public enum SpawnMode {
        SHARED,
        PER_PLAYER;

        public static SpawnMode byName(String name) {
            return tryParse(name).orElse(SHARED);
        }

        public static Optional<SpawnMode> tryParse(String name) {
            if (name == null) {
                return Optional.empty();
            }

            for (SpawnMode mode : values()) {
                if (mode.name().equalsIgnoreCase(name.trim())) {
                    return Optional.of(mode);
                }
            }

            return Optional.empty();
        }
    }

    public record StructureSettings(String template, String externalFile) {
    }

    public record AnchorSettings(
            AnchorMode mode,
            String markerBlock,
            boolean removeMarkerBlock,
            String dataMarker,
            int spawnOffsetX,
            int spawnOffsetY,
            int spawnOffsetZ,
            float spawnAngle,
            AnchorFallback fallback,
            int relativeX,
            int relativeY,
            int relativeZ
    ) {
    }

    public record PlacementSettings(
            PlacementStrategy strategy,
            int searchRadius,
            int searchAttempts,
            SurfaceSearchMode surfaceSearchMode,
            int absoluteY,
            int placementY,
            int surfaceYOffset,
            int smartSearchTopOffset,
            int smartSearchBottomOffset,
            int maxSurfaceStep,
            boolean allowFluidsBelow,
            boolean allowReplaceableAtFeet,
            boolean allowReplaceableAtHead,
            List<String> forbiddenSurfaceBlocks,
            double minLandRatio,
            int maxTreeOverlap,
            boolean avoidFluids
    ) {
    }

    public record TerrainSettings(
            TerrainPreparationMode mode,
            int terrainPadding,
            String topBlock,
            String fillBlock,
            String coreBlock,
            boolean edgeNoise,
            boolean clearStructureVolume,
            int clearVolumePadding,
            boolean fillSupportVoids,
            int supportVoidMaxFillDepth,
            int islandMaxDrop,
            int islandEdgeFalloff
    ) {
    }

    public record PostProcessSettings(
            boolean refreshLighting,
            List<Integer> refreshLightingDelays,
            boolean suppressDrops,
            boolean stabilizeBlocks,
            boolean updateNeighborShapes,
            boolean processDataMarkers
    ) {
    }

    public record SpawnPolicySettings(
            SpawnMode mode,
            boolean assignExistingPlayers,
            boolean respawnAtAssignedStructure,
            boolean respectBedsAndAnchors,
            int maxInstances,
            int minDistanceBetweenInstances
    ) {
    }

    public record ProtectionSettings(
            boolean enabled,
            boolean protectBlocks,
            boolean protectBlockPlacement,
            boolean protectContainers,
            boolean protectExplosions,
            boolean protectFire,
            boolean protectFluids,
            boolean allowOps,
            boolean allowOwner,
            int padding
    ) {
    }

    public static final class Builder {
        private int schemaVersion;
        private String id;
        private boolean enabled;
        private int priority;
        private String targetDimension;
        private List<String> allowedBiomes;
        private StructureSettings structure;
        private AnchorSettings anchor;
        private PlacementSettings placement;
        private TerrainSettings terrain;
        private PostProcessSettings postProcess;
        private SpawnPolicySettings spawnPolicy;
        private ProtectionSettings protection;

        private Builder(SpawnProfile profile) {
            this.schemaVersion = profile.schemaVersion();
            this.id = profile.id();
            this.enabled = profile.enabled();
            this.priority = profile.priority();
            this.targetDimension = profile.targetDimension();
            this.allowedBiomes = List.copyOf(profile.allowedBiomes());
            this.structure = profile.structure();
            this.anchor = profile.anchor();
            this.placement = profile.placement();
            this.terrain = profile.terrain();
            this.postProcess = profile.postProcess();
            this.spawnPolicy = profile.spawnPolicy();
            this.protection = profile.protection();
        }

        public Builder schemaVersion(int schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder targetDimension(String targetDimension) {
            this.targetDimension = targetDimension;
            return this;
        }

        public Builder allowedBiomes(List<String> allowedBiomes) {
            this.allowedBiomes = List.copyOf(allowedBiomes);
            return this;
        }

        public Builder structure(StructureSettings structure) {
            this.structure = structure;
            return this;
        }

        public Builder anchor(AnchorSettings anchor) {
            this.anchor = anchor;
            return this;
        }

        public Builder placement(PlacementSettings placement) {
            this.placement = placement;
            return this;
        }

        public Builder terrain(TerrainSettings terrain) {
            this.terrain = terrain;
            return this;
        }

        public Builder postProcess(PostProcessSettings postProcess) {
            this.postProcess = postProcess;
            return this;
        }

        public Builder spawnPolicy(SpawnPolicySettings spawnPolicy) {
            this.spawnPolicy = spawnPolicy;
            return this;
        }

        public Builder protection(ProtectionSettings protection) {
            this.protection = protection;
            return this;
        }

        public SpawnProfile build() {
            return new SpawnProfile(schemaVersion, id, enabled, priority, targetDimension, allowedBiomes, structure, anchor, placement, terrain, postProcess, spawnPolicy, protection);
        }
    }
}
