package yeah.im.liam.bluestone.mixin;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import yeah.im.liam.bluestone.client.SurfaceRider;

@Mixin(EntityRenderState.class)
abstract class EntityRenderStateMixin implements SurfaceRider {
    @Unique
    private @Nullable Quaternionf bluestone$tilt;
    @Unique
    private Vec3 bluestone$tiltPivot = Vec3.ZERO;
    @Unique
    private Vec3 bluestone$tiltSeat = Vec3.ZERO;

    @Override
    public @Nullable Quaternionf bluestone$tilt() {
        return bluestone$tilt;
    }

    @Override
    public Vec3 bluestone$tiltPivot() {
        return bluestone$tiltPivot;
    }

    @Override
    public Vec3 bluestone$tiltSeat() {
        return bluestone$tiltSeat;
    }

    @Override
    public void bluestone$setTilt(@Nullable Quaternionf tilt, Vec3 pivot, Vec3 seat) {
        bluestone$tilt = tilt;
        bluestone$tiltPivot = pivot;
        bluestone$tiltSeat = seat;
    }
}
