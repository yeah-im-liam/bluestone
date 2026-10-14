package yeah.im.liam.bluestone.rail;

import org.jspecify.annotations.Nullable;

/**
 * What a minecart remembers about its ride on wall and ceiling rails. It isn't saved: a cart loaded halfway up a wall
 * finds its track again from where it is.
 */
public interface RailRider {
    SurfaceRails.@Nullable Ride bluestone$ride();

    void bluestone$setRide(SurfaceRails.@Nullable Ride ride);

    int bluestone$cooldown();

    void bluestone$setCooldown(int ticks);

    /**
     * The speed the cart came onto chain gear at, or NaN when it isn't on chain gear.
     */
    double bluestone$chainEntry();

    void bluestone$setChainEntry(double speed);
}
