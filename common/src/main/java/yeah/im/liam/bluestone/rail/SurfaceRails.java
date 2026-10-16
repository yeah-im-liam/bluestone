package yeah.im.liam.bluestone.rail;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.block.AttachedRailBlock;
import yeah.im.liam.bluestone.block.AttachedRailBlock.Kind;
import yeah.im.liam.bluestone.block.ChainRailBlock;

/**
 * Minecart movement on rails stuck to walls and ceilings.
 */
public final class SurfaceRails {
    public static final double CHAIN_SPEED = 0.2;
    public static final double MIN_CEILING_SPEED = 0.1;
    private static final double GRAVITY = 0.015;
    private static final double DRAG = 0.995;
    private static final double BOOST = 0.06;
    private static final double MAX_SPEED = 1.0;
    private static final int COOLDOWN = 10;

    private SurfaceRails() {
    }

    public record Track(BlockPos pos, Direction attached, Direction.Axis axis, Kind kind, boolean powered, @Nullable Direction travel,
                        @Nullable Direction other) {
        public Track(BlockPos pos, Direction attached, Direction.Axis axis, Kind kind, boolean powered, @Nullable Direction travel) {
            this(pos, attached, axis, kind, powered, travel, null);
        }

        boolean floor() {
            return attached == Direction.DOWN;
        }

        public List<Direction> ends() {
            return other != null ? List.of(travel, other)
                : List.of(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE), Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE));
        }

        boolean hasEnd(Direction way) {
            return other != null ? way == travel || way == other : way.getAxis() == axis;
        }

        Direction out(Direction in) {
            if (other != null && in == travel) {
                return other;
            }
            if (other != null && in == other) {
                return travel;
            }
            return in.getOpposite();
        }

        @Nullable Direction drive() {
            return kind == Kind.CHAIN && powered ? travel : null;
        }

        Vec3 point(double height) {
            double y = switch (attached) {
                case DOWN -> pos.getY() + height / 2.0;
                case UP -> pos.getY() + 1.0 - height / 2.0;
                default -> pos.getY() + 0.5;
            };
            return new Vec3(pos.getX() + 0.5, y, pos.getZ() + 0.5);
        }
    }

    record Step(Track track, Direction exit) {
    }

    public record Ride(Track from, Direction fromExit, Track to, Direction exit, double speed) {
        Ride reversed() {
            return new Ride(to, to.out(exit), from, from.out(fromExit), speed);
        }
    }

    public static @Nullable Track track(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof AttachedRailBlock rail) {
            Direction travel = state.getValue(AttachedRailBlock.TRAVEL);
            return new Track(pos, state.getValue(AttachedRailBlock.ATTACHED), travel.getAxis(), rail.kind(), state.getValue(AttachedRailBlock.POWERED), travel,
                AttachedRailBlock.curveEnd(state));
        }
        if (state.getBlock() instanceof BaseRailBlock rail && BaseRailBlock.isRail(state)) {
            RailShape shape = state.getValue(rail.getShapeProperty());
            Direction.Axis axis = shape == RailShape.NORTH_SOUTH ? Direction.Axis.Z : shape == RailShape.EAST_WEST ? Direction.Axis.X : null;
            if (axis == null) {
                return null;
            }
            if (state.getBlock() instanceof ChainRailBlock) {
                return new Track(pos, Direction.DOWN, axis, Kind.CHAIN, state.getValue(ChainRailBlock.POWERED), ChainRailBlock.drive(state));
            }
            return new Track(pos, Direction.DOWN, axis, Kind.PLAIN, false, null);
        }
        return null;
    }

    static @Nullable Step next(Level level, Track from, Direction exit) {
        return next(level, from, exit, true);
    }

    public static boolean joins(Level level, Track from, Direction exit, boolean strict) {
        return next(level, from, exit, strict) != null;
    }

    private static @Nullable Step next(Level level, Track from, Direction exit, boolean strict) {
        Direction attached = from.attached();
        BlockPos ahead = from.pos().relative(exit);
        if (level.getBlockState(ahead).isFaceSturdy(level, ahead, exit.getOpposite())) {
            BlockPos upPos = from.pos().relative(attached.getOpposite());
            if (exit == Direction.DOWN) {
                return onFloor(level, upPos, attached, strict);
            }
            Track up = track(level, upPos);
            return up != null && up.attached() == exit && (!strict || up.hasEnd(attached)) ? new Step(up, up.out(attached)) : null;
        }
        if (exit == Direction.DOWN) {
            Step floor = onFloor(level, ahead, attached, strict);
            if (floor != null) {
                return floor;
            }
        }
        Track next = track(level, ahead);
        if (next != null && next.attached() == attached && (!strict || next.hasEnd(exit.getOpposite()))) {
            return new Step(next, next.out(exit.getOpposite()));
        }
        if (next != null && next.attached() == exit && (!strict || next.hasEnd(attached))) {
            return new Step(next, next.out(attached));
        }
        if (exit == Direction.UP) {
            return onFloor(level, ahead.relative(attached), attached.getOpposite(), strict);
        }
        Track around = track(level, ahead.relative(attached));
        if (around != null && around.attached() == exit.getOpposite() && (!strict || around.hasEnd(attached.getOpposite()))) {
            return new Step(around, around.out(attached.getOpposite()));
        }
        return null;
    }

    public static List<BlockPos> neighbours(Level level, BlockPos pos) {
        Track here = track(level, pos);
        List<Direction> ends = here != null ? here.ends() : levelEnds(level, pos, level.getBlockState(pos));
        List<BlockPos> neighbours = new ArrayList<>(2);
        for (Direction end : ends) {
            Track from = here != null ? here : new Track(pos, Direction.DOWN, end.getAxis(), Kind.PLAIN, false, null);
            Step step = next(level, from, end, true);
            if (step != null) {
                neighbours.add(step.track().pos());
            }
        }
        return neighbours;
    }

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

    private static final double PIVOT_HEIGHT = 0.375;

    public static @Nullable Vec3 turnedRider(AbstractMinecart cart, Entity rider) {
        Vec3 center = cart.position().add(0.0, cart.getBbHeight() / 2.0, 0.0);
        BlockPos pos = BlockPos.containing(center);
        BlockState rail = cart.level().getBlockState(pos);
        if (!(rail.getBlock() instanceof AttachedRailBlock)) {
            return null;
        }
        Direction attached = rail.getValue(AttachedRailBlock.ATTACHED);
        Vec3 face = Vec3.atCenterOf(pos).add(unit(attached).scale(0.5 - PIVOT_HEIGHT));
        Vec3 pivot = switch (attached.getAxis()) {
            case X -> new Vec3(face.x, center.y, center.z);
            case Y -> new Vec3(center.x, face.y, center.z);
            case Z -> new Vec3(center.x, center.y, face.z);
        };
        Direction out = attached.getOpposite();
        Quaternionf turn = out == Direction.DOWN
            ? new Quaternionf().rotationAxis((float) Math.PI, rail.getValue(AttachedRailBlock.TRAVEL).getStepX(), 0.0F, rail.getValue(AttachedRailBlock.TRAVEL).getStepZ())
            : new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), new Vector3f(out.getStepX(), out.getStepY(), out.getStepZ()));
        Vec3 eye = rider.position().add(0.0, rider.getEyeHeight(), 0.0).subtract(pivot);
        Vector3f turned = new Vector3f((float) eye.x, (float) eye.y, (float) eye.z).rotate(turn);
        return pivot.add(turned.x, turned.y - rider.getEyeHeight(), turned.z);
    }

    private static Vec3 unit(Direction direction) {
        return new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

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
                driveOnFloor(level, cart, rider);
                return false;
            }
        }
        double height = cart.getBbHeight();
        Vec3 pos = cart.position().add(0.0, height / 2.0, 0.0);
        BlockPos onPos = ride.from().floor() && !ride.to().floor() || ride.to().pos().equals(BlockPos.containing(pos)) ? ride.to().pos() : ride.from().pos();
        Track on = track(level, onPos);
        if (on == null) {
            on = onPos.equals(ride.to().pos()) && ride.to().floor() ? ride.to() : onPos.equals(ride.from().pos()) && ride.from().floor() ? ride.from() : null;
        }
        if (on == null) {
            rider.bluestone$setRide(null);
            return false;
        }
        Vec3 heading = heading(pos, ride, height);
        double speed = ride.speed();
        if (on.kind() == Kind.CHAIN) {
            if (Double.isNaN(rider.bluestone$chainEntry())) {
                rider.bluestone$setChainEntry(speed);
            }
        } else if (!Double.isNaN(rider.bluestone$chainEntry())) {
            speed = Math.min(rider.bluestone$chainEntry(), CHAIN_SPEED);
            rider.bluestone$setChainEntry(Double.NaN);
        }
        boolean hold = false;
        switch (on.kind()) {
            case CHAIN -> {
                if (on.powered()) {
                    if (heading.dot(unit(on.travel())) < 0.0) {
                        ride = ride.reversed();
                        heading = heading(pos, ride, height);
                    }
                    speed = CHAIN_SPEED;
                    if (heading.y < 0.0) {
                        rider.bluestone$setChainEntry(rider.bluestone$chainEntry() - GRAVITY * heading.y);
                    }
                } else {
                    hold = true;
                    rider.bluestone$setChainEntry(0.0);
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
                place(cart, pos, height, unit(exit).scale(speed));
                rider.bluestone$setRide(null);
                return true;
            }
            Step step = next(level, at, exit);
            if (step == null) {
                if (exit == Direction.UP) {
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
        rider.bluestone$setChainEntry(Double.NaN);
        rider.bluestone$setCooldown(COOLDOWN);
        return false;
    }

    private static @Nullable Ride start(Level level, AbstractMinecart cart) {
        double height = cart.getBbHeight();
        Vec3 center = cart.position().add(0.0, height / 2.0, 0.0);
        Vec3 velocity = cart.getDeltaMovement();
        Track here = track(level, BlockPos.containing(center));
        if (here != null && !here.floor()) {
            Direction moving = null;
            double along = 1.0E-4;
            for (Direction end : here.ends()) {
                if (velocity.dot(unit(end)) > along) {
                    moving = end;
                    along = velocity.dot(unit(end));
                }
            }
            Direction exit;
            if (here.kind() == Kind.CHAIN || here.kind() == Kind.POWERED && here.powered() && along < 0.01) {
                exit = here.travel();
            } else if (moving != null) {
                exit = moving;
            } else if (here.hasEnd(Direction.DOWN)) {
                exit = Direction.DOWN;
            } else if (here.attached() != Direction.UP) {
                exit = here.travel();
            } else {
                return null;
            }
            double speed = here.drive() != null ? CHAIN_SPEED : moving != null ? along : 0.0;
            if (here.attached() == Direction.UP && here.kind() != Kind.CHAIN && speed < MIN_CEILING_SPEED) {
                return null;
            }
            Vec3 point = here.point(height);
            if (here.other() != null) {
                cart.setPos(point.x, point.y - height / 2.0, point.z);
                Step step = next(level, here, exit);
                return step == null ? new Ride(here, exit, here, exit, speed) : new Ride(here, exit, step.track(), step.exit(), speed);
            }
            Vec3 axis = unit(Direction.fromAxisAndDirection(here.axis(), Direction.AxisDirection.POSITIVE));
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

    private static void driveOnFloor(Level level, AbstractMinecart cart, RailRider rider) {
        BlockState state = level.getBlockState(cart.getCurrentBlockPosOrRailBelow());
        Vec3 velocity = cart.getDeltaMovement();
        if (state.getBlock() instanceof ChainRailBlock) {
            Direction drive = ChainRailBlock.drive(state);
            if (drive == null) {
                return;
            }
            if (Double.isNaN(rider.bluestone$chainEntry())) {
                rider.bluestone$setChainEntry(velocity.horizontalDistance());
            }
            double speed = state.getValue(ChainRailBlock.POWERED) ? CHAIN_SPEED : 0.0;
            cart.setDeltaMovement(drive.getStepX() * speed, velocity.y, drive.getStepZ() * speed);
            return;
        }
        double entry = Math.min(rider.bluestone$chainEntry(), CHAIN_SPEED);
        if (!Double.isNaN(entry)) {
            rider.bluestone$setChainEntry(Double.NaN);
            double horizontal = velocity.horizontalDistance();
            if (BaseRailBlock.isRail(state) && horizontal > 1.0E-4) {
                cart.setDeltaMovement(velocity.x / horizontal * entry, velocity.y, velocity.z / horizontal * entry);
            }
        }
    }
}
