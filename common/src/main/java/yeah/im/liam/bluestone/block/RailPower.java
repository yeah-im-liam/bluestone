package yeah.im.liam.bluestone.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.signal.Channel;
import yeah.im.liam.bluestone.signal.Signals;

/**
 * Power for chain rails and rails on walls and ceilings, the way the game powers its powered rails: a rail is
 * powered by a signal of its colour next to it, or through up to eight rails of the same kind in a line from one that
 * is. Only signals count, not the rails' own powered state, so a line can't keep itself on.
 */
final class RailPower {
    private static final int REACH = 8;

    private RailPower() {
    }

    static boolean direct(Level level, BlockPos pos, Channel channel) {
        return Signals.test(channel, () -> level.hasNeighborSignal(pos));
    }

    // A floor rail: along its track, up and down slopes.
    static boolean floor(Level level, BlockPos pos, BlockState state, Channel channel) {
        if (direct(level, pos, channel)) {
            return true;
        }
        Direction.Axis axis = axis(state.getValue(ChainRailBlock.SHAPE));
        for (Direction.AxisDirection sign : Direction.AxisDirection.values()) {
            Direction way = Direction.fromAxisAndDirection(axis, sign);
            BlockPos at = pos;
            for (int i = 0; i < REACH; i++) {
                BlockPos next = sameFloorRail(level, at.relative(way), state, axis);
                if (next == null) {
                    break;
                }
                if (direct(level, next, channel)) {
                    return true;
                }
                at = next;
            }
        }
        return false;
    }

    private static @Nullable BlockPos sameFloorRail(Level level, BlockPos pos, BlockState rail, Direction.Axis axis) {
        for (BlockPos candidate : new BlockPos[] {pos, pos.above(), pos.below()}) {
            BlockState state = level.getBlockState(candidate);
            if (state.is(rail.getBlock()) && axis(state.getValue(ChainRailBlock.SHAPE)) == axis) {
                return candidate;
            }
        }
        return null;
    }

    static Direction.Axis axis(RailShape shape) {
        return switch (shape) {
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> Direction.Axis.X;
            default -> Direction.Axis.Z;
        };
    }

    // A wall or ceiling rail: along the way it runs, on the same face.
    static boolean attached(Level level, BlockPos pos, BlockState state, Channel channel) {
        if (direct(level, pos, channel)) {
            return true;
        }
        Direction attached = state.getValue(AttachedRailBlock.ATTACHED);
        Direction.Axis axis = state.getValue(AttachedRailBlock.TRAVEL).getAxis();
        for (Direction.AxisDirection sign : Direction.AxisDirection.values()) {
            Direction way = Direction.fromAxisAndDirection(axis, sign);
            BlockPos at = pos;
            for (int i = 0; i < REACH; i++) {
                at = at.relative(way);
                BlockState next = level.getBlockState(at);
                if (!next.is(state.getBlock()) || next.getValue(AttachedRailBlock.ATTACHED) != attached
                    || next.getValue(AttachedRailBlock.TRAVEL).getAxis() != axis) {
                    break;
                }
                if (direct(level, at, channel)) {
                    return true;
                }
            }
        }
        return false;
    }
}
