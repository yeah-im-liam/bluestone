package yeah.im.liam.bluestone.fabric.mixin;

// Minecart behaviours are new in 1.21.2; before, AbstractMinecartMixin covers the cart's own movement.
//#if MC >= 1.21.2
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.vehicle.minecart.OldMinecartBehavior;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yeah.im.liam.bluestone.fabric.Rails;

@Mixin(OldMinecartBehavior.class)
abstract class OldMinecartBehaviorMixin {
    // BlockState.is(Block) compiles to is(Object) from 26.1.
    //#if MC >= 26.1
    @WrapOperation(method = {"tick", "moveAlongTrack"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z"))
    private boolean bluestone$rails(BlockState state, Object block, Operation<Boolean> original) {
    //#else
    //$$ @WrapOperation(method = {"tick", "moveAlongTrack"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z"))
    //$$ private boolean bluestone$rails(BlockState state, Block block, Operation<Boolean> original) {
    //#endif
        return original.call(state, block) || Rails.alsoBluestone(state, block);
    }
}
//#endif
