package yeah.im.liam.bluestone.signal;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A block of this mod that gives out a coloured signal.
 */
public interface SignalBlock {
    Channel channel(BlockState state);

    boolean connectsToWire(BlockState state, Channel wire, @Nullable Direction direction);
}
