package yeah.im.liam.bluestone.rail;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.block.AttachedRailBlock;
import yeah.im.liam.bluestone.block.AttachedRailBlock.Kind;
import yeah.im.liam.bluestone.block.ChainGearRailBlock;

/**
 * Minecart movement on rails stuck to walls and ceilings. The game moves carts on floor rails as usual; once a cart
 * reaches a wall or ceiling rail, this takes over. The cart runs along each rail's middle line and turns at inside
 * corners (track running into a wall carries on up it) and outside corners (track running over an edge carries on
 * down the other side). Climbing slows a cart and coming down speeds it up; a cart that stops climbing rolls back. On
 * a ceiling a cart needs {@link #MIN_CEILING_SPEED} to stay on. Powered chain gear rails move carts at
 * {@link #CHAIN_SPEED} the way they were placed and unpowered ones hold them still; powered rails speed carts up and
 * unpowered ones brake them to a stop. Back on a floor rail, the game takes over again. Floor rails meeting a wall can
 * be straight or curved.
 */
public final class SurfaceRails {
    public static final double CHAIN_SPEED = 0.3;
    public static final double MIN_CEILING_SPEED = 0.2;
    // The minecart's own gravity, a little less drag than on the floor, and the game's powered rail boost.
    private static final double GRAVITY = 0.04;
    private static final double DRAG = 0.995;
    private static final double BOOST = 0.06;
    private static final double MAX_SPEED = 1.0;
    // Ticks after falling off before a cart can be picked up by a wall or ceiling rail again.
    private static final int COOLDOWN = 10;

    private SurfaceRails() {
    }

    /**
     * A piece of track: where it is, the face it sits on, the axis carts run along, what kind of rail it is, whether
     * it is powered, and the way it was placed (the way chain gear drives).
     */
    public record Track(BlockPos pos, Direction attached, Direction.Axis axis, Kind kind, boolean powered, @Nullable Direction travel) {
        boolean floor() {
            return attached == Direction.DOWN;
        }

        // The way a powered chain gear rail drives carts, or null.
        @Nullable Direction drive() {
            return kind == Kind.CHAIN && powered ? travel : null;
        }

        // Where the middle of a cart of the given height is when it sits on this piece of track.
        Vec3 point(double height) {
            double y = switch (attached) {
                case DOWN -> pos.getY() + height / 2.0;
                case UP -> pos.getY() + 1.0 - height / 2.0;
                default -> pos.getY() + 0.5;
            };
            return new Vec3(pos.getX() + 0.5, y, pos.getZ() + 0.5);
        }
    }

    /**
     * The next piece of track and the way a cart goes on from it.
     */
    record Step(Track track, Direction exit) {
    }

    /**
     * Where a cart is going: from one piece of track, which it left going {@code fromExit}, to the next, which it
     * leaves going {@code exit}.
     */
    public record Ride(Track from, Direction fromExit, Track to, Direction exit, double speed) {
        Ride reversed() {
            return new Ride(to, exit.getOpposite(), from, fromExit.getOpposite(), speed);
        }
    }

