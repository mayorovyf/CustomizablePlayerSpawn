package com.mayo.customizableplayerspawn.neoforge;

import com.mayo.customizableplayerspawn.CustomizablePlayerSpawnCommon;
import com.mayo.customizableplayerspawn.PlayerSpawnMarkerBlock;
import com.mayo.customizableplayerspawn.command.CustomizablePlayerSpawnCommands;
import com.mayo.customizableplayerspawn.protection.StructureProtectionService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(CustomizablePlayerSpawnCommon.MODID)
public final class CustomizablePlayerSpawnNeoForge {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, CustomizablePlayerSpawnCommon.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, CustomizablePlayerSpawnCommon.MODID);

    public static final RegistryObject<Block> PLAYER_SPAWN_MARKER = BLOCKS.register("player_spawn_marker", PlayerSpawnMarkerBlock::new);
    public static final RegistryObject<Item> PLAYER_SPAWN_MARKER_ITEM = ITEMS.register(
            "player_spawn_marker",
            () -> new BlockItem(PLAYER_SPAWN_MARKER.get(), new Item.Properties())
    );

    @SuppressWarnings("removal")
    public CustomizablePlayerSpawnNeoForge() {
        CustomizablePlayerSpawnCommon.init(new NeoForgePlatformServices());

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(this::addCreative);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(PLAYER_SPAWN_MARKER_ITEM);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        CustomizablePlayerSpawnCommon.spawnStructureManager().onServerStarting(event.getServer());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        CustomizablePlayerSpawnCommon.spawnStructureManager().onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            CustomizablePlayerSpawnCommon.spawnStructureManager().onPlayerLoggedIn(player);
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            CustomizablePlayerSpawnCommon.spawnStructureManager().onPlayerRespawn(player, event.isEndConquered());
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            CustomizablePlayerSpawnCommon.spawnStructureManager().onServerTickEnd();
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CustomizablePlayerSpawnCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && event.getLevel() instanceof ServerLevel level
                && !StructureProtectionService.canBreakBlock(player, level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getLevel() instanceof ServerLevel level
                && !StructureProtectionService.canPlaceBlock(player, level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        boolean canUse = StructureProtectionService.canUseBlock(player, level, event.getPos());
        boolean canPlace = StructureProtectionService.canPlaceBlock(player, level, event.getHitVec().getBlockPos().relative(event.getHitVec().getDirection()));
        if (!canUse || !canPlace) {
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.DENY);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) {
            event.getAffectedBlocks().removeIf(pos -> !StructureProtectionService.canExplosionAffect(level, pos));
        }
    }

    @SubscribeEvent
    public void onFluidPlaceBlock(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level && !StructureProtectionService.canFluidAffect(level, event.getPos())) {
            event.setNewState(event.getOriginalState());
        }
    }
}
