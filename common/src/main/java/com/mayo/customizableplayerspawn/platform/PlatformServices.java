package com.mayo.customizableplayerspawn.platform;

import java.nio.file.Path;

public interface PlatformServices {
    String loaderName();

    Path configDir();

    default boolean isModLoaded(String modId) {
        return false;
    }
}