    public static @Nullable Track track(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof AttachedRailBlock rail) {
            Direction travel = state.getValue(AttachedRailBlock.TRAVEL);
            return new Track(pos, state.getValue(AttachedRailBlock.ATTACHED), travel.getAxis(), rail.kind(), state.getValue(AttachedRailBlock.POWERED), travel);
        }
        if (state.getBlock() instanceof BaseRailBlock rail && BaseRailBlock.isRail(state)) {
            RailShape shape = state.getValue(rail.getShapeProperty());
            Direction.Axis axis = shape == RailShape.NORTH_SOUTH ? Direction.Axis.Z : shape == RailShape.EAST_WEST ? Direction.Axis.X : null;
            if (axis == null) {
                return null;
            }
            if (state.getBlock() instanceof ChainGearRailBlock) {
                return new Track(pos, Direction.DOWN, axis, Kind.CHAIN, state.getValue(ChainGearRailBlock.POWERED), ChainGearRailBlock.drive(state));
            }
            return new Track(pos, Direction.DOWN, axis, Kind.PLAIN, false, null);
        }
        return null;
    }

    static @Nullable Step next(Level level, Track from, Direction exit) {
        return next(level, from, exit, true);
    }

    /**
     * Whether track carries on from {@code from} going {@code exit}. Unless {@code strict}, track there that runs the
     * wrong way counts too: it could turn to join.
     */
    public static boolean joins(Level level, Track from, Direction exit, boolean strict) {
        return next(level, from, exit, strict) != null;
    }

    private static @Nullable Step next(Level level, Track from, Direction exit, boolean strict) {
        Direction attached = from.attached();
        BlockPos ahead = from.pos().relative(exit);
        if (level.getBlockState(ahead).isFaceSturdy(level, ahead, exit.getOpposite())) {
            // Inside corner: the track carries on up the block in the way.
            BlockPos upPos = from.pos().relative(attached.getOpposite());
            if (exit == Direction.DOWN) {
                return onFloor(level, upPos, attached, strict);
            }
            Track up = track(level, upPos);
            return up != null && up.attached() == exit && (!strict || up.axis() == attached.getAxis()) ? new Step(up, attached.getOpposite()) : null;
        }
        // Down a wall onto the floor rail below it.
        if (exit == Direction.DOWN) {
            Step floor = onFloor(level, ahead, attached, strict);
            if (floor != null) {
                return floor;
            }
        }
        Track next = track(level, ahead);
        if (next != null && next.attached() == attached && (!strict || next.axis() == exit.getAxis())) {
            return new Step(next, exit);
        }
        // Inside corner from the other side: the track ahead is on the block it runs into.
        if (next != null && next.attached() == exit && (!strict || next.axis() == attached.getAxis())) {
            return new Step(next, attached.getOpposite());
        }
        // Outside corner: around the edge of the block the track sits on, onto its top going up a wall.
        if (exit == Direction.UP) {
            return onFloor(level, ahead.relative(attached), attached.getOpposite(), strict);
        }
        Track around = track(level, ahead.relative(attached));
        if (around != null && around.attached() == exit.getOpposite() && (!strict || around.axis() == attached.getAxis())) {
            return new Step(around, attached);
        }
        return null;
    }

    // A floor rail with an end pointing back the way the cart comes, straight or curved, and the end the cart leaves by.
    // Unless strict, any level floor rail will do: it could turn to join.
    private static @Nullable Step onFloor(Level level, BlockPos pos, Direction back, boolean strict) {
        List<Direction> ends = levelEnds(level, pos, level.getBlockState(pos));
        if (ends.isEmpty() || strict && !ends.contains(back)) {
            return null;
        }
        Direction out = !ends.contains(back) ? back.getOpposite() : ends.get(0) == back ? ends.get(1) : ends.get(0);
        Track track = track(level, pos);
        if (track == null || track.axis() != out.getAxis()) {
            track = new Track(pos, Direction.DOWN, out.getAxis(), Kind.PLAIN, false, null);
        }
        return new Step(track, out);
    }

    // The two ends of a floor rail: straight, curved, or a slope up to a wall rail; none for anything else.
    private static List<Direction> levelEnds(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof BaseRailBlock rail) || !BaseRailBlock.isRail(state)) {
            return List.of();
        }
        RailShape shape = state.getValue(rail.getShapeProperty());
        if (shape.isSlope()) {
            List<Direction> ends = WallRails.ends(shape);
            return WallRails.slopesUp(level, pos, ends.getLast()) ? ends : List.of();
        }
        return WallRails.level(shape);
    }

    private static Vec3 unit(Direction direction) {
        return new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    /**
     * Runs before the game's minecart movement each tick; returns true when it moved the cart itself.
     */
    public static boolean tick(AbstractMinecart cart) {
        if (!(cart.level() instanceof ServerLevel level)) {
            return false;
        }
        RailRider rider = (RailRider) cart;
        if (rider.bluestone$cooldown() > 0) {
            rider.bluestone$setCooldown(rider.bluestone$cooldown() - 1);
            return false;
        }
        Ride ride = rider.bluestone$ride();
        if (ride == null) {
            ride = start(level, cart);
            if (ride == null) {
                driveOnFloor(level, cart);
                return false;
            }
        }
        double height = cart.getBbHeight();
        Vec3 pos = cart.position().add(0.0, height / 2.0, 0.0);
        // The track the cart is on now, fresh from the world so power changes count straight away. Turning from a floor
        // rail up onto a wall or ceiling rail counts as being on the new rail.
        BlockPos onPos = ride.from().floor() && !ride.to().floor() || ride.to().pos().equals(BlockPos.containing(pos)) ? ride.to().pos() : ride.from().pos();
        Track on = track(level, onPos);
        if (on == null) {
            rider.bluestone$setRide(null);
            return false;
        }
        Vec3 heading = heading(pos, ride, height);
        double speed = ride.speed();
        boolean hold = false;
        switch (on.kind()) {
            case CHAIN -> {
                if (on.powered()) {
                    if (heading.dot(unit(on.travel())) < 0.0) {
                        ride = ride.reversed();
                        heading = heading(pos, ride, height);
                    }
                    speed = CHAIN_SPEED;
                } else {
                    hold = true;
                }
            }
            case POWERED -> {
                if (on.powered()) {
                    if (speed < 0.01 && heading.dot(unit(on.travel())) < 0.0) {
                        ride = ride.reversed();
                        heading = heading(pos, ride, height);
                    }
                    speed = (speed + BOOST - GRAVITY * heading.y) * DRAG;
                } else {
                    speed *= 0.5;
                    hold = speed < 0.03;
                }
            }
            default -> speed = (speed - GRAVITY * heading.y) * DRAG;
        }
        if (hold) {
            rider.bluestone$setRide(new Ride(ride.from(), ride.fromExit(), ride.to(), ride.exit(), 0.0));
            place(cart, pos, height, Vec3.ZERO);
            affect(level, cart, pos);
            return true;
        }
        if (on.kind() != Kind.CHAIN && ride.from().attached() == Direction.UP && ride.to().attached() == Direction.UP && speed < MIN_CEILING_SPEED) {
            return fallOff(cart, rider, heading.scale(Math.max(speed, 0.0)));
        }
        if (speed < 0.0) {
            ride = ride.reversed();
            speed = -speed;
            heading = heading(pos, ride, height);
        }
        speed = Math.min(speed, MAX_SPEED);

        double remaining = speed;
        for (int guard = 0; guard < 16 && remaining > 1.0E-9; guard++) {
            Vec3 target = ride.to().point(height);
            Vec3 delta = target.subtract(pos);
            double distance = delta.length();
            if (distance > remaining) {
                heading = delta.scale(1.0 / distance);
                pos = pos.add(heading.scale(remaining));
                break;
            }
            pos = target;
            remaining -= distance;
            Track at = ride.to();
            Direction exit = at.drive() != null ? at.drive() : ride.exit();
            if (at.floor() && exit.getAxis().isHorizontal()) {
                // Back on a floor rail: the game carries on from here.
                place(cart, pos, height, unit(exit).scale(speed));
                rider.bluestone$setRide(null);
                return true;
            }
            Step step = next(level, at, exit);
            if (step == null) {
                if (exit == Direction.UP) {
                    // Out of track going up: stop, and roll back next tick.
                    ride = new Ride(at, exit, at, exit, 0.0);
                    speed = 0.0;
                    heading = unit(exit);
                    break;
                }
                place(cart, pos, height, unit(exit).scale(speed));
                return fallOff(cart, rider, unit(exit).scale(speed));
            }
            ride = new Ride(at, exit, step.track(), step.exit(), speed);
            heading = heading(pos, ride, height);
        }
        rider.bluestone$setRide(new Ride(ride.from(), ride.fromExit(), ride.to(), ride.exit(), speed));
        place(cart, pos, height, heading.scale(speed));
        affect(level, cart, pos);
        return true;
    }

    // Detector rails notice the cart, and activator rails activate it.
    private static void affect(ServerLevel level, AbstractMinecart cart, Vec3 center) {
        BlockPos pos = BlockPos.containing(center);
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof AttachedRailBlock rail) {
            if (rail.kind() == Kind.DETECTOR) {
                rail.detect(level, pos, state);
            } else if (rail.kind() == Kind.ACTIVATOR) {
                cart.activateMinecart(level, pos.getX(), pos.getY(), pos.getZ(), state.getValue(AttachedRailBlock.POWERED));
            }
        }
    }

    // Which way the cart is going: towards the next point, or on along the track once there.
    private static Vec3 heading(Vec3 pos, Ride ride, double height) {
        Vec3 delta = ride.to().point(height).subtract(pos);
        return delta.lengthSqr() > 1.0E-12 ? delta.normalize() : unit(ride.exit());
    }

    private static void place(AbstractMinecart cart, Vec3 center, double height, Vec3 velocity) {
        cart.setPos(center.x, center.y - height / 2.0, center.z);
        cart.setDeltaMovement(velocity);
        cart.resetFallDistance();
        double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (horizontal > 1.0E-4) {
            cart.setYRot((float) (Math.atan2(velocity.z, velocity.x) * 180.0 / Math.PI));
        }
        if (velocity.lengthSqr() > 1.0E-8) {
            cart.setXRot((float) (-Math.atan2(velocity.y, horizontal) * 180.0 / Math.PI));
        }
    }

    private static boolean fallOff(AbstractMinecart cart, RailRider rider, Vec3 velocity) {
        cart.setDeltaMovement(velocity);
        rider.bluestone$setRide(null);
        rider.bluestone$setCooldown(COOLDOWN);
        return false;
    }

    // Picks up a cart that is on a wall or ceiling rail, or about to leave a floor rail for one.
    private static @Nullable Ride start(Level level, AbstractMinecart cart) {
        double height = cart.getBbHeight();
        Vec3 center = cart.position().add(0.0, height / 2.0, 0.0);
        Vec3 velocity = cart.getDeltaMovement();
        Track here = track(level, BlockPos.containing(center));
        if (here != null && !here.floor()) {
            Vec3 axis = unit(Direction.fromAxisAndDirection(here.axis(), Direction.AxisDirection.POSITIVE));
            double along = velocity.dot(axis);
            Direction exit;
            if (here.kind() == Kind.CHAIN || here.kind() == Kind.POWERED && here.powered() && Math.abs(along) < 0.01) {
                exit = here.travel();
            } else if (Math.abs(along) > 1.0E-4) {
                exit = Direction.fromAxisAndDirection(here.axis(), along > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
            } else if (here.axis() == Direction.Axis.Y) {
                exit = Direction.DOWN;
            } else if (here.attached() != Direction.UP) {
                // Standing still on a sideways wall rail: it stays where it is.
                exit = here.travel();
            } else {
                return null;
            }
            double speed = here.drive() != null ? CHAIN_SPEED : Math.abs(along);
            if (here.attached() == Direction.UP && here.kind() != Kind.CHAIN && speed < MIN_CEILING_SPEED) {
                return null;
            }
            Vec3 point = here.point(height);
            Vec3 onLine = point.add(axis.scale(center.subtract(point).dot(axis)));
            cart.setPos(onLine.x, onLine.y - height / 2.0, onLine.z);
            if (point.subtract(onLine).dot(unit(exit)) > 0.0) {
                return new Ride(here, exit, here, exit, speed);
            }
            Step step = next(level, here, exit);
            return step == null ? new Ride(here, exit, here, exit, speed) : new Ride(here, exit, step.track(), step.exit(), speed);
        }
        BlockPos railPos = cart.getCurrentBlockPosOrRailBelow();
        Track floor = track(level, railPos);
        if (floor == null) {
            return startOnCurve(level, cart, railPos, center, velocity);
        }
        if (!floor.floor() || floor.kind() == Kind.CHAIN && !floor.powered()) {
            return null;
        }
        Vec3 axis = unit(Direction.fromAxisAndDirection(floor.axis(), Direction.AxisDirection.POSITIVE));
        double along = velocity.dot(axis);
        if (floor.drive() == null && Math.abs(along) < 1.0E-4) {
            return null;
        }
        Direction exit = floor.drive() != null ? floor.drive()
            : Direction.fromAxisAndDirection(floor.axis(), along > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
        double speed = floor.drive() != null ? CHAIN_SPEED : Math.abs(along);
        Vec3 point = floor.point(height);
        // Only once the cart reaches the middle of the floor rail, where the track turns.
        if (center.subtract(point).dot(unit(exit)) < -speed - 0.01) {
            return null;
        }
        Step step = next(level, floor, exit);
        if (step == null || step.track().floor()) {
            return null;
        }
        cart.setPos(point.x, point.y - height / 2.0, point.z);
        return new Ride(floor, exit, step.track(), step.exit(), speed);
    }

    // A cart on a curved floor rail, or a slope up to a wall rail, leaves by the end it didn't come in by. Into a wall it
    // turns up the wall rail as soon as it is on the rail (a curve is too tight for a cart to follow); over an edge, it
    // goes round the curve until past halfway. Rolling back, it leaves by the end it came in by.
    private static @Nullable Ride startOnCurve(Level level, AbstractMinecart cart, BlockPos railPos, Vec3 center, Vec3 velocity) {
        List<Direction> ends = levelEnds(level, railPos, level.getBlockState(railPos));
        if (ends.isEmpty()) {
            return null;
        }
        Direction entry = velocity.dot(unit(ends.get(0))) < velocity.dot(unit(ends.get(1))) ? ends.get(0) : ends.get(1);
        if (velocity.dot(unit(entry)) > -1.0E-4) {
            return null;
        }
        Direction exit = entry == ends.get(0) ? ends.get(1) : ends.get(0);
        Track floor = new Track(railPos, Direction.DOWN, exit.getAxis(), Kind.PLAIN, false, null);
        double speed = velocity.horizontalDistance();
        BlockPos ahead = railPos.relative(exit);
        if (!level.getBlockState(ahead).isFaceSturdy(level, ahead, exit.getOpposite())
            && center.subtract(floor.point(cart.getBbHeight())).dot(unit(exit)) < 0.25 - speed - 0.01) {
            return null;
        }
        Step step = next(level, floor, exit);
        return step == null || step.track().floor() ? null : new Ride(floor, entry.getOpposite(), step.track(), step.exit(), speed);
    }

    // Chain gear rails on the floor: the game moves the cart, at the rail's speed and the way it was placed, or not at
    // all while the rail is unpowered.
    private static void driveOnFloor(Level level, AbstractMinecart cart) {
        BlockState state = level.getBlockState(cart.getCurrentBlockPosOrRailBelow());
        if (state.getBlock() instanceof ChainGearRailBlock) {
            Direction drive = ChainGearRailBlock.drive(state);
            if (drive == null) {
                return;
            }
            double speed = state.getValue(ChainGearRailBlock.POWERED) ? CHAIN_SPEED : 0.0;
            cart.setDeltaMovement(drive.getStepX() * speed, cart.getDeltaMovement().y, drive.getStepZ() * speed);
        }
    }
}
