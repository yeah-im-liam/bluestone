package yeah.im.liam.bluestone.client;

import java.util.Map;
import java.util.WeakHashMap;
//#if MC >= 1.21.2
import net.minecraft.client.renderer.entity.state.EntityRenderState;
//#endif
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import yeah.im.liam.bluestone.block.AttachedRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * How minecarts are drawn turned: eased from one way to the next over a few frames instead of snapping as a cart goes
 * from floor to wall to ceiling, with the riders tilted along with them.
 */
public final class CartTurns {
    private static final Vector3f UP = new Vector3f(0.0F, 1.0F, 0.0F);
    private static final double PIVOT_HEIGHT = 0.375;
    private static final Map<AbstractMinecart, Turn> TURNS = new WeakHashMap<>();

    private CartTurns() {
    }

    private static final class Turn {
        final Quaternionf shown;
        Vec3 pivot;
        long time;
        boolean ours;
        final Quaternionf tilt = new Quaternionf();
        final Vector3f tiltUp = new Vector3f(UP);

        Turn(Quaternionf shown, Vec3 pivot, long time) {
            this.shown = shown;
            this.pivot = pivot;
            this.time = time;
        }
    }

    public record Turned(Quaternionf turn, Vec3 pivot) {
    }

    // A cart drawn the old way: along the rail it is on, from its positions just ahead and behind, as the game's
    // renderer works it out.
    public static Turned railTurn(Vec3 origin, @Nullable Vec3 onRail, @Nullable Vec3 front, @Nullable Vec3 back, float yRot, float xRot) {
        float rotation = yRot;
        Vec3 offset = Vec3.ZERO;
        if (onRail != null && front != null && back != null) {
            offset = new Vec3(onRail.x - origin.x, (front.y + back.y) / 2.0 - origin.y, onRail.z - origin.z);
            Vec3 direction = back.subtract(front);
            if (direction.length() != 0.0) {
                direction = direction.normalize();
                rotation = (float) (Math.atan2(direction.z, direction.x) * 180.0 / Math.PI);
                xRot = (float) (Math.atan(direction.y) * 73.0);
            }
        }
        Quaternionf game = new Quaternionf().rotateY((float) Math.toRadians(180.0F - rotation)).rotateZ((float) Math.toRadians(-xRot));
        return new Turned(game, offset.add(0.0, PIVOT_HEIGHT, 0.0));
    }

    // A cart drawn the new way (the experimental minecarts): turned by its own rotation about its middle.
    public static Turned newTurn(float yRot, float xRot) {
        return new Turned(new Quaternionf().rotateY((float) Math.toRadians(yRot)).rotateZ((float) Math.toRadians(-xRot)), new Vec3(0.0, PIVOT_HEIGHT, 0.0));
    }

