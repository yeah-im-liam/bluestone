package yeah.im.liam.bluestone.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import yeah.im.liam.bluestone.block.ColoredWireBlock;
import yeah.im.liam.bluestone.block.PurplestoneBlock;
import yeah.im.liam.bluestone.block.PurplestoneWireBlock;
import yeah.im.liam.bluestone.signal.Channel;
import yeah.im.liam.bluestone.signal.Signals;

@Mixin(DiodeBlock.class)
abstract class DiodeBlockMixin {
    @Shadow
    protected abstract boolean sideInputDiodesOnly();

    @WrapMethod(method = "getInputSignal")
    private int bluestone$listen(Level level, BlockPos pos, BlockState state, Operation<Integer> original) {
        return Signals.read(Channel.of((Block) (Object) this), () -> original.call(level, pos, state));
    }

    @WrapMethod(method = "getAlternateSignal")
    private int bluestone$listenToSides(SignalGetter level, BlockPos pos, BlockState state, Operation<Integer> original) {
        return Signals.read(Channel.of((Block) (Object) this), () -> original.call(level, pos, state));
    }

    // The game's comparator counts a Block of Redstone beside it at full strength and dust at its power; purplestone
    // counts the same way.
    @ModifyReturnValue(method = "getAlternateSignal", at = @At("RETURN"))
    private int bluestone$purplestoneSides(int signal, @Local(argsOnly = true) SignalGetter level, @Local(argsOnly = true) BlockPos pos,
                                           @Local(argsOnly = true) BlockState state) {
        if (signal >= 15 || sideInputDiodesOnly()) {
            return signal;
        }
        Direction facing = state.getValue(DiodeBlock.FACING);
        for (Direction side : new Direction[] {facing.getClockWise(), facing.getCounterClockWise()}) {
            BlockState sideState = level.getBlockState(pos.relative(side));
            if (sideState.getBlock() instanceof PurplestoneBlock) {
                return 15;
            }
            if (sideState.getBlock() instanceof PurplestoneWireBlock) {
                signal = Math.max(signal, sideState.getValue(ColoredWireBlock.POWER));
            }
        }
        return signal;
    }
}
