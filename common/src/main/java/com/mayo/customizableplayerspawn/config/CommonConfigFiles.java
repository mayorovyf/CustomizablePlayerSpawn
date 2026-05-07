package com.mayo.customizableplayerspawn.config;

import com.mayo.customizableplayerspawn.CustomizablePlayerSpawnCommon;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CommonConfigFiles {
    private CommonConfigFiles() {
    }

    public static void ensureBaseFiles() {
        try {
            Files.createDirectories(modConfigDirectory());
            Files.createDirectories(profilesDirectory());
            Files.createDirectories(structuresDirectory());
            Path settings = settingsFile();
            if (!Files.exists(settings)) {
                Files.writeString(settings, """
                        # Common settings for all loaders.
                        # Leave empty to use the enabled valid profile with the highest priority.
                        selectedProfile = ""
                        """);
            }
            Path exampleProfile = profilesDirectory().resolve("default.toml.example");
            if (!Files.exists(exampleProfile)) {
                Files.writeString(exampleProfile, """
                        schemaVersion = 2
                        id = "default"
                        enabled = true
                        priority = 0
                        targetDimension = "minecraft:overworld"

                        [structure]
                        # Use a datapack structure id, or leave template empty and set file to a compressed .nbt under config/customizableplayerspawn/structures.
                        template = "customizableplayerspawn:start_spawn"
                        file = ""

                        [spawnPolicy]
                        # shared = one structure for all players, per_player = one structure per player.
                        mode = "shared"
                        assignExistingPlayers = true
                        respawnAtAssignedStructure = true
                        respectBedsAndAnchors = true
                        maxInstances = 0
                        minDistanceBetweenInstances = 512

                        [protection]
                        enabled = false
                        protectBlocks = true
                        protectBlockPlacement = true
                        protectContainers = false
                        protectExplosions = true
                        protectFire = true
                        protectFluids = true
                        allowOps = true
                        allowOwner = false
                        padding = 0
                        """);
            }
        } catch (IOException exception) {
            CustomizablePlayerSpawnCommon.LOGGER.error("Unable to initialize Customizable Player Spawn config files.", exception);
        }
    }

    public static Path modConfigDirectory() {
        return CustomizablePlayerSpawnCommon.platform().configDir().resolve(CustomizablePlayerSpawnCommon.MODID);
    }

    public static Path profilesDirectory() {
        return modConfigDirectory().resolve("profiles");
    }

    public static Path structuresDirectory() {
        return modConfigDirectory().resolve("structures");
    }

    public static Path settingsFile() {
        return modConfigDirectory().resolve("settings.toml");
    }

    public static Path legacyCommonConfigFile() {
        return CustomizablePlayerSpawnCommon.platform().configDir().resolve(CustomizablePlayerSpawnCommon.MODID + "-common.toml");
    }
}
