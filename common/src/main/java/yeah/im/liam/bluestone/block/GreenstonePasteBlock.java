package yeah.im.liam.bluestone.block;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.signal.Channel;
import yeah.im.liam.bluestone.signal.SignalBlock;
import yeah.im.liam.bluestone.signal.Signals;

/**
 * Greenstone paste: redstone dust for greenstone that sticks to any face, floor, wall or ceiling. Each piece lies on
 * the face {@link #ATTACHED} names and connects like dust on that face: {@link #NORTH}, {@link #EAST}, {@link #SOUTH}
 * and {@link #WEST} are directions on the face as if it were the floor, turned with it (see {@link #toWorld}). A
 * piece climbs onto a block in its way with an "up" connection, like dust going up a block, to paste on that block's
 * face or on top of it, and runs over edges to paste one step down or round the edge. It loses one level per piece,
 * hears only greenstone and plain switches, and meets purplestone through the wall converter.
 */
public class GreenstonePasteBlock extends Block implements SignalBlock {
    public static final EnumProperty<Direction> ATTACHED = EnumProperty.create("attached", Direction.class);
    public static final EnumProperty<RedstoneSide> NORTH = BlockStateProperties.NORTH_REDSTONE;
    public static final EnumProperty<RedstoneSide> EAST = BlockStateProperties.EAST_REDSTONE;
    public static final EnumProperty<RedstoneSide> SOUTH = BlockStateProperties.SOUTH_REDSTONE;
    public static final EnumProperty<RedstoneSide> WEST = BlockStateProperties.WEST_REDSTONE;
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    private static final Map<Direction, EnumProperty<RedstoneSide>> SIDES = Map.of(
        Direction.NORTH, NORTH, Direction.EAST, EAST, Direction.SOUTH, SOUTH, Direction.WEST, WEST);
    // Redstone's colours with the red and green channels swapped.
    private static final int[] COLORS = ColoredWireBlock.colorsFor((red, green, blue) -> ARGB.colorFromFloat(1.0F, green, red, blue));
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Map.of(
        Direction.DOWN, Block.box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0),
        Direction.UP, Block.box(0.0, 15.0, 0.0, 16.0, 16.0, 16.0),
        Direction.NORTH, Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 1.0),
        Direction.SOUTH, Block.box(0.0, 0.0, 15.0, 16.0, 16.0, 16.0),
        Direction.WEST, Block.box(0.0, 0.0, 0.0, 1.0, 16.0, 16.0),
        Direction.EAST, Block.box(15.0, 0.0, 0.0, 16.0, 16.0, 16.0)
    ));
    private boolean shouldSignal = true;

    public GreenstonePasteBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(ATTACHED, Direction.DOWN)
            .setValue(NORTH, RedstoneSide.NONE)
            .setValue(EAST, RedstoneSide.NONE)
            .setValue(SOUTH, RedstoneSide.NONE)
            .setValue(WEST, RedstoneSide.NONE)
            .setValue(POWER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ATTACHED, NORTH, EAST, SOUTH, WEST, POWER);
    }

    /**
     * Where a direction on the face points in the world. On the floor they are the same; on the ceiling north stays
     * north and east becomes west, as seen from below; on a wall north is up and east is to the right, as seen from
     * in front.
     */
    public static Direction toWorld(Direction attached, Direction side) {
        Direction north = attached.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP;
        Vec3i cross = attached.getUnitVec3i().cross(north.getUnitVec3i());
        Direction east = Direction.getApproximateNearest(cross.getX(), cross.getY(), cross.getZ());
        return switch (side) {
            case NORTH -> north;
            case SOUTH -> north.getOpposite();
            case EAST -> east;
            default -> east.getOpposite();
        };
    }

    public int colorForPower(int power) {
        return COLORS[power];
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

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(ATTACHED, context.getClickedFace().getOpposite());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? withConnections(context.getLevel(), context.getClickedPos(), state) : null;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (directionToNeighbour == state.getValue(ATTACHED) && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return withConnections(level, pos, state);
    }

    // Paste one step over diagonally connects too, so it is told when this piece changes.
    @Override
    protected void updateIndirectNeighbourShapes(BlockState state, LevelAccessor level, BlockPos pos, int flags, int updateLimit) {
        for (BlockPos other : diagonals(pos)) {
            BlockState otherState = level.getBlockState(other);
            if (otherState.is(this)) {
                Block.updateOrDestroy(otherState, withConnections(level, other, otherState), level, other, flags & ~Block.UPDATE_NEIGHBORS, updateLimit);
            }
        }
    }

    private static List<BlockPos> diagonals(BlockPos pos) {
        List<BlockPos> positions = new ArrayList<>(12);
        for (Direction first : Direction.values()) {
            for (Direction second : Direction.values()) {
                if (first.ordinal() < second.ordinal() && first.getAxis() != second.getAxis()) {
                    positions.add(pos.relative(first).relative(second));
                }
            }
        }
        return positions;
    }

    private BlockState withConnections(BlockGetter level, BlockPos pos, BlockState state) {
        Direction attached = state.getValue(ATTACHED);
        Map<Direction, RedstoneSide> sides = new EnumMap<>(Direction.class);
        int connected = 0;
        for (Direction side : SIDES.keySet()) {
            RedstoneSide connection = connection(level, pos, attached, toWorld(attached, side));
            sides.put(side, connection);
            if (connection.isConnected()) {
                connected++;
            }
        }
        // Like dust: a lone piece is a cross, and a piece connected one way runs straight on through.
        if (connected == 0) {
            sides.replaceAll((side, connection) -> RedstoneSide.SIDE);
        } else if (connected == 1) {
            for (Direction side : SIDES.keySet()) {
                if (sides.get(side).isConnected() && !sides.get(side.getOpposite()).isConnected()) {
                    sides.put(side.getOpposite(), RedstoneSide.SIDE);
                    break;
                }
            }
        }
        for (Map.Entry<Direction, RedstoneSide> entry : sides.entrySet()) {
            state = state.setValue(SIDES.get(entry.getKey()), entry.getValue());
        }
        return state;
    }

    private RedstoneSide connection(BlockGetter level, BlockPos pos, Direction attached, Direction way) {
        Direction normal = attached.getOpposite();
        BlockPos ahead = pos.relative(way);
        boolean solidAhead = level.getBlockState(ahead).isRedstoneConductor(level, ahead);
        BlockPos above = pos.relative(normal);
        if (solidAhead && !level.getBlockState(above).isRedstoneConductor(level, above)
            && (isPaste(level, above, way) || isPaste(level, ahead.relative(normal), attached))) {
            return RedstoneSide.UP;
        }
        if (isPaste(level, ahead, attached) || isPaste(level, ahead, way)
            || !solidAhead && (isPaste(level, ahead.relative(attached), attached) || isPaste(level, ahead.relative(attached), way.getOpposite()))) {
            return RedstoneSide.SIDE;
        }
        return connectsTo(level.getBlockState(ahead), way) ? RedstoneSide.SIDE : RedstoneSide.NONE;
    }

    private boolean isPaste(BlockGetter level, BlockPos pos, Direction attached) {
        BlockState state = level.getBlockState(pos);
        return state.is(this) && state.getValue(ATTACHED) == attached;
    }

    // Greenstone parts and plain switches bend paste towards them, the way dust bends towards a lever or a torch.
    private static boolean connectsTo(BlockState state, Direction way) {
        if (state.getBlock() instanceof WallConverterBlock) {
            return true;
        }
        if (state.getBlock() instanceof GreenstoneDiodeBlock) {
            return state.getValue(GreenstoneDiodeBlock.OUTPUT).getAxis() == way.getAxis();
        }
        if (!state.isSignalSource()) {
            return false;
        }
        Channel channel = Channel.ofSource(state);
        return channel == null || channel == Channel.GREENSTONE;
    }

    /**
     * The paste this piece passes power to and takes it from: the same places its connections point at.
     */
    private List<BlockPos> connectedPaste(Level level, BlockPos pos, BlockState state) {
        Direction attached = state.getValue(ATTACHED);
        Direction normal = attached.getOpposite();
        List<BlockPos> paste = new ArrayList<>();
        for (Direction side : SIDES.keySet()) {
            Direction way = toWorld(attached, side);
            BlockPos ahead = pos.relative(way);
            boolean solidAhead = level.getBlockState(ahead).isRedstoneConductor(level, ahead);
            BlockPos above = pos.relative(normal);
            if (solidAhead && !level.getBlockState(above).isRedstoneConductor(level, above)) {
                addIfPaste(level, paste, above, way);
                addIfPaste(level, paste, ahead.relative(normal), attached);
            }
            addIfPaste(level, paste, ahead, attached);
            addIfPaste(level, paste, ahead, way);
            if (!solidAhead) {
                addIfPaste(level, paste, ahead.relative(attached), attached);
                addIfPaste(level, paste, ahead.relative(attached), way.getOpposite());
            }
        }
        return paste;
    }

    private void addIfPaste(Level level, List<BlockPos> paste, BlockPos pos, Direction attached) {
        if (isPaste(level, pos, attached)) {
            paste.add(pos);
        }
    }

    // Like dust, it powers the block it lies on and the blocks its lines point into, but nothing in front of it.
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (!shouldSignal) {
            return 0;
        }
        Direction toward = direction.getOpposite();
        Direction attached = state.getValue(ATTACHED);
        if (toward == attached.getOpposite()) {
            return 0;
        }
        if (toward == attached) {
            return state.getValue(POWER);
        }
        for (Map.Entry<Direction, EnumProperty<RedstoneSide>> side : SIDES.entrySet()) {
            if (toWorld(attached, side.getKey()) == toward) {
                return state.getValue(side.getValue()).isConnected() ? state.getValue(POWER) : 0;
            }
        }
        return 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return shouldSignal;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(this) && !level.isClientSide()) {
            updatePower(level, pos, state);
            updateAround(level, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (!movedByPiston) {
            updateAround(level, pos);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide()) {
            updatePower(level, pos, state);
        }
    }

    private void updatePower(Level level, BlockPos pos, BlockState state) {
        int power = Math.max(blockSignal(level, pos), wireSignal(level, pos, state));
        if (power != state.getValue(POWER)) {
            if (level.getBlockState(pos) == state) {
                level.setBlock(pos, state.setValue(POWER, power), Block.UPDATE_CLIENTS);
            }
            updateAround(level, pos);
        }
    }

    // Everything within two spaces, so paste one step over diagonally hears about the change too.
    private void updateAround(Level level, BlockPos pos) {
        level.updateNeighborsAt(pos, this);
        for (Direction direction : Direction.values()) {
            level.updateNeighborsAt(pos.relative(direction), this);
        }
    }

    private int blockSignal(Level level, BlockPos pos) {
        return Signals.readForDust(Channel.GREENSTONE, () -> {
            shouldSignal = false;
            try {
                return level.getBestNeighborSignal(pos);
            } finally {
                shouldSignal = true;
            }
        });
    }

    private int wireSignal(Level level, BlockPos pos, BlockState state) {
        int signal = 0;
        for (BlockPos other : connectedPaste(level, pos, state)) {
            signal = Math.max(signal, level.getBlockState(other).getValue(POWER) - 1);
        }
        for (Direction direction : Direction.values()) {
            BlockState neighbour = level.getBlockState(pos.relative(direction));
            if (neighbour.getBlock() instanceof WallConverterBlock) {
                signal = Math.max(signal, neighbour.getValue(WallConverterBlock.POWER) - 1);
            }
        }
        return Math.max(0, signal);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int power = state.getValue(POWER);
        if (power == 0 || random.nextFloat() > 0.2F) {
            return;
        }
        Direction attached = state.getValue(ATTACHED);
        double x = pos.getX() + 0.5 + 0.45 * attached.getStepX() + (attached.getStepX() == 0 ? (random.nextDouble() - 0.5) * 0.4 : 0.0);
        double y = pos.getY() + 0.5 + 0.45 * attached.getStepY() + (attached.getStepY() == 0 ? (random.nextDouble() - 0.5) * 0.4 : 0.0);
        double z = pos.getZ() + 0.5 + 0.45 * attached.getStepZ() + (attached.getStepZ() == 0 ? (random.nextDouble() - 0.5) * 0.4 : 0.0);
        level.addParticle(new DustParticleOptions(COLORS[power], 1.0F), x, y, z, 0.0, 0.0, 0.0);
    }

    @Override
    public Channel channel(BlockState state) {
        return Channel.GREENSTONE;
    }

    // No dust bends towards paste.
    @Override
    public boolean connectsToWire(BlockState state, Channel wire, @Nullable Direction direction) {
        return false;
    }

    @Override
    protected boolean shouldRedstoneWireConnectTo(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return false;
    }

    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return false;
    }
}
