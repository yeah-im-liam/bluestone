package yeah.im.liam.bluestone.mixin.wire;

// The wire class has this name only from Minecraft 26.3.
//#if MC >= 26.1
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedstoneWireBlock;
import org.spongepowered.asm.mixin.Mixin;
import yeah.im.liam.bluestone.block.ConverterBoxBlock;
import yeah.im.liam.bluestone.signal.Channel;
import yeah.im.liam.bluestone.signal.Signals;

// Minecraft 26.3 and later.
@Mixin(RedstoneWireBlock.class)
abstract class ModernWireMixin {
    @WrapMethod(method = "getBlockSignal")
    private int bluestone$listen(Level level, BlockPos pos, Operation<Integer> original) {
        return Math.max(Signals.readForDust(Channel.REDSTONE, () -> original.call(level, pos)), ConverterBoxBlock.powerToDustOnTop(level, pos));
    }
}
//#endif
