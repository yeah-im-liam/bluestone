package yeah.im.liam.bluestone.mixin;

// Minecraft 1.20.6 and earlier, which build the view from the camera's yaw and pitch rather than its rotation, so the
// tilt CameraMixin gives the rotation is added here; from 1.21 the view follows the rotation.
//#if MC < 1.21
//$$ import net.minecraft.client.Camera;
//$$ import net.minecraft.client.renderer.GameRenderer;
//$$ import org.joml.Quaternionf;
//$$ import org.spongepowered.asm.mixin.Final;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.Shadow;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import yeah.im.liam.bluestone.client.TiltedCamera;
//#if MC >= 1.20.5
//$$ import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
//$$ import org.joml.Matrix4f;
//#else
//$$ import com.mojang.blaze3d.vertex.PoseStack;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//#endif
//$$
//$$ @Mixin(GameRenderer.class)
//$$ abstract class GameRendererMixin {
//$$     @Shadow
//$$     @Final
//$$     private Camera mainCamera;
//$$
//#if MC >= 1.20.5
//$$     @ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;rotationXYZ(FFF)Lorg/joml/Matrix4f;"))
//$$     private Matrix4f bluestone$tilt(Matrix4f view) {
//$$         Quaternionf tilt = ((TiltedCamera) mainCamera).bluestone$viewTilt();
//$$         return tilt == null ? view : view.rotate(new Quaternionf(tilt).conjugate());
//$$     }
//#else
//$$     // Right after the view's yaw and pitch, before anything reads the view.
//$$     @Inject(method = "renderLevel", at = @At(value = "NEW", target = "(Lorg/joml/Matrix3fc;)Lorg/joml/Matrix3f;"))
//$$     private void bluestone$tilt(float partialTicks, long finishNanos, PoseStack poseStack, CallbackInfo ci) {
//$$         Quaternionf tilt = ((TiltedCamera) mainCamera).bluestone$viewTilt();
//$$         if (tilt != null) {
//$$             poseStack.mulPose(new Quaternionf(tilt).conjugate());
//$$         }
//$$     }
//#endif
//$$ }
//#endif
