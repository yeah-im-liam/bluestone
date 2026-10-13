package yeah.im.liam.bluestone.client;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * Extra render state for a minecart on a wall or ceiling rail: how it is turned, and where it pivots relative to the
 * entity, or null on any other rail.
 */
public interface SurfaceCart {
    @Nullable Quaternionf bluestone$turn();

    Vec3 bluestone$pivot();

    void bluestone$set(@Nullable Quaternionf turn, Vec3 pivot);
}
