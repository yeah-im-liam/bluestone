package yeah.im.liam.bluestone.block;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

final class DefaultBluestoneWireEvaluator extends BluestoneWireEvaluator {
    DefaultBluestoneWireEvaluator(BluestoneWireBlock wireBlock) {
        super(wireBlock);
    }

    @Override
    public void updatePowerStrength(Level level, BlockPos pos, BlockState state, @Nullable Orientation orientation, boolean skipShapeUpdates) {
        int targetStrength = calculateTargetStrength(level, pos);
        if (state.getValue(BluestoneWireBlock.POWER) != targetStrength) {
            if (level.getBlockState(pos) == state) {
                level.setBlock(pos, state.setValue(BluestoneWireBlock.POWER, targetStrength), 2);
            }
            Set<BlockPos> toUpdate = new HashSet<>();
            toUpdate.add(pos);
            for (Direction direction : Direction.values()) {
                toUpdate.add(pos.relative(direction));
            }
            for (BlockPos blockPos : toUpdate) {
                level.updateNeighborsAt(blockPos, wireBlock);
            }
        }
    }

    private int calculateTargetStrength(Level level, BlockPos pos) {
        int blockSignal = getBlockSignal(level, pos);
        return blockSignal == 15 ? blockSignal : Math.max(blockSignal, getIncomingWireSignal(level, pos));
    }
}
