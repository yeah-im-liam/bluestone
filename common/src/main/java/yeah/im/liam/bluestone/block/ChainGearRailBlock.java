package yeah.im.liam.bluestone.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jspecify.annotations.Nullable;

/**
 * A straight rail that moves minecarts at a fixed speed the way it was placed, whatever speed or direction they come
 * in with. The way it drives is along its track: south or east, or north or west when {@link #REVERSED}, so it stays
 * right when other rails bend it round to the other axis.
 */
public class ChainGearRailBlock extends RailBlock {
    public static final BooleanProperty REVERSED = BooleanProperty.create("reversed");

    public ChainGearRailBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(REVERSED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(REVERSED);
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
