package yeah.im.liam.bluestone.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

abstract class WireEvaluator {
    protected final ColoredWireBlock wireBlock;

    protected WireEvaluator(ColoredWireBlock wireBlock) {
        this.wireBlock = wireBlock;
    }

    public abstract void updatePowerStrength(Level level, BlockPos pos, BlockState state, @Nullable Orientation orientation, boolean skipShapeUpdates);

    protected int getBlockSignal(Level level, BlockPos pos) {
        return wireBlock.getBlockSignal(level, pos);
    }

    protected int getWireSignal(BlockPos pos, BlockState state) {
        return state.is(wireBlock) ? state.getValue(ColoredWireBlock.POWER) : 0;
    }

    protected int getIncomingWireSignal(Level level, BlockPos pos) {
        int wireSignal = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            wireSignal = Math.max(wireSignal, getReceivedSignal(neighborPos, neighborState));
            BlockPos abovePos = pos.above();
            if (neighborState.isRedstoneConductor(level, neighborPos) && !level.getBlockState(abovePos).isRedstoneConductor(level, abovePos)) {
                BlockPos aboveNeighborPos = neighborPos.above();
                wireSignal = Math.max(wireSignal, getReceivedSignal(aboveNeighborPos, level.getBlockState(aboveNeighborPos)));
            } else if (!neighborState.isRedstoneConductor(level, neighborPos)) {
                BlockPos belowNeighborPos = neighborPos.below();
                wireSignal = Math.max(wireSignal, getReceivedSignal(belowNeighborPos, level.getBlockState(belowNeighborPos)));
            }
        }
        return wireSignal;
    }

    // What this dust gets from a piece of dust next to it: one level less than its own kind has, and whatever the dust
    // makes of other colours.
    private int getReceivedSignal(BlockPos pos, BlockState state) {
        return state.is(wireBlock) ? Math.max(0, getWireSignal(pos, state) - 1) : wireBlock.powerFromOtherDust(state);
    }
}
