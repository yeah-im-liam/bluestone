package yeah.im.liam.bluestone.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.signal.Channel;
import yeah.im.liam.bluestone.signal.SignalBlock;
import yeah.im.liam.bluestone.signal.Signals;

/**
 * Greenstone paste: wiring that sticks to floors, walls and ceilings. All the paste in one block space is one piece of
 * wire, whichever faces it covers. It passes power to paste in the six spaces around it and, around block edges, to
 * paste one space over diagonally, one level weaker each time. It powers every block it touches, hears only
 * greenstone and plain switches, and meets purplestone through the wall converter.
 */
public class GreenstonePasteBlock extends MultifaceBlock implements SignalBlock {
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    // Redstone's colours with the red and green channels swapped.
    private static final int[] COLORS = ColoredWireBlock.colorsFor((red, green, blue) -> ARGB.colorFromFloat(1.0F, green, red, blue));
    private boolean shouldSignal = true;

    public GreenstonePasteBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWER);
    }

    public int colorForPower(int power) {
        return COLORS[power];
    }

    // Only more paste goes into its space; other blocks can't be placed over it, like dust.
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return context.getItemInHand().is(asItem()) && super.canBeReplaced(state, context);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return shouldSignal;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return shouldSignal ? state.getValue(POWER) : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(this) && !level.isClientSide()) {
            updatePower(level, pos, state);
            updateAround(level, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (!movedByPiston) {
            updateAround(level, pos);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide()) {
            updatePower(level, pos, state);
        }
    }

    private void updatePower(Level level, BlockPos pos, BlockState state) {
        int power = Math.max(blockSignal(level, pos), wireSignal(level, pos));
        if (power != state.getValue(POWER)) {
            if (level.getBlockState(pos) == state) {
                level.setBlock(pos, state.setValue(POWER, power), Block.UPDATE_CLIENTS);
            }
            updateAround(level, pos);
        }
    }

    // Everything within two spaces, so paste one space over diagonally hears about the change too.
    private void updateAround(Level level, BlockPos pos) {
        level.updateNeighborsAt(pos, this);
        for (Direction direction : Direction.values()) {
            level.updateNeighborsAt(pos.relative(direction), this);
        }
    }

    private int blockSignal(Level level, BlockPos pos) {
        return Signals.readForDust(Channel.GREENSTONE, () -> {
            shouldSignal = false;
            try {
                return level.getBestNeighborSignal(pos);
            } finally {
                shouldSignal = true;
            }
        });
    }

    private int wireSignal(Level level, BlockPos pos) {
        int signal = 0;
        for (Direction first : Direction.values()) {
            BlockPos side = pos.relative(first);
            BlockState sideState = level.getBlockState(side);
            if (sideState.is(this)) {
                signal = Math.max(signal, sideState.getValue(POWER) - 1);
            } else if (sideState.getBlock() instanceof WallConverterBlock) {
                signal = Math.max(signal, sideState.getValue(WallConverterBlock.POWER) - 1);
            }
            for (Direction second : Direction.values()) {
                // Each diagonal once, and not through a block edge with solid blocks on both sides.
                if (second.getAxis() == first.getAxis() || second.ordinal() < first.ordinal()
                    || solid(level, side) && solid(level, pos.relative(second))) {
                    continue;
                }
                BlockState diagonal = level.getBlockState(side.relative(second));
                if (diagonal.is(this)) {
                    signal = Math.max(signal, diagonal.getValue(POWER) - 1);
                }
            }
        }
        return Math.max(0, signal);
    }

    private static boolean solid(Level level, BlockPos pos) {
        return level.getBlockState(pos).isRedstoneConductor(level, pos);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int power = state.getValue(POWER);
        if (power == 0 || random.nextFloat() > 0.2F) {
            return;
        }
        for (Direction face : Direction.values()) {
            if (hasFace(state, face) && random.nextBoolean()) {
                double x = pos.getX() + 0.5 + 0.45 * face.getStepX() + (face.getStepX() == 0 ? random.nextDouble() - 0.5 : 0.0);
                double y = pos.getY() + 0.5 + 0.45 * face.getStepY() + (face.getStepY() == 0 ? random.nextDouble() - 0.5 : 0.0);
                double z = pos.getZ() + 0.5 + 0.45 * face.getStepZ() + (face.getStepZ() == 0 ? random.nextDouble() - 0.5 : 0.0);
                level.addParticle(new DustParticleOptions(COLORS[power], 1.0F), x, y, z, 0.0, 0.0, 0.0);
                return;
            }
        }
    }

    @Override
    public Channel channel(BlockState state) {
        return Channel.GREENSTONE;
    }

    // Paste lies flat on its faces; no dust bends towards it.
    @Override
    public boolean connectsToWire(BlockState state, Channel wire, @Nullable Direction direction) {
        return false;
    }

    @Override
    protected boolean shouldRedstoneWireConnectTo(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return false;
    }

    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return false;
    }
}
