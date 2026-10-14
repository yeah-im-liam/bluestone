package yeah.im.liam.bluestone.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yeah.im.liam.bluestone.block.WallConverterBlock;

/**
 * Pistons don't move blocks with a block entity, so nothing in it is lost. The wall converter's holds nothing, so it
 * moves like any other block.
 */
@Mixin(PistonBaseBlock.class)
abstract class PistonBaseBlockMixin {
    @WrapOperation(method = "isPushable", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;hasBlockEntity()Z"))
    private static boolean bluestone$pushWallConverter(BlockState state, Operation<Boolean> original) {
        return original.call(state) && !(state.getBlock() instanceof WallConverterBlock);
    }
}
