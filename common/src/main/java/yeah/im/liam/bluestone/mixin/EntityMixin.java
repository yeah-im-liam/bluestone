package yeah.im.liam.bluestone.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yeah.im.liam.bluestone.rail.SurfaceRails;

/**
 * A rider of a cart on a wall or ceiling rail sits turned with the cart, so its eyes aren't inside the wall or ceiling
 * (see {@link SurfaceRails#turnedRider}).
 */
@Mixin(Entity.class)
abstract class EntityMixin {
    @Inject(method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V", at = @At("TAIL"))
    private void bluestone$turnedRider(Entity passenger, Entity.MoveFunction moveFunction, CallbackInfo ci) {
        if ((Object) this instanceof AbstractMinecart cart) {
            Vec3 turned = SurfaceRails.turnedRider(cart, passenger);
            if (turned != null) {
                moveFunction.accept(passenger, turned.x, turned.y, turned.z);
            }
        }
    }
}
