package yeah.im.liam.bluestone.neoforge;

import java.util.List;
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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import yeah.im.liam.bluestone.Bluestone;
import yeah.im.liam.bluestone.registry.BluestoneBlocks;
import yeah.im.liam.bluestone.registry.Registrar;

@Mod(Bluestone.MOD_ID)
public final class BluestoneNeoForge {
    public BluestoneNeoForge(IEventBus modEventBus) {
        modEventBus.addListener(BluestoneNeoForge::register);
        modEventBus.addListener(BluestoneNeoForge::addComparator);
        modEventBus.addListener(BluestoneNeoForge::fillCreativeTabs);
        modEventBus.addListener(BluestoneNeoForge::addPacks);
    }

    private static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> BluestoneBlocks.registerBlocks(registrar(Registries.BLOCK, helper)));
        event.register(Registries.ITEM, helper -> BluestoneBlocks.registerItems(registrar(Registries.ITEM, helper)));
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> BluestoneBlocks.registerBlockEntities(registrar(Registries.BLOCK_ENTITY_TYPE, helper)));
        event.register(Registries.CREATIVE_MODE_TAB, helper -> helper.register(
            Bluestone.key(Registries.CREATIVE_MODE_TAB, "main"),
            CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.bluestone.main"))
                .icon(() -> new ItemStack(BluestoneBlocks.dust()))
                .displayItems((parameters, output) -> BluestoneBlocks.creativeTabItems().forEach(output::accept))
                .build()
        ));
    }

    private static void addComparator(BlockEntityTypeAddBlocksEvent event) {
        event.modify(ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, Identifier.withDefaultNamespace("comparator")), BluestoneBlocks.comparator());
    }

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
            event.insertAfter(previous, stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            previous = stack;
        }
    }

    // Fabric applies the textures in programmer_art/ together with the game's Programmer Art pack; NeoForge lists them
    // as a pack of their own. A NeoForge run from source doesn't count the common module's files as the mod's.
    private static void addPacks(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.CLIENT_RESOURCES
            && ModList.get().getModFileById(Bluestone.MOD_ID).getFile().getContents().containsFile("programmer_art/pack.mcmeta")) {
            event.addPackFinders(Bluestone.id("programmer_art"), PackType.CLIENT_RESOURCES,
                Component.translatable("pack.bluestone.programmer_art"), PackSource.BUILT_IN, false, Pack.Position.TOP);
        }
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
