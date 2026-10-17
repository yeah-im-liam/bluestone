package yeah.im.liam.bluestone.mixin;

// Entity render states are new in 1.21.2; MinecartRendererMixin and EntityRenderDispatcherLegacyMixin before.
//#if MC >= 1.21.2
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.AbstractMinecartRenderer;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yeah.im.liam.bluestone.client.CartTurns;
import yeah.im.liam.bluestone.client.SurfaceCart;

/**
 * Draws a minecart on a wall or ceiling rail turned to match the rail: its floor against the rail's face and its length
 * along the rail, the way the game draws a cart on a floor rail.
 */
@Mixin(AbstractMinecartRenderer.class)
abstract class AbstractMinecartRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;F)V",
        at = @At("TAIL"))
    private void bluestone$surface(AbstractMinecart cart, MinecartRenderState state, float partialTicks, CallbackInfo ci) {
        Vec3 origin = state.isNewRender && state.renderPos != null ? state.renderPos : new Vec3(state.x, state.y, state.z);
        CartTurns.Turned game = state.isNewRender ? CartTurns.newTurn(state.yRot, state.xRot)
            : CartTurns.railTurn(origin, state.posOnRail, state.frontPos, state.backPos, state.yRot, state.xRot);
        CartTurns.Turned turned = CartTurns.surfaceTurn(cart, origin, game);
        ((SurfaceCart) state).bluestone$set(turned != null ? turned.turn() : null, turned != null ? turned.pivot() : Vec3.ZERO);
    }

    // The cart is drawn in submit from 1.21.9 and in render before.
    //#if MC >= 1.21.9
    @WrapOperation(method = "submit", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/entity/AbstractMinecartRenderer;newRender(Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    //#else
    //$$ @WrapOperation(method = "render", at = @At(value = "INVOKE",
    //$$     target = "Lnet/minecraft/client/renderer/entity/AbstractMinecartRenderer;newRender(Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    //#endif
    private void bluestone$turnNew(MinecartRenderState state, PoseStack poseStack, Operation<Void> original) {
        if (!bluestone$turn(state, poseStack)) {
            original.call(state, poseStack);
        }
    }

    // The cart is drawn in submit from 1.21.9 and in render before.
    //#if MC >= 1.21.9
    @WrapOperation(method = "submit", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/entity/AbstractMinecartRenderer;oldRender(Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    //#else
    //$$ @WrapOperation(method = "render", at = @At(value = "INVOKE",
    //$$     target = "Lnet/minecraft/client/renderer/entity/AbstractMinecartRenderer;oldRender(Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    //#endif
    private void bluestone$turnOld(MinecartRenderState state, PoseStack poseStack, Operation<Void> original) {
        if (!bluestone$turn(state, poseStack)) {
            original.call(state, poseStack);
        }
    }

    @Unique
    private static boolean bluestone$turn(MinecartRenderState state, PoseStack poseStack) {
        SurfaceCart surface = (SurfaceCart) state;
        Quaternionf turn = surface.bluestone$turn();
        if (turn == null) {
            return false;
        }
        Vec3 pivot = surface.bluestone$pivot();
        poseStack.translate(pivot.x, pivot.y, pivot.z);
        poseStack.mulPose(new Matrix4f().rotation(turn));
        return true;
    }
}
//#endif
