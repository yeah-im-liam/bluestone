package yeah.im.liam.bluestone.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yeah.im.liam.bluestone.fabric.Rails;

@Mixin(AbstractMinecart.class)
abstract class AbstractMinecartMixin {
    // BlockState.is(Block) compiles to is(Object) from 26.1.
    //#if MC >= 26.1
    @WrapOperation(method = "getRedstoneDirection", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z"))
    private boolean bluestone$rails(BlockState state, Object block, Operation<Boolean> original) {
    //#elseif MC >= 1.21.2
    //$$ @WrapOperation(method = "getRedstoneDirection", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z"))
    //$$ private boolean bluestone$rails(BlockState state, Block block, Operation<Boolean> original) {
    //#else
    //$$ // Before 1.21.2 the cart itself checks for powered and activator rails while it moves.
    //$$ @WrapOperation(method = {"tick", "moveAlongTrack"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z"))
    //$$ private boolean bluestone$rails(BlockState state, Block block, Operation<Boolean> original) {
    //#endif
        return original.call(state, block) || Rails.alsoBluestone(state, block);
    }
}
