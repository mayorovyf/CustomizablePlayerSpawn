package com.mayo.customizableplayerspawn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public class SpawnStructureSavedData extends SavedData {
    private static final String FILE_ID = CustomizablePlayerSpawnCommon.MODID + "_spawn_structure";
    private static final String DATA_VERSION_TAG = "dataVersion";
    private static final String SHARED_INSTANCE_ID_TAG = "sharedInstanceId";
    private static final String INSTANCES_TAG = "instances";
    private static final String PLAYER_ASSIGNMENTS_TAG = "playerAssignments";
    private static final String INITIAL_SPAWN_PLAYERS_TAG = "initialSpawnPlayers";

    private static final String ID_TAG = "id";
    private static final String OWNER_TAG = "owner";
    private static final String DIMENSION_TAG = "dimension";
    private static final String SPAWN_X_TAG = "spawnX";
    private static final String SPAWN_Y_TAG = "spawnY";
    private static final String SPAWN_Z_TAG = "spawnZ";
    private static final String SPAWN_ANGLE_TAG = "spawnAngle";
    private static final String ORIGIN_X_TAG = "originX";
    private static final String ORIGIN_Y_TAG = "originY";
    private static final String ORIGIN_Z_TAG = "originZ";
    private static final String BOUNDS_TAG = "bounds";
    private static final String MIN_X_TAG = "minX";
    private static final String MIN_Y_TAG = "minY";
    private static final String MIN_Z_TAG = "minZ";
    private static final String MAX_X_TAG = "maxX";
    private static final String MAX_Y_TAG = "maxY";
    private static final String MAX_Z_TAG = "maxZ";
    private static final String CREATED_AT_TAG = "createdAt";
    private static final String PROFILE_NAME_TAG = "profileName";

    private static final String LEGACY_GENERATED_TAG = "generated";

    private String sharedInstanceId = "";
    private final Map<String, SpawnStructureInstance> instancesById = new LinkedHashMap<>();
    private final Map<UUID, String> playerAssignments = new LinkedHashMap<>();
    private final Set<UUID> initialSpawnPlayers = new LinkedHashSet<>();

    public static SpawnStructureSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SpawnStructureSavedData::load, SpawnStructureSavedData::new, FILE_ID);
    }

    private static SpawnStructureSavedData load(CompoundTag tag) {
        SpawnStructureSavedData data = new SpawnStructureSavedData();
        data.sharedInstanceId = tag.getString(SHARED_INSTANCE_ID_TAG);

        ListTag instances = tag.getList(INSTANCES_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < instances.size(); i++) {
            SpawnStructureInstance instance = readInstance(instances.getCompound(i));
            if (!instance.id().isBlank()) {
                data.instancesById.put(instance.id(), instance);
            }
        }

        CompoundTag assignments = tag.getCompound(PLAYER_ASSIGNMENTS_TAG);
        for (String playerId : assignments.getAllKeys()) {
            try {
                data.playerAssignments.put(UUID.fromString(playerId), assignments.getString(playerId));
            } catch (IllegalArgumentException ignored) {
            }
        }

        readInitialSpawnPlayers(data, tag);

        if (data.instancesById.isEmpty() && tag.getBoolean(LEGACY_GENERATED_TAG)) {
            data.migrateLegacySingleSpawn(tag);
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt(DATA_VERSION_TAG, 2);
        tag.putString(SHARED_INSTANCE_ID_TAG, sharedInstanceId);

        ListTag instances = new ListTag();
        for (SpawnStructureInstance instance : instancesById.values()) {
            instances.add(writeInstance(instance));
        }
        tag.put(INSTANCES_TAG, instances);

        CompoundTag assignments = new CompoundTag();
        for (Map.Entry<UUID, String> entry : playerAssignments.entrySet()) {
            assignments.putString(entry.getKey().toString(), entry.getValue());
        }
        tag.put(PLAYER_ASSIGNMENTS_TAG, assignments);

        ListTag players = new ListTag();
        for (UUID playerId : initialSpawnPlayers) {
            players.add(StringTag.valueOf(playerId.toString()));
        }
        tag.put(INITIAL_SPAWN_PLAYERS_TAG, players);
        return tag;
    }

    public Collection<SpawnStructureInstance> instances() {
        return List.copyOf(instancesById.values());
    }

    public List<SpawnStructureInstance> instancesInDimension(ResourceKey<Level> dimension) {
        return instancesById.values().stream()
                .filter(instance -> instance.isInDimension(dimension))
                .toList();
    }

    public Optional<SpawnStructureInstance> instance(String id) {
        return Optional.ofNullable(instancesById.get(id));
    }

    public Optional<SpawnStructureInstance.ResolvedSpawn> assignedSpawn(MinecraftServer server, UUID playerId) {
        String assignedInstanceId = playerAssignments.get(playerId);
        if (assignedInstanceId == null || assignedInstanceId.isBlank()) {
            return Optional.empty();
        }

        return instance(assignedInstanceId).flatMap(instance -> instance.resolve(server));
    }

    public Optional<SpawnStructureInstance.ResolvedSpawn> sharedSpawn(MinecraftServer server) {
        if (sharedInstanceId.isBlank()) {
            return Optional.empty();
        }

        return instance(sharedInstanceId).flatMap(instance -> instance.resolve(server));
    }

    public String sharedInstanceId() {
        return sharedInstanceId;
    }

    public int instanceCount() {
        return instancesById.size();
    }

    public int assignmentCount() {
        return playerAssignments.size();
    }

    public void addInstance(SpawnStructureInstance instance) {
        instancesById.put(instance.id(), instance);
        setDirty();
    }

    public void setSharedInstanceId(String sharedInstanceId) {
        this.sharedInstanceId = sharedInstanceId == null ? "" : sharedInstanceId;
        setDirty();
    }

    public void assignPlayer(UUID playerId, String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return;
        }

        String previous = playerAssignments.put(playerId, instanceId);
        if (!instanceId.equals(previous)) {
            setDirty();
        }
    }

    public void unassignPlayer(UUID playerId) {
        boolean changed = playerAssignments.remove(playerId) != null;
        changed |= initialSpawnPlayers.remove(playerId);
        if (changed) {
            setDirty();
        }
    }

    public void resetAll() {
        if (!instancesById.isEmpty() || !playerAssignments.isEmpty() || !initialSpawnPlayers.isEmpty() || !sharedInstanceId.isBlank()) {
            instancesById.clear();
            playerAssignments.clear();
            initialSpawnPlayers.clear();
            sharedInstanceId = "";
            setDirty();
        }
    }

    public boolean hasInitialSpawnBeenApplied(UUID playerId) {
        return initialSpawnPlayers.contains(playerId);
    }

    public void markInitialSpawnApplied(UUID playerId) {
        if (initialSpawnPlayers.add(playerId)) {
            setDirty();
        }
    }

    public List<SpawnStructureInstance> protectedInstances(ResourceKey<Level> dimension, BlockPos pos, int padding) {
        List<SpawnStructureInstance> matches = new ArrayList<>();
        for (SpawnStructureInstance instance : instancesById.values()) {
            if (instance.isInDimension(dimension) && instance.bounds().contains(pos, padding)) {
                matches.add(instance);
            }
        }
        return matches;
    }

    public String nextInstanceId(UUID ownerUuid, SpawnProfile.SpawnMode mode) {
        String base = mode == SpawnProfile.SpawnMode.SHARED
                ? "shared"
                : "player-" + ownerUuid;
        if (!instancesById.containsKey(base)) {
            return base;
        }

        int index = 2;
        while (instancesById.containsKey(base + "-" + index)) {
            index++;
        }
        return base + "-" + index;
    }

    private static SpawnStructureInstance readInstance(CompoundTag tag) {
        String id = tag.getString(ID_TAG);
        UUID owner = null;
        if (tag.contains(OWNER_TAG, Tag.TAG_STRING)) {
            try {
                owner = UUID.fromString(tag.getString(OWNER_TAG));
            } catch (IllegalArgumentException ignored) {
            }
        }

        return new SpawnStructureInstance(
                id,
                owner,
                tag.getString(DIMENSION_TAG),
                new BlockPos(tag.getInt(ORIGIN_X_TAG), tag.getInt(ORIGIN_Y_TAG), tag.getInt(ORIGIN_Z_TAG)),
                new BlockPos(tag.getInt(SPAWN_X_TAG), tag.getInt(SPAWN_Y_TAG), tag.getInt(SPAWN_Z_TAG)),
                tag.getFloat(SPAWN_ANGLE_TAG),
                readBounds(tag.getCompound(BOUNDS_TAG)),
                tag.getLong(CREATED_AT_TAG),
                tag.getString(PROFILE_NAME_TAG)
        );
    }

    private static CompoundTag writeInstance(SpawnStructureInstance instance) {
        CompoundTag tag = new CompoundTag();
        tag.putString(ID_TAG, instance.id());
        if (instance.ownerUuid() != null) {
            tag.putString(OWNER_TAG, instance.ownerUuid().toString());
        }
        tag.putString(DIMENSION_TAG, instance.dimensionId());
        tag.putInt(ORIGIN_X_TAG, instance.origin().getX());
        tag.putInt(ORIGIN_Y_TAG, instance.origin().getY());
        tag.putInt(ORIGIN_Z_TAG, instance.origin().getZ());
        tag.putInt(SPAWN_X_TAG, instance.spawnPos().getX());
        tag.putInt(SPAWN_Y_TAG, instance.spawnPos().getY());
        tag.putInt(SPAWN_Z_TAG, instance.spawnPos().getZ());
        tag.putFloat(SPAWN_ANGLE_TAG, instance.spawnAngle());
        tag.put(BOUNDS_TAG, writeBounds(instance.bounds()));
        tag.putLong(CREATED_AT_TAG, instance.createdAt());
        tag.putString(PROFILE_NAME_TAG, instance.profileName());
        return tag;
    }

    private static SpawnStructureBounds readBounds(CompoundTag tag) {
        return new SpawnStructureBounds(
                tag.getInt(MIN_X_TAG),
                tag.getInt(MIN_Y_TAG),
                tag.getInt(MIN_Z_TAG),
                tag.getInt(MAX_X_TAG),
                tag.getInt(MAX_Y_TAG),
                tag.getInt(MAX_Z_TAG)
        );
    }

    private static CompoundTag writeBounds(SpawnStructureBounds bounds) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(MIN_X_TAG, bounds.minX());
        tag.putInt(MIN_Y_TAG, bounds.minY());
        tag.putInt(MIN_Z_TAG, bounds.minZ());
        tag.putInt(MAX_X_TAG, bounds.maxX());
        tag.putInt(MAX_Y_TAG, bounds.maxY());
        tag.putInt(MAX_Z_TAG, bounds.maxZ());
        return tag;
    }

    private static void readInitialSpawnPlayers(SpawnStructureSavedData data, CompoundTag tag) {
        ListTag players = tag.getList(INITIAL_SPAWN_PLAYERS_TAG, Tag.TAG_STRING);
        for (int i = 0; i < players.size(); i++) {
            try {
                data.initialSpawnPlayers.add(UUID.fromString(players.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void migrateLegacySingleSpawn(CompoundTag tag) {
        BlockPos origin = new BlockPos(tag.getInt(ORIGIN_X_TAG), tag.getInt(ORIGIN_Y_TAG), tag.getInt(ORIGIN_Z_TAG));
        BlockPos spawnPos = new BlockPos(tag.getInt(SPAWN_X_TAG), tag.getInt(SPAWN_Y_TAG), tag.getInt(SPAWN_Z_TAG));
        SpawnStructureBounds bounds = new SpawnStructureBounds(
                Math.min(origin.getX(), spawnPos.getX()),
                Math.min(origin.getY(), spawnPos.getY()),
                Math.min(origin.getZ(), spawnPos.getZ()),
                Math.max(origin.getX(), spawnPos.getX()),
                Math.max(origin.getY(), spawnPos.getY() + 1),
                Math.max(origin.getZ(), spawnPos.getZ())
        );
        SpawnStructureInstance instance = new SpawnStructureInstance(
                "shared",
                null,
                tag.getString(DIMENSION_TAG),
                origin,
                spawnPos,
                tag.getFloat(SPAWN_ANGLE_TAG),
                bounds,
                System.currentTimeMillis(),
                "legacy"
        );
        instancesById.put(instance.id(), instance);
        sharedInstanceId = instance.id();
        setDirty();
    }
}
