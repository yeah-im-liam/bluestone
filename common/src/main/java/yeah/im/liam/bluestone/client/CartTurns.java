package yeah.im.liam.bluestone.client;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * How minecarts are drawn turned: eased from one way to the next over a few frames instead of snapping as a cart goes
 * from floor to wall to ceiling, with the riders tilted along with them.
 */
public final class CartTurns {
    private static final Vector3f UP = new Vector3f(0.0F, 1.0F, 0.0F);
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
        Vec3 seat = cart.getPassengerRidingPosition(rider).subtract(rider.getVehicleAttachmentPoint(cart)).subtract(cart.position());
        return new Tilt(new Quaternionf(turn.tilt), cart.getPosition(partialTicks).add(turn.pivot), cart.getPosition(partialTicks).add(seat));
    }

    public static void ride(Entity rider, EntityRenderState state, float partialTicks) {
        Tilt tilt = tilt(rider, partialTicks);
        if (tilt == null) {
            ((SurfaceRider) state).bluestone$setTilt(null, Vec3.ZERO, Vec3.ZERO);
        } else {
            ((SurfaceRider) state).bluestone$setTilt(tilt.tilt(), tilt.pivot().subtract(state.x, state.y, state.z), tilt.seat().subtract(tilt.pivot()));
        }
    }
}
