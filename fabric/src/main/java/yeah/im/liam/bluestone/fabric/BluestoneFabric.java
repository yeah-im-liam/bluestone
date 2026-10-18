package yeah.im.liam.bluestone.fabric;

import java.util.List;
//#if MC >= 26.1
import java.util.Set;
//#endif
import java.util.function.Function;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
//#if MC >= 26.1
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
//#else
//$$ import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
//$$ import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
//#endif
//#if MC >= 1.21
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityType;
//#endif
//#if MC < 26.1
//$$ import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
//#endif
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.levelgen.GenerationStep;
import yeah.im.liam.bluestone.Bluestone;
import yeah.im.liam.bluestone.block.ConverterBoxBlockEntity;
import yeah.im.liam.bluestone.registry.BluestoneBlocks;
import yeah.im.liam.bluestone.registry.Registrar;
//#if MC < 26.1
//$$ import yeah.im.liam.bluestone.trade.ClericTrades;
//#endif

public final class BluestoneFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        BluestoneBlocks.registerBlocks(registrar(BuiltInRegistries.BLOCK));
        BluestoneBlocks.registerItems(registrar(BuiltInRegistries.ITEM));
        //#if MC >= 26.1
        BluestoneBlocks.registerBlockEntities(registrar(BuiltInRegistries.BLOCK_ENTITY_TYPE), block -> new BlockEntityType<>(ConverterBoxBlockEntity::new, Set.of(block)));
        ((FabricBlockEntityType) BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(Bluestone.vanilla("comparator")))
            .addValidBlock(BluestoneBlocks.comparator());
        //#else
        //$$ BluestoneBlocks.registerBlockEntities(registrar(BuiltInRegistries.BLOCK_ENTITY_TYPE), block -> FabricBlockEntityTypeBuilder.create(ConverterBoxBlockEntity::new, block).build());
        //#if MC >= 1.21
        //$$ ((FabricBlockEntityType) BlockEntityType.COMPARATOR).addSupportedBlock(BluestoneBlocks.comparator());
        //#endif
        //#endif
        registerCreativeTabs();
        addOres();
        //#if MC < 26.1
        //$$ ClericTrades.wrapGameTrades();
        //#endif
    }

    // Same as the NeoForge biome modifiers in data/bluestone/neoforge/biome_modifier.
    private static void addOres() {
        for (String feature : new String[]{"ore_bluestone", "ore_bluestone_lower"}) {
            BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES, Bluestone.key(Registries.PLACED_FEATURE, feature));
        }
        TagKey<Biome> waterBiomes = TagKey.create(Registries.BIOME, Bluestone.id("has_more_bluestone"));
        for (String feature : new String[]{"ore_bluestone_extra", "ore_bluestone_lower_extra"}) {
            BiomeModifications.addFeature(BiomeSelectors.tag(waterBiomes), GenerationStep.Decoration.UNDERGROUND_ORES, Bluestone.key(Registries.PLACED_FEATURE, feature));
        }
    }

    private static void registerCreativeTabs() {
        //#if MC >= 26.1
        CreativeModeTab tab = FabricCreativeModeTab.builder()
        //#else
        //$$ CreativeModeTab tab = FabricItemGroup.builder()
        //#endif
            .title(Component.translatable("itemGroup.bluestone.main"))
            .icon(() -> new ItemStack(BluestoneBlocks.dust()))
            .displayItems((parameters, output) -> BluestoneBlocks.creativeTabItems().forEach(output::accept))
            .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Bluestone.key(Registries.CREATIVE_MODE_TAB, "main"), tab);

        after(CreativeModeTabs.REDSTONE_BLOCKS, Items.COMPARATOR, BluestoneBlocks.components());
        after(CreativeModeTabs.REDSTONE_BLOCKS, Items.ACTIVATOR_RAIL, BluestoneBlocks.rails());
        after(CreativeModeTabs.REDSTONE_BLOCKS, Items.REDSTONE_ORE, List.of(Items.DEEPSLATE_REDSTONE_ORE));
        after(CreativeModeTabs.REDSTONE_BLOCKS, Items.DEEPSLATE_REDSTONE_ORE, BluestoneBlocks.ores());
        after(CreativeModeTabs.TOOLS_AND_UTILITIES, Items.ACTIVATOR_RAIL, BluestoneBlocks.rails());
        after(CreativeModeTabs.NATURAL_BLOCKS, Items.DEEPSLATE_REDSTONE_ORE, BluestoneBlocks.ores());
        after(CreativeModeTabs.BUILDING_BLOCKS, Items.REDSTONE_BLOCK, BluestoneBlocks.storageBlocks());
        after(CreativeModeTabs.FUNCTIONAL_BLOCKS, Items.REDSTONE_TORCH, BluestoneBlocks.torches());
        after(CreativeModeTabs.INGREDIENTS, Items.REDSTONE, List.of(BluestoneBlocks.dust(), BluestoneBlocks.pasteItem(), BluestoneBlocks.purplestone()));
    }

    // The stacks are made when the tab fills: 26.3 gives items their components only after mods have started.
    private static void after(ResourceKey<CreativeModeTab> tab, Item anchor, List<Item> items) {
        //#if MC >= 26.1
        CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> output.insertAfter(anchor, stacks(items)));
        //#else
        //$$ ItemGroupEvents.modifyEntriesEvent(tab).register(entries -> entries.addAfter(anchor, stacks(items)));
        //#endif
    }

    private static List<ItemStack> stacks(List<Item> items) {
        return items.stream().map(ItemStack::new).toList();
    }

    private static <T> Registrar<T> registrar(Registry<T> registry) {
        return new Registrar<>() {
            @Override
            public <V extends T> V register(String path, Function<ResourceKey<T>, V> factory) {
                ResourceKey<T> key = Bluestone.key(registry.key(), path);
                V value = factory.apply(key);
                Registry.register(registry, key, value);
                return value;
            }
        };
    }
}