    // The turn to draw a cart at origin with: matched to the face of a wall or ceiling rail it is on, eased from the
    // game's own turn, or null to leave it to the game.
    public static @Nullable Turned surfaceTurn(AbstractMinecart cart, Vec3 origin, Turned game) {
        Vec3 center = origin.add(0.0, cart.getBbHeight() / 2.0, 0.0);
        BlockPos pos = BlockPos.containing(center);
        BlockState rail = cart.level().getBlockState(pos);
        Quaternionf surface = null;
        Vec3 surfacePivot = Vec3.ZERO;
        if (rail.getBlock() instanceof AttachedRailBlock) {
            Direction attached = rail.getValue(AttachedRailBlock.ATTACHED);
            Direction along = rail.getValue(AttachedRailBlock.TRAVEL);
            Direction other = AttachedRailBlock.curveEnd(rail);
            Vec3 velocity = cart.getDeltaMovement();
            if (other != null && velocity.dot(Vec3.atLowerCornerOf(other.getUnitVec3i())) > velocity.dot(Vec3.atLowerCornerOf(along.getUnitVec3i()))) {
                along = other;
            }
            Vector3f x = new Vector3f(along.getStepX(), along.getStepY(), along.getStepZ());
            Vector3f y = new Vector3f(-attached.getStepX(), -attached.getStepY(), -attached.getStepZ());
            surface = new Matrix3f(x, y, x.cross(y, new Vector3f())).getNormalizedRotation(new Quaternionf());
            Vec3 face = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(attached.getUnitVec3i()).scale(0.5 - PIVOT_HEIGHT));
            Vec3 pivot = switch (attached.getAxis()) {
                case X -> new Vec3(face.x, center.y, center.z);
                case Y -> new Vec3(center.x, face.y, center.z);
                case Z -> new Vec3(center.x, center.y, face.z);
            };
            surfacePivot = pivot.subtract(origin);
        }
        return turn(cart, surface, surfacePivot, game.turn(), game.pivot());
    }

    public static @Nullable Turned turn(AbstractMinecart cart, @Nullable Quaternionf surface, Vec3 surfacePivot, Quaternionf game, Vec3 gamePivot) {
        Quaternionf goal = surface != null ? surface : game;
        Vec3 goalPivot = surface != null ? surfacePivot : gamePivot;
        long now = System.nanoTime();
        Turn turn = TURNS.get(cart);
        if (turn == null) {
            turn = new Turn(new Quaternionf(goal), goalPivot, now);
            TURNS.put(cart, turn);
        }
        Quaternionf endToEnd = new Quaternionf(goal).rotateY((float) Math.PI);
        if (Math.abs(dot(turn.shown, endToEnd)) > Math.abs(dot(turn.shown, goal))) {
            goal = endToEnd;
        }
        float seconds = Math.min((now - turn.time) / 1.0E9F, 0.25F);
        float step = 1.0F - (float) Math.exp(-CartViewSettings.get().speed.ease * seconds);
        turn.shown.slerp(goal, step);
        turn.pivot = turn.pivot.lerp(goalPivot, step);
        turn.time = now;
        float apart = 2.0F * (float) Math.acos(Math.min(1.0F, Math.abs(dot(turn.shown, goal))));
        turn.ours = surface != null || apart > 0.01F || turn.pivot.distanceToSqr(goalPivot) > 1.0E-4;
        Vector3f up = turn.shown.transform(new Vector3f(UP));
        if (turn.ours) {
            turn.tilt.premul(new Quaternionf().rotationTo(turn.tiltUp, up));
            turn.tiltUp.set(up);
        } else {
            turn.tilt.identity();
            turn.tiltUp.set(UP);
        }
        return turn.ours ? new Turned(new Quaternionf(turn.shown), turn.pivot) : null;
    }

    // Worked out here: the JOML in 26.1 and 26.2 has no Quaternionf.dot.
    private static float dot(Quaternionf a, Quaternionf b) {
        return a.x() * b.x() + a.y() * b.y() + a.z() * b.z() + a.w() * b.w();
    }

    public record Tilt(Quaternionf tilt, Vec3 pivot, Vec3 seat) {
    }

    public static @Nullable Tilt tilt(Entity rider, float partialTicks) {
        if (!(rider.getVehicle() instanceof AbstractMinecart cart)) {
            return null;
        }
        Turn turn = TURNS.get(cart);
        if (turn == null || !turn.ours) {
            return null;
        }
        // Where the game seats the rider (Entity.positionRider); the rider's own offset is a height before 1.20.5.
        //#if MC >= 1.20.5
        Vec3 seat = cart.getPassengerRidingPosition(rider).subtract(rider.getVehicleAttachmentPoint(cart)).subtract(cart.position());
        //#else
        //$$ Vec3 seat = cart.getPassengerRidingPosition(rider).add(0.0, rider.getMyRidingOffset(cart), 0.0).subtract(cart.position());
        //#endif
        return new Tilt(new Quaternionf(turn.tilt), cart.getPosition(partialTicks).add(turn.pivot), cart.getPosition(partialTicks).add(seat));
    }

    //#if MC >= 1.21.2
    public static void ride(Entity rider, EntityRenderState state, float partialTicks) {
        Tilt tilt = tilt(rider, partialTicks);
        if (tilt == null) {
            ((SurfaceRider) state).bluestone$setTilt(null, Vec3.ZERO, Vec3.ZERO);
        } else {
            ((SurfaceRider) state).bluestone$setTilt(tilt.tilt(), tilt.pivot().subtract(state.x, state.y, state.z), tilt.seat().subtract(tilt.pivot()));
        }
    }
    //#endif
}
