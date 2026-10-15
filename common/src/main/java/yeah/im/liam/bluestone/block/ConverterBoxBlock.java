package yeah.im.liam.bluestone.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.signal.Channel;
import yeah.im.liam.bluestone.signal.Listener;
import yeah.im.liam.bluestone.signal.SignalBlock;
import yeah.im.liam.bluestone.signal.Signals;

/**
 * Joins every kind of dust and paste: redstone, bluestone and purplestone dust and greenstone paste. It takes on the
 * strongest of them touching it, on any side, and passes that strength on unchanged, so a signal crosses from any kind
 * to any other without losing anything. It is not a repeater: dust and paste fed by it are one level weaker, as from
 * any wire. Signals of any colour from blocks next to it count as well. Dust of any colour lying on top of it is fed
 * by it too, as paste lying on it is.
 * <p>
 * Each face has a light at the middle of each edge and one in its middle. A light comes on, in the colour of the dust or
 * paste, where powered dust or paste meets the block: dust or paste beside the block lights the edge of the face it
 * runs into, and dust or paste lying on the block lights the middle of the face it lies on and the edges its lines run
 * to. While any light is on (LIT) the whole block glows: its renderer draws it glowing over the world's block
 * (ConverterBoxRenderer), and its block entity is only there for that.
 */
public class ConverterBoxBlock extends Block implements SignalBlock, EntityBlock {
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final Listener LISTENER = source -> true;

    /**
     * Where a light is lit: the face it is on, the edge of that face (or null for the light in the middle of the face),
     * and the colour.
     */
    public interface Light {
        void lit(Direction face, @Nullable Direction edge, Channel colour);
    }

    public ConverterBoxBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWER, 0).setValue(LIT, false));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConverterBoxBlockEntity(pos, state);
    }

    // Redstone dust is the game's own block; the others are the mod's.
    private static boolean isDust(BlockState state) {
        return state.is(Blocks.REDSTONE_WIRE) || state.getBlock() instanceof ColoredWireBlock;
    }

    /**
     * The lights of the converter box at a place that are lit.
     */
    public static void lights(BlockGetter level, BlockPos pos, Light light) {
        for (Direction face : Direction.values()) {
            BlockState there = level.getBlockState(pos.relative(face));
            if (isDust(there)) {
                Channel colour = Channel.ofSource(there);
                if (colour == null || there.getValue(ColoredWireBlock.POWER) == 0) {
                    continue;
                }
                if (face.getAxis().isHorizontal()) {
                    // Dust on the floor beside the block, and up its face when it climbs onto it.
                    light.lit(face, Direction.DOWN, colour);
                    if (there.getValue(ColoredWireBlock.PROPERTY_BY_DIRECTION.get(face.getOpposite())) == RedstoneSide.UP) {
                        light.lit(face, Direction.UP, colour);
                    }
                } else if (face == Direction.UP) {
                    // Dust on top of the block.
                    light.lit(face, null, colour);
                    for (Direction edge : Direction.Plane.HORIZONTAL) {
                        if (there.getValue(ColoredWireBlock.PROPERTY_BY_DIRECTION.get(edge)).isConnected()) {
                            light.lit(face, edge, colour);
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
                if (GreenstonePasteBlock.hasFace(there, onBlock)) {
                    light.lit(face, null, Channel.GREENSTONE);
                }
            }
        }
    }

    /**
     * The power a converter box gives dust of any colour lying on top of it: one level less, as along a wire. Paste on
     * top takes it as from any box it lies on.
     */
    public static int powerToDustOnTop(BlockGetter level, BlockPos dust) {
        BlockState below = level.getBlockState(dust.below());
        return below.getBlock() instanceof ConverterBoxBlock ? Math.max(0, below.getValue(POWER) - 1) : 0;
    }

    private static boolean anyLit(BlockGetter level, BlockPos pos) {
        boolean[] lit = {false};
        lights(level, pos, (face, edge, colour) -> lit[0] = true);
        return lit[0];
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER, LIT);
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

    // Dust and paste change their lines without telling their neighbours as such, so the lights are looked at again.
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return state.setValue(LIT, anyLit(level, pos));
    }

    private void updatePower(Level level, BlockPos pos, BlockState state) {
        int power = Signals.readForDust(LISTENER, () -> level.getBestNeighborSignal(pos));
        for (Direction direction : Direction.values()) {
            BlockState neighbour = level.getBlockState(pos.relative(direction));
            if (isDust(neighbour)) {
                power = Math.max(power, neighbour.getValue(ColoredWireBlock.POWER));
            } else if (neighbour.getBlock() instanceof GreenstonePasteBlock
                && neighbour.getValue(GreenstonePasteBlock.side(direction.getOpposite())).reaches()) {
                // Paste that lies on this block or runs into it.
                power = Math.max(power, neighbour.getValue(GreenstonePasteBlock.POWER));
            }
        }
        BlockState updated = state.setValue(POWER, power).setValue(LIT, anyLit(level, pos));
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
            if (power != state.getValue(POWER)) {
                level.updateNeighborsAt(pos, this);
            }
        }
    }

    @Override
    public Channel channel(BlockState state) {
        return Channel.PURPLESTONE;
    }

    // Dust of every colour bends towards it.
    @Override
    public boolean connectsToWire(BlockState state, Channel wire, @Nullable Direction direction) {
        return direction != null;
    }

    @Override
    protected boolean shouldRedstoneWireConnectTo(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return direction != null;
    }

    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return direction != null;
    }
}
