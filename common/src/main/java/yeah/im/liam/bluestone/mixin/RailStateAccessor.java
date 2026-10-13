package yeah.im.liam.bluestone.mixin;

import net.minecraft.world.level.block.RailState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * For {@link yeah.im.liam.bluestone.rail.WallRails}: the game's check of which connections still lead to a rail.
 */
@Mixin(RailState.class)
public interface RailStateAccessor {
    @Invoker("removeSoftConnections")
    void bluestone$removeSoftConnections();
}
