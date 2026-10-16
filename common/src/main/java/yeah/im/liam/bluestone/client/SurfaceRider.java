package yeah.im.liam.bluestone.client;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * Extra render state for an entity riding a cart on a wall or ceiling rail: the tilt it is drawn with, the cart's pivot
 * relative to it, and its seat relative to the pivot; or null when it isn't tilted.
 */
public interface SurfaceRider {
    @Nullable Quaternionf bluestone$tilt();

    Vec3 bluestone$tiltPivot();

    Vec3 bluestone$tiltSeat();

    void bluestone$setTilt(@Nullable Quaternionf tilt, Vec3 pivot, Vec3 seat);
}
