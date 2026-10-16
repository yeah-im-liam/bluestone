package yeah.im.liam.bluestone.signal;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.PoweredBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Redstone and bluestone parts hear their own kind and purplestone, but not each other; purplestone hears both.
 */
public enum Channel implements Listener {
    REDSTONE,
    BLUESTONE,
    PURPLESTONE,
    GREENSTONE;

    @Override
    public boolean hears(Channel source) {
        if (this == source) {
            return true;
        }
        if (this == GREENSTONE || source == GREENSTONE) {
            return false;
        }
        return this == PURPLESTONE || source == PURPLESTONE;
    }

    public static Channel of(Block block) {
        return block instanceof SignalBlock signalBlock ? signalBlock.channel(block.defaultBlockState()) : REDSTONE;
    }

    public static @Nullable Channel ofSource(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof SignalBlock signalBlock) {
            return signalBlock.channel(state);
        }
        if (block == Blocks.REDSTONE_WIRE
            || block instanceof DiodeBlock
            || block instanceof RedstoneTorchBlock
            || block instanceof PoweredBlock
            || block instanceof DetectorRailBlock) {
            return REDSTONE;
        }
        return null;
    }
}
