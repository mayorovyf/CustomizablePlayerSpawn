package com.mayo.customizableplayerspawn.profile;

import com.mayo.customizableplayerspawn.SpawnProfile;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

public final class SpawnProfileValidator {
    private SpawnProfileValidator() {
    }

    public static SpawnProfileValidationResult validate(SpawnProfile profile) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (profile.schemaVersion() != 2 && profile.schemaVersion() != 1) {
            warnings.add("schemaVersion " + profile.schemaVersion() + " is unknown; trying to read it as schemaVersion=2");
        }

        if (profile.id().isBlank()) {
            errors.add("id is empty");
        }

        if (ResourceLocation.tryParse(profile.targetDimension()) == null) {
            errors.add("dimension is not a valid resource location: " + profile.targetDimension());
        }

        validateResourceLocationList("placement.allowedBiomes", profile.allowedBiomes(), errors);
        validateResourceLocationList("placement.forbiddenSurfaceBlocks", profile.placement().forbiddenSurfaceBlocks(), errors);

        if (profile.hasExternalStructureFile()) {
            if (profile.externalStructureTemplatePath().isEmpty()) {
                errors.add("structure file path escapes config/customizableplayerspawn/structures: " + profile.externalStructureFileName());
            } else if (!Files.isRegularFile(profile.externalStructureTemplatePath().get())) {
                errors.add("structure file does not exist: " + profile.externalStructureTemplatePath().get());
            }
        } else if (profile.hasStructureTemplate() && ResourceLocation.tryParse(profile.structure().template()) == null) {
            errors.add("structure template id is invalid: " + profile.structure().template());
        }

        validateOptionalBlock("anchor.markerBlock", profile.spawnMarkerBlockName(), errors);
        validateBlock("terrain.topBlock", profile.terrain().topBlock(), errors);
        validateBlock("terrain.fillBlock", profile.terrain().fillBlock(), errors);
        validateBlock("terrain.coreBlock", profile.terrain().coreBlock(), errors);

        if (profile.placement().searchAttempts() <= 0) {
            errors.add("placement.searchAttempts must be greater than 0");
        }

        if (profile.placement().searchRadius() < 0) {
            errors.add("placement.searchRadius must be 0 or greater");
        }

        if (profile.placement().minLandRatio() < 0.0D || profile.placement().minLandRatio() > 1.0D) {
            errors.add("placement.minLandRatio must be between 0 and 1");
        }

        if (profile.placement().maxTreeOverlap() < 0) {
            errors.add("placement.maxTreeOverlap must be 0 or greater");
        }

        if (profile.usesStructurePlacement()
                && profile.anchor().mode() == SpawnProfile.AnchorMode.MARKER_BLOCK
                && profile.spawnMarkerBlockName().isBlank()
                && profile.anchor().fallback() == SpawnProfile.AnchorFallback.FAIL) {
            errors.add("anchor.mode is MARKER_BLOCK but anchor.markerBlock is empty and fallback is FAIL");
        }

        if (profile.usesStructurePlacement()
                && profile.anchor().mode() == SpawnProfile.AnchorMode.DATA_MARKER
                && profile.dataMarkerName().isBlank()
                && profile.anchor().fallback() == SpawnProfile.AnchorFallback.FAIL) {
            errors.add("anchor.mode is DATA_MARKER but anchor.dataMarker is empty and fallback is FAIL");
        }

        if (profile.postProcess().refreshLightingDelays().stream().anyMatch(delay -> delay < 0)) {
            errors.add("postprocess.refreshLightingDelays cannot contain negative values");
        }

        return errors.isEmpty()
                ? SpawnProfileValidationResult.valid(warnings)
                : SpawnProfileValidationResult.invalid(errors, warnings);
    }

    private static void validateResourceLocationList(String key, List<String> values, List<String> errors) {
        for (String value : values) {
            if (ResourceLocation.tryParse(value) == null) {
                errors.add(key + " contains invalid id: " + value);
            }
        }
    }

    private static void validateOptionalBlock(String key, String value, List<String> errors) {
        if (!value.isBlank()) {
            validateBlock(key, value, errors);
        }
    }

    private static void validateBlock(String key, String value, List<String> errors) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            errors.add(key + " is not a valid resource location: " + value);
            return;
        }

        if (!BuiltInRegistries.BLOCK.containsKey(id) || BuiltInRegistries.BLOCK.get(id) == Blocks.AIR) {
            errors.add(key + " does not exist as a loaded block: " + value);
        }
    }
}
