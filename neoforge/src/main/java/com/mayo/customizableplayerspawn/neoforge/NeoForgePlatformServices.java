package com.mayo.customizableplayerspawn.neoforge;

import com.mayo.customizableplayerspawn.platform.PlatformServices;
import java.nio.file.Path;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

public final class NeoForgePlatformServices implements PlatformServices {
    @Override
    public String loaderName() {
        return "neoforge";
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
}

