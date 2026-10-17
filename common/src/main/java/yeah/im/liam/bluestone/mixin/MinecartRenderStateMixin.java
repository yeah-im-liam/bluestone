package yeah.im.liam.bluestone.mixin;

// Entity render states are new in 1.21.2; MinecartRendererMixin and EntityRenderDispatcherLegacyMixin before.
//#if MC >= 1.21.2
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import yeah.im.liam.bluestone.client.SurfaceCart;

@Mixin(MinecartRenderState.class)
abstract class MinecartRenderStateMixin implements SurfaceCart {
    @Unique
    private @Nullable Quaternionf bluestone$turn;
    @Unique
    private Vec3 bluestone$pivot = Vec3.ZERO;

    @Override
    public @Nullable Quaternionf bluestone$turn() {
        return bluestone$turn;
    }

    @Override
    public Vec3 bluestone$pivot() {
        return bluestone$pivot;
    }

    @Override
    public void bluestone$set(@Nullable Quaternionf turn, Vec3 pivot) {
        bluestone$turn = turn;
        bluestone$pivot = pivot;
    }
}
//#endif
