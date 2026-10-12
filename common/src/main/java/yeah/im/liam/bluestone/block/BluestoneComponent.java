package yeah.im.liam.bluestone.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Marks the blocks that carry bluestone signals instead of redstone ones.
 *
 * <p>Each of them also tells redstone dust not to bend towards it: Minecraft 26.3 asks the block through
 * {@code shouldRedstoneWireConnectTo}, NeoForge on 26.1 and 26.2 through {@code canConnectRedstone}, and on
 * Fabric 26.1 and 26.2 a mixin answers for them.
 */
public interface BluestoneComponent {
    default boolean connectsToBluestoneWire(BlockState state, @Nullable Direction direction) {
        return state.isSignalSource() && direction != null;
    }
}
