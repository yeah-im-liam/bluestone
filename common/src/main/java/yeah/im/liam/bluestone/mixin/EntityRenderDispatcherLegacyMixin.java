package yeah.im.liam.bluestone.mixin;

// Minecraft 1.21.1 and earlier, whose entity renderers work from the entity; EntityRenderDispatcherMixin from 1.21.2.
//#if MC < 1.21.2
//$$ import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
//$$ import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
//$$ import com.mojang.blaze3d.vertex.PoseStack;
//$$ import net.minecraft.client.renderer.MultiBufferSource;
//$$ import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
//$$ import net.minecraft.client.renderer.entity.EntityRenderer;
//$$ import net.minecraft.world.entity.Entity;
//$$ import net.minecraft.world.phys.Vec3;
//$$ import org.joml.Matrix4f;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import yeah.im.liam.bluestone.client.CartTurns;
//$$
//$$ /**
//$$  * Draws a rider sitting where the game seats it, tilted about its cart's pivot (see {@link CartTurns}).
//$$  */
//$$ @Mixin(EntityRenderDispatcher.class)
//$$ abstract class EntityRenderDispatcherLegacyMixin {
//$$     @WrapOperation(method = "render", at = @At(value = "INVOKE",
//$$         target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
//$$     private void bluestone$tilt(EntityRenderer<?> renderer, Entity entity, float yRot, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light,
//$$                                 Operation<Void> original) {
//$$         CartTurns.Tilt tilt = CartTurns.tilt(entity, partialTicks);
//$$         if (tilt == null) {
//$$             original.call(renderer, entity, yRot, partialTicks, poseStack, buffers, light);
//$$             return;
//$$         }
//$$         Vec3 pivot = tilt.pivot().subtract(entity.getPosition(partialTicks));
//$$         Vec3 seat = tilt.seat().subtract(tilt.pivot());
//$$         poseStack.pushPose();
//$$         poseStack.translate(pivot.x, pivot.y, pivot.z);
//$$         poseStack.mulPose(tilt.tilt());
//$$         poseStack.translate(seat.x, seat.y, seat.z);
//$$         original.call(renderer, entity, yRot, partialTicks, poseStack, buffers, light);
//$$         poseStack.popPose();
//$$     }
//$$ }
//#endif
