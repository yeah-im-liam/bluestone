package yeah.im.liam.bluestone.fabric;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import yeah.im.liam.bluestone.registry.BluestoneBlocks;
import yeah.im.liam.bluestone.registry.BluestoneRails;

/**
 * Minecarts on Fabric check for the game's own powered and activator rails by block; the bluestone and sticky ones
 * count as them.
 */
public final class Rails {
    private Rails() {
    }

    public static boolean alsoBluestone(BlockState state, Object block) {
        if (block == Blocks.POWERED_RAIL) {
            return state.is(BluestoneBlocks.poweredRail()) || BluestoneRails.poweredRail(state);
        }
        if (block == Blocks.ACTIVATOR_RAIL) {
            return state.is(BluestoneBlocks.activatorRail()) || BluestoneRails.activatorRail(state);
        }
        return false;
    }
}
