package com.mayo.customizableplayerspawn;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

public class SpawnStructureSavedData extends SavedData {
    private static final String FILE_ID = CustomizablePlayerSpawnMod.MODID + "_spawn_structure";
    private static final String GENERATED_TAG = "generated";
    private static final String DIMENSION_TAG = "dimension";
    private static final String SPAWN_X_TAG = "spawnX";
    private static final String SPAWN_Y_TAG = "spawnY";
    private static final String SPAWN_Z_TAG = "spawnZ";
    private static final String SPAWN_ANGLE_TAG = "spawnAngle";
    private static final String ORIGIN_X_TAG = "originX";
    private static final String ORIGIN_Y_TAG = "originY";
    private static final String ORIGIN_Z_TAG = "originZ";

    private boolean generated;
    private String dimensionId = Level.OVERWORLD.location().toString();
    private BlockPos spawnPos = BlockPos.ZERO;
    private float spawnAngle;
    private BlockPos structureOrigin = BlockPos.ZERO;

    public static SpawnStructureSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    public static SavedData.Factory<SpawnStructureSavedData> factory() {
        return new SavedData.Factory<>(SpawnStructureSavedData::new, SpawnStructureSavedData::load);
    }

    private static SpawnStructureSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        SpawnStructureSavedData data = new SpawnStructureSavedData();
        data.generated = tag.getBoolean(GENERATED_TAG);
        data.dimensionId = tag.getString(DIMENSION_TAG);
        data.spawnPos = new BlockPos(tag.getInt(SPAWN_X_TAG), tag.getInt(SPAWN_Y_TAG), tag.getInt(SPAWN_Z_TAG));
        data.spawnAngle = tag.getFloat(SPAWN_ANGLE_TAG);
        data.structureOrigin = new BlockPos(tag.getInt(ORIGIN_X_TAG), tag.getInt(ORIGIN_Y_TAG), tag.getInt(ORIGIN_Z_TAG));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean(GENERATED_TAG, generated);
        tag.putString(DIMENSION_TAG, dimensionId);
        tag.putInt(SPAWN_X_TAG, spawnPos.getX());
        tag.putInt(SPAWN_Y_TAG, spawnPos.getY());
        tag.putInt(SPAWN_Z_TAG, spawnPos.getZ());
        tag.putFloat(SPAWN_ANGLE_TAG, spawnAngle);
        tag.putInt(ORIGIN_X_TAG, structureOrigin.getX());
        tag.putInt(ORIGIN_Y_TAG, structureOrigin.getY());
        tag.putInt(ORIGIN_Z_TAG, structureOrigin.getZ());
        return tag;
    }

    public boolean isGenerated() {
        return generated;
    }

    public void setGenerated(ResourceKey<Level> dimension, BlockPos spawnPos, float spawnAngle, BlockPos structureOrigin) {
        this.generated = true;
        this.dimensionId = dimension.location().toString();
        this.spawnPos = spawnPos.immutable();
        this.spawnAngle = spawnAngle;
        this.structureOrigin = structureOrigin.immutable();
        setDirty();
    }

    public Optional<ResolvedSpawn> resolve(MinecraftServer server) {
        if (!generated) {
            return Optional.empty();
        }

        ResourceLocation dimensionLocation = ResourceLocation.tryParse(dimensionId);
        if (dimensionLocation == null) {
            return Optional.empty();
        }

        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionLocation));
        if (level == null) {
            return Optional.empty();
        }

        return Optional.of(new ResolvedSpawn(level, spawnPos, spawnAngle, structureOrigin));
    }

    public record ResolvedSpawn(ServerLevel level, BlockPos spawnPos, float spawnAngle, BlockPos structureOrigin) {
        public Vec3 teleportPosition() {
            return Vec3.atBottomCenterOf(spawnPos);
        }
    }
}
