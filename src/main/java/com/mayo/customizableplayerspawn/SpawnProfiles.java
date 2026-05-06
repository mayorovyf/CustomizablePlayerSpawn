package com.mayo.customizableplayerspawn;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.mayo.customizableplayerspawn.profile.SpawnProfileValidationResult;
import com.mayo.customizableplayerspawn.profile.SpawnProfileValidator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.loading.FMLPaths;

public final class SpawnProfiles {
    private static SpawnProfile activeProfile;

    public static final ProfileValue<Integer> SEARCH_RADIUS = new ProfileValue<>(profile -> profile.placement().searchRadius());
    public static final ProfileValue<Integer> SEARCH_ATTEMPTS = new ProfileValue<>(profile -> profile.placement().searchAttempts());
    public static final ProfileValue<Integer> ABSOLUTE_Y = new ProfileValue<>(profile -> profile.placement().absoluteY());
    public static final ProfileValue<Integer> PLACEMENT_Y = new ProfileValue<>(profile -> profile.placement().placementY());
    public static final ProfileValue<Integer> SURFACE_Y_OFFSET = new ProfileValue<>(profile -> profile.placement().surfaceYOffset());
    public static final ProfileValue<Integer> SMART_SEARCH_TOP_OFFSET = new ProfileValue<>(profile -> profile.placement().smartSearchTopOffset());
    public static final ProfileValue<Integer> SMART_SEARCH_BOTTOM_OFFSET = new ProfileValue<>(profile -> profile.placement().smartSearchBottomOffset());
    public static final ProfileValue<Integer> MAX_SURFACE_STEP = new ProfileValue<>(profile -> profile.placement().maxSurfaceStep());
    public static final ProfileValue<Boolean> ALLOW_FLUIDS_BELOW = new ProfileValue<>(profile -> profile.placement().allowFluidsBelow());
    public static final ProfileValue<Boolean> ALLOW_REPLACEABLE_AT_FEET = new ProfileValue<>(profile -> profile.placement().allowReplaceableAtFeet());
    public static final ProfileValue<Boolean> ALLOW_REPLACEABLE_AT_HEAD = new ProfileValue<>(profile -> profile.placement().allowReplaceableAtHead());
    public static final ProfileValue<Boolean> REMOVE_SPAWN_MARKER_BLOCK = new ProfileValue<>(profile -> profile.anchor().removeMarkerBlock());
    public static final ProfileValue<Integer> SPAWN_OFFSET_X = new ProfileValue<>(profile -> profile.anchor().spawnOffsetX());
    public static final ProfileValue<Integer> SPAWN_OFFSET_Y = new ProfileValue<>(profile -> profile.anchor().spawnOffsetY());
    public static final ProfileValue<Integer> SPAWN_OFFSET_Z = new ProfileValue<>(profile -> profile.anchor().spawnOffsetZ());
    public static final ProfileValue<Integer> TERRAIN_PADDING = new ProfileValue<>(profile -> profile.terrain().terrainPadding());
    public static final ProfileValue<Boolean> TERRAIN_EDGE_NOISE = new ProfileValue<>(profile -> profile.terrain().edgeNoise());
    public static final ProfileValue<Boolean> CLEAR_STRUCTURE_VOLUME = new ProfileValue<>(profile -> profile.terrain().clearStructureVolume());
    public static final ProfileValue<Integer> CLEAR_VOLUME_PADDING = new ProfileValue<>(profile -> profile.terrain().clearVolumePadding());
    public static final ProfileValue<Boolean> FILL_SUPPORT_VOIDS = new ProfileValue<>(profile -> profile.terrain().fillSupportVoids());
    public static final ProfileValue<Integer> SUPPORT_VOID_MAX_FILL_DEPTH = new ProfileValue<>(profile -> profile.terrain().supportVoidMaxFillDepth());
    public static final ProfileValue<Integer> ISLAND_MAX_DROP = new ProfileValue<>(profile -> profile.terrain().islandMaxDrop());
    public static final ProfileValue<Integer> ISLAND_EDGE_FALLOFF = new ProfileValue<>(profile -> profile.terrain().islandEdgeFalloff());

    private SpawnProfiles() {
    }

    public static SpawnProfile active() {
        if (activeProfile == null) {
            activeProfile = loadActiveProfile();
        }

        return activeProfile;
    }

