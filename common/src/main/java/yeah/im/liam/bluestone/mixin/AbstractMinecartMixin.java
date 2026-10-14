package yeah.im.liam.bluestone.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import yeah.im.liam.bluestone.rail.RailRider;
import yeah.im.liam.bluestone.rail.SurfaceRails;

/**
 * Lets {@link SurfaceRails} move carts on wall and ceiling rails in place of the game's movement, for both the old
 * and the experimental minecart behaviour.
 */
@Mixin(AbstractMinecart.class)
abstract class AbstractMinecartMixin implements RailRider {
    @Unique
    private SurfaceRails.@Nullable Ride bluestone$ride;
    @Unique
    private int bluestone$cooldown;
    @Unique
    private double bluestone$chainEntry = Double.NaN;

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/vehicle/minecart/MinecartBehavior;tick()V"))
    private void bluestone$surfaceRails(MinecartBehavior behavior, Operation<Void> original) {
        if (!SurfaceRails.tick((AbstractMinecart) (Object) this)) {
            original.call(behavior);
        }
    }

    @Override
    public SurfaceRails.@Nullable Ride bluestone$ride() {
        return bluestone$ride;
    }

    @Override
    public void bluestone$setRide(SurfaceRails.@Nullable Ride ride) {
        bluestone$ride = ride;
    }

    @Override
    public int bluestone$cooldown() {
        return bluestone$cooldown;
    }

    @Override
    public void bluestone$setCooldown(int ticks) {
        bluestone$cooldown = ticks;
    }

    @Override
    public double bluestone$chainEntry() {
        return bluestone$chainEntry;
    }

    @Override
    public void bluestone$setChainEntry(double speed) {
        bluestone$chainEntry = speed;
    }
}
