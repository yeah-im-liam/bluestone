package yeah.im.liam.bluestone.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.signal.Channel;
import yeah.im.liam.bluestone.signal.SignalBlock;

/**
 * Marks the blocks that carry bluestone signals instead of redstone ones.
 */
public interface BluestoneComponent extends SignalBlock {
    @Override
    default Channel channel(BlockState state) {
        return Channel.BLUESTONE;
    }

    @Override
    default boolean connectsToWire(BlockState state, Channel wire, @Nullable Direction direction) {
        return wire != Channel.REDSTONE && state.isSignalSource() && direction != null;
    }
}
