package yeah.im.liam.bluestone.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import yeah.im.liam.bluestone.signal.Channel;
import yeah.im.liam.bluestone.signal.Signals;

@Mixin(DiodeBlock.class)
abstract class DiodeBlockMixin {
    @WrapMethod(method = "getInputSignal")
    private int bluestone$listen(Level level, BlockPos pos, BlockState state, Operation<Integer> original) {
        return Signals.read(Channel.of((Block) (Object) this), () -> original.call(level, pos, state));
    }

    @WrapMethod(method = "getAlternateSignal")
    private int bluestone$listenToSides(SignalGetter level, BlockPos pos, BlockState state, Operation<Integer> original) {
        return Signals.read(Channel.of((Block) (Object) this), () -> original.call(level, pos, state));
    }
}
