package com.mayo.customizableplayerspawn.forge;

import com.mayo.customizableplayerspawn.platform.PlatformServices;
import java.nio.file.Path;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

public final class ForgePlatformServices implements PlatformServices {
    @Override
    public String loaderName() {
        return "forge";
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

