package yeah.im.liam.bluestone.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.signal.Channel;
import yeah.im.liam.bluestone.signal.SignalBlock;
import yeah.im.liam.bluestone.signal.Signals;

/**
 * A one-tick repeater that takes one kind of signal and gives out the other. Right-clicking swaps the direction, and
 * like a repeater it is locked by a repeater or comparator (of either kind) pointing into its side.
 */
public class ConverterBlock extends DiodeBlock implements SimpleWaterloggedBlock, SignalBlock {
    public static final EnumProperty<Mode> MODE = EnumProperty.create("mode", Mode.class);
    public static final BooleanProperty LOCKED = BlockStateProperties.LOCKED;

    public ConverterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(MODE, Mode.RED_TO_BLUE)
            .setValue(LOCKED, false)
            .setValue(POWERED, false)
            .setValue(BlockStateProperties.WATERLOGGED, false));
    }

    @Override
    protected int getDelay(BlockState state) {
        return 2;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getAbilities().mayBuild) {
            return InteractionResult.PASS;
        }
        BlockState switched = state.cycle(MODE);
        level.playSound(player, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3F, switched.getValue(MODE) == Mode.RED_TO_BLUE ? 0.5F : 0.55F);
        level.setBlockAndUpdate(pos, switched);
        if (!level.isClientSide()) {
            // The output changed colour, so whatever it powers through the block in front has to look again.
            updateNeighborsInFront(level, pos, switched);
            checkTickOnNeighbor(level, pos, switched);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected int getInputSignal(Level level, BlockPos pos, BlockState state) {
        Channel input = state.getValue(MODE).input;
        return Signals.read(input, () -> {
            Direction direction = state.getValue(FACING);
            BlockPos targetPos = pos.relative(direction);
            int signal = level.getSignal(targetPos, direction);
            if (signal >= 15) {
                return signal;
            }
            BlockState target = level.getBlockState(targetPos);
            boolean dust = input == Channel.REDSTONE ? target.is(Blocks.REDSTONE_WIRE) : target.getBlock() instanceof BluestoneWireBlock;
            return Math.max(signal, dust ? target.getValue(BlockStateProperties.POWER) : 0);
        });
    }

    // Repeaters and comparators of either kind lock it.
    @Override
    protected int getAlternateSignal(SignalGetter level, BlockPos pos, BlockState state) {
        return Signals.read(null, () -> {
            Direction direction = state.getValue(FACING);
            return Math.max(sideDiode(level, pos, direction.getClockWise()), sideDiode(level, pos, direction.getCounterClockWise()));
        });
    }

    private static int sideDiode(SignalGetter level, BlockPos pos, Direction side) {
        BlockPos sidePos = pos.relative(side);
        return DiodeBlock.isDiode(level.getBlockState(sidePos)) ? level.getDirectSignal(sidePos, side) : 0;
    }

    @Override
    public boolean isLocked(LevelReader level, BlockPos pos, BlockState state) {
        return getAlternateSignal(level, pos, state) > 0;
    }

    @Override
    protected boolean sideInputDiodesOnly() {
        return true;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        state = state.setValue(LOCKED, isLocked(context.getLevel(), context.getClickedPos(), state));
        return Waterlogging.place(state, context);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        Waterlogging.scheduleTick(state, level, ticks, pos);
        if (directionToNeighbour == Direction.DOWN && !canSurviveOn(level, neighbourPos, neighbourState)) {
            return Blocks.AIR.defaultBlockState();
        }
        if (!level.isClientSide() && directionToNeighbour.getAxis() != state.getValue(FACING).getAxis()) {
            return state.setValue(LOCKED, isLocked(level, pos, state));
        }
        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Waterlogging.isWet(state) ? Waterlogging.WATER : super.getFluidState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MODE, LOCKED, POWERED, BlockStateProperties.WATERLOGGED);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(POWERED)) {
            return;
        }
        Direction direction = state.getValue(FACING);
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
        double y = pos.getY() + 0.4 + (random.nextDouble() - 0.5) * 0.2;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
        // Like a one-tick repeater: the torch at the output end, or the one nearer the input.
        boolean inputTorch = random.nextBoolean();
        float offset = (inputTorch ? 1.0F : -5.0F) / 16.0F;
        Channel channel = inputTorch ? state.getValue(MODE).input : state.getValue(MODE).output;
        DustParticleOptions particle = channel == Channel.REDSTONE ? DustParticleOptions.REDSTONE : BluestoneDiodes.PARTICLE;
        level.addParticle(particle, x + offset * direction.getStepX(), y, z + offset * direction.getStepZ(), 0.0, 0.0, 0.0);
    }

    @Override
    public Channel channel(BlockState state) {
        return state.getValue(MODE).output;
    }

    /**
     * Dust of the input's kind bends to the back, dust of the output's kind to the front, and purplestone to both.
     */
    @Override
    public boolean connectsToWire(BlockState state, Channel wire, @Nullable Direction direction) {
        Direction facing = state.getValue(FACING);
        Mode mode = state.getValue(MODE);
        if (wire == Channel.PURPLESTONE) {
            return direction == facing || direction == facing.getOpposite();
        }
        return wire == mode.input && direction == facing.getOpposite() || wire == mode.output && direction == facing;
    }

    @Override
    protected boolean shouldRedstoneWireConnectTo(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return connectsToWire(state, Channel.REDSTONE, direction);
    }

    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return connectsToWire(state, Channel.REDSTONE, direction);
    }

    public enum Mode implements StringRepresentable {
        RED_TO_BLUE("red_to_blue", Channel.REDSTONE, Channel.BLUESTONE),
        BLUE_TO_RED("blue_to_red", Channel.BLUESTONE, Channel.REDSTONE);

        private final String name;
        private final Channel input;
        private final Channel output;

        Mode(String name, Channel input, Channel output) {
            this.name = name;
            this.input = input;
            this.output = output;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
