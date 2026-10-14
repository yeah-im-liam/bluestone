package yeah.im.liam.bluestone.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yeah.im.liam.bluestone.client.CartTurns;

/**
 * The view of a rider in a cart on a wall or ceiling rail turns with the cart: the camera is tilted the way the rider
 * is drawn, from where the game seats it and about the cart's pivot, in first and third person alike.
 */
@Mixin(Camera.class)
abstract class CameraMixin {
    @Shadow
    private @Nullable Entity entity;
    @Shadow
    @Final
    private Quaternionf rotation;
    @Shadow
    @Final
    private Vector3f forwards;
    @Shadow
    @Final
    private Vector3f up;
    @Shadow
    @Final
    private Vector3f left;
    @Shadow
    private int matrixPropertiesDirty;

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    public abstract Vec3 position();

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void bluestone$tilt(float partialTicks, CallbackInfo ci) {
        CartTurns.Tilt tilt = entity != null ? CartTurns.tilt(entity, partialTicks) : null;
        if (tilt == null) {
            return;
        }
        rotation.premul(tilt.tilt());
        forwards.set(0.0F, 0.0F, -1.0F).rotate(rotation);
        up.set(0.0F, 1.0F, 0.0F).rotate(rotation);
        left.set(-1.0F, 0.0F, 0.0F).rotate(rotation);
        matrixPropertiesDirty |= 3;
        // The camera's offset from the rider (its eyes, and the distance back in third person), from the seat.
        Vec3 offset = tilt.seat().add(position().subtract(entity.getPosition(partialTicks))).subtract(tilt.pivot());
        Vector3f turned = new Vector3f((float) offset.x, (float) offset.y, (float) offset.z).rotate(tilt.tilt());
        setPosition(tilt.pivot().add(turned.x, turned.y, turned.z));
    }
}
