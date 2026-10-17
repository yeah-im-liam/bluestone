package yeah.im.liam.bluestone.mixin;

// Minecraft 1.21.1 and earlier, whose minecart renderer works from the entity; AbstractMinecartRendererMixin from 1.21.2.
//#if MC < 1.21.2
//$$ import com.mojang.blaze3d.vertex.PoseStack;
//$$ import net.minecraft.client.renderer.MultiBufferSource;
//$$ import net.minecraft.client.renderer.entity.MinecartRenderer;
//$$ import net.minecraft.util.Mth;
//$$ import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
//$$ import net.minecraft.world.phys.Vec3;
//$$ import org.joml.Matrix3f;
//$$ import org.joml.Matrix4f;
//$$ import org.jspecify.annotations.Nullable;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.Unique;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import org.spongepowered.asm.mixin.injection.Inject;
//$$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$$ import yeah.im.liam.bluestone.client.CartTurns;
//$$
//$$ /**
//$$  * Draws a minecart on a wall or ceiling rail turned to match the rail: the pose the game sets up after its small
//$$  * random offset is replaced, once it has turned the cart, by the turn CartTurns works out.
//$$  */
//$$ @Mixin(MinecartRenderer.class)
//$$ abstract class MinecartRendererMixin {
//$$     @Unique
//$$     private CartTurns.@Nullable Turned bluestone$turned;
//$$     @Unique
//$$     private final Matrix4f bluestone$pose = new Matrix4f();
//$$     @Unique
//$$     private final Matrix3f bluestone$normal = new Matrix3f();
//$$
//$$     @Inject(method = "render(Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
//$$         at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0, shift = At.Shift.AFTER))
//$$     private void bluestone$surface(AbstractMinecart cart, float yRot, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
//$$         Vec3 origin = new Vec3(Mth.lerp(partialTicks, cart.xOld, cart.getX()), Mth.lerp(partialTicks, cart.yOld, cart.getY()), Mth.lerp(partialTicks, cart.zOld, cart.getZ()));
//$$         Vec3 onRail = cart.getPos(origin.x, origin.y, origin.z);
//$$         Vec3 front = onRail == null ? null : cart.getPosOffs(origin.x, origin.y, origin.z, 0.3F);
//$$         Vec3 back = onRail == null ? null : cart.getPosOffs(origin.x, origin.y, origin.z, -0.3F);
//$$         CartTurns.Turned game = CartTurns.railTurn(origin, onRail, front != null ? front : onRail, back != null ? back : onRail, yRot,
//$$             Mth.lerp(partialTicks, cart.xRotO, cart.getXRot()));
//$$         bluestone$turned = CartTurns.surfaceTurn(cart, origin, game);
//$$         bluestone$pose.set(poseStack.last().pose());
//$$         bluestone$normal.set(poseStack.last().normal());
//$$     }
//$$
//$$     @Inject(method = "render(Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
//$$         at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V", ordinal = 1, shift = At.Shift.AFTER))
//$$     private void bluestone$turn(AbstractMinecart cart, float yRot, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
//$$         CartTurns.Turned turned = bluestone$turned;
//$$         bluestone$turned = null;
//$$         if (turned != null) {
//$$             Vec3 pivot = turned.pivot();
//$$             poseStack.last().pose().set(bluestone$pose).translate((float) pivot.x, (float) pivot.y, (float) pivot.z).rotate(turned.turn());
//$$             poseStack.last().normal().set(bluestone$normal).rotate(turned.turn());
//$$         }
//$$     }
//$$ }
//#endif
