package yeah.im.liam.bluestone.block;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

/**
 * Used when the world has the redstone experiments enabled, like the game's own experimental evaluator.
 */
final class ExperimentalBluestoneWireEvaluator extends BluestoneWireEvaluator {
    private final Deque<BlockPos> wiresToTurnOff = new ArrayDeque<>();
    private final Deque<BlockPos> wiresToTurnOn = new ArrayDeque<>();
    private final Object2IntMap<BlockPos> updatedWires = new Object2IntLinkedOpenHashMap<>();

    ExperimentalBluestoneWireEvaluator(BluestoneWireBlock wireBlock) {
        super(wireBlock);
    }

    @Override
    public void updatePowerStrength(Level level, BlockPos initialPos, BlockState ignored, @Nullable Orientation orientation, boolean shapeUpdateWiresAroundInitialPosition) {
        Orientation initialOrientation = getInitialOrientation(level, orientation);
        calculateCurrentChanges(level, initialPos, initialOrientation);
        ObjectIterator<Object2IntMap.Entry<BlockPos>> iterator = updatedWires.object2IntEntrySet().iterator();
        for (boolean initialWire = true; iterator.hasNext(); initialWire = false) {
            Object2IntMap.Entry<BlockPos> next = iterator.next();
            BlockPos pos = next.getKey();
            int newLevel = unpackPower(next.getIntValue());
            BlockState state = level.getBlockState(pos);
            if (state.is(wireBlock) && state.getValue(BluestoneWireBlock.POWER) != newLevel) {
                int updateFlags = Block.UPDATE_CLIENTS;
                if (!shapeUpdateWiresAroundInitialPosition || !initialWire) {
                    updateFlags |= Block.UPDATE_SKIP_SHAPE_UPDATE_ON_WIRE;
                }
                level.setBlock(pos, state.setValue(BluestoneWireBlock.POWER, newLevel), updateFlags);
            } else {
                iterator.remove();
            }
        }
        causeNeighborUpdates(level);
    }

    private void causeNeighborUpdates(Level level) {
        updatedWires.forEach((wirePos, packed) -> {
            Orientation orientation = unpackOrientation(packed);
            BlockState state = level.getBlockState(wirePos);
            for (Direction neighborDirection : orientation.getDirections()) {
                if (!isConnected(state, neighborDirection)) {
                    continue;
                }
                BlockPos neighborPos = wirePos.relative(neighborDirection);
                BlockState neighborState = level.getBlockState(neighborPos);
                Orientation neighborOrientation = orientation.withFrontPreserveUp(neighborDirection);
                level.neighborChanged(neighborState, neighborPos, wireBlock, neighborOrientation, false);
                if (neighborState.isRedstoneConductor(level, neighborPos)) {
                    for (Direction direction : neighborOrientation.getDirections()) {
                        if (direction != neighborDirection.getOpposite()) {
                            level.neighborChanged(neighborPos.relative(direction), wireBlock, neighborOrientation.withFrontPreserveUp(direction));
                        }
                    }
                }
            }
        });
    }

    private static boolean isConnected(BlockState state, Direction direction) {
        EnumProperty<RedstoneSide> property = BluestoneWireBlock.PROPERTY_BY_DIRECTION.get(direction);
        if (property == null) {
            return direction == Direction.DOWN;
        }
        return state.getValue(property).isConnected();
    }

    private static Orientation getInitialOrientation(Level level, @Nullable Orientation incoming) {
        Orientation orientation = incoming != null ? incoming : Orientation.random(level.getRandom());
        return orientation.withUp(Direction.UP).withSideBias(Orientation.SideBias.LEFT);
    }

