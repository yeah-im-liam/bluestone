package yeah.im.liam.bluestone.neoforge;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
//#if MC < 26.1
//$$ import net.minecraft.world.entity.npc.villager.VillagerProfession;
//$$ import net.neoforged.neoforge.common.NeoForge;
//$$ import net.neoforged.neoforge.event.village.VillagerTradesEvent;
//$$ import yeah.im.liam.bluestone.trade.ClericTrades;
//#endif
import net.neoforged.neoforge.event.AddPackFindersEvent;
//#if MC >= 1.21
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
//#endif
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import yeah.im.liam.bluestone.Bluestone;
import yeah.im.liam.bluestone.block.ConverterBoxBlockEntity;
import yeah.im.liam.bluestone.registry.BluestoneBlocks;
import yeah.im.liam.bluestone.registry.Registrar;

@Mod(Bluestone.MOD_ID)
public final class BluestoneNeoForge {
    //#if MC >= 1.21
    public BluestoneNeoForge(IEventBus modEventBus) {
    //#else
    //#if FORGE
    //$$ // Forge hands mods nothing; the bus and the container come from the loading context.
    //$$ public BluestoneNeoForge() {
    //$$     IEventBus modEventBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
    //$$     net.minecraftforge.fml.ModContainer container = net.minecraftforge.fml.ModLoadingContext.get().getActiveContainer();
    //#else
    //$$ public BluestoneNeoForge(IEventBus modEventBus, net.neoforged.fml.ModContainer container) {
    //#endif
    //$$     // NeoForge starts mods for one side (BluestoneNeoForgeClient) only from 20.6.
    //$$     if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
    //$$         new yeah.im.liam.bluestone.neoforge.client.BluestoneNeoForgeClient(modEventBus, container);
    //$$     }
    //#endif
        modEventBus.addListener(BluestoneNeoForge::register);
        //#if MC >= 1.21
        modEventBus.addListener(BluestoneNeoForge::addComparator);
        //#endif
        modEventBus.addListener(BluestoneNeoForge::fillCreativeTabs);
        modEventBus.addListener(BluestoneNeoForge::addPacks);
        //#if MC < 26.1
        //$$ NeoForge.EVENT_BUS.addListener((VillagerTradesEvent event) -> {
        //$$     if (event.getType() == VillagerProfession.CLERIC) {
        //$$         event.getTrades().get(1).replaceAll(ClericTrades::wrap);
        //$$     }
        //$$ });
        //#endif
    }

