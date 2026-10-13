package yeah.im.liam.bluestone.block;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.rail.SurfaceRails;
import yeah.im.liam.bluestone.rail.WallRails;
import yeah.im.liam.bluestone.signal.Channel;
import yeah.im.liam.bluestone.signal.SignalBlock;

/**
 * A sticky rail on a wall or ceiling (the floor ones are ordinary rails). {@link #ATTACHED} is the face it sits on and
 * {@link #TRAVEL} the way along it carts run; chain gear rails drive carts that way. Like the game's rails it turns to
 * join the track around it, including floor rails it runs up from or down to, and {@link #BEND}s round an inside corner
 * to meet the track on the next face. Its {@link Kind} says what it does to carts, and powered, chain gear and
 * activator rails are powered by their colour like the game's powered rails. {@link SurfaceRails} moves the carts.
 */
public class AttachedRailBlock extends Block implements SimpleWaterloggedBlock, SignalBlock {
    public static final EnumProperty<Direction> ATTACHED = EnumProperty.create("attached", Direction.class, direction -> direction != Direction.DOWN);
    public static final EnumProperty<Direction> TRAVEL = EnumProperty.create("travel", Direction.class);
    public static final EnumProperty<Bend> BEND = EnumProperty.create("bend", Bend.class);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Map.of(
        Direction.UP, Block.box(0.0, 14.0, 0.0, 16.0, 16.0, 16.0),
        Direction.NORTH, Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 2.0),
        Direction.SOUTH, Block.box(0.0, 0.0, 14.0, 16.0, 16.0, 16.0),
        Direction.WEST, Block.box(0.0, 0.0, 0.0, 2.0, 16.0, 16.0),
        Direction.EAST, Block.box(14.0, 0.0, 0.0, 16.0, 16.0, 16.0)
    ));

    public enum Kind {
        // Carts roll freely.
        PLAIN,
        // Powered: carts move at a fixed speed the way it travels. Unpowered: carts are held still.
        CHAIN,
        // Powered: carts speed up. Unpowered: carts are braked to a stop.
        POWERED,
        // Gives out a signal while a cart is on it.
        DETECTOR,
        // Powered: carts on it are activated, as on the game's activator rail.
        ACTIVATOR
    }

    /**
     * Where the rail bends round an inside corner onto the next face, like a rail sloping up a block: nowhere, at the
     * end it travels towards, or at the end behind.
     */
    public enum Bend implements StringRepresentable {
        NONE("none"),
        AHEAD("ahead"),
        BEHIND("behind");

        private final String name;

        Bend(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    private final Kind kind;
    private final @Nullable Channel channel;

    public AttachedRailBlock(Properties properties, Kind kind, @Nullable Channel channel) {
        super(properties);
        this.kind = kind;
        this.channel = channel;
        registerDefaultState(stateDefinition.any()
            .setValue(ATTACHED, Direction.NORTH)
            .setValue(TRAVEL, Direction.UP)
            .setValue(BEND, Bend.NONE)
            .setValue(POWERED, false)
            .setValue(BlockStateProperties.WATERLOGGED, false));
    }

    public Kind kind() {
        return kind;
    }

    private boolean takesPower() {
        return channel != null && kind != Kind.DETECTOR && kind != Kind.PLAIN;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ATTACHED, TRAVEL, BEND, POWERED, BlockStateProperties.WATERLOGGED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(ATTACHED));
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction attached = state.getValue(ATTACHED);
        BlockPos support = pos.relative(attached);
        return level.getBlockState(support).isFaceSturdy(level, support, attached.getOpposite());
    }

    // On the face clicked, running whichever way joins the most track around it, or else the way the player looks
    // along that face. Next to a rail running the same way it takes that rail's direction, so chain gear keeps driving
    // the same way.
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction attached = context.getClickedFace().getOpposite();
        if (attached == Direction.DOWN) {
            return null;
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction.Axis axis = null;
        for (Direction direction : context.getNearestLookingDirections()) {
            if (direction.getAxis() != attached.getAxis()) {
                axis = bestAxis(level, pos, attached, direction.getAxis());
                break;
            }
        }
        Direction travel = following(level, pos, attached, axis);
        if (travel == null) {
            for (Direction direction : context.getNearestLookingDirections()) {
                if (direction.getAxis() == axis) {
                    travel = direction;
                    break;
                }
            }
        }
        BlockState state = defaultBlockState().setValue(ATTACHED, attached).setValue(TRAVEL, travel).setValue(BEND, bend(level, pos, attached, travel));
        return state.canSurvive(level, pos) ? Waterlogging.place(state, context) : null;
    }

    // The axis along the face that joins the most track: 2 for each end that meets track running the same way, 1 for
    // track that could turn to meet it. The preferred axis wins a tie.
    private Direction.Axis bestAxis(Level level, BlockPos pos, Direction attached, Direction.Axis preferred) {
        Direction.Axis best = preferred;
        int bestScore = -1;
        for (Direction.Axis axis : new Direction.Axis[]{preferred, Direction.Axis.X, Direction.Axis.Y, Direction.Axis.Z}) {
            if (axis == attached.getAxis()) {
                continue;
            }
            int score = 0;
            for (Direction.AxisDirection sign : Direction.AxisDirection.values()) {
                Direction exit = Direction.fromAxisAndDirection(axis, sign);
                SurfaceRails.Track track = new SurfaceRails.Track(pos, attached, axis, kind, false, exit);
                score += SurfaceRails.joins(level, track, exit, true) ? 2 : SurfaceRails.joins(level, track, exit, false) ? 1 : 0;
            }
            if (score > bestScore) {
                best = axis;
                bestScore = score;
            }
        }
        return best;
    }

    // The direction of a rail on the same face just before or after this one along the axis, if it runs that way too.
    private static @Nullable Direction following(Level level, BlockPos pos, Direction attached, Direction.Axis axis) {
        for (Direction.AxisDirection sign : Direction.AxisDirection.values()) {
            BlockState next = level.getBlockState(pos.relative(Direction.fromAxisAndDirection(axis, sign)));
            if (next.getBlock() instanceof AttachedRailBlock && next.getValue(ATTACHED) == attached && next.getValue(TRAVEL).getAxis() == axis) {
                return next.getValue(TRAVEL);
            }
        }
        return null;
    }

    // Where the rail meets track on the face of a block in its way, it bends to that face.
    private Bend bend(Level level, BlockPos pos, Direction attached, Direction travel) {
        for (Direction end : new Direction[]{travel, travel.getOpposite()}) {
            BlockPos ahead = pos.relative(end);
            if (level.getBlockState(ahead).isFaceSturdy(level, ahead, end.getOpposite())
                && SurfaceRails.joins(level, new SurfaceRails.Track(pos, attached, travel.getAxis(), kind, false, travel), end, true)) {
                return end == travel ? Bend.AHEAD : Bend.BEHIND;
            }
        }
        return Bend.NONE;
    }

    // Turns to join more track when the track around it changes, like the game's rails when one is laid next to them,
    // and bends to meet track round an inside corner.
    private void turn(Level level, BlockPos pos, BlockState state) {
        Direction attached = state.getValue(ATTACHED);
        Direction travel = state.getValue(TRAVEL);
        Direction.Axis axis = bestAxis(level, pos, attached, travel.getAxis());
        boolean turning = axis != travel.getAxis();
        if (turning) {
            travel = following(level, pos, attached, axis);
            if (travel == null) {
                travel = axis == Direction.Axis.Y ? Direction.UP : Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
            }
        }
        BlockState updated = state.setValue(TRAVEL, travel).setValue(BEND, bend(level, pos, attached, travel));
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_ALL);
            if (turning) {
                joined(level, pos, updated);
            }
        }
    }

    // Tells the track it may now join: wall and ceiling rails round an outside corner, which aren't next to it, and
    // floor rails at the foot and the top of the wall it runs up.
    private void joined(Level level, BlockPos pos, BlockState state) {
        Direction attached = state.getValue(ATTACHED);
        for (Direction way : Direction.values()) {
            BlockPos around = pos.relative(way).relative(attached);
            if (way.getAxis() != attached.getAxis() && level.getBlockState(around).getBlock() instanceof AttachedRailBlock) {
                level.neighborChanged(around, this, null);
            }
        }
        if (attached.getAxis().isHorizontal() && state.getValue(TRAVEL).getAxis() == Direction.Axis.Y) {
            WallRails.join(level, pos.below(), attached);
            WallRails.join(level, pos.above().relative(attached), attached.getOpposite());
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        Waterlogging.scheduleTick(state, level, ticks, pos);
        return directionToNeighbour == state.getValue(ATTACHED) && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Waterlogging.isWet(state) ? Waterlogging.WATER : super.getFluidState(state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(this) && !level.isClientSide()) {
            joined(level, pos, state);
            updatePowered(level, pos);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide()) {
            turn(level, pos, state);
            updatePowered(level, pos);
        }
    }

    private void updatePowered(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!takesPower() || !state.is(this)) {
            return;
        }
        boolean powered = RailPower.attached(level, pos, state, channel);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
            level.updateNeighborsAt(pos.relative(state.getValue(ATTACHED)), this);
        }
    }

    /**
     * A cart is on this rail: a detector rail turns on, and checks again in a second whether it should turn off.
     */
    public void detect(ServerLevel level, BlockPos pos, BlockState state) {
        if (kind != Kind.DETECTOR) {
            return;
        }
        if (!state.getValue(POWERED)) {
            setDetected(level, pos, state, true);
        }
        level.scheduleTick(pos, this, 20);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (kind != Kind.DETECTOR || !state.getValue(POWERED)) {
            return;
        }
        if (level.getEntitiesOfClass(AbstractMinecart.class, new AABB(pos).deflate(0.1)).isEmpty()) {
            setDetected(level, pos, state, false);
        } else {
            level.scheduleTick(pos, this, 20);
        }
    }

    private void setDetected(Level level, BlockPos pos, BlockState state, boolean powered) {
        level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
        level.updateNeighborsAt(pos, this);
        level.updateNeighborsAt(pos.relative(state.getValue(ATTACHED)), this);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return kind == Kind.DETECTOR;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return kind == Kind.DETECTOR && state.getValue(POWERED) ? 15 : 0;
    }

    // Like the game's detector rail, it strongly powers only the block it sits on.
    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return kind == Kind.DETECTOR && state.getValue(POWERED) && direction == state.getValue(ATTACHED).getOpposite() ? 15 : 0;
    }

    @Override
    public Channel channel(BlockState state) {
        return channel != null ? channel : Channel.REDSTONE;
    }

    // Dust of its colour, and purplestone dust, bend towards a detector rail.
    @Override
    public boolean connectsToWire(BlockState state, Channel wire, @Nullable Direction direction) {
        return kind == Kind.DETECTOR && direction != null && (wire == channel || wire == Channel.PURPLESTONE);
    }

    @Override
    protected boolean shouldRedstoneWireConnectTo(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return connectsToWire(state, Channel.REDSTONE, direction);
    }

    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return connectsToWire(state, Channel.REDSTONE, direction);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(ATTACHED, rotation.rotate(state.getValue(ATTACHED))).setValue(TRAVEL, rotation.rotate(state.getValue(TRAVEL)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(ATTACHED, mirror.mirror(state.getValue(ATTACHED))).setValue(TRAVEL, mirror.mirror(state.getValue(TRAVEL)));
    }
}
