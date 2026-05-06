package com.mayo.customizableplayerspawn.structure;

import com.mayo.customizableplayerspawn.CustomizablePlayerSpawnMod;
import com.mayo.customizableplayerspawn.SpawnProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class StructureTemplateProvider {
    public Optional<LoadedStructureTemplate> resolve(MinecraftServer server, SpawnProfile profile) {
        if (profile.hasExternalStructureFile()) {
            Optional<Path> externalPath = profile.externalStructureTemplatePath();
            if (externalPath.isEmpty()) {
                CustomizablePlayerSpawnMod.LOGGER.error(
                        "Configured structure file {} is not a valid relative .nbt path inside {}.",
                        profile.externalStructureFileName(),
                        profile.externalStructureBaseDirectory()
                );
                return Optional.empty();
            }

            return loadExternal(server, profile, externalPath.get());
        }

        if (!profile.hasStructureTemplate()) {
            return Optional.empty();
        }

        ResourceLocation templateId = profile.structureTemplateId();
        Optional<StructureTemplate> optionalTemplate = server.getStructureManager().get(templateId);
        if (optionalTemplate.isEmpty()) {
            CustomizablePlayerSpawnMod.LOGGER.error(
                    "Structure template {} was not found. Use a mod/datapack resource or config:{}/structures/*.nbt.",
                    templateId,
                    CustomizablePlayerSpawnMod.MODID
            );
            return Optional.empty();
        }

        StructureTemplate template = optionalTemplate.get();
        return Optional.of(new LoadedStructureTemplate(
                templateId.toString(),
                template,
                StructureTemplateBounds.full(template.getSize())
        ));
    }

    private Optional<LoadedStructureTemplate> loadExternal(MinecraftServer server, SpawnProfile profile, Path configuredPath) {
        Path absoluteConfiguredPath = configuredPath.toAbsolutePath().normalize();
        Path absoluteBaseDirectory = profile.externalStructureBaseDirectory().toAbsolutePath().normalize();
        if (!absoluteConfiguredPath.startsWith(absoluteBaseDirectory)) {
            CustomizablePlayerSpawnMod.LOGGER.error(
                    "Configured structure path {} escapes the allowed directory {}.",
                    absoluteConfiguredPath,
                    absoluteBaseDirectory
            );
            return Optional.empty();
        }

        if (!Files.isRegularFile(absoluteConfiguredPath)) {
            CustomizablePlayerSpawnMod.LOGGER.error("Configured structure file {} was not found.", absoluteConfiguredPath);
            return Optional.empty();
        }

        try {
            CompoundTag structureTag = NbtIo.readCompressed(absoluteConfiguredPath.toFile());
            StructureTemplate template = server.getStructureManager().readStructure(structureTag);
            return Optional.of(new LoadedStructureTemplate(
                    absoluteConfiguredPath.toString(),
                    template,
                    StructureTemplateBounds.compute(structureTag, template.getSize())
            ));
        } catch (IOException exception) {
            CustomizablePlayerSpawnMod.LOGGER.error("Couldn't load external structure file {}.", absoluteConfiguredPath, exception);
            return Optional.empty();
        }
    }
}
