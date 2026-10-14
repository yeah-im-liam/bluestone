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
     * The speed the cart goes on at when it leaves the chain rails it is on (what it came on at, plus what it would have
     * gained going down since), or NaN when it isn't on chain rails.
     */
    double bluestone$chainEntry();

    void bluestone$setChainEntry(double speed);
}
