package com.mayo.customizableplayerspawn;

import com.mayo.customizableplayerspawn.config.CommonConfigFiles;
import com.mayo.customizableplayerspawn.platform.PlatformServices;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public final class CustomizablePlayerSpawnCommon {
    public static final String MODID = "customizableplayerspawn";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static PlatformServices platform;
    private static final SpawnStructureManager SPAWN_STRUCTURE_MANAGER = new SpawnStructureManager();

    private CustomizablePlayerSpawnCommon() {
    }

    public static void init(PlatformServices platformServices) {
        platform = platformServices;
        CommonConfigFiles.ensureBaseFiles();
        SpawnProfiles.reload();
    }

    public static PlatformServices platform() {
        if (platform == null) {
            throw new IllegalStateException("Customizable Player Spawn platform services were not initialized");
        }

        return platform;
    }

    public static SpawnStructureManager spawnStructureManager() {
        return SPAWN_STRUCTURE_MANAGER;
    }
}
