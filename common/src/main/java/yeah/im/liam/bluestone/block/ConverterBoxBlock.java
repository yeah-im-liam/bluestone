package yeah.im.liam.bluestone.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.PoweredBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.signal.Channel;
import yeah.im.liam.bluestone.signal.Listener;
import yeah.im.liam.bluestone.signal.SignalBlock;
import yeah.im.liam.bluestone.signal.Signals;

/**
 * Joins every kind of dust and paste.
 */
public class ConverterBoxBlock extends Block implements SignalBlock, EntityBlock {
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    private static final Listener LISTENER = source -> true;

    public interface Light {
        void lit(Direction face, @Nullable Direction edge, Channel colour);
    }

    private interface Touch {
        void touch(Direction face, @Nullable Direction edge, Channel colour, int power);
    }

    public ConverterBoxBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWER, 0));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConverterBoxBlockEntity(pos, state);
    }

    private static boolean isDust(BlockState state) {
        return state.is(Blocks.REDSTONE_WIRE) || state.getBlock() instanceof ColoredWireBlock;
    }

    public static void lights(BlockGetter level, BlockPos pos, Light light) {
        touches(level, pos, (face, edge, colour, power) -> {
            if (power > 0) {
                light.lit(face, edge, colour);
            }
        });
        for (Direction face : Direction.values()) {
            BlockState there = level.getBlockState(pos.relative(face));
            Channel colour = there.getBlock() instanceof PoweredBlock ? Channel.ofSource(there) : null;
            if (colour != null) {
                light.lit(face, null, colour);
                for (Direction edge : Direction.values()) {
                    if (edge.getAxis() != face.getAxis()) {
                        light.lit(face, edge, colour);
                    }
                }
            }
        }
    }

    private static void touches(BlockGetter level, BlockPos pos, Touch touch) {
        for (Direction face : Direction.values()) {
            Direction inward = face.getOpposite();
            BlockState there = level.getBlockState(pos.relative(face));
            Channel dust = isDust(there) ? Channel.ofSource(there) : null;
            if (dust != null) {
                int power = there.getValue(ColoredWireBlock.POWER);
                if (face.getAxis().isHorizontal()) {
                    touch.touch(face, Direction.DOWN, dust, power);
                    if (there.getValue(ColoredWireBlock.PROPERTY_BY_DIRECTION.get(inward)) == RedstoneSide.UP) {
                        touch.touch(face, Direction.UP, dust, power);
                    }
                } else if (face == Direction.UP) {
                    touch.touch(face, null, dust, power);
                    for (Direction edge : Direction.Plane.HORIZONTAL) {
                        if (there.getValue(ColoredWireBlock.PROPERTY_BY_DIRECTION.get(edge)).isConnected()) {
                            touch.touch(face, edge, dust, power);
                        }
                    }
                }
            } else if (there.getBlock() instanceof GreenstonePasteBlock) {
                int power = there.getValue(GreenstonePasteBlock.POWER);
                boolean on = GreenstonePasteBlock.hasFace(there, inward);
                if (on) {
                    touch.touch(face, null, Channel.GREENSTONE, power);
                }
                for (Direction edge : Direction.values()) {
                    if (edge.getAxis() == face.getAxis()) {
                        continue;
                    }
                    boolean beside = GreenstonePasteBlock.hasFace(there, edge) && GreenstonePasteBlock.draws(there, edge, inward);
                    if (beside || on && GreenstonePasteBlock.draws(there, inward, edge)) {
                        touch.touch(face, edge, Channel.GREENSTONE, power);
                    }
                }
            }
            if (face.getAxis().isHorizontal()) {
                BlockState climbing = level.getBlockState(pos.relative(face).below());
                Channel climbingDust = isDust(climbing) ? Channel.ofSource(climbing) : null;
                if (climbingDust != null && climbing.getValue(ColoredWireBlock.PROPERTY_BY_DIRECTION.get(inward)) == RedstoneSide.UP) {
                    touch.touch(face, Direction.DOWN, climbingDust, climbing.getValue(ColoredWireBlock.POWER));
                }
            }
            for (Direction edge : Direction.values()) {
                if (edge.getAxis() == face.getAxis()) {
                    continue;
                }
                BlockState flush = level.getBlockState(pos.relative(edge).relative(face));
                Direction back = edge.getOpposite();
                Channel flushDust = face == Direction.UP && isDust(flush) ? Channel.ofSource(flush) : null;
                if (flushDust != null && flush.getValue(ColoredWireBlock.PROPERTY_BY_DIRECTION.get(back)).isConnected()) {
                    touch.touch(face, edge, flushDust, flush.getValue(ColoredWireBlock.POWER));
                } else if (flush.getBlock() instanceof GreenstonePasteBlock && GreenstonePasteBlock.hasFace(flush, inward)
                    && GreenstonePasteBlock.draws(flush, inward, back)) {
                    touch.touch(face, edge, Channel.GREENSTONE, flush.getValue(GreenstonePasteBlock.POWER));
                }
            }
        }
    }

    public static int powerToDustOnTop(BlockGetter level, BlockPos dust) {
        BlockState below = level.getBlockState(dust.below());
        return below.getBlock() instanceof ConverterBoxBlock ? Math.max(0, below.getValue(POWER) - 1) : 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(this) && !level.isClientSide()) {
            updatePower(level, pos, state);
        }
    }

    // Removal is affectNeighborsAfterRemoval from 1.21.5; before, onRemove also ran for changes within the block.
    //#if MC >= 1.21.5
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
    //#else
    //$$ @Override
    //$$ protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
    //$$     super.onRemove(state, level, pos, newState, movedByPiston);
    //$$     if (state.is(newState.getBlock()) || level.isClientSide()) {
    //$$         return;
    //$$     }
    //#endif
        updateAround(level, pos);
    }

    //#if MC >= 1.21.2
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
    //#else
    //$$ @Override
    //$$ protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighbourPos, boolean movedByPiston) {
    //$$     Orientation orientation = null;
    //#endif
        if (!level.isClientSide()) {
            updatePower(level, pos, state);
        }
    }

    // Dust and paste running onto a face from beside the box are diagonal to it, so they look at their lines again.
    @Override
    protected void updateIndirectNeighbourShapes(BlockState state, LevelAccessor level, BlockPos pos, int flags, int updateLimit) {
        for (Direction a : Direction.values()) {
            for (Direction b : Direction.values()) {
                if (a.getAxis() != b.getAxis() && a.ordinal() < b.ordinal()) {
                    BlockPos other = pos.relative(a).relative(b);
                    BlockState wire = level.getBlockState(other);
                    if (isDust(wire) || wire.getBlock() instanceof GreenstonePasteBlock) {
                        Block.updateOrDestroy(wire, Block.updateFromNeighbourShapes(wire, level, other), level, other, flags & ~Block.UPDATE_NEIGHBORS, updateLimit);
                    }
                }
            }
        }
    }

    private void updatePower(Level level, BlockPos pos, BlockState state) {
        int[] power = {Signals.readForDust(LISTENER, () -> level.getBestNeighborSignal(pos))};
        for (Direction direction : Direction.values()) {
            BlockState neighbour = level.getBlockState(pos.relative(direction));
            if (isDust(neighbour)) {
                power[0] = Math.max(power[0], neighbour.getValue(ColoredWireBlock.POWER));
            } else if (neighbour.getBlock() instanceof GreenstonePasteBlock
                && neighbour.getValue(GreenstonePasteBlock.side(direction.getOpposite())).reaches()) {
                power[0] = Math.max(power[0], neighbour.getValue(GreenstonePasteBlock.POWER));
            }
        }
        touches(level, pos, (face, edge, colour, wire) -> power[0] = Math.max(power[0], wire));
        if (power[0] != state.getValue(POWER)) {
            level.setBlock(pos, state.setValue(POWER, power[0]), Block.UPDATE_CLIENTS);
            updateAround(level, pos);
        }
    }

    private void updateAround(Level level, BlockPos pos) {
        level.updateNeighborsAt(pos, this);
        for (Direction direction : Direction.values()) {
            level.updateNeighborsAt(pos.relative(direction), this);
        }
    }

    @Override
    public Channel channel(BlockState state) {
        return Channel.PURPLESTONE;
    }

    @Override
    public boolean connectsToWire(BlockState state, Channel wire, @Nullable Direction direction) {
        return true;
    }

    //#if MC >= 26.1
    @Override
    //#endif
    protected boolean shouldRedstoneWireConnectTo(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return true;
    }

    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return true;
    }
}
