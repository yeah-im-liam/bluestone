package yeah.im.liam.bluestone.block;

import com.google.common.collect.Maps;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.signal.Channel;
import yeah.im.liam.bluestone.signal.Listener;
import yeah.im.liam.bluestone.signal.SignalBlock;
import yeah.im.liam.bluestone.signal.Signals;

/**
 * The game's redstone dust for another signal channel. Dust that is also a {@link SimpleWaterloggedBlock} can sit in
 * water; the rest washes away like redstone dust.
 */
public abstract class ColoredWireBlock extends Block implements SignalBlock {
    public static final EnumProperty<RedstoneSide> NORTH = BlockStateProperties.NORTH_REDSTONE;
    public static final EnumProperty<RedstoneSide> EAST = BlockStateProperties.EAST_REDSTONE;
    public static final EnumProperty<RedstoneSide> SOUTH = BlockStateProperties.SOUTH_REDSTONE;
    public static final EnumProperty<RedstoneSide> WEST = BlockStateProperties.WEST_REDSTONE;
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final Map<Direction, EnumProperty<RedstoneSide>> PROPERTY_BY_DIRECTION = Maps.immutableEnumMap(Map.of(
        Direction.NORTH, NORTH,
        Direction.EAST, EAST,
        Direction.SOUTH, SOUTH,
        Direction.WEST, WEST
    ));
    private final Channel channel;
    private final int[] colors;
    private final Function<BlockState, VoxelShape> shapes;
    private final BlockState crossState;
    private final WireEvaluator evaluator = new DefaultWireEvaluator(this);
    private boolean shouldSignal = true;

    protected ColoredWireBlock(Properties properties, Channel channel, ColorMix mix) {
        super(properties);
        this.channel = channel;
        this.colors = colorsFor(mix);
        registerDefaultState(withWater(stateDefinition.any()
            .setValue(NORTH, RedstoneSide.NONE)
            .setValue(EAST, RedstoneSide.NONE)
            .setValue(SOUTH, RedstoneSide.NONE)
            .setValue(WEST, RedstoneSide.NONE)
            .setValue(POWER, 0), false));
        shapes = makeShapes();
        crossState = defaultBlockState()
            .setValue(NORTH, RedstoneSide.SIDE)
            .setValue(EAST, RedstoneSide.SIDE)
            .setValue(SOUTH, RedstoneSide.SIDE)
            .setValue(WEST, RedstoneSide.SIDE);
    }

    private Function<BlockState, VoxelShape> makeShapes() {
        VoxelShape dot = Block.column(10.0, 0.0, 1.0);
        Map<Direction, VoxelShape> floor = Shapes.rotateHorizontal(Block.boxZ(10.0, 0.0, 1.0, 0.0, 8.0));
        Map<Direction, VoxelShape> up = Shapes.rotateHorizontal(Block.boxZ(10.0, 16.0, 0.0, 1.0));
        return getShapeForEachState(state -> {
            VoxelShape shape = dot;
            for (Map.Entry<Direction, EnumProperty<RedstoneSide>> entry : PROPERTY_BY_DIRECTION.entrySet()) {
                shape = switch (state.getValue(entry.getValue())) {
                    case UP -> Shapes.or(shape, floor.get(entry.getKey()), up.get(entry.getKey()));
                    case SIDE -> Shapes.or(shape, floor.get(entry.getKey()));
                    case NONE -> shape;
                };
            }
            return shape;
        }, defaultBlockState().hasProperty(WATERLOGGED) ? new Property<?>[] {POWER, WATERLOGGED} : new Property<?>[] {POWER});
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.apply(state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = Waterlogging.place(crossState, context);
        return getConnectionState(context.getLevel(), state, context.getClickedPos());
    }

    private BlockState getConnectionState(BlockGetter level, BlockState state, BlockPos pos) {
        boolean wasDot = isDot(state);
        state = getMissingConnections(level, withWater(defaultBlockState()
            .setValue(POWER, state.getValue(POWER)), Waterlogging.isWet(state)), pos);
        if (wasDot && isDot(state)) {
            return state;
        }
        boolean north = state.getValue(NORTH).isConnected();
        boolean south = state.getValue(SOUTH).isConnected();
        boolean east = state.getValue(EAST).isConnected();
        boolean west = state.getValue(WEST).isConnected();
        boolean northSouthEmpty = !north && !south;
        boolean eastWestEmpty = !east && !west;
        if (!west && northSouthEmpty) {
            state = state.setValue(WEST, RedstoneSide.SIDE);
        }
        if (!east && northSouthEmpty) {
            state = state.setValue(EAST, RedstoneSide.SIDE);
        }
        if (!north && eastWestEmpty) {
            state = state.setValue(NORTH, RedstoneSide.SIDE);
        }
        if (!south && eastWestEmpty) {
            state = state.setValue(SOUTH, RedstoneSide.SIDE);
        }
        return state;
    }

    private BlockState getMissingConnections(BlockGetter level, BlockState state, BlockPos pos) {
        boolean canConnectUp = !level.getBlockState(pos.above()).isRedstoneConductor(level, pos);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!state.getValue(PROPERTY_BY_DIRECTION.get(direction)).isConnected()) {
                RedstoneSide side = getConnectingSide(level, pos, direction, canConnectUp);
                state = state.setValue(PROPERTY_BY_DIRECTION.get(direction), side);
            }
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        Waterlogging.scheduleTick(state, level, ticks, pos);
        if (directionToNeighbour == Direction.DOWN) {
            return !canSurviveOn(level, neighbourPos, neighbourState) ? Blocks.AIR.defaultBlockState() : state;
        }
        if (directionToNeighbour == Direction.UP) {
            return getConnectionState(level, state, pos);
        }
        RedstoneSide side = getConnectingSide(level, pos, directionToNeighbour);
        EnumProperty<RedstoneSide> property = PROPERTY_BY_DIRECTION.get(directionToNeighbour);
        if (side.isConnected() == state.getValue(property).isConnected() && !isCross(state)) {
            return state.setValue(property, side);
        }
        BlockState cross = withWater(crossState
            .setValue(POWER, state.getValue(POWER))
            .setValue(property, side), Waterlogging.isWet(state));
        return getConnectionState(level, cross, pos);
    }

