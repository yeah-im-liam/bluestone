package yeah.im.liam.bluestone.rail;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RailState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.block.AttachedRailBlock;
import yeah.im.liam.bluestone.mixin.RailStateAccessor;

/**
 * Floor rails joining sticky rails on walls, the way rails join a rail one block up or down. A floor rail joins a
 * wall rail that runs up the block in its way (that rail is just above it) or down the side of the block it sits on.
 * RailStateMixin lets the game's rail logic count those as rails to connect to.
 */
public final class WallRails {
    private WallRails() {
    }

    /**
     * Whether a floor rail at {@code pos} has a wall rail to join going {@code way}.
     */
    public static boolean joins(BlockGetter level, BlockPos pos, Direction way) {
        BlockPos ahead = pos.relative(way);
        if (level.getBlockState(ahead).isFaceSturdy(level, ahead, way.getOpposite())) {
            return upright(level.getBlockState(pos.above()), way);
        }
        return upright(level.getBlockState(ahead.below()), way.getOpposite());
    }

    private static boolean upright(BlockState state, Direction attached) {
        return state.getBlock() instanceof AttachedRailBlock
            && state.getValue(AttachedRailBlock.ATTACHED) == attached
            && state.getValue(AttachedRailBlock.TRAVEL).getAxis() == Direction.Axis.Y;
    }

    /**
     * The way from one floor rail to the next one along, or null if they aren't side by side.
     */
    public static @Nullable Direction way(BlockPos from, BlockPos to) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (from.relative(direction).equals(to)) {
                return direction;
            }
        }
        return null;
    }

    /**
     * The ways a floor rail of this shape leaves level, not up a slope.
     */
    public static List<Direction> level(RailShape shape) {
        return switch (shape) {
            case NORTH_SOUTH -> List.of(Direction.NORTH, Direction.SOUTH);
            case EAST_WEST -> List.of(Direction.EAST, Direction.WEST);
            case ASCENDING_EAST -> List.of(Direction.WEST);
            case ASCENDING_WEST -> List.of(Direction.EAST);
            case ASCENDING_NORTH -> List.of(Direction.SOUTH);
            case ASCENDING_SOUTH -> List.of(Direction.NORTH);
            case SOUTH_EAST -> List.of(Direction.SOUTH, Direction.EAST);
            case SOUTH_WEST -> List.of(Direction.SOUTH, Direction.WEST);
            case NORTH_WEST -> List.of(Direction.NORTH, Direction.WEST);
            case NORTH_EAST -> List.of(Direction.NORTH, Direction.EAST);
        };
    }

    /**
     * A wall rail has appeared: the floor rail at {@code pos} turns to join it going {@code way}, if it isn't already
     * joined at both ends, the same as when a rail is laid next to it.
     */
    public static void join(Level level, BlockPos pos, Direction way) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BaseRailBlock rail) || !BaseRailBlock.isRail(state) || !joins(level, pos, way)) {
            return;
        }
        RailState railState = new RailState(level, pos, state);
        ((RailStateAccessor) railState).bluestone$removeSoftConnections();
        List<BlockPos> connections = railState.getConnections();
        Set<Direction> ways = EnumSet.noneOf(Direction.class);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos to = pos.relative(direction);
            if (connections.stream().anyMatch(connection -> connection.getX() == to.getX() && connection.getZ() == to.getZ())) {
                ways.add(direction);
            }
        }
        if (ways.contains(way) || connections.size() == 2) {
            return;
        }
        ways.add(way);
        level.setBlock(pos, state.setValue(rail.getShapeProperty(), shape(level, pos, ways, rail.isStraight())), Block.UPDATE_ALL);
    }

    // The game's choice of shape for a rail joined the given ways.
    private static RailShape shape(Level level, BlockPos pos, Set<Direction> ways, boolean straight) {
        boolean n = ways.contains(Direction.NORTH);
        boolean s = ways.contains(Direction.SOUTH);
        boolean w = ways.contains(Direction.WEST);
        boolean e = ways.contains(Direction.EAST);
        RailShape shape = null;
        if (n || s) {
            shape = RailShape.NORTH_SOUTH;
        }
        if (w || e) {
            shape = RailShape.EAST_WEST;
        }
        if (!straight) {
            if (s && e && !n && !w) {
                shape = RailShape.SOUTH_EAST;
            }
            if (s && w && !n && !e) {
                shape = RailShape.SOUTH_WEST;
            }
            if (n && w && !s && !e) {
                shape = RailShape.NORTH_WEST;
            }
            if (n && e && !s && !w) {
                shape = RailShape.NORTH_EAST;
            }
        }
        if (shape == RailShape.NORTH_SOUTH) {
            if (BaseRailBlock.isRail(level, pos.north().above())) {
                shape = RailShape.ASCENDING_NORTH;
            }
            if (BaseRailBlock.isRail(level, pos.south().above())) {
                shape = RailShape.ASCENDING_SOUTH;
            }
        }
        if (shape == RailShape.EAST_WEST) {
            if (BaseRailBlock.isRail(level, pos.east().above())) {
                shape = RailShape.ASCENDING_EAST;
            }
            if (BaseRailBlock.isRail(level, pos.west().above())) {
                shape = RailShape.ASCENDING_WEST;
            }
        }
        return shape == null ? RailShape.NORTH_SOUTH : shape;
    }
}
