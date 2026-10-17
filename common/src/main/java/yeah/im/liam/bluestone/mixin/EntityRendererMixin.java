package yeah.im.liam.bluestone.mixin;

// Entity render states are new in 1.21.2; MinecartRendererMixin and EntityRenderDispatcherLegacyMixin before.
//#if MC >= 1.21.2
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yeah.im.liam.bluestone.client.CartTurns;

/**
 * Riders of a cart on a wall or ceiling rail are drawn tilted with the cart (see {@link CartTurns}).
 */
@Mixin(EntityRenderer.class)
abstract class EntityRendererMixin {
    @Inject(method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
        at = @At("RETURN"))
    private void bluestone$tilt(Entity entity, float partialTicks, CallbackInfoReturnable<EntityRenderState> cir) {
        CartTurns.ride(entity, cir.getReturnValue(), partialTicks);
    }
}
//#endif
