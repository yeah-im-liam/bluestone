package yeah.im.liam.bluestone.client;

import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/** The game's camera, which knows the tilt it gave the view this frame (see CameraMixin). */
public interface TiltedCamera {
    @Nullable Quaternionf bluestone$viewTilt();
}