    private void calculateCurrentChanges(Level level, BlockPos initialPosition, Orientation initialOrientation) {
        BlockState initialState = level.getBlockState(initialPosition);
        if (initialState.is(wireBlock)) {
            setPower(initialPosition, initialState.getValue(BluestoneWireBlock.POWER), initialOrientation);
            wiresToTurnOff.add(initialPosition);
        } else {
            propagateChangeToNeighbors(level, initialPosition, 0, initialOrientation, true);
        }

        while (!wiresToTurnOff.isEmpty()) {
            BlockPos pos = wiresToTurnOff.removeFirst();
            int packed = updatedWires.getInt(pos);
            Orientation orientation = unpackOrientation(packed);
            int oldPower = unpackPower(packed);
            int blockPower = getBlockSignal(level, pos);
            int wirePower = getIncomingWireSignal(level, pos);
            int newPower = Math.max(blockPower, wirePower);
            int powerToSet;
            if (newPower < oldPower) {
                if (blockPower > 0 && !wiresToTurnOn.contains(pos)) {
                    wiresToTurnOn.add(pos);
                }
                powerToSet = 0;
            } else {
                powerToSet = newPower;
            }
            if (powerToSet != oldPower) {
                setPower(pos, powerToSet, orientation);
            }
            propagateChangeToNeighbors(level, pos, powerToSet, orientation, oldPower > newPower);
        }

        while (!wiresToTurnOn.isEmpty()) {
            BlockPos pos = wiresToTurnOn.removeFirst();
            int packed = updatedWires.getInt(pos);
            int oldPower = unpackPower(packed);
            int blockPower = getBlockSignal(level, pos);
            int wirePower = getIncomingWireSignal(level, pos);
            int newPower = Math.max(blockPower, wirePower);
            Orientation orientation = unpackOrientation(packed);
            if (newPower > oldPower) {
                setPower(pos, newPower, orientation);
            } else if (newPower < oldPower) {
                throw new IllegalStateException("Turning off wire while trying to turn it on. Should not happen.");
            }
            propagateChangeToNeighbors(level, pos, newPower, orientation, false);
        }
    }

    private static int packOrientationAndPower(Orientation orientation, int power) {
        return orientation.getIndex() << 4 | power;
    }

    private static Orientation unpackOrientation(int packed) {
        return Orientation.fromIndex(packed >> 4);
    }

    private static int unpackPower(int packed) {
        return packed & 15;
    }

    private void setPower(BlockPos pos, int newPower, Orientation orientation) {
        updatedWires.compute(pos, (key, packed) -> packed == null
            ? packOrientationAndPower(orientation, newPower)
            : packOrientationAndPower(unpackOrientation(packed), newPower));
    }

    private void propagateChangeToNeighbors(Level level, BlockPos pos, int newPower, Orientation orientation, boolean allowTurningOff) {
        for (Direction direction : orientation.getHorizontalDirections()) {
            enqueueNeighborWire(level, pos.relative(direction), newPower, orientation.withFront(direction), allowTurningOff);
        }
        for (Direction directionVertical : orientation.getVerticalDirections()) {
            BlockPos offsetPos = pos.relative(directionVertical);
            boolean solidBlock = level.getBlockState(offsetPos).isRedstoneConductor(level, offsetPos);
            for (Direction directionHorizontal : orientation.getHorizontalDirections()) {
                BlockPos neighbor = pos.relative(directionHorizontal);
                if (directionVertical == Direction.UP && !solidBlock
                    || directionVertical == Direction.DOWN && !level.getBlockState(neighbor).isRedstoneConductor(level, neighbor)) {
                    enqueueNeighborWire(level, offsetPos.relative(directionHorizontal), newPower, orientation.withFront(directionHorizontal), allowTurningOff);
                }
            }
        }
    }

    private void enqueueNeighborWire(Level level, BlockPos pos, int newFromPower, Orientation orientation, boolean allowTurningOff) {
        BlockState state = level.getBlockState(pos);
        if (state.is(wireBlock)) {
            int toPower = getWireSignal(pos, state);
            if (toPower < newFromPower - 1 && !wiresToTurnOn.contains(pos)) {
                wiresToTurnOn.add(pos);
                setPower(pos, toPower, orientation);
            }
            if (allowTurningOff && toPower > newFromPower && !wiresToTurnOff.contains(pos)) {
                wiresToTurnOff.add(pos);
                setPower(pos, toPower, orientation);
            }
        }
    }

    @Override
    protected int getWireSignal(BlockPos pos, BlockState state) {
        int packed = updatedWires.getOrDefault(pos, -1);
        return packed != -1 ? unpackPower(packed) : super.getWireSignal(pos, state);
    }
}
