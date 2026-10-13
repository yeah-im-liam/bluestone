package yeah.im.liam.bluestone.mixin.wire;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedstoneWireBlock;
import org.spongepowered.asm.mixin.Mixin;
import yeah.im.liam.bluestone.signal.Channel;
import yeah.im.liam.bluestone.signal.Signals;

// Minecraft 26.3 and later. Dust there asks each block whether to bend towards it, which the bluestone blocks answer.
@Mixin(RedstoneWireBlock.class)
abstract class ModernWireMixin {
    @WrapMethod(method = "getBlockSignal")
    private int bluestone$listen(Level level, BlockPos pos, Operation<Integer> original) {
        return Signals.readForDust(Channel.REDSTONE, () -> original.call(level, pos));
    }
}
