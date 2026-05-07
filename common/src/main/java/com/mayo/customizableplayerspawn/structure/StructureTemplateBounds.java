package com.mayo.customizableplayerspawn.structure;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

public record StructureTemplateBounds(
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        Vec3i templateSize,
        Set<Long> occupiedColumns,
        Map<Long, Integer> lowestBlockYByColumn,
        Map<Long, Integer> supportBottomYByColumn
) {
    public static StructureTemplateBounds full(Vec3i templateSize) {
        Set<Long> columns = new LinkedHashSet<>();
        Map<Long, Integer> lowestBlockYByColumn = new LinkedHashMap<>();
        Map<Long, Integer> supportBottomYByColumn = new LinkedHashMap<>();
        for (int x = 0; x < Math.max(1, templateSize.getX()); x++) {
            for (int z = 0; z < Math.max(1, templateSize.getZ()); z++) {
                long column = encodeColumn(x, z);
                columns.add(column);
                lowestBlockYByColumn.put(column, 0);
                supportBottomYByColumn.put(column, 0);
            }
        }

        return new StructureTemplateBounds(
                0,
                0,
                0,
                Math.max(0, templateSize.getX() - 1),
                Math.max(0, templateSize.getY() - 1),
                Math.max(0, templateSize.getZ() - 1),
                templateSize,
                columns,
                lowestBlockYByColumn,
                supportBottomYByColumn
        );
    }

    public static StructureTemplateBounds compute(CompoundTag structureTag, Vec3i templateSize) {
        ListTag palette = structureTag.getList("palette", Tag.TAG_COMPOUND);
        ListTag blocks = structureTag.getList("blocks", Tag.TAG_COMPOUND);
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int minAnchorY = Integer.MAX_VALUE;
        Set<Long> occupiedColumns = new LinkedHashSet<>();
        Map<Long, Integer> lowestBlockYByColumn = new LinkedHashMap<>();
        Map<Long, Integer> supportBottomYByColumn = new LinkedHashMap<>();

        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag blockTag = blocks.getCompound(i);
            int stateIndex = blockTag.getInt("state");
            if (stateIndex < 0 || stateIndex >= palette.size()) {
                continue;
            }

            ResourceLocation blockId = ResourceLocation.tryParse(palette.getCompound(stateIndex).getString("Name"));
            if (blockId == null || isIgnoredTemplateBlock(blockId)) {
                continue;
            }

            ListTag pos = blockTag.getList("pos", Tag.TAG_INT);
            if (pos.size() < 3) {
                continue;
            }

            int x = pos.getInt(0);
            int y = pos.getInt(1);
            int z = pos.getInt(2);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
            long column = encodeColumn(x, z);
            occupiedColumns.add(column);
            lowestBlockYByColumn.merge(column, y, Math::min);

            if (isTemplateAnchorBlock(blockId)) {
                minAnchorY = Math.min(minAnchorY, y);
                supportBottomYByColumn.merge(column, y, Math::min);
            }
        }

        if (minX == Integer.MAX_VALUE) {
            return full(templateSize);
        }

        int anchorY = minAnchorY == Integer.MAX_VALUE ? minY : minAnchorY;
        if (supportBottomYByColumn.isEmpty()) {
            for (long column : occupiedColumns) {
                supportBottomYByColumn.put(column, minY);
            }
        }

        return new StructureTemplateBounds(
                minX,
                anchorY,
                minZ,
                maxX,
                maxY,
                maxZ,
                templateSize,
                occupiedColumns,
                lowestBlockYByColumn,
                supportBottomYByColumn
        );
    }

    public int centerX() {
        return minX + Math.max(0, maxX - minX) / 2;
    }

    public int centerY() {
        return minY + Math.max(0, maxY - minY) / 2;
    }

    public int centerZ() {
        return minZ + Math.max(0, maxZ - minZ) / 2;
    }

    public Vec3i footprintSize() {
        return new Vec3i(Math.max(1, maxX - minX + 1), Math.max(1, maxY - minY + 1), Math.max(1, maxZ - minZ + 1));
    }

    public int distanceToOccupiedColumn(int x, int z) {
        if (occupiedColumns.isEmpty() || occupiedColumns.contains(encodeColumn(x, z))) {
            return 0;
        }

        int nearest = Integer.MAX_VALUE;
        for (long column : occupiedColumns) {
            nearest = Math.min(nearest, Math.max(Math.abs(x - columnX(column)), Math.abs(z - columnZ(column))));
            if (nearest == 1) {
                return nearest;
            }
        }

        return nearest;
    }

    public static long encodeColumn(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    public static int columnX(long column) {
        return (int) (column >> 32);
    }

    public static int columnZ(long column) {
        return (int) column;
    }

    private static boolean isIgnoredTemplateBlock(ResourceLocation blockId) {
        return blockId.equals(BuiltInRegistries.BLOCK.getKey(Blocks.AIR))
                || blockId.equals(BuiltInRegistries.BLOCK.getKey(Blocks.STRUCTURE_VOID))
                || blockId.equals(BuiltInRegistries.BLOCK.getKey(Blocks.BARRIER));
    }

    private static boolean isTemplateAnchorBlock(ResourceLocation blockId) {
        String path = blockId.getPath();
        if (path.contains("leaves")
                || path.contains("vine")
                || path.contains("grass")
                || path.contains("flower")
                || path.contains("fern")
                || path.contains("carpet")
                || path.contains("ladder")
                || path.contains("chain")
                || path.contains("bars")
                || path.contains("lantern")) {
            return false;
        }

        return true;
    }
}
