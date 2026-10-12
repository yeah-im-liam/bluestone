package yeah.im.liam.bluestone.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yeah.im.liam.bluestone.signal.Signals;

@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class BlockStateBaseMixin {
    @ModifyReturnValue(method = {"getSignal", "getDirectSignal"}, at = @At("RETURN"))
    private int bluestone$hideOtherChannel(int signal) {
        return signal != 0 && Signals.hidden((BlockState) (Object) this) ? 0 : signal;
    }
}