    private static boolean isCross(BlockState state) {
        return state.getValue(NORTH).isConnected()
            && state.getValue(SOUTH).isConnected()
            && state.getValue(EAST).isConnected()
            && state.getValue(WEST).isConnected();
    }

    private static boolean isDot(BlockState state) {
        return !state.getValue(NORTH).isConnected()
            && !state.getValue(SOUTH).isConnected()
            && !state.getValue(EAST).isConnected()
            && !state.getValue(WEST).isConnected();
    }

    @Override
    protected void updateIndirectNeighbourShapes(BlockState state, LevelAccessor level, BlockPos pos, @Block.UpdateFlags int updateFlags, int updateLimit) {
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            RedstoneSide value = state.getValue(PROPERTY_BY_DIRECTION.get(direction));
            if (value != RedstoneSide.NONE && !level.getBlockState(blockPos.setWithOffset(pos, direction)).is(this)) {
                blockPos.move(Direction.DOWN);
                if (level.getBlockState(blockPos).is(this)) {
                    BlockPos neighborPos = blockPos.relative(direction.getOpposite());
                    level.neighborShapeChanged(direction.getOpposite(), blockPos, neighborPos, level.getBlockState(neighborPos), updateFlags, updateLimit);
                }
                blockPos.setWithOffset(pos, direction).move(Direction.UP);
                if (level.getBlockState(blockPos).is(this)) {
                    BlockPos neighborPos = blockPos.relative(direction.getOpposite());
                    level.neighborShapeChanged(direction.getOpposite(), blockPos, neighborPos, level.getBlockState(neighborPos), updateFlags, updateLimit);
                }
            }
        }
    }

    private RedstoneSide getConnectingSide(BlockGetter level, BlockPos pos, Direction direction) {
        return getConnectingSide(level, pos, direction, !level.getBlockState(pos.above()).isRedstoneConductor(level, pos));
    }

    private RedstoneSide getConnectingSide(BlockGetter level, BlockPos pos, Direction direction, boolean canConnectUp) {
        BlockPos relativePos = pos.relative(direction);
        BlockState relativeState = level.getBlockState(relativePos);
        if (canConnectUp) {
            boolean isPlaceableAbove = relativeState.getBlock() instanceof TrapDoorBlock || canSurviveOn(level, relativePos, relativeState);
            if (isPlaceableAbove && connectsTo(level, relativePos.above())) {
                if (relativeState.isFaceSturdy(level, relativePos, direction.getOpposite())) {
                    return RedstoneSide.UP;
                }
                return RedstoneSide.SIDE;
            }
        }
        if (connectsTo(relativeState, level, relativePos, direction)) {
            return RedstoneSide.SIDE;
        }
        return !relativeState.isRedstoneConductor(level, relativePos) && connectsTo(level, relativePos.below()) ? RedstoneSide.SIDE : RedstoneSide.NONE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return canSurviveOn(level, below, level.getBlockState(below));
    }

    private boolean canSurviveOn(BlockGetter level, BlockPos relativePos, BlockState relativeState) {
        return relativeState.isFaceSturdy(level, relativePos, Direction.UP) || relativeState.is(Blocks.HOPPER);
    }

    private void updatePowerStrength(Level level, BlockPos pos, BlockState state, @Nullable Orientation orientation, boolean shapeUpdateWiresAroundInitialPosition) {
        if (useExperimentalEvaluator(level)) {
            new ExperimentalWireEvaluator(this).updatePowerStrength(level, pos, state, orientation, shapeUpdateWiresAroundInitialPosition);
        } else {
            evaluator.updatePowerStrength(level, pos, state, orientation, shapeUpdateWiresAroundInitialPosition);
        }
    }

    public int getBlockSignal(Level level, BlockPos pos) {
        return Signals.readForDust(listener(), () -> {
            shouldSignal = false;
            try {
                return level.getBestNeighborSignal(pos);
            } finally {
                shouldSignal = true;
            }
        });
    }

    /**
     * How this dust hears the blocks around it.
     */
    protected Listener listener() {
        return channel;
    }

    /**
     * The power this dust takes along the wire from a piece of dust of another colour: one level less, as from its own
     * kind, if it hears that colour, and none otherwise.
     */
    protected int powerFromOtherDust(BlockState state) {
        return Signals.isDust(state) && channel.hears(Channel.ofSource(state)) ? Math.max(0, state.getValue(POWER) - 1) : 0;
    }

    private void checkCornerChangeAt(Level level, BlockPos pos) {
        if (level.getBlockState(pos).is(this)) {
            level.updateNeighborsAt(pos, this);
            for (Direction direction : Direction.values()) {
                level.updateNeighborsAt(pos.relative(direction), this);
            }
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock()) && !level.isClientSide()) {
            updatePowerStrength(level, pos, state, null, true);
            for (Direction direction : Direction.Plane.VERTICAL) {
                level.updateNeighborsAt(pos.relative(direction), this);
            }
            updateNeighborsOfNeighboringWires(level, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (!movedByPiston) {
            for (Direction direction : Direction.values()) {
                level.updateNeighborsAt(pos.relative(direction), this);
            }
            updatePowerStrength(level, pos, state, null, false);
            updateNeighborsOfNeighboringWires(level, pos);
        }
    }

    private void updateNeighborsOfNeighboringWires(Level level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            checkCornerChangeAt(level, pos.relative(direction));
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos target = pos.relative(direction);
            if (level.getBlockState(target).isRedstoneConductor(level, target)) {
                checkCornerChangeAt(level, target.above());
            } else {
                checkCornerChangeAt(level, target.below());
            }
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide() && (block != this || !useExperimentalEvaluator(level))) {
            if (state.canSurvive(level, pos)) {
                updatePowerStrength(level, pos, state, orientation, false);
            } else {
                dropResources(state, level, pos);
                level.removeBlock(pos, false);
            }
        }
    }

    private static boolean useExperimentalEvaluator(Level level) {
        return level.enabledFeatures().contains(FeatureFlags.REDSTONE_EXPERIMENTS);
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return !shouldSignal ? 0 : state.getSignal(level, pos, direction);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (!shouldSignal || direction == Direction.DOWN) {
            return 0;
        }
        int power = state.getValue(POWER);
        if (power == 0) {
            return 0;
        }
        if (direction != Direction.UP && !getConnectionState(level, state, pos).getValue(PROPERTY_BY_DIRECTION.get(direction.getOpposite())).isConnected()) {
            return 0;
        }
        return power;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return shouldSignal;
    }

    @Override
    public Channel channel(BlockState state) {
        return channel;
    }

    private boolean connectsTo(BlockGetter level, BlockPos pos) {
        return connectsTo(level.getBlockState(pos), level, pos, null);
    }

    private boolean connectsTo(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        if (state.getBlock() instanceof SignalBlock signalBlock) {
            return signalBlock.connectsToWire(state, channel, direction);
        }
        Channel source = Channel.ofSource(state);
        if (source != null && !channel.hears(source)) {
            return false;
        }
        return WireConnections.connects(state, level, pos, direction);
    }

    @Override
    protected boolean shouldRedstoneWireConnectTo(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return connectsToWire(state, Channel.REDSTONE, direction);
    }

    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return connectsToWire(state, Channel.REDSTONE, direction);
    }

    public int colorForPower(int power) {
        return colors[power];
    }

    /**
     * Turns one of redstone dust's colours into this dust's colour.
     */
    @FunctionalInterface
    interface ColorMix {
        int mix(float red, float green, float blue);
    }

    static int[] colorsFor(ColorMix mix) {
        int[] colors = new int[16];
        for (int i = 0; i <= 15; i++) {
            float power = i / 15.0F;
            float red = power * 0.6F + (power > 0.0F ? 0.4F : 0.3F);
            float green = Mth.clamp(power * power * 0.7F - 0.5F, 0.0F, 1.0F);
            float blue = Mth.clamp(power * power * 0.6F - 0.7F, 0.0F, 1.0F);
            colors[i] = mix.mix(red, green, blue);
        }
        return colors;
    }

    private static void spawnParticlesAlongLine(Level level, RandomSource random, BlockPos pos, int color, Direction side, Direction along, float from, float to) {
        float span = to - from;
        if (random.nextFloat() < 0.2F * span) {
            float positionOnLine = from + span * random.nextFloat();
            double x = 0.5 + 0.4375F * side.getStepX() + positionOnLine * along.getStepX();
            double y = 0.5 + 0.4375F * side.getStepY() + positionOnLine * along.getStepY();
            double z = 0.5 + 0.4375F * side.getStepZ() + positionOnLine * along.getStepZ();
            level.addParticle(new DustParticleOptions(color, 1.0F), pos.getX() + x, pos.getY() + y, pos.getZ() + z, 0.0, 0.0, 0.0);
            // Only bluestone dust is ever underwater.
            BluestoneDiodes.bubble(level, pos.getX() + x, pos.getY() + y, pos.getZ() + z);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int power = state.getValue(POWER);
        if (power == 0) {
            return;
        }
        for (Direction horizontal : Direction.Plane.HORIZONTAL) {
            switch (state.getValue(PROPERTY_BY_DIRECTION.get(horizontal))) {
                case UP:
                    spawnParticlesAlongLine(level, random, pos, colors[power], horizontal, Direction.UP, -0.5F, 0.5F);
                case SIDE:
                    spawnParticlesAlongLine(level, random, pos, colors[power], Direction.DOWN, horizontal, 0.0F, 0.5F);
                    break;
                case NONE:
                default:
                    spawnParticlesAlongLine(level, random, pos, colors[power], Direction.DOWN, horizontal, 0.0F, 0.3F);
            }
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_180 -> state
                .setValue(NORTH, state.getValue(SOUTH))
                .setValue(EAST, state.getValue(WEST))
                .setValue(SOUTH, state.getValue(NORTH))
                .setValue(WEST, state.getValue(EAST));
            case COUNTERCLOCKWISE_90 -> state
                .setValue(NORTH, state.getValue(EAST))
                .setValue(EAST, state.getValue(SOUTH))
                .setValue(SOUTH, state.getValue(WEST))
                .setValue(WEST, state.getValue(NORTH));
            case CLOCKWISE_90 -> state
                .setValue(NORTH, state.getValue(WEST))
                .setValue(EAST, state.getValue(NORTH))
                .setValue(SOUTH, state.getValue(EAST))
                .setValue(WEST, state.getValue(SOUTH));
            default -> state;
        };
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return switch (mirror) {
            case LEFT_RIGHT -> state.setValue(NORTH, state.getValue(SOUTH)).setValue(SOUTH, state.getValue(NORTH));
            case FRONT_BACK -> state.setValue(EAST, state.getValue(WEST)).setValue(WEST, state.getValue(EAST));
            default -> super.mirror(state, mirror);
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, POWER);
        if (this instanceof SimpleWaterloggedBlock) {
            builder.add(WATERLOGGED);
        }
    }

    // Only dust that can sit in water has the waterlogged property.
    private static BlockState withWater(BlockState state, boolean water) {
        return state.hasProperty(WATERLOGGED) ? state.setValue(WATERLOGGED, water) : state;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Waterlogging.isWet(state) ? Waterlogging.WATER : super.getFluidState(state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getAbilities().mayBuild) {
            return InteractionResult.PASS;
        }
        if (isCross(state) || isDot(state)) {
            BlockState newState = withWater((isCross(state) ? defaultBlockState() : crossState)
                .setValue(POWER, state.getValue(POWER)), Waterlogging.isWet(state));
            newState = getConnectionState(level, newState, pos);
            if (newState != state) {
                level.setBlockAndUpdate(pos, newState);
                updatesOnShapeChange(level, pos, state, newState);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    private void updatesOnShapeChange(Level level, BlockPos pos, BlockState oldState, BlockState newState) {
        Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, null, Direction.UP);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos relativePos = pos.relative(direction);
            EnumProperty<RedstoneSide> property = PROPERTY_BY_DIRECTION.get(direction);
            if (oldState.getValue(property).isConnected() != newState.getValue(property).isConnected() && level.getBlockState(relativePos).isRedstoneConductor(level, relativePos)) {
                level.updateNeighborsAtExceptFromFacing(relativePos, newState.getBlock(), direction.getOpposite(), ExperimentalRedstoneUtils.withFront(orientation, direction));
            }
        }
    }
}
