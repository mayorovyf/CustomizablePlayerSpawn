package com.mayo.customizableplayerspawn;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class PlayerSpawnMarkerBlock extends Block {
    public PlayerSpawnMarkerBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_RED)
                .strength(1.0F)
                .sound(SoundType.METAL));
    }
}
