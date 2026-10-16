package yeah.im.liam.bluestone.registry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.block.AttachedRailBlock;
import yeah.im.liam.bluestone.block.AttachedRailBlock.Kind;
import yeah.im.liam.bluestone.block.BluestoneDetectorRailBlock;
import yeah.im.liam.bluestone.block.BluestonePoweredRailBlock;
import yeah.im.liam.bluestone.block.ChainRailBlock;
import yeah.im.liam.bluestone.block.StickyPoweredRailBlock;
import yeah.im.liam.bluestone.block.StickyRailBlock;
import yeah.im.liam.bluestone.signal.Channel;

/**
 * Chain rails, and sticky rails, which also go on walls and ceilings: each sticky rail is a floor rail and an {@link
 * AttachedRailBlock} placed by the same item.
 */
public final class BluestoneRails {
    private static final Map<String, Item> ITEMS = new HashMap<>();
    private static final List<Block> POWERED_RAILS = new ArrayList<>();
    private static final List<Block> ACTIVATOR_RAILS = new ArrayList<>();
    private static final List<Runnable> ITEM_REGISTRATIONS = new ArrayList<>();
    private static @Nullable Registrar<Item> itemRegistrar;

    private BluestoneRails() {
    }

    static void registerBlocks(Registrar<Block> blocks) {
        floorOnly(blocks, "chain_rail", properties -> new ChainRailBlock(properties, Channel.REDSTONE));
        floorOnly(blocks, "bluestone_chain_rail", properties -> new ChainRailBlock(properties, Channel.BLUESTONE));
        sticky(blocks, "sticky_rail", StickyRailBlock::new, Kind.PLAIN, null);
        sticky(blocks, "sticky_powered_rail", properties -> new StickyPoweredRailBlock(properties, false), Kind.POWERED, Channel.REDSTONE);
        sticky(blocks, "sticky_bluestone_powered_rail", properties -> new BluestonePoweredRailBlock(properties, false), Kind.POWERED, Channel.BLUESTONE);
        sticky(blocks, "sticky_detector_rail", DetectorRailBlock::new, Kind.DETECTOR, Channel.REDSTONE);
        sticky(blocks, "sticky_bluestone_detector_rail", BluestoneDetectorRailBlock::new, Kind.DETECTOR, Channel.BLUESTONE);
        sticky(blocks, "sticky_activator_rail", properties -> new StickyPoweredRailBlock(properties, true), Kind.ACTIVATOR, Channel.REDSTONE);
        sticky(blocks, "sticky_bluestone_activator_rail", properties -> new BluestonePoweredRailBlock(properties, true), Kind.ACTIVATOR, Channel.BLUESTONE);
        sticky(blocks, "sticky_chain_rail", properties -> new ChainRailBlock(properties, Channel.REDSTONE), Kind.CHAIN, Channel.REDSTONE);
        sticky(blocks, "sticky_bluestone_chain_rail", properties -> new ChainRailBlock(properties, Channel.BLUESTONE), Kind.CHAIN, Channel.BLUESTONE);
    }

    static void registerItems(Registrar<Item> items) {
        itemRegistrar = items;
        ITEM_REGISTRATIONS.forEach(Runnable::run);
    }

    private static void floorOnly(Registrar<Block> blocks, String name, Function<BlockBehaviour.Properties, Block> factory) {
        Block floor = blocks.register(name, key -> factory.apply(properties(key)));
        ITEM_REGISTRATIONS.add(() -> ITEMS.put(name, itemRegistrar.register(name, key -> new BlockItem(floor, itemProperties(key)))));
    }

    private static void sticky(Registrar<Block> blocks, String name, Function<BlockBehaviour.Properties, Block> factory, Kind kind, @Nullable Channel channel) {
        Block floor = blocks.register(name, key -> factory.apply(properties(key)));
        Block attached = blocks.register("attached_" + name, key -> new AttachedRailBlock(properties(key)
            .overrideLootTable(floor.getLootTable())
            .overrideDescription(floor.getDescriptionId()), kind, channel));
        if (kind == Kind.POWERED) {
            POWERED_RAILS.add(floor);
        } else if (kind == Kind.ACTIVATOR) {
            ACTIVATOR_RAILS.add(floor);
        }
        ITEM_REGISTRATIONS.add(() -> ITEMS.put(name, itemRegistrar.register(name, key -> new SurfaceRailItem(floor, attached, itemProperties(key)))));
    }

    private static BlockBehaviour.Properties properties(ResourceKey<Block> key) {
        return BlockBehaviour.Properties.of()
            .noCollision()
            .strength(0.7F)
            .sound(SoundType.METAL)
            .setId(key);
    }

    private static Item.Properties itemProperties(ResourceKey<Item> key) {
        return new Item.Properties().setId(key).useBlockDescriptionPrefix();
    }

    static Item item(String name) {
        return ITEMS.get(name);
    }

    // Sticky powered and activator rails on the floor: minecarts on Fabric look for the game's own by block.
    public static boolean poweredRail(BlockState state) {
        return POWERED_RAILS.contains(state.getBlock());
    }

    public static boolean activatorRail(BlockState state) {
        return ACTIVATOR_RAILS.contains(state.getBlock());
    }
}
