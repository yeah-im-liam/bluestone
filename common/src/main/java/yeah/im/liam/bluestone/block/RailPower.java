package yeah.im.liam.bluestone.block;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.block.AttachedRailBlock.Kind;
import yeah.im.liam.bluestone.rail.SurfaceRails;
import yeah.im.liam.bluestone.signal.Channel;
import yeah.im.liam.bluestone.signal.Signals;

/**
 * Power for chain rails and rails on walls and ceilings, the way the game powers its powered rails: a rail is powered
 * by a signal of its colour next to it, or through up to eight rails of the same kind and colour along the track from
 * one that is.
 */
public final class RailPower {
    private static final int REACH = 8;

    private RailPower() {
    }

    public static boolean direct(Level level, BlockPos pos, Channel channel) {
        return Signals.test(channel, () -> level.hasNeighborSignal(pos));
    }

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

    public static boolean along(Level level, BlockPos pos, BlockState state) {
        Kind kind = kind(state);
        if (kind == null) {
            return false;
        }
        Channel channel = channel(state);
        Set<BlockPos> seen = new HashSet<>(List.of(pos));
        List<BlockPos> edge = List.of(pos);
        for (int i = 0; i < REACH && !edge.isEmpty(); i++) {
            List<BlockPos> further = new ArrayList<>();
            for (BlockPos at : edge) {
                for (BlockPos next : SurfaceRails.neighbours(level, at)) {
                    BlockState nextState = level.getBlockState(next);
                    if (seen.add(next) && kind(nextState) == kind && channel(nextState) == channel) {
                        if (direct(level, next, channel)) {
                            return true;
                        }
                        further.add(next);
                    }
                }
            }
            edge = further;
        }
        return false;
    }

    public static boolean samePower(BlockState a, BlockState b) {
        Kind kind = kind(a);
        return kind != null && kind == kind(b) && channel(a) == channel(b);
    }

    private static @Nullable Kind kind(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof AttachedRailBlock rail) {
            return rail.kind() == Kind.POWERED || rail.kind() == Kind.ACTIVATOR || rail.kind() == Kind.CHAIN ? rail.kind() : null;
        }
        if (block instanceof ChainRailBlock) {
            return Kind.CHAIN;
        }
        if (block instanceof PoweredRailBlock) {
            boolean activator = block == Blocks.ACTIVATOR_RAIL || block instanceof StickyPoweredRailBlock sticky && sticky.isActivatorRail()
                || block instanceof BluestonePoweredRailBlock bluestone && bluestone.isActivatorRail();
            return activator ? Kind.ACTIVATOR : Kind.POWERED;
        }
        return null;
    }

    static boolean takesPower(BlockState state) {
        return kind(state) != null;
    }

    static Channel channel(BlockState state) {
        return state.getBlock() instanceof ChainRailBlock chain ? chain.channel() : Channel.of(state.getBlock());
    }
}
