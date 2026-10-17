package yeah.im.liam.bluestone.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
//#if MC >= 1.21.9
import net.minecraft.client.renderer.SubmitNodeCollector;
//#else
//$$ import net.minecraft.client.renderer.MultiBufferSource;
//#endif
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
//#if MC >= 26.1
import net.minecraft.client.renderer.state.level.CameraRenderState;
//#elseif MC >= 1.21.9
//$$ import net.minecraft.client.renderer.state.CameraRenderState;
//#endif
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yeah.im.liam.bluestone.client.SurfaceRider;

/**
 * Draws a rider sitting where the game seats it, tilted about its cart's pivot (see {@link
 * yeah.im.liam.bluestone.client.CartTurns}).
 */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherMixin {
    //#if MC >= 1.21.9
    //#if MC >= 26.1
    @WrapOperation(method = "submit", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
    //#else
    //$$ @WrapOperation(method = "submit", at = @At(value = "INVOKE",
    //$$     target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V"))
    //#endif
    private void bluestone$tilt(EntityRenderer<?, ?> renderer, EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                CameraRenderState camera, Operation<Void> original) {
        tilted(state, poseStack, () -> original.call(renderer, state, poseStack, collector, camera));
    }
    //#else
    //$$ @WrapOperation(method = "render(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/EntityRenderer;)V", at = @At(value = "INVOKE",
    //$$     target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    //$$ private void bluestone$tilt(EntityRenderer<?, ?> renderer, EntityRenderState state, PoseStack poseStack, MultiBufferSource buffers, int light,
    //$$                             Operation<Void> original) {
    //$$     tilted(state, poseStack, () -> original.call(renderer, state, poseStack, buffers, light));
    //$$ }
    //#endif

    private static void tilted(EntityRenderState state, PoseStack poseStack, Runnable draw) {
        Quaternionf tilt = ((SurfaceRider) state).bluestone$tilt();
        if (tilt == null) {
            draw.run();
            return;
        }
        Vec3 pivot = ((SurfaceRider) state).bluestone$tiltPivot();
        Vec3 seat = ((SurfaceRider) state).bluestone$tiltSeat();
        poseStack.pushPose();
        poseStack.translate(pivot.x, pivot.y, pivot.z);
        poseStack.mulPose(new Matrix4f().rotation(tilt));
        poseStack.translate(seat.x, seat.y, seat.z);
        draw.run();
        poseStack.popPose();
    }
}
