package yeah.im.liam.bluestone.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
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
 */
public class WallConverterBlock extends Block implements SignalBlock {
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    private static final Listener LISTENER = source -> source == Channel.PURPLESTONE || source == Channel.GREENSTONE;

    public WallConverterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWER, 0));
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
            } else if (neighbour.getBlock() instanceof GreenstonePasteBlock) {
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
