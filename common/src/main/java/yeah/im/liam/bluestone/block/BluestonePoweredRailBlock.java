package yeah.im.liam.bluestone.block;

import net.minecraft.world.level.block.PoweredRailBlock;

/**
 * Both the bluestone powered rail and the bluestone activator rail, like the game uses one class for its two.
 */
public class BluestonePoweredRailBlock extends PoweredRailBlock implements BluestoneComponent {
    private final boolean activator;

    public BluestonePoweredRailBlock(Properties properties, boolean activator) {
        super(properties);
        this.activator = activator;
    }

    // NeoForge tells powered and activator rails apart through this method instead of by block.
    public boolean isActivatorRail() {
        return activator;
    }
}
