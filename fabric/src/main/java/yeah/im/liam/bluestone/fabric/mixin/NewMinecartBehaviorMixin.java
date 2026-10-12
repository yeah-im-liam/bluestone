package yeah.im.liam.bluestone.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yeah.im.liam.bluestone.fabric.Rails;

@Mixin(NewMinecartBehavior.class)
abstract class NewMinecartBehaviorMixin {
    @WrapOperation(method = {"moveAlongTrack", "calculateHaltTrackSpeed", "calculateBoostTrackSpeed"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z"))
    private boolean bluestone$rails(BlockState state, Object block, Operation<Boolean> original) {
        return original.call(state, block) || Rails.alsoBluestone(state, block);
    }
}
