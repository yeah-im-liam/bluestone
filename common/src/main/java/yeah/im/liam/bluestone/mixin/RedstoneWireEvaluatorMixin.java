package yeah.im.liam.bluestone.mixin;

// The game's wire evaluator is new in 1.21.2; LegacyWireMixin hooks the wire itself before.
//#if MC >= 1.21.2
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
        return signal == 0 ? ColoredWireBlock.powerForRedstoneDust(state) : signal;
    }
}
//#endif
