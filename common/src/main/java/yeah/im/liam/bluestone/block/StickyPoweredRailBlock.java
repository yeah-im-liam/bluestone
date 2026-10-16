package yeah.im.liam.bluestone.block;

import net.minecraft.world.level.block.PoweredRailBlock;

/**
 * The sticky powered rail and sticky activator rail on a floor: the game's powered and activator rails, on redstone.
 */
public class StickyPoweredRailBlock extends PoweredRailBlock {
    private final boolean activator;

    public StickyPoweredRailBlock(Properties properties, boolean activator) {
        super(properties);
        this.activator = activator;
    }

    // NeoForge tells powered and activator rails apart through this method instead of by block.
    public boolean isActivatorRail() {
        return activator;
    }
}
