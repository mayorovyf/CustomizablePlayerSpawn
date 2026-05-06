package com.mayo.customizableplayerspawn.placement;

import net.minecraft.core.BlockPos;

public record PlacementResult(BlockPos origin, int score, String strategy, String reason) {
}
