package yeah.im.liam.bluestone.registry;

import java.util.List;
import java.util.function.ToIntFunction;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import yeah.im.liam.bluestone.Bluestone;
import yeah.im.liam.bluestone.block.AttachedRailBlock;
import yeah.im.liam.bluestone.block.BluestoneBlock;
import yeah.im.liam.bluestone.block.BluestoneComparatorBlock;
import yeah.im.liam.bluestone.block.BluestoneDetectorRailBlock;
import yeah.im.liam.bluestone.block.BluestoneOreBlock;
import yeah.im.liam.bluestone.block.BluestonePoweredRailBlock;
import yeah.im.liam.bluestone.block.BluestoneRepeaterBlock;
import yeah.im.liam.bluestone.block.BluestoneTorchBlock;
import yeah.im.liam.bluestone.block.BluestoneWallTorchBlock;
import yeah.im.liam.bluestone.block.BluestoneWireBlock;
import yeah.im.liam.bluestone.block.ChainGearRailBlock;
import yeah.im.liam.bluestone.block.ColoredWireBlock;
import yeah.im.liam.bluestone.block.ConverterBlock;
import yeah.im.liam.bluestone.block.GreenstoneBlock;
import yeah.im.liam.bluestone.block.GreenstoneComparatorBlock;
import yeah.im.liam.bluestone.block.GreenstonePasteBlock;
import yeah.im.liam.bluestone.block.GreenstoneRepeaterBlock;
import yeah.im.liam.bluestone.block.GreenstoneTorchBlock;
import yeah.im.liam.bluestone.block.GreenstoneWallTorchBlock;
import yeah.im.liam.bluestone.block.PurplestoneBlock;
import yeah.im.liam.bluestone.block.PurplestoneTorchBlock;
import yeah.im.liam.bluestone.block.PurplestoneWallTorchBlock;
import yeah.im.liam.bluestone.block.PurplestoneWireBlock;
import yeah.im.liam.bluestone.block.SlimeRailBlock;
import yeah.im.liam.bluestone.block.WallConverterBlock;

/**
 * Block and item settings copy the redstone versions.
 */
public final class BluestoneBlocks {
    // Broken off when a piston pushes it. Called DESTROY before Minecraft 26.3.
    private static final PushReaction POPPED = pushReaction("POPPED", "DESTROY");

    private static BluestoneWireBlock wire;
    private static BluestoneBlock block;
    private static BluestoneOreBlock ore;
    private static BluestoneOreBlock deepslateOre;
    private static BluestoneTorchBlock torch;
    private static BluestoneWallTorchBlock wallTorch;
    private static BluestoneRepeaterBlock repeater;
    private static BluestoneComparatorBlock comparator;
    private static BluestonePoweredRailBlock poweredRail;
    private static BluestonePoweredRailBlock activatorRail;
    private static BluestoneDetectorRailBlock detectorRail;
    private static PurplestoneWireBlock purplestoneWire;
    private static PurplestoneBlock purplestoneBlock;
    private static PurplestoneTorchBlock purplestoneTorch;
    private static PurplestoneWallTorchBlock purplestoneWallTorch;
    private static ConverterBlock converter;
    private static GreenstonePasteBlock paste;
    private static GreenstoneTorchBlock greenstoneTorch;
    private static GreenstoneWallTorchBlock greenstoneWallTorch;
    private static GreenstoneBlock greenstoneBlock;
    private static GreenstoneRepeaterBlock greenstoneRepeater;
    private static GreenstoneComparatorBlock greenstoneComparator;
    private static WallConverterBlock wallConverter;
    private static SlimeRailBlock slimeRail;
    private static AttachedRailBlock attachedSlimeRail;
    private static ChainGearRailBlock chainGearRail;
    private static ChainGearRailBlock slimeChainGearRail;
    private static AttachedRailBlock attachedSlimeChainGearRail;

    private static Item dust;
    private static Item blockItem;
    private static Item oreItem;
    private static Item deepslateOreItem;
    private static Item torchItem;
    private static Item repeaterItem;
    private static Item comparatorItem;
    private static Item poweredRailItem;
    private static Item activatorRailItem;
    private static Item detectorRailItem;
    private static Item purplestone;
    private static Item purplestoneTorchItem;
    private static Item purplestoneBlockItem;
    private static Item converterItem;
    private static Item pasteItem;
    private static Item greenstoneTorchItem;
    private static Item greenstoneBlockItem;
    private static Item greenstoneRepeaterItem;
    private static Item greenstoneComparatorItem;
    private static Item wallConverterItem;
    private static Item slimeRailItem;
    private static Item chainGearRailItem;
    private static Item slimeChainGearRailItem;

