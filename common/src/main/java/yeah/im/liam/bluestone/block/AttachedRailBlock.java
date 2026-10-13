package yeah.im.liam.bluestone.block;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
import yeah.im.liam.bluestone.signal.Channel;
import yeah.im.liam.bluestone.signal.SignalBlock;

/**
 * A sticky rail on a wall or ceiling (the floor ones are ordinary rails). {@link #ATTACHED} is the face it sits on and
 * {@link #TRAVEL} the way along it carts run; chain gear rails drive carts that way. Its {@link Kind} says what it does
 * to carts, and powered, chain gear and activator rails are powered by their colour like the game's powered rails.
 * {@link yeah.im.liam.bluestone.rail.SurfaceRails} moves the carts.
 */
public class AttachedRailBlock extends Block implements SimpleWaterloggedBlock, SignalBlock {
    public static final EnumProperty<Direction> ATTACHED = EnumProperty.create("attached", Direction.class, direction -> direction != Direction.DOWN);
    public static final EnumProperty<Direction> TRAVEL = EnumProperty.create("travel", Direction.class);
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

    private final Kind kind;
    private final @Nullable Channel channel;

    public AttachedRailBlock(Properties properties, Kind kind, @Nullable Channel channel) {
        super(properties);
        this.kind = kind;
        this.channel = channel;
        registerDefaultState(stateDefinition.any()
            .setValue(ATTACHED, Direction.NORTH)
            .setValue(TRAVEL, Direction.UP)
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
        builder.add(ATTACHED, TRAVEL, POWERED, BlockStateProperties.WATERLOGGED);
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

    // On the face clicked, running the way the player looks along that face.
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction attached = context.getClickedFace().getOpposite();
        if (attached == Direction.DOWN) {
            return null;
        }
        for (Direction direction : context.getNearestLookingDirections()) {
            if (direction.getAxis() != attached.getAxis()) {
                BlockState state = defaultBlockState().setValue(ATTACHED, attached).setValue(TRAVEL, direction);
                return state.canSurvive(context.getLevel(), context.getClickedPos()) ? Waterlogging.place(state, context) : null;
            }
        }
        return null;
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
            updatePowered(level, pos);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide()) {
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
