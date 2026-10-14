package yeah.im.liam.bluestone.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.RedstoneWireEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yeah.im.liam.bluestone.block.ColoredWireBlock;
import yeah.im.liam.bluestone.block.ConverterBoxBlock;
import yeah.im.liam.bluestone.signal.Channel;

/**
 * Redstone dust takes power along the wire from purplestone dust and from a converter box, one level weaker per block,
 * the same way it does from redstone dust.
 */
@Mixin(RedstoneWireEvaluator.class)
abstract class RedstoneWireEvaluatorMixin {
    @ModifyReturnValue(method = "getWireSignal", at = @At("RETURN"))
    private int bluestone$otherDust(int signal, @Local(argsOnly = true) BlockState state) {
        if (signal == 0 && state.getBlock() instanceof ColoredWireBlock wire && Channel.REDSTONE.hears(wire.channel(state))) {
            return state.getValue(ColoredWireBlock.POWER);
        }
        if (signal == 0 && state.getBlock() instanceof ConverterBoxBlock) {
            return state.getValue(ConverterBoxBlock.POWER);
        }
        return signal;
    }
}
