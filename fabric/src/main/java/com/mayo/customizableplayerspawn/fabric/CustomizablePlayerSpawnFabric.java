package com.mayo.customizableplayerspawn.fabric;

import com.mayo.customizableplayerspawn.CustomizablePlayerSpawnCommon;
import com.mayo.customizableplayerspawn.PlayerSpawnMarkerBlock;
import com.mayo.customizableplayerspawn.command.CustomizablePlayerSpawnCommands;
import com.mayo.customizableplayerspawn.protection.StructureProtectionService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class CustomizablePlayerSpawnFabric implements ModInitializer {
    public static final ResourceLocation PLAYER_SPAWN_MARKER_ID = new ResourceLocation(CustomizablePlayerSpawnCommon.MODID, "player_spawn_marker");
    public static final Block PLAYER_SPAWN_MARKER = new PlayerSpawnMarkerBlock();
    public static final Item PLAYER_SPAWN_MARKER_ITEM = new BlockItem(PLAYER_SPAWN_MARKER, new Item.Properties());

    @Override
    public void onInitialize() {
        CustomizablePlayerSpawnCommon.init(new FabricPlatformServices());

        Registry.register(BuiltInRegistries.BLOCK, PLAYER_SPAWN_MARKER_ID, PLAYER_SPAWN_MARKER);
        Registry.register(BuiltInRegistries.ITEM, PLAYER_SPAWN_MARKER_ID, PLAYER_SPAWN_MARKER_ITEM);
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> entries.accept(PLAYER_SPAWN_MARKER_ITEM));

        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> CustomizablePlayerSpawnCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTING.register(server ->
                CustomizablePlayerSpawnCommon.spawnStructureManager().onServerStarting(server)
        );
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                CustomizablePlayerSpawnCommon.spawnStructureManager().onServerStarted(server)
        );
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                CustomizablePlayerSpawnCommon.spawnStructureManager().onPlayerLoggedIn(handler.player)
        );
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                CustomizablePlayerSpawnCommon.spawnStructureManager().onPlayerRespawn(newPlayer, alive)
        );
        ServerTickEvents.END_SERVER_TICK.register(server ->
                CustomizablePlayerSpawnCommon.spawnStructureManager().onServerTickEnd()
        );
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                !(player instanceof ServerPlayer serverPlayer)
                        || !(level instanceof ServerLevel serverLevel)
                        || StructureProtectionService.canBreakBlock(serverPlayer, serverLevel, pos)
        );
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
                return InteractionResult.PASS;
            }

            BlockPos clickedPos = hitResult.getBlockPos();
            BlockPos placePos = clickedPos.relative(hitResult.getDirection());
            if (!StructureProtectionService.canUseBlock(serverPlayer, serverLevel, clickedPos)
                    || !StructureProtectionService.canPlaceBlock(serverPlayer, serverLevel, placePos)) {
                return InteractionResult.FAIL;
            }

            return InteractionResult.PASS;
        });
    }
}
