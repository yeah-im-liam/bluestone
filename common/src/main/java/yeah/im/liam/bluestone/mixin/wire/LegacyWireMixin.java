package yeah.im.liam.bluestone.mixin.wire;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yeah.im.liam.bluestone.block.ConverterBoxBlock;
import yeah.im.liam.bluestone.signal.Channel;
import yeah.im.liam.bluestone.signal.SignalBlock;
import yeah.im.liam.bluestone.signal.Signals;

// Minecraft 26.2 and older, where the dust class was still called RedStoneWireBlock.
//#if MC >= 26.1
@Mixin(targets = "net.minecraft.world.level.block.RedStoneWireBlock")
//#else
//$$ @Mixin(net.minecraft.world.level.block.RedStoneWireBlock.class)
//#endif
abstract class LegacyWireMixin {
    @WrapMethod(method = "getBlockSignal")
    private int bluestone$listen(Level level, BlockPos pos, Operation<Integer> original) {
        return Math.max(Signals.readForDust(Channel.REDSTONE, () -> original.call(level, pos)), ConverterBoxBlock.powerToDustOnTop(level, pos));
    }

    // Fabric's dust asks here whether to bend towards a block; NeoForge asks the block's canConnectRedstone instead.
    @Inject(method = "shouldConnectTo(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z", at = @At("HEAD"), cancellable = true)
    private static void bluestone$skipBluestone(BlockState state, @Nullable Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (state.getBlock() instanceof SignalBlock signalBlock) {
            cir.setReturnValue(signalBlock.connectsToWire(state, Channel.REDSTONE, direction));
        }
    }
}
