package yeah.im.liam.bluestone.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yeah.im.liam.bluestone.block.RailPower;

/**
 * The game passes power along only rails of the same block; the game's and sticky powered (or activator) rails of one
 * colour pass it to each other too, as on NeoForge.
 */
@Mixin(PoweredRailBlock.class)
abstract class PoweredRailBlockMixin {
    @WrapOperation(method = "isSameRailWithPower", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z"))
    private boolean bluestone$samePower(BlockState state, Object block, Operation<Boolean> original) {
        return original.call(state, block) || RailPower.samePower(state, ((Block) (Object) this).defaultBlockState());
    }
}
