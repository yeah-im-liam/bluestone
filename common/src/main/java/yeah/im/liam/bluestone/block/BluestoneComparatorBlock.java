package yeah.im.liam.bluestone.block;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Keeps the game's comparator block entity; each loader adds this block to the comparator block entity type.
 */
public class BluestoneComparatorBlock extends ComparatorBlock implements SimpleWaterloggedBlock, BluestoneComponent {
    public BluestoneComparatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false));
    }

    @Override
    protected int getInputSignal(Level level, BlockPos pos, BlockState state) {
        int resultSignal = BluestoneDiodes.input(level, pos, state);
        Direction direction = state.getValue(FACING);
        BlockPos targetPos = pos.relative(direction);
        BlockState targetState = level.getBlockState(targetPos);
        if (targetState.hasAnalogOutputSignal()) {
            resultSignal = targetState.getAnalogOutputSignal(level, targetPos, direction.getOpposite());
        } else if (resultSignal < 15 && targetState.isRedstoneConductor(level, targetPos)) {
            targetPos = targetPos.relative(direction);
            targetState = level.getBlockState(targetPos);
            ItemFrame itemFrame = getItemFrame(level, direction, targetPos);
            int itemFrameOrBlockSignal = Math.max(
                itemFrame == null ? Integer.MIN_VALUE : itemFrame.getAnalogOutput(),
                targetState.hasAnalogOutputSignal() ? targetState.getAnalogOutputSignal(level, targetPos, direction.getOpposite()) : Integer.MIN_VALUE
            );
            if (itemFrameOrBlockSignal != Integer.MIN_VALUE) {
                resultSignal = itemFrameOrBlockSignal;
            }
        }
        return resultSignal;
    }

    private static @Nullable ItemFrame getItemFrame(Level level, Direction direction, BlockPos pos) {
        List<ItemFrame> itemFrames = level.getEntitiesOfClass(ItemFrame.class, new AABB(pos), entity -> entity.getDirection() == direction);
        return itemFrames.size() == 1 ? itemFrames.get(0) : null;
    }

    @Override
    protected int getAlternateSignal(SignalGetter level, BlockPos pos, BlockState state) {
        return BluestoneDiodes.sides(level, pos, state, sideInputDiodesOnly());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return Waterlogging.place(super.getStateForPlacement(context), context);
    }

    @Override
    public BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        Waterlogging.scheduleTick(state, level, ticks, pos);
        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Waterlogging.isWet(state) ? Waterlogging.WATER : super.getFluidState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BlockStateProperties.WATERLOGGED);
    }

    @Override
    protected boolean shouldRedstoneWireConnectTo(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return false;
    }

    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return false;
    }

    // NeoForge only lets comparators of the game's own block notice containers changing behind a solid block.
    public boolean getWeakChanges(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }
}
