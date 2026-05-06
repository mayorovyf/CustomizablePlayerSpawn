package com.mayo.customizableplayerspawn.structure;

import com.mayo.customizableplayerspawn.SpawnProfile;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class StructurePlacementService {
    public static final int UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    public static final int SUPPRESSING_UPDATE_FLAGS = UPDATE_FLAGS | Block.UPDATE_SUPPRESS_DROPS;

    private StructurePlacementService() {
    }

    public static StructurePlaceSettings createPlacementSettings() {
        return new StructurePlaceSettings()
                .setKnownShape(true)
                .addProcessor(new BlockIgnoreProcessor(List.of(Blocks.STRUCTURE_BLOCK, Blocks.BARRIER)));
    }

    public static boolean place(SpawnProfile profile, StructureTemplate template, ServerLevel level, BlockPos origin, StructurePlaceSettings settings) {
        int flags = profile.postProcess().suppressDrops() ? SUPPRESSING_UPDATE_FLAGS : UPDATE_FLAGS;
        return template.placeInWorld(level, origin, origin, settings, level.getRandom(), flags);
    }

    public static int worldEditFlags(SpawnProfile profile) {
        return profile.postProcess().suppressDrops() ? SUPPRESSING_UPDATE_FLAGS : UPDATE_FLAGS;
    }
}
