package com.mayo.customizableplayerspawn.placement;

import com.mayo.customizableplayerspawn.SpawnStructureBounds;
import com.mayo.customizableplayerspawn.structure.StructureTemplateBounds;
import net.minecraft.core.BlockPos;

public record PlacementResult(BlockPos origin, SpawnStructureBounds bounds, int score, String strategy, String reason) {
    public static PlacementResult of(BlockPos origin, StructureTemplateBounds templateBounds, int score, String strategy, String reason) {
        return new PlacementResult(origin, SpawnStructureBounds.fromTemplate(origin, templateBounds), score, strategy, reason);
    }
}
