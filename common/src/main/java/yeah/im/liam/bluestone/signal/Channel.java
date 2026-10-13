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
 * Redstone and bluestone parts only hear their own kind; purplestone hears both. Levers, buttons, observers and other
 * plain sources belong to no channel, so everything hears them.
 */
public enum Channel implements Listener {
    REDSTONE,
    BLUESTONE,
    PURPLESTONE;

    @Override
    public boolean hears(Channel source) {
        return this == PURPLESTONE || this == source;
    }

    /**
     * The channel a game block listens on when its own code reads signals: its own channel, or redstone for the
     * game's blocks.
     */
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
