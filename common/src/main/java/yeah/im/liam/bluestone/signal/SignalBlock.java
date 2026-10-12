package yeah.im.liam.bluestone.signal;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A block of this mod that gives out a coloured signal.
 *
 * <p>Redstone dust asks these blocks whether to bend towards them through {@code shouldRedstoneWireConnectTo}
 * (Minecraft 26.3), {@code canConnectRedstone} (NeoForge on 26.1 and 26.2) or a mixin (Fabric on 26.1 and 26.2);
 * each block answers with {@link #connectsToWire} for {@link Channel#REDSTONE}.
 */
public interface SignalBlock {
    Channel channel(BlockState state);

    /**
     * Whether dust of the given channel, lying next to this block in the given direction from it, bends towards it.
     */
    boolean connectsToWire(BlockState state, Channel wire, @Nullable Direction direction);
}
