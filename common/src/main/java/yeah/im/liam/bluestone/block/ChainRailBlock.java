package yeah.im.liam.bluestone.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.signal.Channel;

/**
 * A straight rail that, while powered by its colour, moves minecarts at a fixed speed the way it was placed, whatever
 * speed or direction they come in with; unpowered, it holds them still. It gets power like a powered rail. The way it
 * drives is along its track: south or east, or north or west when {@link #REVERSED}, so it stays right when other
 * rails bend it round to the other axis.
 */
public class ChainRailBlock extends RailBlock {
    public static final BooleanProperty REVERSED = BooleanProperty.create("reversed");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private final Channel channel;

    public ChainRailBlock(Properties properties, Channel channel) {
        super(properties);
        this.channel = channel;
        registerDefaultState(defaultBlockState().setValue(REVERSED, false).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(REVERSED, POWERED);
    }

    public Channel channel() {
        return channel;
    }

    // Straight and sloped only, like a powered rail.
    @Override
    public boolean isStraight() {
        return true;
    }

    public static @Nullable Direction drive(BlockState state) {
        boolean reversed = state.getValue(REVERSED);
        return switch (state.getValue(SHAPE)) {
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> reversed ? Direction.NORTH : Direction.SOUTH;
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> reversed ? Direction.WEST : Direction.EAST;
            default -> null;
        };
    }

    private static BlockState withDrive(BlockState state, @Nullable Direction drive) {
        return drive == null ? state : state.setValue(REVERSED, drive == Direction.NORTH || drive == Direction.WEST);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : withDrive(state, context.getHorizontalDirection());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this) && !level.isClientSide()) {
            updatePowered(level, pos);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (!level.isClientSide()) {
            updatePowered(level, pos);
        }
    }

    private void updatePowered(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this)) {
            return;
        }
        boolean powered = RailPower.floor(level, pos, state, channel) || RailPower.along(level, pos, state);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
            level.updateNeighborsAt(pos.below(), this);
            if (state.getValue(SHAPE).isSlope()) {
                level.updateNeighborsAt(pos.above(), this);
            }
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Direction drive = drive(state);
        return withDrive(super.rotate(state, rotation), drive == null ? null : rotation.rotate(drive));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        Direction drive = drive(state);
        return withDrive(super.mirror(state, mirror), drive == null ? null : mirror.mirror(drive));
    }
}