    public static void reload() {
        activeProfile = null;
    }

    public static ResourceKey<Level> targetDimensionKey() {
        return active().targetDimensionKey();
    }

    public static ResourceLocation structureTemplateId() {
        return active().structureTemplateId();
    }

    public static boolean hasStructureTemplate() {
        return active().hasStructureTemplate();
    }

    public static boolean hasExternalStructureFile() {
        return active().hasExternalStructureFile();
    }

    public static boolean usesStructurePlacement() {
        return active().usesStructurePlacement();
    }

    public static String externalStructureFileName() {
        return active().externalStructureFileName();
    }

    public static Path externalStructureBaseDirectory() {
        return active().externalStructureBaseDirectory();
    }

    public static Optional<Path> externalStructureTemplatePath() {
        return active().externalStructureTemplatePath();
    }

    public static String spawnMarkerBlockName() {
        return active().spawnMarkerBlockName();
    }

    public static String dataMarkerName() {
        return active().dataMarkerName();
    }

    public static float spawnAngle() {
        return active().spawnAngle();
    }

    public static java.util.Set<ResourceLocation> allowedBiomeIds() {
        return active().allowedBiomeIds();
    }

    public static boolean allowsAnyBiome() {
        return active().allowsAnyBiome();
    }

    public static java.util.Set<ResourceLocation> forbiddenSurfaceBlockIds() {
        return active().forbiddenSurfaceBlockIds();
    }

    public static SpawnProfile.SurfaceSearchMode surfaceSearchMode() {
        return active().surfaceSearchMode();
    }

    public static SpawnProfile.TerrainPreparationMode terrainPreparationMode() {
        return active().terrainPreparationMode();
    }

    public static SpawnProfile.PlacementStrategy placementStrategy() {
        return active().placementStrategy();
    }

    public static SpawnProfile.AnchorMode anchorMode() {
        return active().anchor().mode();
    }

    public static SpawnProfile.AnchorFallback anchorFallback() {
        return active().anchor().fallback();
    }

    public static BlockState terrainTopBlockState() {
        return active().terrainTopBlockState();
    }

    public static BlockState terrainFillBlockState() {
        return active().terrainFillBlockState();
    }

    public static BlockState terrainCoreBlockState() {
        return active().terrainCoreBlockState();
    }

