package com.mayo.customizableplayerspawn.anchor;

import com.mayo.customizableplayerspawn.SpawnProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class SpawnPointValidator {
    public boolean isSafe(ServerLevel level, SpawnProfile profile, BlockPos pos) {
        if (pos.getY() <= level.getMinBuildHeight() || pos.getY() + 1 >= level.getMaxBuildHeight()) {
            return false;
        }

        BlockPos floorPos = pos.below();
        BlockState floorState = level.getBlockState(floorPos);
        BlockState feetState = level.getBlockState(pos);
        BlockState headState = level.getBlockState(pos.above());
        return isSafeFloor(level, floorPos, floorState, profile)
                && isPassable(level, pos, feetState, profile.placement().allowReplaceableAtFeet())
                && isPassable(level, pos.above(), headState, profile.placement().allowReplaceableAtHead());
    }

    private boolean isSafeFloor(ServerLevel level, BlockPos pos, BlockState state, SpawnProfile profile) {
        if (!profile.placement().allowFluidsBelow() && !state.getFluidState().isEmpty()) {
            return false;
        }

        if (isDangerous(state)) {
            return false;
        }

        return !state.getCollisionShape(level, pos).isEmpty() && state.isFaceSturdy(level, pos, Direction.UP);
    }

    private boolean isPassable(ServerLevel level, BlockPos pos, BlockState state, boolean allowReplaceable) {
        if (state.isAir()) {
            return true;
        }

        if (!state.getFluidState().isEmpty() || isDangerous(state)) {
            return false;
        }

        if (state.canBeReplaced()) {
            return allowReplaceable;
        }

        return state.getCollisionShape(level, pos).isEmpty();
    }

    private boolean isDangerous(BlockState state) {
        return state.getFluidState().is(FluidTags.LAVA)
                || state.is(Blocks.LAVA)
                || state.is(Blocks.LAVA_CAULDRON)
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.POINTED_DRIPSTONE)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW);
    }
}
