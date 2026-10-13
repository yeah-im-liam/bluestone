package yeah.im.liam.bluestone.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.AbstractMinecartRenderer;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yeah.im.liam.bluestone.block.AttachedRailBlock;
import yeah.im.liam.bluestone.client.SurfaceCart;

/**
 * Draws a minecart on a wall or ceiling rail turned to match the rail: its floor against the rail's face and its
 * length along the rail, the way the game draws a cart on a floor rail.
 */
@Mixin(AbstractMinecartRenderer.class)
abstract class AbstractMinecartRendererMixin {
    // The game turns the cart about a point this far off the rail.
    @Unique
    private static final double PIVOT_HEIGHT = 0.375;

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;F)V",
        at = @At("TAIL"))
    private void bluestone$surface(AbstractMinecart cart, MinecartRenderState state, float partialTicks, CallbackInfo ci) {
        SurfaceCart surface = (SurfaceCart) state;
        Vec3 origin = state.isNewRender && state.renderPos != null ? state.renderPos : new Vec3(state.x, state.y, state.z);
        Vec3 center = origin.add(0.0, cart.getBbHeight() / 2.0, 0.0);
        BlockPos pos = BlockPos.containing(center);
        BlockState rail = cart.level().getBlockState(pos);
        if (!(rail.getBlock() instanceof AttachedRailBlock)) {
            surface.bluestone$set(null, Vec3.ZERO);
            return;
        }
        Direction attached = rail.getValue(AttachedRailBlock.ATTACHED);
        // Along the rail: towards the end it is moving to, or the way the rail was placed.
        Direction along = rail.getValue(AttachedRailBlock.TRAVEL);
        Direction other = AttachedRailBlock.curveEnd(rail);
        Vec3 velocity = cart.getDeltaMovement();
        if (other != null && velocity.dot(Vec3.atLowerCornerOf(other.getUnitVec3i())) > velocity.dot(Vec3.atLowerCornerOf(along.getUnitVec3i()))) {
            along = other;
        }
        Vector3f x = new Vector3f(along.getStepX(), along.getStepY(), along.getStepZ());
        Vector3f y = new Vector3f(-attached.getStepX(), -attached.getStepY(), -attached.getStepZ());
        Quaternionf turn = new Matrix3f(x, y, x.cross(y, new Vector3f())).getNormalizedRotation(new Quaternionf());
        // The pivot sits the game's height off the face the rail is on, wherever the cart is along it.
        Vec3 face = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(attached.getUnitVec3i()).scale(0.5 - PIVOT_HEIGHT));
        Vec3 pivot = switch (attached.getAxis()) {
            case X -> new Vec3(face.x, center.y, center.z);
            case Y -> new Vec3(center.x, face.y, center.z);
            case Z -> new Vec3(center.x, center.y, face.z);
        };
        surface.bluestone$set(turn, pivot.subtract(origin));
    }

    @WrapOperation(method = "submit", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/entity/AbstractMinecartRenderer;newRender(Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void bluestone$turnNew(MinecartRenderState state, PoseStack poseStack, Operation<Void> original) {
        if (!bluestone$turn(state, poseStack)) {
            original.call(state, poseStack);
        }
    }

    @WrapOperation(method = "submit", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/entity/AbstractMinecartRenderer;oldRender(Lnet/minecraft/client/renderer/entity/state/MinecartRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
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
