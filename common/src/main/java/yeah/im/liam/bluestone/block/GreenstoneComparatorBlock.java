package yeah.im.liam.bluestone.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.ticks.TickPriority;

/**
 * The comparator for greenstone, on floors, walls and ceilings.
 */
public class GreenstoneComparatorBlock extends GreenstoneDiodeBlock {
    public static final EnumProperty<ComparatorMode> MODE = BlockStateProperties.MODE_COMPARATOR;
    public static final IntegerProperty STRENGTH = BlockStateProperties.POWER;

    public GreenstoneComparatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(ATTACHED, Direction.DOWN)
            .setValue(OUTPUT, Direction.NORTH)
            .setValue(MODE, ComparatorMode.COMPARE)
            .setValue(STRENGTH, 0)
            .setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MODE, STRENGTH);
    }

    @Override
    protected int getDelay(BlockState state) {
        return 2;
    }

    @Override
    protected int getOutputSignal(BlockGetter level, BlockPos pos, BlockState state) {
        return state.getValue(STRENGTH);
    }

    @Override
    protected int getInputSignal(Level level, BlockPos pos, BlockState state) {
        int signal = super.getInputSignal(level, pos, state);
        Direction input = input(state);
        BlockPos behind = pos.relative(input);
        BlockState behindState = level.getBlockState(behind);
        if (behindState.hasAnalogOutputSignal()) {
            return BluestoneDiodes.analogOutput(behindState, level, behind, input.getOpposite());
        }
        if (signal < 15 && behindState.isRedstoneConductor(level, behind)) {
            BlockPos further = behind.relative(input);
            BlockState furtherState = level.getBlockState(further);
            if (furtherState.hasAnalogOutputSignal()) {
                return BluestoneDiodes.analogOutput(furtherState, level, further, input.getOpposite());
            }
        }
        return signal;
    }

    private int calculateOutputSignal(Level level, BlockPos pos, BlockState state) {
        int input = getInputSignal(level, pos, state);
        if (input == 0) {
            return 0;
        }
        int side = getSideSignal(level, pos, state, false);
        if (side > input) {
            return 0;
        }
        return state.getValue(MODE) == ComparatorMode.SUBTRACT ? input - side : input;
    }

    @Override
    protected boolean shouldTurnOn(Level level, BlockPos pos, BlockState state) {
        int input = getInputSignal(level, pos, state);
        if (input == 0) {
            return false;
        }
        int side = getSideSignal(level, pos, state, false);
        return input > side || input == side && state.getValue(MODE) == ComparatorMode.COMPARE;
    }

    @Override
    protected void checkTickOnNeighbor(Level level, BlockPos pos, BlockState state) {
        if (level.getBlockTicks().willTickThisTick(pos, this)) {
            return;
        }
        if (calculateOutputSignal(level, pos, state) != state.getValue(STRENGTH) || state.getValue(POWERED) != shouldTurnOn(level, pos, state)) {
            level.scheduleTick(pos, this, getDelay(state), TickPriority.NORMAL);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        refreshOutputState(level, pos, state);
    }

    private void refreshOutputState(Level level, BlockPos pos, BlockState state) {
        int strength = calculateOutputSignal(level, pos, state);
        boolean powered = shouldTurnOn(level, pos, state);
        if (strength != state.getValue(STRENGTH) || powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(STRENGTH, strength).setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getAbilities().mayBuild) {
            return InteractionResult.PASS;
        }
        BlockState switched = state.cycle(MODE);
        level.playSound(player, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3F, switched.getValue(MODE) == ComparatorMode.SUBTRACT ? 0.55F : 0.5F);
        level.setBlock(pos, switched, Block.UPDATE_CLIENTS);
        if (!level.isClientSide()) {
            refreshOutputState(level, pos, switched);
        }
        return InteractionResult.SUCCESS;
    }
}
