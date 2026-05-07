package com.mayo.customizableplayerspawn;

import com.mayo.customizableplayerspawn.structure.StructureTemplateBounds;
import net.minecraft.core.BlockPos;

public record SpawnStructureBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public static SpawnStructureBounds fromTemplate(BlockPos origin, StructureTemplateBounds templateBounds) {
        return new SpawnStructureBounds(
                origin.getX() + templateBounds.minX(),
                origin.getY() + templateBounds.minY(),
                origin.getZ() + templateBounds.minZ(),
                origin.getX() + templateBounds.maxX(),
                origin.getY() + templateBounds.maxY(),
                origin.getZ() + templateBounds.maxZ()
        );
    }

    public boolean contains(BlockPos pos, int padding) {
        return pos.getX() >= minX - padding
                && pos.getX() <= maxX + padding
                && pos.getY() >= minY - padding
                && pos.getY() <= maxY + padding
                && pos.getZ() >= minZ - padding
                && pos.getZ() <= maxZ + padding;
    }

    public boolean intersects(SpawnStructureBounds other) {
        return minX <= other.maxX
                && maxX >= other.minX
                && minY <= other.maxY
                && maxY >= other.minY
                && minZ <= other.maxZ
                && maxZ >= other.minZ;
    }

    public int horizontalDistanceTo(SpawnStructureBounds other) {
        int dx = intervalDistance(minX, maxX, other.minX, other.maxX);
        int dz = intervalDistance(minZ, maxZ, other.minZ, other.maxZ);
        return Math.max(dx, dz);
    }

    private static int intervalDistance(int minA, int maxA, int minB, int maxB) {
        if (maxA < minB) {
            return minB - maxA;
        }

        if (maxB < minA) {
            return minA - maxB;
        }

        return 0;
    }
}
