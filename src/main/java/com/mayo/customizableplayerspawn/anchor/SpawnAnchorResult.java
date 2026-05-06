package com.mayo.customizableplayerspawn.anchor;

import net.minecraft.core.BlockPos;

public record SpawnAnchorResult(BlockPos markerPos, BlockPos spawnPos, float angle, String source) {
}
