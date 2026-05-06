package com.mayo.customizableplayerspawn;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.loading.FMLPaths;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> TARGET_DIMENSION = BUILDER
            .comment(
                    "The dimension where the start spawn will be created.",
                    "If structure placement is enabled, the structure is generated there.",
                    "If structure placement is disabled, the player is spawned there directly.",
                    "Example: minecraft:overworld"
            )
            .define("targetDimension", "minecraft:the_end");

    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALLOWED_BIOMES = BUILDER
            .comment(
                    "Allowed biome ids for the start structure search.",
                    "Leave empty to allow any biome.",
                    "Examples: minecraft:plains, minecraft:forest"
            )
            .defineListAllowEmpty("allowedBiomes", List.of(), () -> "", Config::validateResourceLocation);

    public static final ModConfigSpec.ConfigValue<String> SELECTED_PROFILE = BUILDER
            .comment(
                    "Optional 2.0 profile id to force from config/" + CustomizablePlayerSpawnMod.MODID + "/profiles.",
                    "Leave empty to select the enabled valid profile with the highest priority.",
                    "Legacy settings below are used only when no enabled valid profile exists."
            )
            .define("selectedProfile", "");

    public static final ModConfigSpec.ConfigValue<String> STRUCTURE_TEMPLATE = BUILDER
            .comment(
                    "The structure template resource location.",
                    "Can point to templates provided by vanilla, other mods, datapacks, or the world save.",
                    "The .nbt can be stored inside a datapack/mod resources under data/<namespace>/structure/<path>.nbt",
                    "or inside the world save under generated/<namespace>/structures/<path>.nbt",
                    "Examples: minecraft:end_city/ship, someothermod:start_house",
                    "Leave empty together with externalStructureFile to disable structure placement."
            )
            .define("structureTemplate", "minecraft:end_city/ship");

    public static final ModConfigSpec.ConfigValue<String> EXTERNAL_STRUCTURE_FILE = BUILDER
            .comment(
                    "Optional external .nbt file relative to config/" + CustomizablePlayerSpawnMod.MODID + "/structures.",
                    "Intended for modpacks that ship a loose structure file without rebuilding any jar.",
                    "When set, this file is loaded before structureTemplate and is available for every newly created world.",
                    "Examples: spawn.nbt, pack/start_house.nbt",
                    "Leave empty to use structureTemplate."
            )
            .define("externalStructureFile", "", Config::validateOptionalExternalStructureFile);

    public static final ModConfigSpec.ConfigValue<String> SPAWN_MARKER_BLOCK = BUILDER
            .comment(
                    "Optional block id used as the spawn anchor inside the placed structure.",
                    "Can be a block from this mod, vanilla, or another mod.",
                    "Leave empty to disable block-based spawn anchors and use only dataMarker."
            )
            .define("spawnMarkerBlock", CustomizablePlayerSpawnMod.MODID + ":player_spawn_marker", Config::validateOptionalResourceLocation);

    public static final ModConfigSpec.BooleanValue REMOVE_SPAWN_MARKER_BLOCK = BUILDER
            .comment(
                    "Remove the configured spawn marker block after the structure is placed.",
                    "Disable this if you intentionally use a real decorative block from another mod as the anchor."
            )
            .define("removeSpawnMarkerBlock", true);

    public static final ModConfigSpec.ConfigValue<String> DATA_MARKER = BUILDER
            .comment(
                    "Optional structure data marker name to use as spawn anchor when the custom marker block is absent.",
                    "Works with DATA structure_block markers from vanilla or other mods.",
                    "Useful for templates that already contain data markers, for example minecraft:end_city/ship with Elytra.",
                    "Leave empty to disable."
            )
            .define("dataMarker", "Elytra");

    public static final ModConfigSpec.IntValue SEARCH_RADIUS = BUILDER
            .comment("Max radius in blocks for finding a valid biome around 0 0.")
            .defineInRange("searchRadius", 0, 0, 30_000_000);

    public static final ModConfigSpec.IntValue SEARCH_ATTEMPTS = BUILDER
            .comment("How many candidate positions to try before failing.")
            .defineInRange("searchAttempts", 1, 1, 100_000);

    public static final ModConfigSpec.ConfigValue<String> SURFACE_SEARCH_MODE = BUILDER
            .comment(
                    "How to determine the placement Y level.",
                    "HEIGHTMAP keeps the legacy behaviour.",
                    "SMART scans the column top-down and validates a real walkable floor.",
                    "ABSOLUTE_Y uses absoluteY as the base height."
            )
            .define("surfaceSearchMode", SurfaceSearchMode.HEIGHTMAP.name(), Config::validateSurfaceSearchMode);

    public static final ModConfigSpec.IntValue ABSOLUTE_Y = BUILDER
            .comment("Base Y used when surfaceSearchMode is ABSOLUTE_Y.")
            .defineInRange("absoluteY", 96, Integer.MIN_VALUE, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue PLACEMENT_Y = BUILDER
            .comment(
                    "Y offset from the top surface of the found position.",
                    "0 places the structure on the surface, positive values place it higher, negative values lower."
            )
            .defineInRange("placementY", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue SURFACE_Y_OFFSET = BUILDER
            .comment("Additional legacy Y offset added after placementY. Usually keep this at 0.")
            .defineInRange("surfaceYOffset", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue SMART_SEARCH_TOP_OFFSET = BUILDER
            .comment("Extra offset applied to the top Y bound of SMART search before scanning downward.")
            .defineInRange("smartSearchTopOffset", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue SMART_SEARCH_BOTTOM_OFFSET = BUILDER
            .comment("Extra offset applied to the bottom Y bound of SMART search before scanning downward.")
            .defineInRange("smartSearchBottomOffset", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue MAX_SURFACE_STEP = BUILDER
            .comment("Maximum allowed height difference across the placement footprint in SMART and ABSOLUTE_Y modes.")
            .defineInRange("maxSurfaceStep", 3, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.BooleanValue ALLOW_FLUIDS_BELOW = BUILDER
            .comment("Allow fluid blocks to be used as the supporting floor under the placement.")
            .define("allowFluidsBelow", false);

    public static final ModConfigSpec.BooleanValue ALLOW_REPLACEABLE_AT_FEET = BUILDER
            .comment("Allow replaceable blocks such as grass at the feet position during SMART validation.")
            .define("allowReplaceableAtFeet", true);

    public static final ModConfigSpec.BooleanValue ALLOW_REPLACEABLE_AT_HEAD = BUILDER
            .comment("Allow replaceable blocks such as grass at the head position during SMART validation.")
            .define("allowReplaceableAtHead", true);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FORBIDDEN_SURFACE_BLOCKS = BUILDER
            .comment(
                    "Block ids that are never allowed as the support block in SMART and ABSOLUTE_Y modes.",
                    "Examples: minecraft:lava, minecraft:magma_block"
            )
            .defineListAllowEmpty("forbiddenSurfaceBlocks", List.of("minecraft:lava", "minecraft:magma_block"), () -> "", Config::validateResourceLocation);

    public static final ModConfigSpec.ConfigValue<String> TERRAIN_PREPARATION_MODE = BUILDER
            .comment(
                    "How to prepare terrain before placing the configured structure.",
                    "NONE places the structure directly.",
                    "ISLAND creates a deterministic natural-looking mound under the structure and clears the placement volume."
            )
            .define("terrainPreparationMode", TerrainPreparationMode.NONE.name(), Config::validateTerrainPreparationMode);

    public static final ModConfigSpec.IntValue TERRAIN_PADDING = BUILDER
            .comment("Extra horizontal blocks around the structure footprint affected by ISLAND terrain preparation.")
            .defineInRange("terrainPadding", 10, 0, 64);

    public static final ModConfigSpec.ConfigValue<String> TERRAIN_TOP_BLOCK = BUILDER
            .comment("Top block used by ISLAND terrain preparation.")
            .define("terrainTopBlock", "minecraft:grass_block", Config::validateBlockResourceLocation);

    public static final ModConfigSpec.ConfigValue<String> TERRAIN_FILL_BLOCK = BUILDER
            .comment("Subsurface fill block used by ISLAND terrain preparation.")
            .define("terrainFillBlock", "minecraft:dirt", Config::validateBlockResourceLocation);

    public static final ModConfigSpec.ConfigValue<String> TERRAIN_CORE_BLOCK = BUILDER
            .comment("Deep core block used by ISLAND terrain preparation.")
            .define("terrainCoreBlock", "minecraft:stone", Config::validateBlockResourceLocation);

    public static final ModConfigSpec.BooleanValue TERRAIN_EDGE_NOISE = BUILDER
            .comment("Use deterministic noise to break up ISLAND terrain edges.")
            .define("terrainEdgeNoise", true);

    public static final ModConfigSpec.BooleanValue CLEAR_STRUCTURE_VOLUME = BUILDER
            .comment("Clear solid blocks, fluids, trees, and plants from the structure placement volume before placement.")
            .define("clearStructureVolume", true);

    public static final ModConfigSpec.IntValue CLEAR_VOLUME_PADDING = BUILDER
            .comment("Extra horizontal and vertical air clearance around the structure volume.")
            .defineInRange("clearVolumePadding", 2, 0, 32);

    public static final ModConfigSpec.BooleanValue FILL_SUPPORT_VOIDS = BUILDER
            .comment("Fill small air gaps directly under the placed structure without creating an artificial mound.")
            .define("fillSupportVoids", true);

    public static final ModConfigSpec.IntValue SUPPORT_VOID_MAX_FILL_DEPTH = BUILDER
            .comment("Maximum blocks to fill downward under each real structure support column.")
            .defineInRange("supportVoidMaxFillDepth", 8, 0, 32);

    public static final ModConfigSpec.IntValue ISLAND_MAX_DROP = BUILDER
            .comment("Maximum Y drop from the structure floor to the outer ISLAND edge.")
            .defineInRange("islandMaxDrop", 8, 0, 64);

    public static final ModConfigSpec.IntValue ISLAND_EDGE_FALLOFF = BUILDER
            .comment("How many blocks outside the structure footprint are used for the main ISLAND slope.")
            .defineInRange("islandEdgeFalloff", 6, 1, 64);

    public static final ModConfigSpec.IntValue SPAWN_OFFSET_X = BUILDER
            .comment("Extra spawn offset X from the chosen marker position.")
            .defineInRange("spawnOffsetX", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue SPAWN_OFFSET_Y = BUILDER
            .comment("Extra spawn offset Y from the chosen marker position.")
            .defineInRange("spawnOffsetY", -1, Integer.MIN_VALUE, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue SPAWN_OFFSET_Z = BUILDER
            .comment("Extra spawn offset Z from the chosen marker position.")
            .defineInRange("spawnOffsetZ", 1, Integer.MIN_VALUE, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue SPAWN_ANGLE = BUILDER
            .comment("Yaw angle used for the saved spawn point.")
            .defineInRange("spawnAngle", 180, -360, 360);

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    public static ResourceKey<Level> targetDimensionKey() {
        return ResourceKey.create(Registries.DIMENSION, parseResourceLocation(TARGET_DIMENSION.get(), Level.OVERWORLD.location()));
    }

    public static ResourceLocation structureTemplateId() {
        return parseResourceLocation(
                STRUCTURE_TEMPLATE.get(),
                ResourceLocation.tryParse(CustomizablePlayerSpawnMod.MODID + ":start_spawn")
        );
    }

    public static boolean hasStructureTemplate() {
        return !STRUCTURE_TEMPLATE.get().trim().isEmpty();
    }

    public static boolean hasExternalStructureFile() {
        return !EXTERNAL_STRUCTURE_FILE.get().trim().isEmpty();
    }

    public static boolean usesStructurePlacement() {
        return hasExternalStructureFile() || hasStructureTemplate();
    }

    public static String externalStructureFileName() {
        return EXTERNAL_STRUCTURE_FILE.get().trim();
    }

    public static Path externalStructureBaseDirectory() {
        return FMLPaths.CONFIGDIR.get().resolve(CustomizablePlayerSpawnMod.MODID).resolve("structures");
    }

    public static Optional<Path> externalStructureTemplatePath() {
        return resolveExternalStructurePath(EXTERNAL_STRUCTURE_FILE.get());
    }

    public static String spawnMarkerBlockName() {
        return SPAWN_MARKER_BLOCK.get().trim();
    }

    public static Set<ResourceLocation> allowedBiomeIds() {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (String entry : ALLOWED_BIOMES.get()) {
            ResourceLocation id = ResourceLocation.tryParse(entry);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    public static boolean allowsAnyBiome() {
        return ALLOWED_BIOMES.get().isEmpty();
    }

    public static SurfaceSearchMode surfaceSearchMode() {
        return SurfaceSearchMode.byName(SURFACE_SEARCH_MODE.get());
    }

    public static Set<ResourceLocation> forbiddenSurfaceBlockIds() {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (String entry : FORBIDDEN_SURFACE_BLOCKS.get()) {
            ResourceLocation id = ResourceLocation.tryParse(entry);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    public static TerrainPreparationMode terrainPreparationMode() {
        return TerrainPreparationMode.byName(TERRAIN_PREPARATION_MODE.get());
    }

    public static BlockState terrainTopBlockState() {
        return blockStateByName(TERRAIN_TOP_BLOCK.get(), Blocks.GRASS_BLOCK);
    }

    public static BlockState terrainFillBlockState() {
        return blockStateByName(TERRAIN_FILL_BLOCK.get(), Blocks.DIRT);
    }

    public static BlockState terrainCoreBlockState() {
        return blockStateByName(TERRAIN_CORE_BLOCK.get(), Blocks.STONE);
    }

    public static String dataMarkerName() {
        return DATA_MARKER.get().trim();
    }

    public static float spawnAngle() {
        return SPAWN_ANGLE.get().floatValue();
    }

    private static ResourceLocation parseResourceLocation(String value, ResourceLocation fallback) {
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        return parsed != null ? parsed : fallback;
    }

    private static Optional<Path> resolveExternalStructurePath(String value) {
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

    private static boolean validateResourceLocation(Object value) {
        return value instanceof String string && ResourceLocation.tryParse(string) != null;
    }

    private static boolean validateOptionalExternalStructureFile(Object value) {
        return value instanceof String string && resolveExternalStructurePath(string).isPresent() == !string.isBlank();
    }

    private static boolean validateOptionalResourceLocation(Object value) {
        return value instanceof String string && (string.isBlank() || ResourceLocation.tryParse(string) != null);
    }

    private static boolean validateSurfaceSearchMode(Object value) {
        return value instanceof String string && SurfaceSearchMode.tryParse(string).isPresent();
    }

    private static boolean validateTerrainPreparationMode(Object value) {
        return value instanceof String string && TerrainPreparationMode.tryParse(string).isPresent();
    }

    private static boolean validateBlockResourceLocation(Object value) {
        if (!(value instanceof String string)) {
            return false;
        }

        ResourceLocation id = ResourceLocation.tryParse(string);
        return id != null && BuiltInRegistries.BLOCK.containsKey(id);
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
}
