package yeah.im.liam.bluestone.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import yeah.im.liam.bluestone.signal.Channel;
import yeah.im.liam.bluestone.signal.Signals;

@Mixin(RedstoneWallTorchBlock.class)
abstract class RedstoneWallTorchBlockMixin {
    @WrapMethod(method = "hasNeighborSignal")
    private boolean bluestone$listen(Level level, BlockPos pos, BlockState state, Operation<Boolean> original) {
        return Signals.test(Channel.of((Block) (Object) this), () -> original.call(level, pos, state));
    }
}
