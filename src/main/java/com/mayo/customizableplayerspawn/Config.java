package com.mayo.customizableplayerspawn;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> TARGET_DIMENSION = BUILDER
            .comment(
                    "The dimension where the start structure will be generated.",
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

    public static final ModConfigSpec.ConfigValue<String> STRUCTURE_TEMPLATE = BUILDER
            .comment(
                    "The structure template resource location.",
                    "Can point to templates provided by vanilla, other mods, datapacks, or the world save.",
                    "The .nbt can be stored inside a datapack/mod resources under data/<namespace>/structure/<path>.nbt",
                    "or inside the world save under generated/<namespace>/structures/<path>.nbt",
                    "Examples: minecraft:end_city/ship, someothermod:start_house"
            )
            .define("structureTemplate", "minecraft:end_city/ship");

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

    public static final ModConfigSpec.IntValue PLACEMENT_Y = BUILDER
            .comment(
                    "Fixed Y level for the structure origin.",
                    "Set to -1 to use the top surface of the found position."
            )
            .defineInRange("placementY", 80, Integer.MIN_VALUE, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue SURFACE_Y_OFFSET = BUILDER
            .comment("Extra Y offset added when placementY is -1.")
            .defineInRange("surfaceYOffset", 0, Integer.MIN_VALUE, Integer.MAX_VALUE);

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
        return parseResourceLocation(STRUCTURE_TEMPLATE.get(), ResourceLocation.fromNamespaceAndPath(CustomizablePlayerSpawnMod.MODID, "start_spawn"));
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

    private static boolean validateResourceLocation(Object value) {
        return value instanceof String string && ResourceLocation.tryParse(string) != null;
    }

    private static boolean validateOptionalResourceLocation(Object value) {
        return value instanceof String string && (string.isBlank() || ResourceLocation.tryParse(string) != null);
    }
}
