package yeah.im.liam.bluestone.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
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
 * Joins purplestone dust and greenstone paste. It takes on the strongest purplestone dust or greenstone paste touching
 * it, on any side, and passes that strength on unchanged, so a signal crosses between the two either way without
 * losing anything. It is not a repeater: dust and paste fed by it are one level weaker, as from any wire. Purplestone
 * and greenstone signals from blocks next to it count as well.
 * <p>
 * Each face has a light at the middle of each edge. A light comes on, in the colour of the dust or paste, where powered
 * dust or paste meets that edge: dust or paste beside the block lights the edge of the face it runs into, and dust or
 * paste lying on the block lights the edges its lines run to. Its block entity only carries the renderer that draws them
 * (WallConverterRenderer).
 */
public class WallConverterBlock extends Block implements SignalBlock, EntityBlock {
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    private static final Listener LISTENER = source -> source == Channel.PURPLESTONE || source == Channel.GREENSTONE;

    /**
     * Where a light is lit: the face it is on, the edge of that face, and the colour.
     */
    public interface Light {
        void lit(Direction face, Direction edge, Channel colour);
    }

    public WallConverterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWER, 0));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WallConverterBlockEntity(pos, state);
    }

    /**
     * The lights of the wall converter at a place that are lit.
     */
    public static void lights(BlockGetter level, BlockPos pos, Light light) {
        for (Direction face : Direction.values()) {
            BlockState there = level.getBlockState(pos.relative(face));
            if (there.getBlock() instanceof PurplestoneWireBlock) {
                if (there.getValue(ColoredWireBlock.POWER) == 0) {
                    continue;
                }
                if (face.getAxis().isHorizontal()) {
                    // Dust on the floor beside the block, and up its face when it climbs onto it.
                    light.lit(face, Direction.DOWN, Channel.PURPLESTONE);
                    if (there.getValue(ColoredWireBlock.PROPERTY_BY_DIRECTION.get(face.getOpposite())) == RedstoneSide.UP) {
                        light.lit(face, Direction.UP, Channel.PURPLESTONE);
                    }
                } else if (face == Direction.UP) {
                    // Dust on top of the block.
                    for (Direction edge : Direction.Plane.HORIZONTAL) {
                        if (there.getValue(ColoredWireBlock.PROPERTY_BY_DIRECTION.get(edge)).isConnected()) {
                            light.lit(face, edge, Channel.PURPLESTONE);
                        }
                    }
                }
            } else if (there.getBlock() instanceof GreenstonePasteBlock && there.getValue(GreenstonePasteBlock.POWER) > 0) {
                Direction onBlock = face.getOpposite();
                for (Direction edge : Direction.values()) {
                    if (edge.getAxis() == face.getAxis()) {
                        continue;
                    }
                    // Paste beside the block, on the face of its space at this edge, running into the block.
                    boolean beside = GreenstonePasteBlock.hasFace(there, edge) && GreenstonePasteBlock.draws(there, edge, onBlock);
                    // Paste lying on the block, running to this edge.
                    boolean on = GreenstonePasteBlock.hasFace(there, onBlock) && GreenstonePasteBlock.draws(there, onBlock, edge);
                    if (beside || on) {
                        light.lit(face, edge, Channel.GREENSTONE);
                    }
                }
            }
        }
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

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        level.updateNeighborsAt(pos, this);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide()) {
            updatePower(level, pos, state);
        }
    }

    private void updatePower(Level level, BlockPos pos, BlockState state) {
        int power = Signals.readForDust(LISTENER, () -> level.getBestNeighborSignal(pos));
        for (Direction direction : Direction.values()) {
            BlockState neighbour = level.getBlockState(pos.relative(direction));
            if (neighbour.getBlock() instanceof PurplestoneWireBlock) {
                power = Math.max(power, neighbour.getValue(ColoredWireBlock.POWER));
            } else if (neighbour.getBlock() instanceof GreenstonePasteBlock
                && neighbour.getValue(GreenstonePasteBlock.side(direction.getOpposite())).reaches()) {
                // Paste that lies on this block or runs into it.
                power = Math.max(power, neighbour.getValue(GreenstonePasteBlock.POWER));
            }
        }
        if (power != state.getValue(POWER)) {
            level.setBlock(pos, state.setValue(POWER, power), Block.UPDATE_CLIENTS);
            level.updateNeighborsAt(pos, this);
        }
    }

    @Override
    public Channel channel(BlockState state) {
        return Channel.PURPLESTONE;
    }

    // Purplestone dust bends towards it; redstone and bluestone dust have nothing to do with it.
    @Override
    public boolean connectsToWire(BlockState state, Channel wire, @Nullable Direction direction) {
        return wire == Channel.PURPLESTONE && direction != null;
    }

    @Override
    protected boolean shouldRedstoneWireConnectTo(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return false;
    }

    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return false;
    }
}
