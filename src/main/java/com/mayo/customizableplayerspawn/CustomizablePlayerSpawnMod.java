package com.mayo.customizableplayerspawn;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(CustomizablePlayerSpawnMod.MODID)
public class CustomizablePlayerSpawnMod {
    public static final String MODID = "customizableplayerspawn";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final RegistryObject<Block> PLAYER_SPAWN_MARKER = BLOCKS.register("player_spawn_marker", PlayerSpawnMarkerBlock::new);
    public static final RegistryObject<BlockItem> PLAYER_SPAWN_MARKER_ITEM = ITEMS.register(
            "player_spawn_marker",
            () -> new BlockItem(PLAYER_SPAWN_MARKER.get(), new Item.Properties())
    );

    @SuppressWarnings("removal")
    public CustomizablePlayerSpawnMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);

        modEventBus.addListener(this::addCreative);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        MinecraftForge.EVENT_BUS.register(new SpawnStructureManager());
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(PLAYER_SPAWN_MARKER_ITEM);
        }
    }
}
