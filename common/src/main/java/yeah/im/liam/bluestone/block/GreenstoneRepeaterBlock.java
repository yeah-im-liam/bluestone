package yeah.im.liam.bluestone.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The repeater for greenstone, on floors, walls and ceilings.
 */
public class GreenstoneRepeaterBlock extends GreenstoneDiodeBlock {
    public static final IntegerProperty DELAY = BlockStateProperties.DELAY;
    public static final BooleanProperty LOCKED = BlockStateProperties.LOCKED;

    public GreenstoneRepeaterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(ATTACHED, Direction.DOWN)
            .setValue(OUTPUT, Direction.NORTH)
            .setValue(DELAY, 1)
            .setValue(LOCKED, false)
            .setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DELAY, LOCKED);
    }

    @Override
    protected int getDelay(BlockState state) {
        return state.getValue(DELAY) * 2;
    }

    @Override
    protected boolean shouldTurnOn(Level level, BlockPos pos, BlockState state) {
        return getInputSignal(level, pos, state) > 0;
    }

    @Override
    protected int getOutputSignal(BlockGetter level, BlockPos pos, BlockState state) {
        return 15;
    }

    @Override
    public boolean isLocked(LevelReader level, BlockPos pos, BlockState state) {
        return getSideSignal(level, pos, state, true) > 0;
    }

    @Override
    protected BlockState placed(Level level, BlockPos pos, BlockState state) {
        return state.setValue(LOCKED, isLocked(level, pos, state));
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
        //#if MC >= 1.21.2
        BlockState updated = super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
        //#else
        //$$ BlockState updated = super.updateShape(state, directionToNeighbour, neighbourState, level, pos, neighbourPos);
        //#endif
        if (updated.is(this) && !level.isClientSide()
            && directionToNeighbour.getAxis() != state.getValue(ATTACHED).getAxis()
            && directionToNeighbour.getAxis() != state.getValue(OUTPUT).getAxis()) {
            return updated.setValue(LOCKED, isLocked(level, pos, updated));
        }
        return updated;
    }

    // Blocks are used through use() with any item before 1.20.5.
    //#if MC >= 1.20.5
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
    //#else
    //$$ @Override
    //$$ public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
    //#endif
        if (!player.getAbilities().mayBuild) {
            return InteractionResult.PASS;
        }
        level.setBlock(pos, state.cycle(DELAY), Block.UPDATE_ALL);
        return InteractionResult.SUCCESS;
    }
}