    private static SpawnProfile loadActiveProfile() {
        SpawnProfile legacyProfile = SpawnProfile.legacy();
        Path profilesDirectory = FMLPaths.CONFIGDIR.get().resolve(CustomizablePlayerSpawnMod.MODID).resolve("profiles");
        if (!Files.isDirectory(profilesDirectory)) {
            CustomizablePlayerSpawnMod.LOGGER.info("Using legacy spawn profile from customizableplayerspawn-common.toml.");
            return legacyProfile;
        }

        List<ProfileCandidate> candidates;
        try (var files = Files.list(profilesDirectory)) {
            candidates = files
                    .filter(path -> path.getFileName().toString().endsWith(".toml"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(path -> loadProfile(path, legacyProfile))
                    .flatMap(Optional::stream)
                    .toList();
        } catch (IOException exception) {
            CustomizablePlayerSpawnMod.LOGGER.error("Unable to scan spawn profile directory {}.", profilesDirectory, exception);
            return legacyProfile;
        }

        List<ProfileCandidate> validEnabledProfiles = candidates.stream()
                .filter(candidate -> candidate.profile().enabled())
                .filter(ProfileCandidate::valid)
                .toList();

        String selectedProfileId = Config.SELECTED_PROFILE.get().trim();
        if (!selectedProfileId.isEmpty()) {
            Optional<ProfileCandidate> selectedProfile = validEnabledProfiles.stream()
                    .filter(candidate -> candidate.profile().id().equals(selectedProfileId))
                    .findFirst();
            if (selectedProfile.isPresent()) {
                return useProfile(selectedProfile.get());
            }

            CustomizablePlayerSpawnMod.LOGGER.error(
                    "Selected spawn profile {} was not found as an enabled valid profile; using legacy customizableplayerspawn-common.toml.",
                    selectedProfileId
            );
            return legacyProfile;
        }

        return validEnabledProfiles.stream()
                .max(Comparator
                        .comparingInt((ProfileCandidate candidate) -> candidate.profile().priority())
                        .thenComparing(candidate -> candidate.path().getFileName().toString()))
                .map(SpawnProfiles::useProfile)
                .orElseGet(() -> {
                    CustomizablePlayerSpawnMod.LOGGER.info("No enabled valid spawn profile found; using legacy customizableplayerspawn-common.toml.");
                    return legacyProfile;
                });
    }

    private static SpawnProfile useProfile(ProfileCandidate candidate) {
        CustomizablePlayerSpawnMod.LOGGER.info(
                "Using spawn profile {} priority={} from {}.",
                candidate.profile().id(),
                candidate.profile().priority(),
                candidate.path()
        );
        return candidate.profile();
    }

    private static Optional<ProfileCandidate> loadProfile(Path profileFile, SpawnProfile defaults) {
        try (CommentedFileConfig config = CommentedFileConfig.builder(profileFile).build()) {
            config.load();
            SpawnProfile profile = readProfile(profileFile, config, defaults);
            SpawnProfileValidationResult validation = SpawnProfileValidator.validate(profile);
            validation.warnings().forEach(warning -> CustomizablePlayerSpawnMod.LOGGER.warn(
                    "Spawn profile {} warning: {}",
                    profileFile,
                    warning
            ));
            validation.errors().forEach(error -> CustomizablePlayerSpawnMod.LOGGER.error(
                    "Spawn profile {} error: {}",
                    profileFile,
                    error
            ));
            return Optional.of(new ProfileCandidate(profileFile, profile, validation.valid()));
        } catch (RuntimeException exception) {
            CustomizablePlayerSpawnMod.LOGGER.error("Unable to load spawn profile {}.", profileFile, exception);
            return Optional.empty();
        }
    }

    private static SpawnProfile readProfile(Path profileFile, CommentedFileConfig config, SpawnProfile defaults) {
        SpawnProfile.Builder builder = defaults.toBuilder();
        String defaultId = stripTomlExtension(profileFile.getFileName().toString());
        builder.schemaVersion(getInt(config, 2, "schemaVersion"));
        builder.id(getString(config, defaults.id().equals("legacy") ? defaultId : defaults.id(), "id"));
        builder.enabled(getBoolean(config, defaults.enabled(), "enabled"));
        builder.priority(getInt(config, defaults.priority(), "priority"));
        builder.targetDimension(getString(config, defaults.targetDimension(), "targetDimension", "dimension"));
        builder.allowedBiomes(getStringList(config, defaults.allowedBiomes(), "allowedBiomes", "placement.allowedBiomes"));

        builder.structure(readStructure(config, defaults.structure()));
        builder.anchor(readAnchor(config, defaults.anchor()));
        builder.placement(readPlacement(config, defaults.placement()));
        builder.terrain(readTerrain(config, defaults.terrain()));
        builder.postProcess(readPostProcess(config, defaults.postProcess()));
        return builder.build();
    }

    private static SpawnProfile.StructureSettings readStructure(CommentedFileConfig config, SpawnProfile.StructureSettings defaults) {
        String template = defaults.template();
        String externalFile = defaults.externalFile();
        String structure = getString(config, "", "structure");
        if (!structure.isBlank()) {
            if (structure.startsWith("config:")) {
                template = "";
                externalFile = structure.substring("config:".length());
            } else {
                template = structure;
                externalFile = "";
            }
        }

        template = getString(config, template, "structureTemplate", "structure.template");
        externalFile = getString(config, externalFile, "externalStructureFile", "structureFile", "structure.file");
        return new SpawnProfile.StructureSettings(template, externalFile);
    }

    private static SpawnProfile.AnchorSettings readAnchor(CommentedFileConfig config, SpawnProfile.AnchorSettings defaults) {
        SpawnProfile.AnchorMode mode = SpawnProfile.AnchorMode.byName(getString(config, defaults.mode().name(), "anchor.mode"));
        SpawnProfile.AnchorFallback fallback = SpawnProfile.AnchorFallback.byName(getString(config, defaults.fallback().name(), "anchor.fallback"));

        return new SpawnProfile.AnchorSettings(
                mode,
                getString(config, defaults.markerBlock(), "spawnMarkerBlock", "anchor.markerBlock"),
                getBoolean(config, defaults.removeMarkerBlock(), "removeSpawnMarkerBlock", "anchor.removeMarkerBlock"),
                getString(config, defaults.dataMarker(), "dataMarker", "anchor.dataMarker"),
                getInt(config, defaults.spawnOffsetX(), "spawnOffsetX", "anchor.offsetX"),
                getInt(config, defaults.spawnOffsetY(), "spawnOffsetY", "anchor.offsetY"),
                getInt(config, defaults.spawnOffsetZ(), "spawnOffsetZ", "anchor.offsetZ"),
                getFloat(config, defaults.spawnAngle(), "spawnAngle", "anchor.angle"),
                fallback,
                getInt(config, defaults.relativeX(), "anchor.relativeX", "anchor.relative.x"),
                getInt(config, defaults.relativeY(), "anchor.relativeY", "anchor.relative.y"),
                getInt(config, defaults.relativeZ(), "anchor.relativeZ", "anchor.relative.z")
        );
    }

    private static SpawnProfile.PlacementSettings readPlacement(CommentedFileConfig config, SpawnProfile.PlacementSettings defaults) {
        SpawnProfile.PlacementStrategy strategy = SpawnProfile.PlacementStrategy.byName(
                getString(config, defaults.strategy().name(), "placementStrategy", "placement.strategy")
        );
        SpawnProfile.SurfaceSearchMode surfaceSearchMode = SpawnProfile.SurfaceSearchMode.byName(
                getString(config, defaults.surfaceSearchMode().name(), "surfaceSearchMode", "placement.surfaceSearchMode")
        );

        return new SpawnProfile.PlacementSettings(
                strategy,
                getInt(config, defaults.searchRadius(), "searchRadius", "placement.searchRadius"),
                getInt(config, defaults.searchAttempts(), "searchAttempts", "placement.searchAttempts"),
                surfaceSearchMode,
                getInt(config, defaults.absoluteY(), "absoluteY", "placement.absoluteY"),
                getInt(config, defaults.placementY(), "placementY", "placement.placementY"),
                getInt(config, defaults.surfaceYOffset(), "surfaceYOffset", "placement.surfaceYOffset"),
                getInt(config, defaults.smartSearchTopOffset(), "smartSearchTopOffset", "placement.smartSearchTopOffset"),
                getInt(config, defaults.smartSearchBottomOffset(), "smartSearchBottomOffset", "placement.smartSearchBottomOffset"),
                getInt(config, defaults.maxSurfaceStep(), "maxSurfaceStep", "placement.maxSurfaceStep"),
                getBoolean(config, defaults.allowFluidsBelow(), "allowFluidsBelow", "placement.allowFluidsBelow"),
                getBoolean(config, defaults.allowReplaceableAtFeet(), "allowReplaceableAtFeet", "placement.allowReplaceableAtFeet"),
                getBoolean(config, defaults.allowReplaceableAtHead(), "allowReplaceableAtHead", "placement.allowReplaceableAtHead"),
                getStringList(config, defaults.forbiddenSurfaceBlocks(), "forbiddenSurfaceBlocks", "placement.forbiddenSurfaceBlocks"),
                getDouble(config, defaults.minLandRatio(), "placement.minLandRatio"),
                getInt(config, defaults.maxTreeOverlap(), "placement.maxTreeOverlap"),
                getBoolean(config, defaults.avoidFluids(), "placement.avoidFluids")
        );
    }

    private static SpawnProfile.TerrainSettings readTerrain(CommentedFileConfig config, SpawnProfile.TerrainSettings defaults) {
        SpawnProfile.TerrainPreparationMode mode = SpawnProfile.TerrainPreparationMode.byName(
                getString(config, defaults.mode().name(), "terrainPreparationMode", "terrain.mode")
        );
        String topBlock = getString(config, defaults.topBlock(), "terrainTopBlock", "terrain.topBlock");
        String fillBlock = getString(config, defaults.fillBlock(), "terrainFillBlock", "terrain.fillBlock");
        String coreBlock = getString(config, defaults.coreBlock(), "terrainCoreBlock", "terrain.coreBlock");

        return new SpawnProfile.TerrainSettings(
                mode,
                getInt(config, defaults.terrainPadding(), "terrainPadding", "terrain.padding"),
                topBlock,
                fillBlock,
                coreBlock,
                getBoolean(config, defaults.edgeNoise(), "terrainEdgeNoise", "terrain.edgeNoise"),
                getBoolean(config, defaults.clearStructureVolume(), "clearStructureVolume", "terrain.clearStructureVolume"),
                getInt(config, defaults.clearVolumePadding(), "clearVolumePadding", "terrain.clearVolumePadding"),
                getBoolean(config, defaults.fillSupportVoids(), "fillSupportVoids", "terrain.fillSupportVoids"),
                getInt(config, defaults.supportVoidMaxFillDepth(), "supportVoidMaxFillDepth", "terrain.supportVoidMaxFillDepth"),
                getInt(config, defaults.islandMaxDrop(), "islandMaxDrop", "terrain.islandMaxDrop"),
                getInt(config, defaults.islandEdgeFalloff(), "islandEdgeFalloff", "terrain.islandEdgeFalloff")
        );
    }

    private static SpawnProfile.PostProcessSettings readPostProcess(CommentedFileConfig config, SpawnProfile.PostProcessSettings defaults) {
        return new SpawnProfile.PostProcessSettings(
                getBoolean(config, defaults.refreshLighting(), "postprocess.refreshLighting", "postProcess.refreshLighting"),
                getIntList(config, defaults.refreshLightingDelays(), "postprocess.refreshLightingDelays", "postProcess.refreshLightingDelays"),
                getBoolean(config, defaults.suppressDrops(), "postprocess.suppressDrops", "postProcess.suppressDrops"),
                getBoolean(config, defaults.stabilizeBlocks(), "postprocess.stabilizeBlocks", "postProcess.stabilizeBlocks"),
                getBoolean(config, defaults.updateNeighborShapes(), "postprocess.updateNeighborShapes", "postProcess.updateNeighborShapes"),
                getBoolean(config, defaults.processDataMarkers(), "postprocess.processDataMarkers", "postProcess.processDataMarkers")
        );
    }

    private static String getString(CommentedFileConfig config, String fallback, String... paths) {
        for (String path : paths) {
            Object value = get(config, path);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return fallback;
    }

    private static int getInt(CommentedFileConfig config, int fallback, String... paths) {
        for (String path : paths) {
            Object value = get(config, path);
            if (value instanceof Number number) {
                return number.intValue();
            }
        }
        return fallback;
    }

    private static double getDouble(CommentedFileConfig config, double fallback, String... paths) {
        for (String path : paths) {
            Object value = get(config, path);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
        }
        return fallback;
    }

    private static float getFloat(CommentedFileConfig config, float fallback, String... paths) {
        for (String path : paths) {
            Object value = get(config, path);
            if (value instanceof Number number) {
                return number.floatValue();
            }
        }
        return fallback;
    }

    private static boolean getBoolean(CommentedFileConfig config, boolean fallback, String... paths) {
        for (String path : paths) {
            Object value = get(config, path);
            if (value instanceof Boolean bool) {
                return bool;
            }
        }
        return fallback;
    }

    private static List<String> getStringList(CommentedFileConfig config, List<String> fallback, String... paths) {
        for (String path : paths) {
            Object value = get(config, path);
            if (value instanceof List<?> list) {
                return list.stream().map(String::valueOf).toList();
            }

            if (value instanceof String string && !string.isBlank()) {
                return List.of(string);
            }
        }
        return fallback;
    }

    private static List<Integer> getIntList(CommentedFileConfig config, List<Integer> fallback, String... paths) {
        for (String path : paths) {
            Object value = get(config, path);
            if (value instanceof List<?> list) {
                return list.stream()
                        .filter(Number.class::isInstance)
                        .map(Number.class::cast)
                        .map(Number::intValue)
                        .toList();
            }
        }
        return fallback;
    }

    private static Object get(CommentedFileConfig config, String path) {
        return config.get(Arrays.asList(path.split("\\.")));
    }

    private static String stripTomlExtension(String fileName) {
        return fileName.endsWith(".toml") ? fileName.substring(0, fileName.length() - ".toml".length()) : fileName;
    }

    private record ProfileCandidate(Path path, SpawnProfile profile, boolean valid) {
    }

    public static final class ProfileValue<T> {
        private final Function<SpawnProfile, T> getter;

        private ProfileValue(Function<SpawnProfile, T> getter) {
            this.getter = getter;
        }

        public T get() {
            return getter.apply(active());
        }
    }
}
