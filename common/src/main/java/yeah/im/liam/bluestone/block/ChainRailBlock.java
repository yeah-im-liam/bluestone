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
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.rail.SurfaceRails;
import yeah.im.liam.bluestone.signal.Channel;

/**
 * A straight rail that, while powered by its colour, moves minecarts at a fixed speed the way it was placed, whatever
 * speed or direction they come in with; unpowered, it holds them still.
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

    // Unless placed while sneaking, it drives the way of a chain rail it joins, or of one beside it.
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        Direction follow = context.isSecondaryUseActive() ? null : follow(context.getLevel(), context.getClickedPos());
        if (follow != null) {
            return withDrive(state.setValue(SHAPE, follow.getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH), follow);
        }
        return withDrive(state, context.getHorizontalDirection());
    }

    private static @Nullable Direction follow(Level level, BlockPos pos) {
        for (Direction exit : Direction.Plane.HORIZONTAL) {
            Direction drive = SurfaceRails.chainDrive(level, new SurfaceRails.Track(pos, Direction.DOWN, exit.getAxis(), AttachedRailBlock.Kind.CHAIN, false, exit), exit);
            if (drive != null) {
                return drive;
            }
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockState next = level.getBlockState(pos.relative(side));
            Direction drive = next.getBlock() instanceof ChainRailBlock ? drive(next) : null;
            if (drive != null) {
                return drive;
            }
        }
        return null;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this) && !level.isClientSide()) {
            updatePowered(level, pos);
        }
    }

    //#if MC >= 1.21.2
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
    //#else
    //$$ @Override
    //$$ protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighbourPos, boolean movedByPiston) {
    //$$     Orientation orientation = null;
    //#endif
        //#if MC >= 1.21.2
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        //#else
        //$$ super.neighborChanged(state, level, pos, block, neighbourPos, movedByPiston);
        //#endif
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
