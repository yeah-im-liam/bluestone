package yeah.im.liam.bluestone.client;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * Extra render state for an entity riding a cart on a wall or ceiling rail: the tilt it is drawn with, and the
 * cart's pivot relative to it, or null when it isn't tilted.
 */
public interface SurfaceRider {
    @Nullable Quaternionf bluestone$tilt();

    Vec3 bluestone$tiltPivot();

    void bluestone$setTilt(@Nullable Quaternionf tilt, Vec3 pivot);
}
