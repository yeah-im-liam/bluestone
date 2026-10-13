package yeah.im.liam.bluestone.rail;

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
import yeah.im.liam.bluestone.block.ChainGearRailBlock;

/**
 * Minecart movement on rails stuck to walls and ceilings. The game moves carts on floor rails as usual; once a cart
 * reaches a wall or ceiling rail, this takes over. The cart runs along each rail's middle line and turns at inside
 * corners (track running into a wall carries on up it) and outside corners (track running over an edge carries on
 * down the other side). Climbing slows a cart and coming down speeds it up; a cart that stops climbing rolls back. On
 * a ceiling a cart needs {@link #MIN_CEILING_SPEED} to stay on. Chain gear rails move carts at {@link #CHAIN_SPEED}
 * the way they were placed, whatever speed they come in with. Back on a floor rail, the game takes over again.
 */
public final class SurfaceRails {
    public static final double CHAIN_SPEED = 0.3;
    public static final double MIN_CEILING_SPEED = 0.2;
    // The minecart's own gravity, and a little less drag than on the floor.
    private static final double GRAVITY = 0.04;
    private static final double DRAG = 0.995;
    private static final double MAX_SPEED = 1.0;
    // Ticks after falling off before a cart can be picked up by a wall or ceiling rail again.
    private static final int COOLDOWN = 10;

    private SurfaceRails() {
    }

    /**
     * A piece of track: where it is, the face it sits on, the axis carts run along and, for chain gear, the way it
     * drives them.
     */
    public record Track(BlockPos pos, Direction attached, Direction.Axis axis, @Nullable Direction drive) {
        boolean floor() {
            return attached == Direction.DOWN;
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
            return new Ride(to, exit.getOpposite(), from, fromExit.getOpposite(), -speed);
        }
    }

    public static @Nullable Track track(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof AttachedRailBlock rail) {
            Direction travel = state.getValue(AttachedRailBlock.TRAVEL);
            return new Track(pos, state.getValue(AttachedRailBlock.ATTACHED), travel.getAxis(), rail.drives() ? travel : null);
        }
        if (state.getBlock() instanceof BaseRailBlock rail && BaseRailBlock.isRail(state)) {
            RailShape shape = state.getValue(rail.getShapeProperty());
            Direction.Axis axis = shape == RailShape.NORTH_SOUTH ? Direction.Axis.Z : shape == RailShape.EAST_WEST ? Direction.Axis.X : null;
            if (axis == null) {
                return null;
            }
            return new Track(pos, Direction.DOWN, axis, state.getBlock() instanceof ChainGearRailBlock ? ChainGearRailBlock.drive(state) : null);
        }
        return null;
    }

    static @Nullable Step next(Level level, Track from, Direction exit) {
        Direction attached = from.attached();
        BlockPos ahead = from.pos().relative(exit);
        if (level.getBlockState(ahead).isFaceSturdy(level, ahead, exit.getOpposite())) {
            // Inside corner: the track carries on up the block in the way.
            Track up = track(level, from.pos().relative(attached.getOpposite()));
            return up != null && up.attached() == exit && up.axis() == attached.getAxis() ? new Step(up, attached.getOpposite()) : null;
        }
        Track next = track(level, ahead);
        if (next != null && next.attached() == attached && next.axis() == exit.getAxis()) {
            return new Step(next, exit);
        }
        // Inside corner from the other side: the track ahead is on the block it runs into.
        if (next != null && next.attached() == exit && next.axis() == attached.getAxis()) {
            return new Step(next, attached.getOpposite());
        }
        // Outside corner: around the edge of the block the track sits on.
        Track around = track(level, ahead.relative(attached));
        if (around != null && around.attached() == exit.getOpposite() && around.axis() == attached.getAxis()) {
            return new Step(around, attached);
        }
        return null;
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
        Vec3 heading = heading(pos, ride, height);
        double speed = ride.speed();
        Track on = ride.to().pos().equals(BlockPos.containing(pos)) ? ride.to() : ride.from();
        if (on.drive() != null) {
            speed = CHAIN_SPEED;
        } else {
            speed = (speed - GRAVITY * heading.y) * DRAG;
            if (ride.from().attached() == Direction.UP && ride.to().attached() == Direction.UP && speed < MIN_CEILING_SPEED) {
                return fallOff(cart, rider, heading.scale(Math.max(speed, 0.0)));
            }
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
        return true;
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
            if (here.drive() != null) {
                exit = here.drive();
            } else if (Math.abs(along) > 1.0E-4) {
                exit = Direction.fromAxisAndDirection(here.axis(), along > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
            } else if (here.axis() == Direction.Axis.Y) {
                exit = Direction.DOWN;
            } else {
                return null;
            }
            double speed = here.drive() != null ? CHAIN_SPEED : Math.abs(along);
            if (here.attached() == Direction.UP && here.drive() == null && speed < MIN_CEILING_SPEED) {
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
        Track floor = track(level, cart.getCurrentBlockPosOrRailBelow());
        if (floor == null || !floor.floor()) {
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

    // Chain gear rails on the floor: the game moves the cart, at the rail's speed and the way it was placed.
    private static void driveOnFloor(Level level, AbstractMinecart cart) {
        BlockState state = level.getBlockState(cart.getCurrentBlockPosOrRailBelow());
        if (state.getBlock() instanceof ChainGearRailBlock) {
            Direction drive = ChainGearRailBlock.drive(state);
            if (drive != null) {
                cart.setDeltaMovement(drive.getStepX() * CHAIN_SPEED, cart.getDeltaMovement().y, drive.getStepZ() * CHAIN_SPEED);
            }
        }
    }
}
