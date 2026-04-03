package com.mayo.customizableplayerspawn;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(CustomizablePlayerSpawnMod.MODID)
public class CustomizablePlayerSpawnMod {
    public static final String MODID = "customizableplayerspawn";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static final DeferredBlock<Block> PLAYER_SPAWN_MARKER = BLOCKS.register("player_spawn_marker", PlayerSpawnMarkerBlock::new);
    public static final DeferredItem<BlockItem> PLAYER_SPAWN_MARKER_ITEM = ITEMS.registerSimpleBlockItem("player_spawn_marker", PLAYER_SPAWN_MARKER);

    public CustomizablePlayerSpawnMod(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);

        modEventBus.addListener(this::addCreative);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        NeoForge.EVENT_BUS.register(new SpawnStructureManager());
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(PLAYER_SPAWN_MARKER_ITEM);
        }
    }
}
