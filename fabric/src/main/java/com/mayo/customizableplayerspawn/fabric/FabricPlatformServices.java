package com.mayo.customizableplayerspawn.fabric;

import com.mayo.customizableplayerspawn.platform.PlatformServices;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class FabricPlatformServices implements PlatformServices {
    @Override
    public String loaderName() {
        return "fabric";
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}

