package yeah.im.liam.bluestone.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yeah.im.liam.bluestone.block.RailPower;
import yeah.im.liam.bluestone.signal.Channel;
import yeah.im.liam.bluestone.signal.Signals;

@Mixin(PoweredRailBlock.class)
abstract class PoweredRailBlockMixin {
    @WrapOperation(method = "updateState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;hasNeighborSignal(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean bluestone$listen(Level level, BlockPos pos, Operation<Boolean> original) {
        return Signals.test(Channel.of((Block) (Object) this), () -> original.call(level, pos)) || RailPower.along(level, pos, level.getBlockState(pos));
    }

    @WrapMethod(method = "findPoweredRailSignal")
    private boolean bluestone$listenAlongTrack(Level level, BlockPos pos, BlockState state, boolean forward, int searchDepth, Operation<Boolean> original) {
        return Signals.test(Channel.of((Block) (Object) this), () -> original.call(level, pos, state, forward, searchDepth));
    }

    // NeoForge passes power between any two powered rails of the same kind, so stop it at the other channel's rails.
    @Inject(method = "isSameRailWithPower", at = @At("HEAD"), cancellable = true)
    private void bluestone$sameChannel(Level level, BlockPos pos, boolean forward, int searchDepth, RailShape dir, CallbackInfoReturnable<Boolean> cir) {
        if (Channel.of(level.getBlockState(pos).getBlock()) != Channel.of((Block) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