    private BluestoneBlocks() {
    }

    public static void registerBlocks(Registrar<Block> blocks) {
        wire = blocks.register("bluestone_wire", key -> new BluestoneWireBlock(props(key, wireProperties())));
        purplestoneWire = blocks.register("purplestone_wire", key -> new PurplestoneWireBlock(props(key, wireProperties())));
        block = blocks.register("bluestone_block", key -> new BluestoneBlock(props(key, BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_BLUE)
            .requiresCorrectToolForDrops()
            .strength(5.0F, 6.0F)
            .sound(SoundType.METAL)
            .isRedstoneConductor((state, level, pos) -> false))));
        purplestoneBlock = blocks.register("purplestone_block", key -> new PurplestoneBlock(props(key, BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_PURPLE)
            .requiresCorrectToolForDrops()
            .strength(5.0F, 6.0F)
            .sound(SoundType.METAL)
            .isRedstoneConductor((state, level, pos) -> false))));
        ore = blocks.register("bluestone_ore", key -> new BluestoneOreBlock(props(key, BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .instrument(NoteBlockInstrument.BASEDRUM)
            .requiresCorrectToolForDrops()
            .randomTicks()
            .lightLevel(litEmission(9))
            .strength(3.0F, 3.0F))));
        deepslateOre = blocks.register("deepslate_bluestone_ore", key -> new BluestoneOreBlock(props(key, BlockBehaviour.Properties.ofLegacyCopy(ore)
            .mapColor(MapColor.DEEPSLATE)
            .strength(4.5F, 3.0F)
            .sound(SoundType.DEEPSLATE))));
        torch = blocks.register("bluestone_torch", key -> new BluestoneTorchBlock(props(key, torchProperties(BlockBehaviour.Properties.of()))));
        wallTorch = blocks.register("bluestone_wall_torch", key -> new BluestoneWallTorchBlock(props(key, torchProperties(BlockBehaviour.Properties.of()
            .overrideLootTable(torch.getLootTable())
            .overrideDescription(torch.getDescriptionId())))));
        purplestoneTorch = blocks.register("purplestone_torch", key -> new PurplestoneTorchBlock(props(key, torchProperties(BlockBehaviour.Properties.of()))));
        purplestoneWallTorch = blocks.register("purplestone_wall_torch", key -> new PurplestoneWallTorchBlock(props(key, torchProperties(BlockBehaviour.Properties.of()
            .overrideLootTable(purplestoneTorch.getLootTable())
            .overrideDescription(purplestoneTorch.getDescriptionId())))));
        repeater = blocks.register("bluestone_repeater", key -> new BluestoneRepeaterBlock(props(key, diodeProperties())));
        comparator = blocks.register("bluestone_comparator", key -> new BluestoneComparatorBlock(props(key, diodeProperties())));
        converter = blocks.register("converter", key -> new ConverterBlock(props(key, diodeProperties())));
        paste = blocks.register("greenstone_paste", key -> new GreenstonePasteBlock(props(key, BlockBehaviour.Properties.of()
            .noCollision()
            .instabreak()
            .sound(SoundType.SLIME_BLOCK)
            .pushReaction(POPPED))));
        greenstoneTorch = blocks.register("greenstone_torch", key -> new GreenstoneTorchBlock(props(key, torchProperties(BlockBehaviour.Properties.of()))));
        greenstoneWallTorch = blocks.register("greenstone_wall_torch", key -> new GreenstoneWallTorchBlock(props(key, torchProperties(BlockBehaviour.Properties.of()
            .overrideLootTable(greenstoneTorch.getLootTable())
            .overrideDescription(greenstoneTorch.getDescriptionId())))));
        greenstoneBlock = blocks.register("greenstone_block", key -> new GreenstoneBlock(props(key, BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_GREEN)
            .requiresCorrectToolForDrops()
            .strength(5.0F, 6.0F)
            .sound(SoundType.METAL)
            .isRedstoneConductor((state, level, pos) -> false))));
        greenstoneRepeater = blocks.register("greenstone_repeater", key -> new GreenstoneRepeaterBlock(props(key, diodeProperties())));
        greenstoneComparator = blocks.register("greenstone_comparator", key -> new GreenstoneComparatorBlock(props(key, diodeProperties())));
        wallConverter = blocks.register("wall_converter", key -> new WallConverterBlock(props(key, BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .requiresCorrectToolForDrops()
            .strength(1.5F, 6.0F)
            .isRedstoneConductor((state, level, pos) -> false))));
        slimeRail = blocks.register("slime_rail", key -> new SlimeRailBlock(props(key, railProperties().sound(SoundType.SLIME_BLOCK))));
        attachedSlimeRail = blocks.register("attached_slime_rail", key -> new AttachedRailBlock(props(key, railProperties().sound(SoundType.SLIME_BLOCK)
            .overrideLootTable(slimeRail.getLootTable())
            .overrideDescription(slimeRail.getDescriptionId())), false));
        chainGearRail = blocks.register("chain_gear_rail", key -> new ChainGearRailBlock(props(key, railProperties())));
        slimeChainGearRail = blocks.register("slime_chain_gear_rail", key -> new ChainGearRailBlock(props(key, railProperties().sound(SoundType.SLIME_BLOCK))));
        attachedSlimeChainGearRail = blocks.register("attached_slime_chain_gear_rail", key -> new AttachedRailBlock(props(key, railProperties().sound(SoundType.SLIME_BLOCK)
            .overrideLootTable(slimeChainGearRail.getLootTable())
            .overrideDescription(slimeChainGearRail.getDescriptionId())), true));
        poweredRail = blocks.register("bluestone_powered_rail", key -> new BluestonePoweredRailBlock(props(key, railProperties()), false));
        detectorRail = blocks.register("bluestone_detector_rail", key -> new BluestoneDetectorRailBlock(props(key, railProperties())));
        activatorRail = blocks.register("bluestone_activator_rail", key -> new BluestonePoweredRailBlock(props(key, railProperties()), true));
    }

    public static void registerItems(Registrar<Item> items) {
        dust = items.register("bluestone", key -> new BlockItem(wire, new Item.Properties().setId(key).useItemDescriptionPrefix()
            .trimMaterial(Bluestone.key(Registries.TRIM_MATERIAL, "bluestone"))));
        purplestone = items.register("purplestone", key -> new BlockItem(purplestoneWire, new Item.Properties().setId(key).useItemDescriptionPrefix()
            .trimMaterial(Bluestone.key(Registries.TRIM_MATERIAL, "purplestone"))));
        blockItem = blockItem(items, "bluestone_block", block);
        purplestoneBlockItem = blockItem(items, "purplestone_block", purplestoneBlock);
        oreItem = blockItem(items, "bluestone_ore", ore);
        deepslateOreItem = blockItem(items, "deepslate_bluestone_ore", deepslateOre);
        torchItem = items.register("bluestone_torch", key -> new StandingAndWallBlockItem(torch, wallTorch, Direction.DOWN, itemProps(key)));
        purplestoneTorchItem = items.register("purplestone_torch", key -> new StandingAndWallBlockItem(purplestoneTorch, purplestoneWallTorch, Direction.DOWN, itemProps(key)));
        repeaterItem = blockItem(items, "bluestone_repeater", repeater);
        comparatorItem = blockItem(items, "bluestone_comparator", comparator);
        converterItem = blockItem(items, "converter", converter);
        pasteItem = blockItem(items, "greenstone_paste", paste);
        greenstoneTorchItem = items.register("greenstone_torch", key -> new StandingAndWallBlockItem(greenstoneTorch, greenstoneWallTorch, Direction.DOWN, itemProps(key)));
        greenstoneBlockItem = blockItem(items, "greenstone_block", greenstoneBlock);
        greenstoneRepeaterItem = blockItem(items, "greenstone_repeater", greenstoneRepeater);
        greenstoneComparatorItem = blockItem(items, "greenstone_comparator", greenstoneComparator);
        wallConverterItem = blockItem(items, "wall_converter", wallConverter);
        slimeRailItem = items.register("slime_rail", key -> new SurfaceRailItem(slimeRail, attachedSlimeRail, itemProps(key)));
        chainGearRailItem = blockItem(items, "chain_gear_rail", chainGearRail);
        slimeChainGearRailItem = items.register("slime_chain_gear_rail", key -> new SurfaceRailItem(slimeChainGearRail, attachedSlimeChainGearRail, itemProps(key)));
        poweredRailItem = blockItem(items, "bluestone_powered_rail", poweredRail);
        detectorRailItem = blockItem(items, "bluestone_detector_rail", detectorRail);
        activatorRailItem = blockItem(items, "bluestone_activator_rail", activatorRail);
    }

    public static Block comparator() {
        return comparator;
    }

    public static List<ColoredWireBlock> wires() {
        return List.of(wire, purplestoneWire);
    }

    public static GreenstonePasteBlock paste() {
        return paste;
    }

    public static Block poweredRail() {
        return poweredRail;
    }

    public static Block activatorRail() {
        return activatorRail;
    }

    public static Item dust() {
        return dust;
    }

    public static Item purplestone() {
        return purplestone;
    }

    /**
     * Everything for the mod's own creative tab: the bluestone, greenstone and purplestone parts, each in the order the
     * redstone versions appear, then the converters.
     */
    public static List<Item> creativeTabItems() {
        return List.of(dust, torchItem, blockItem, repeaterItem, comparatorItem,
            pasteItem, greenstoneTorchItem, greenstoneBlockItem, greenstoneRepeaterItem, greenstoneComparatorItem,
            purplestone, purplestoneTorchItem, purplestoneBlockItem, converterItem, wallConverterItem,
            poweredRailItem, detectorRailItem, activatorRailItem, slimeRailItem, chainGearRailItem, slimeChainGearRailItem, oreItem, deepslateOreItem);
    }

    public static List<Item> components() {
        return List.of(dust, torchItem, blockItem, repeaterItem, comparatorItem,
            pasteItem, greenstoneTorchItem, greenstoneBlockItem, greenstoneRepeaterItem, greenstoneComparatorItem,
            purplestone, purplestoneTorchItem, purplestoneBlockItem, converterItem, wallConverterItem);
    }

    public static List<Item> rails() {
        return List.of(poweredRailItem, detectorRailItem, activatorRailItem, slimeRailItem, chainGearRailItem, slimeChainGearRailItem);
    }

    public static List<Item> ores() {
        return List.of(oreItem, deepslateOreItem);
    }

    public static List<Item> storageBlocks() {
        return List.of(blockItem, greenstoneBlockItem, purplestoneBlockItem);
    }

    public static Item pasteItem() {
        return pasteItem;
    }

    public static List<Item> torches() {
        return List.of(torchItem, greenstoneTorchItem, purplestoneTorchItem);
    }

    private static Item blockItem(Registrar<Item> items, String path, Block block) {
        return items.register(path, key -> new BlockItem(block, itemProps(key)));
    }

    private static Item.Properties itemProps(ResourceKey<Item> key) {
        return new Item.Properties().setId(key).useBlockDescriptionPrefix();
    }

    private static BlockBehaviour.Properties props(ResourceKey<Block> key, BlockBehaviour.Properties properties) {
        return properties.setId(key);
    }

    private static BlockBehaviour.Properties wireProperties() {
        return BlockBehaviour.Properties.of()
            .noCollision()
            .instabreak()
            .pushReaction(POPPED);
    }

    private static BlockBehaviour.Properties torchProperties(BlockBehaviour.Properties properties) {
        return properties
            .noCollision()
            .instabreak()
            .lightLevel(litEmission(7))
            .sound(SoundType.WOOD)
            .pushReaction(POPPED);
    }

    private static BlockBehaviour.Properties diodeProperties() {
        return BlockBehaviour.Properties.of()
            .instabreak()
            .sound(SoundType.STONE)
            .pushReaction(POPPED);
    }

    private static BlockBehaviour.Properties railProperties() {
        return BlockBehaviour.Properties.of()
            .noCollision()
            .strength(0.7F)
            .sound(SoundType.METAL);
    }

    private static PushReaction pushReaction(String... names) {
        for (String name : names) {
            for (PushReaction reaction : PushReaction.values()) {
                if (reaction.name().equals(name)) {
                    return reaction;
                }
            }
        }
        throw new IllegalStateException("No push reaction named " + String.join(" or ", names));
    }

    private static ToIntFunction<BlockState> litEmission(int light) {
        return state -> state.getValue(BlockStateProperties.LIT) ? light : 0;
    }
}
