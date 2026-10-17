package yeah.im.liam.bluestone.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
//#if MC >= 26.1
//#elseif MC >= 1.21.11
//$$ import net.minecraft.world.level.Level;
//#else
//$$ import net.minecraft.world.level.BlockGetter;
//#endif
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yeah.im.liam.bluestone.client.CartTurns;
import yeah.im.liam.bluestone.client.CartViewSettings;
import yeah.im.liam.bluestone.client.TiltedCamera;

/**
 * The view of a rider in a cart on a wall or ceiling rail turns with the cart, tilted the way the rider is drawn and
 * about the cart's pivot: in first person, and in third person if the settings say so (CartViewSettings).
 */
@Mixin(Camera.class)
abstract class CameraMixin implements TiltedCamera {
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
    //#if MC >= 26.1
    @Shadow
    private int matrixPropertiesDirty;
    //#endif
    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setPosition(Vec3 position);

    // The camera's position getter is position() from 1.21.9 and getPosition() before.
    //#if MC >= 1.21.9
    @Shadow
    public abstract Vec3 position();
    //#else
    //$$ @Shadow
    //$$ public abstract Vec3 getPosition();
    //#endif

    @Unique
    private @Nullable Quaternionf bluestone$viewTilt;

    @Override
    public @Nullable Quaternionf bluestone$viewTilt() {
        return bluestone$viewTilt;
    }

    @Unique
    private Vec3 bluestone$position() {
        //#if MC >= 1.21.9
        return position();
        //#else
        //$$ return getPosition();
        //#endif
    }

    //#if MC >= 26.1
    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void bluestone$tilt(float partialTicks, CallbackInfo ci) {
    //#elseif MC >= 1.21.11
    //$$ @Inject(method = "setup", at = @At("TAIL"))
    //$$ private void bluestone$tilt(Level level, Entity cameraEntity, boolean detachedCamera, boolean mirrored, float partialTicks, CallbackInfo ci) {
    //#else
    //$$ @Inject(method = "setup", at = @At("TAIL"))
    //$$ private void bluestone$tilt(BlockGetter level, Entity cameraEntity, boolean detachedCamera, boolean mirrored, float partialTicks, CallbackInfo ci) {
    //#endif
        CartViewSettings settings = CartViewSettings.get();
        CartTurns.Tilt tilt = entity != null && (detached ? settings.thirdPerson : settings.firstPerson) ? CartTurns.tilt(entity, partialTicks) : null;
        bluestone$viewTilt = tilt == null ? null : tilt.tilt();
        if (tilt == null) {
            return;
        }
        rotation.premul(tilt.tilt());
        forwards.set(0.0F, 0.0F, -1.0F).rotate(rotation);
        up.set(0.0F, 1.0F, 0.0F).rotate(rotation);
        left.set(-1.0F, 0.0F, 0.0F).rotate(rotation);
        //#if MC >= 26.1
        matrixPropertiesDirty |= 3;
        //#endif
        Vec3 offset = tilt.seat().add(bluestone$position().subtract(entity.getPosition(partialTicks))).subtract(tilt.pivot());
        Vector3f turned = new Vector3f((float) offset.x, (float) offset.y, (float) offset.z).rotate(tilt.tilt());
        setPosition(tilt.pivot().add(turned.x, turned.y, turned.z));
    }
}
