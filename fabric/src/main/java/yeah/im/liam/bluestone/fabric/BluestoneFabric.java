package yeah.im.liam.bluestone.fabric;

import java.util.List;
import java.util.function.Function;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityType;
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
import net.minecraft.world.level.levelgen.GenerationStep;
import yeah.im.liam.bluestone.Bluestone;
import yeah.im.liam.bluestone.registry.BluestoneBlocks;
import yeah.im.liam.bluestone.registry.Registrar;

public final class BluestoneFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        BluestoneBlocks.registerBlocks(registrar(BuiltInRegistries.BLOCK));
        BluestoneBlocks.registerItems(registrar(BuiltInRegistries.ITEM));
        // The bluestone comparator keeps the game's comparator block entity.
        ((FabricBlockEntityType) BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("comparator")))
            .addValidBlock(BluestoneBlocks.comparator());
        registerCreativeTabs();
        addOres();
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
        CreativeModeTab tab = FabricCreativeModeTab.builder()
            .title(Component.translatable("itemGroup.bluestone.main"))
            .icon(() -> new ItemStack(BluestoneBlocks.dust()))
            .displayItems((parameters, output) -> BluestoneBlocks.creativeTabItems().forEach(output::accept))
            .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Bluestone.key(Registries.CREATIVE_MODE_TAB, "main"), tab);

        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(output -> {
            output.insertAfter(Items.COMPARATOR, stacks(BluestoneBlocks.components()));
            output.insertAfter(Items.ACTIVATOR_RAIL, stacks(BluestoneBlocks.rails()));
            output.insertAfter(Items.REDSTONE_ORE, stacks(List.of(BluestoneBlocks.ores().getFirst())));
        });
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.insertAfter(Items.ACTIVATOR_RAIL, stacks(BluestoneBlocks.rails())));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> output.insertAfter(Items.DEEPSLATE_REDSTONE_ORE, stacks(BluestoneBlocks.ores())));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> output.insertAfter(Items.REDSTONE_BLOCK, new ItemStack(BluestoneBlocks.blockItem())));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.insertAfter(Items.REDSTONE_TORCH, new ItemStack(BluestoneBlocks.torchItem())));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.insertAfter(Items.REDSTONE, new ItemStack(BluestoneBlocks.dust())));
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
