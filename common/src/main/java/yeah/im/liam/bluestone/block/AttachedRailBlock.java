package yeah.im.liam.bluestone.block;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
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
 * to meet the track on the next face. Plain sticky rails also {@link #CURVE} like the game's rails. Its {@link Kind} says what it does to carts, and powered, chain gear and
 * activator rails are powered by their colour like the game's powered rails. {@link SurfaceRails} moves the carts.
 */
public class AttachedRailBlock extends Block implements SimpleWaterloggedBlock, SignalBlock {
    public static final EnumProperty<Direction> ATTACHED = EnumProperty.create("attached", Direction.class, direction -> direction != Direction.DOWN);
    public static final EnumProperty<Direction> TRAVEL = EnumProperty.create("travel", Direction.class);
    public static final EnumProperty<Bend> BEND = EnumProperty.create("bend", Bend.class);
    public static final EnumProperty<Curve> CURVE = EnumProperty.create("curve", Curve.class);
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

    /**
     * Whether the rail curves: not at all, or round to the left or right of the way it travels, as seen from in front of
     * the face with that way up.
     */
    public enum Curve implements StringRepresentable {
        NONE("none"),
        LEFT("left"),
        RIGHT("right");

        private final String name;

        Curve(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    // A shape the rail can take: the way it travels, and how it curves.
    private record Shape(Direction travel, Curve curve) {
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
            .setValue(CURVE, Curve.NONE)
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
        builder.add(ATTACHED, TRAVEL, BEND, CURVE, POWERED, BlockStateProperties.WATERLOGGED);
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

    /**
     * The other end of a curve: to the left or right of the way it travels, seen from in front of the face.
     */
    public static Direction otherEnd(Direction attached, Direction travel, Curve curve) {
        if (curve == Curve.NONE) {
            return travel.getOpposite();
        }
        Vec3i right = travel.getUnitVec3i().cross(attached.getOpposite().getUnitVec3i());
        Direction toRight = Direction.getApproximateNearest(right.getX(), right.getY(), right.getZ());
        return curve == Curve.RIGHT ? toRight : toRight.getOpposite();
    }

    /**
     * The other end of a curved rail, or null if it runs straight.
     */
    public static @Nullable Direction curveEnd(BlockState state) {
        Curve curve = state.getValue(CURVE);
        return curve == Curve.NONE ? null : otherEnd(state.getValue(ATTACHED), state.getValue(TRAVEL), curve);
    }

    /**
     * Whether a wall or ceiling rail has an end pointing the given way.
     */
    public static boolean hasEnd(BlockState state, Direction way) {
        Direction travel = state.getValue(TRAVEL);
        Direction other = curveEnd(state);
        return other == null ? travel.getAxis() == way.getAxis() : travel == way || other == way;
    }

    private SurfaceRails.Track track(BlockPos pos, Direction attached, Shape shape) {
        Direction other = shape.curve() == Curve.NONE ? null : otherEnd(attached, shape.travel(), shape.curve());
        return new SurfaceRails.Track(pos, attached, shape.travel().getAxis(), kind, false, shape.travel(), other);
    }

    // On the face clicked, in whichever shape joins the most track around it, or else running the way the player looks
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
        Direction looking = null;
        for (Direction direction : context.getNearestLookingDirections()) {
            if (direction.getAxis() != attached.getAxis()) {
                looking = direction;
                break;
            }
        }
        Shape shape = bestShape(level, pos, attached, new Shape(looking, Curve.NONE));
        Direction travel = shape.travel();
        if (shape.curve() == Curve.NONE) {
            travel = following(level, pos, attached, travel.getAxis());
            if (travel == null) {
                for (Direction direction : context.getNearestLookingDirections()) {
                    if (direction.getAxis() == shape.travel().getAxis()) {
                        travel = direction;
                        break;
                    }
                }
            }
        }
        BlockState state = defaultBlockState().setValue(ATTACHED, attached).setValue(TRAVEL, travel).setValue(CURVE, shape.curve())
            .setValue(BEND, shape.curve() == Curve.NONE ? bend(level, pos, attached, travel) : Bend.NONE);
        return state.canSurvive(level, pos) ? Waterlogging.place(state, context) : null;
    }

    // The shape that joins the most track: 2 for each end that meets track that runs on from it, 1 for track that could
    // turn to meet it. The current shape wins a tie, then a straight one. Only plain sticky rails curve, like the
    // game's rails.
    private Shape bestShape(Level level, BlockPos pos, Direction attached, Shape current) {
        List<Shape> shapes = new ArrayList<>();
        shapes.add(current);
        List<Direction> along = new ArrayList<>(4);
        for (Direction direction : Direction.values()) {
            if (direction.getAxis() != attached.getAxis()) {
                along.add(direction);
                if (direction.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
                    shapes.add(new Shape(direction, Curve.NONE));
                }
            }
        }
        if (kind == Kind.PLAIN) {
            for (Direction first : along) {
                for (Direction second : along) {
                    if (first.getAxis() != second.getAxis() && first.ordinal() < second.ordinal()) {
                        shapes.add(new Shape(first, otherEnd(attached, first, Curve.RIGHT) == second ? Curve.RIGHT : Curve.LEFT));
                    }
                }
            }
        }
        Shape best = current;
        int bestScore = -1;
        for (Shape shape : shapes) {
            if (shape.travel().getAxis() == attached.getAxis()) {
                continue;
            }
            SurfaceRails.Track track = track(pos, attached, shape);
            int score = 0;
            for (Direction end : track.ends()) {
                score += SurfaceRails.joins(level, track, end, true) ? 2 : SurfaceRails.joins(level, track, end, false) ? 1 : 0;
            }
            if (score > bestScore) {
                best = shape;
                bestScore = score;
            }
        }
        return best;
    }

    // The direction of a rail on the same face just before or after this one along the axis, if it runs that way too.
    private static @Nullable Direction following(Level level, BlockPos pos, Direction attached, Direction.Axis axis) {
        for (Direction.AxisDirection sign : Direction.AxisDirection.values()) {
            BlockState next = level.getBlockState(pos.relative(Direction.fromAxisAndDirection(axis, sign)));
            if (next.getBlock() instanceof AttachedRailBlock && next.getValue(ATTACHED) == attached && next.getValue(CURVE) == Curve.NONE
                && next.getValue(TRAVEL).getAxis() == axis) {
                return next.getValue(TRAVEL);
            }
        }
        return null;
    }

    // Where a straight rail meets track on the face of a block in its way, it bends to that face.
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

    // Turns or curves to join more track when the track around it changes, like the game's rails when one is laid next
    // to them, and bends to meet track round an inside corner.
    private void turn(Level level, BlockPos pos, BlockState state) {
        Direction attached = state.getValue(ATTACHED);
        Shape current = new Shape(state.getValue(TRAVEL), state.getValue(CURVE));
        Shape shape = bestShape(level, pos, attached, current);
        boolean turning = !shape.equals(current);
        Direction travel = shape.travel();
        if (turning && shape.curve() == Curve.NONE) {
            if (current.curve() == Curve.NONE && current.travel().getAxis() == travel.getAxis()) {
                travel = current.travel();
            } else {
                Direction following = following(level, pos, attached, travel.getAxis());
                travel = following != null ? following : travel.getAxis() == Direction.Axis.Y ? Direction.UP : travel;
            }
        }
        BlockState updated = state.setValue(TRAVEL, travel).setValue(CURVE, shape.curve())
            .setValue(BEND, shape.curve() == Curve.NONE ? bend(level, pos, attached, travel) : Bend.NONE);
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
        if (attached.getAxis().isHorizontal()) {
            if (hasEnd(state, Direction.DOWN)) {
                WallRails.join(level, pos.below(), attached);
            }
            if (hasEnd(state, Direction.UP)) {
                WallRails.join(level, pos.above().relative(attached), attached.getOpposite());
            }
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
