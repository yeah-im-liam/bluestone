package yeah.im.liam.bluestone.rail;

import org.jspecify.annotations.Nullable;

/**
 * What a minecart remembers about its ride on wall and ceiling rails.
 */
public interface RailRider {
    SurfaceRails.@Nullable Ride bluestone$ride();

    void bluestone$setRide(SurfaceRails.@Nullable Ride ride);

    int bluestone$cooldown();

    void bluestone$setCooldown(int ticks);

    double bluestone$chainEntry();

    void bluestone$setChainEntry(double speed);
}
