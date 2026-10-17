package yeah.im.liam.bluestone.block;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ticks.TickPriority;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.signal.Channel;
import yeah.im.liam.bluestone.signal.SignalBlock;
import yeah.im.liam.bluestone.signal.Signals;

/**
 * The game's repeater and comparator logic for greenstone, on any face: floor, wall or ceiling.
 */
public abstract class GreenstoneDiodeBlock extends Block implements SignalBlock {
    public static final EnumProperty<Direction> ATTACHED = EnumProperty.create("attached", Direction.class);
    public static final EnumProperty<Direction> OUTPUT = EnumProperty.create("output", Direction.class);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    static final DustParticleOptions PARTICLE = GreenstoneTorchBlock.PARTICLE;
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Map.of(
        Direction.DOWN, Block.box(0.0, 0.0, 0.0, 16.0, 2.0, 16.0),
        Direction.UP, Block.box(0.0, 14.0, 0.0, 16.0, 16.0, 16.0),
        Direction.NORTH, Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 2.0),
        Direction.SOUTH, Block.box(0.0, 0.0, 14.0, 16.0, 16.0, 16.0),
        Direction.WEST, Block.box(0.0, 0.0, 0.0, 2.0, 16.0, 16.0),
        Direction.EAST, Block.box(14.0, 0.0, 0.0, 16.0, 16.0, 16.0)
    ));

    protected GreenstoneDiodeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ATTACHED, OUTPUT, POWERED);
    }

    protected abstract int getDelay(BlockState state);

    protected abstract boolean shouldTurnOn(Level level, BlockPos pos, BlockState state);

    protected abstract int getOutputSignal(BlockGetter level, BlockPos pos, BlockState state);

    public static Direction input(BlockState state) {
        return state.getValue(OUTPUT).getOpposite();
    }

    static Direction[] sides(BlockState state) {
        Direction attached = state.getValue(ATTACHED);
        Direction output = state.getValue(OUTPUT);
        Direction[] sides = new Direction[2];
        int i = 0;
        for (Direction direction : Direction.values()) {
            if (direction.getAxis() != attached.getAxis() && direction.getAxis() != output.getAxis()) {
                sides[i++] = direction;
            }
        }
        return sides;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(ATTACHED));
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction attached = state.getValue(ATTACHED);
        BlockPos support = pos.relative(attached);
        return level.getBlockState(support).isFaceSturdy(level, support, attached.getOpposite(), SupportType.RIGID);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction attached = context.getClickedFace().getOpposite();
        for (Direction direction : context.getNearestLookingDirections()) {
            if (direction.getAxis() != attached.getAxis()) {
                BlockState state = defaultBlockState().setValue(ATTACHED, attached).setValue(OUTPUT, direction);
                return state.canSurvive(context.getLevel(), context.getClickedPos()) ? placed(context.getLevel(), context.getClickedPos(), state) : null;
            }
        }
        return null;
    }

    protected BlockState placed(Level level, BlockPos pos, BlockState state) {
        return state;
    }

    //#if MC >= 1.21.2
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
    //#else
    //$$ @Override
    //$$ protected BlockState updateShape(BlockState state, Direction directionToNeighbour, BlockState neighbourState, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
    //$$     LevelAccessor ticks = level;
    //$$     RandomSource random = level.getRandom();
    //#endif
        if (directionToNeighbour == state.getValue(ATTACHED) && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (isLocked(level, pos, state)) {
            return;
        }
        boolean powered = state.getValue(POWERED);
        boolean shouldTurnOn = shouldTurnOn(level, pos, state);
        if (powered && !shouldTurnOn) {
            level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_CLIENTS);
        } else if (!powered) {
            level.setBlock(pos, state.setValue(POWERED, true), Block.UPDATE_CLIENTS);
            if (!shouldTurnOn) {
                level.scheduleTick(pos, this, getDelay(state), TickPriority.VERY_HIGH);
            }
        }
    }

    //#if MC >= 1.21.2
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
    //#else
    //$$ @Override
    //$$ protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighbourPos, boolean movedByPiston) {
    //$$     Orientation orientation = null;
    //#endif
        checkTickOnNeighbor(level, pos, state);
    }

    protected void checkTickOnNeighbor(Level level, BlockPos pos, BlockState state) {
        if (isLocked(level, pos, state)) {
            return;
        }
        boolean powered = state.getValue(POWERED);
        if (powered != shouldTurnOn(level, pos, state) && !level.getBlockTicks().willTickThisTick(pos, this)) {
            level.scheduleTick(pos, this, getDelay(state), powered ? TickPriority.VERY_HIGH : TickPriority.HIGH);
        }
    }

    public boolean isLocked(LevelReader level, BlockPos pos, BlockState state) {
        return false;
    }

    protected int getInputSignal(Level level, BlockPos pos, BlockState state) {
        Direction input = input(state);
        BlockPos inputPos = pos.relative(input);
        return Signals.read(Channel.GREENSTONE, () -> {
            int signal = level.getSignal(inputPos, input);
            BlockState inputState = level.getBlockState(inputPos);
            return Math.max(signal, onSurface(inputState, state) ? inputState.getValue(GreenstonePasteBlock.POWER) : 0);
        });
    }

    protected int getSideSignal(SignalGetter level, BlockPos pos, BlockState state, boolean diodesOnly) {
        return Signals.read(Channel.GREENSTONE, () -> {
            int signal = 0;
            for (Direction side : sides(state)) {
                BlockPos sidePos = pos.relative(side);
                BlockState sideState = level.getBlockState(sidePos);
                if (sideState.getBlock() instanceof GreenstoneDiodeBlock) {
                    signal = Math.max(signal, level.getDirectSignal(sidePos, side));
                } else if (diodesOnly) {
                    continue;
                } else if (sideState.getBlock() instanceof GreenstoneBlock) {
                    signal = 15;
                } else if (onSurface(sideState, state)) {
                    signal = Math.max(signal, sideState.getValue(GreenstonePasteBlock.POWER));
                } else if (sideState.isSignalSource()) {
                    signal = Math.max(signal, level.getDirectSignal(sidePos, side));
                }
            }
            return signal;
        });
    }

    private static boolean onSurface(BlockState paste, BlockState state) {
        return paste.getBlock() instanceof GreenstonePasteBlock && GreenstonePasteBlock.hasFace(paste, state.getValue(ATTACHED));
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) && direction == input(state) ? getOutputSignal(level, pos, state) : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        updateNeighborsInFront(level, pos, state);
        if (!level.isClientSide() && !oldState.is(this)) {
            checkTickOnNeighbor(level, pos, state);
        }
    }

    // Removal is affectNeighborsAfterRemoval from 1.21.5; before, onRemove also ran for changes within the block.
    //#if MC >= 1.21.5
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
    //#else
    //$$ @Override
    //$$ protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
    //$$     super.onRemove(state, level, pos, newState, movedByPiston);
    //$$     if (state.is(newState.getBlock()) || level.isClientSide()) {
    //$$         return;
    //$$     }
    //#endif
        if (!movedByPiston) {
            updateNeighborsInFront(level, pos, state);
        }
    }

    protected void updateNeighborsInFront(Level level, BlockPos pos, BlockState state) {
        Direction output = state.getValue(OUTPUT);
        BlockPos front = pos.relative(output);
        //#if MC >= 1.21.2
        level.neighborChanged(front, this, null);
        level.updateNeighborsAtExceptFromFacing(front, this, output.getOpposite(), null);
        //#else
        //$$ level.neighborChanged(front, this, pos);
        //$$ level.updateNeighborsAtExceptFromFacing(front, this, output.getOpposite());
        //#endif
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(POWERED)) {
            return;
        }
        Direction attached = state.getValue(ATTACHED);
        Direction output = state.getValue(OUTPUT);
        double along = (random.nextBoolean() ? 5.0 : -5.0) / 16.0;
        double x = pos.getX() + 0.5 + 0.3 * attached.getStepX() + along * output.getStepX() + (random.nextDouble() - 0.5) * 0.2;
        double y = pos.getY() + 0.5 + 0.3 * attached.getStepY() + along * output.getStepY() + (random.nextDouble() - 0.5) * 0.2;
        double z = pos.getZ() + 0.5 + 0.3 * attached.getStepZ() + along * output.getStepZ() + (random.nextDouble() - 0.5) * 0.2;
        level.addParticle(PARTICLE, x, y, z, 0.0, 0.0, 0.0);
    }

    @Override
    public Channel channel(BlockState state) {
        return Channel.GREENSTONE;
    }

    @Override
    public boolean connectsToWire(BlockState state, Channel wire, @Nullable Direction direction) {
        return false;
    }

    //#if MC >= 26.1
    @Override
    //#endif
    protected boolean shouldRedstoneWireConnectTo(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return false;
    }

    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return false;
    }
}
