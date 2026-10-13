package yeah.im.liam.bluestone.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.state.BlockState;
import yeah.im.liam.bluestone.signal.Channel;
import yeah.im.liam.bluestone.signal.Signals;

/**
 * Input reading shared by the bluestone repeater and comparator: the game's diode logic with bluestone dust and
 * blocks in place of the redstone ones.
 */
final class BluestoneDiodes {
    static final DustParticleOptions PARTICLE = new DustParticleOptions(0x0000FF, 1.0F);

    private BluestoneDiodes() {
    }

    static int input(Level level, BlockPos pos, BlockState state) {
        return Signals.read(Channel.BLUESTONE, () -> {
            Direction direction = state.getValue(DiodeBlock.FACING);
            BlockPos targetPos = pos.relative(direction);
            int input = level.getSignal(targetPos, direction);
            if (input >= 15) {
                return input;
            }
            BlockState target = level.getBlockState(targetPos);
            // Bluestone and purplestone dust, the dust that bluestone hears.
            return Math.max(input, target.getBlock() instanceof ColoredWireBlock ? target.getValue(ColoredWireBlock.POWER) : 0);
        });
    }

    static int sides(SignalGetter level, BlockPos pos, BlockState state, boolean diodesOnly) {
        return Signals.read(Channel.BLUESTONE, () -> {
            Direction direction = state.getValue(DiodeBlock.FACING);
            Direction clockWise = direction.getClockWise();
            Direction counterClockWise = direction.getCounterClockWise();
            return Math.max(
                controlInput(level, pos.relative(clockWise), clockWise, diodesOnly),
                controlInput(level, pos.relative(counterClockWise), counterClockWise, diodesOnly)
            );
        });
    }

    private static int controlInput(SignalGetter level, BlockPos pos, Direction direction, boolean diodesOnly) {
        BlockState state = level.getBlockState(pos);
        if (diodesOnly) {
            return DiodeBlock.isDiode(state) ? level.getDirectSignal(pos, direction) : 0;
        }
        if (state.getBlock() instanceof BluestoneBlock || state.getBlock() instanceof PurplestoneBlock) {
            return 15;
        }
        if (state.getBlock() instanceof ColoredWireBlock) {
            return state.getValue(ColoredWireBlock.POWER);
        }
        return state.isSignalSource() ? level.getDirectSignal(pos, direction) : 0;
    }
}
