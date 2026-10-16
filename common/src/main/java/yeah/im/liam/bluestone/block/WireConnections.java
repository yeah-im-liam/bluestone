package yeah.im.liam.bluestone.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Whether dust bends towards a block of the game, such as a lever, an observer or (for purplestone dust) redstone
 * parts.
 */
final class WireConnections {
    // Minecraft 26.3 lets each block decide; 26.1 and 26.2 hard-code redstone dust, repeaters and observers.
    private static final boolean BLOCKS_DECIDE = hasBlockHook();

    private WireConnections() {
    }

    static boolean connects(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        if (BLOCKS_DECIDE) {
            return state.shouldRedstoneWireConnectTo(level, pos, direction);
        }
        if (state.is(Blocks.REDSTONE_WIRE)) {
            return true;
        }
        if (state.is(Blocks.REPEATER)) {
            Direction facing = state.getValue(RepeaterBlock.FACING);
            return facing == direction || facing.getOpposite() == direction;
        }
        if (state.is(Blocks.OBSERVER)) {
            return direction == state.getValue(ObserverBlock.FACING);
        }
        return state.isSignalSource() && direction != null;
    }

    private static boolean hasBlockHook() {
        try {
            BlockBehaviour.BlockStateBase.class.getMethod("shouldRedstoneWireConnectTo", BlockGetter.class, BlockPos.class, Direction.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