    private static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> BluestoneBlocks.registerBlocks(registrar(Registries.BLOCK, helper)));
        event.register(Registries.ITEM, helper -> BluestoneBlocks.registerItems(registrar(Registries.ITEM, helper)));
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> BluestoneBlocks.registerBlockEntities(registrar(Registries.BLOCK_ENTITY_TYPE, helper),
            //#if MC >= 1.21.2
            block -> new BlockEntityType<>(ConverterBoxBlockEntity::new, Set.of(block))));
            //#else
            //$$ block -> BlockEntityType.Builder.of(ConverterBoxBlockEntity::new, block).build(null)));
            //#endif
        event.register(Registries.CREATIVE_MODE_TAB, helper -> helper.register(
            Bluestone.key(Registries.CREATIVE_MODE_TAB, "main"),
            CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.bluestone.main"))
                .icon(() -> new ItemStack(BluestoneBlocks.dust()))
                .displayItems((parameters, output) -> BluestoneBlocks.creativeTabItems().forEach(output::accept))
                .build()
        ));
    }

    // Before 1.21 BlockEntityTypeMixin does this.
    //#if MC >= 1.21
    private static void addComparator(BlockEntityTypeAddBlocksEvent event) {
        event.modify(ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, Bluestone.vanilla("comparator")), BluestoneBlocks.comparator());
    }
    //#endif

    private static void fillCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        ResourceKey<CreativeModeTab> tab = event.getTabKey();
        if (tab.equals(CreativeModeTabs.REDSTONE_BLOCKS)) {
            insertAfter(event, Items.COMPARATOR, BluestoneBlocks.components());
            insertAfter(event, Items.ACTIVATOR_RAIL, BluestoneBlocks.rails());
            insertAfter(event, Items.REDSTONE_ORE, List.of(Items.DEEPSLATE_REDSTONE_ORE));
            insertAfter(event, Items.DEEPSLATE_REDSTONE_ORE, BluestoneBlocks.ores());
        } else if (tab.equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) {
            insertAfter(event, Items.ACTIVATOR_RAIL, BluestoneBlocks.rails());
        } else if (tab.equals(CreativeModeTabs.NATURAL_BLOCKS)) {
            insertAfter(event, Items.DEEPSLATE_REDSTONE_ORE, BluestoneBlocks.ores());
        } else if (tab.equals(CreativeModeTabs.BUILDING_BLOCKS)) {
            insertAfter(event, Items.REDSTONE_BLOCK, BluestoneBlocks.storageBlocks());
        } else if (tab.equals(CreativeModeTabs.FUNCTIONAL_BLOCKS)) {
            insertAfter(event, Items.REDSTONE_TORCH, BluestoneBlocks.torches());
        } else if (tab.equals(CreativeModeTabs.INGREDIENTS)) {
            insertAfter(event, Items.REDSTONE, List.of(BluestoneBlocks.dust(), BluestoneBlocks.pasteItem(), BluestoneBlocks.purplestone()));
        }
    }

    private static void insertAfter(BuildCreativeModeTabContentsEvent event, ItemLike anchor, List<Item> items) {
        ItemStack previous = new ItemStack(anchor);
        for (Item item : items) {
            ItemStack stack = new ItemStack(item);
            //#if MC >= 1.21
            event.insertAfter(previous, stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            //#else
            //$$ event.getEntries().putAfter(previous, stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            //#endif
            previous = stack;
        }
    }

    // Fabric applies the textures in programmer_art/ together with the game's Programmer Art pack; NeoForge lists them
    // as a pack of their own. Both list Alt Torches Programmer Art.
    private static void addPacks(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.CLIENT_RESOURCES) {
            addPack(event, "programmer_art", "programmer_art");
            addPack(event, "resourcepacks/alt_torches_programmer_art", "alt_torches_programmer_art");
        }
    }

    // A NeoForge run from source doesn't count the common module's files as the mod's.
    private static void addPack(AddPackFindersEvent event, String folder, String name) {
        //#if MC >= 1.21.9
        boolean hasPack = ModList.get().getModFileById(Bluestone.MOD_ID).getFile().getContents().containsFile(folder + "/pack.mcmeta");
        //#else
        //$$ boolean hasPack = java.nio.file.Files.exists(ModList.get().getModFileById(Bluestone.MOD_ID).getFile().findResource(folder, "pack.mcmeta"));
        //#endif
        if (!hasPack) {
            return;
        }
        Component title = Component.translatable("pack.bluestone." + name);
        //#if MC >= 1.21
        event.addPackFinders(Bluestone.id(folder), PackType.CLIENT_RESOURCES, title, PackSource.BUILT_IN, false, Pack.Position.TOP);
        //#else
        //$$ // NeoForge 20.5 and older have no addPackFinders; this is what it does from 20.6.
        //$$ java.nio.file.Path root = ModList.get().getModFileById(Bluestone.MOD_ID).getFile().findResource(folder);
        //#if MC >= 1.20.5
        //$$ Pack pack = Pack.readMetaAndCreate(
        //$$     new net.minecraft.server.packs.PackLocationInfo("mod/" + Bluestone.id(folder), title, PackSource.BUILT_IN, java.util.Optional.empty()),
        //$$     net.minecraft.server.packs.repository.BuiltInPackSource.fromName(path -> new net.minecraft.server.packs.PathPackResources(path, root)),
        //$$     PackType.CLIENT_RESOURCES, new net.minecraft.server.packs.PackSelectionConfig(false, Pack.Position.TOP, false));
        //#elseif MC >= 1.20.2
        //$$ Pack pack = Pack.readMetaAndCreate("mod/" + Bluestone.id(folder), title, false,
        //$$     new net.minecraft.server.packs.PathPackResources.PathResourcesSupplier(root, true), PackType.CLIENT_RESOURCES, Pack.Position.TOP, PackSource.BUILT_IN);
        //#else
        //$$ Pack pack = Pack.readMetaAndCreate("mod/" + Bluestone.id(folder), title, false,
        //$$     id -> new net.minecraft.server.packs.PathPackResources(id, root, true), PackType.CLIENT_RESOURCES, Pack.Position.TOP, PackSource.BUILT_IN);
        //#endif
        //$$ if (pack != null) {
        //$$     event.addRepositorySource(packs -> packs.accept(pack));
        //$$ }
        //#endif
    }

    private static <T> Registrar<T> registrar(ResourceKey<? extends Registry<T>> registry, RegisterEvent.RegisterHelper<T> helper) {
        return new Registrar<>() {
            @Override
            public <V extends T> V register(String path, Function<ResourceKey<T>, V> factory) {
                ResourceKey<T> key = Bluestone.key(registry, path);
                V value = factory.apply(key);
                helper.register(key, value);
                return value;
            }
        };
    }
}
