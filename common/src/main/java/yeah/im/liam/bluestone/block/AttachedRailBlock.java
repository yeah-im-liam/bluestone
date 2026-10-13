package yeah.im.liam.bluestone.block;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A slime rail stuck to a wall or ceiling (the floor kind is an ordinary rail). {@link #ATTACHED} is the face it sits
 * on and {@link #TRAVEL} the way along it carts run; for chain gear rails that is also the way they drive carts.
 * {@link yeah.im.liam.bluestone.rail.SurfaceRails} moves the carts.
 */
public class AttachedRailBlock extends Block implements SimpleWaterloggedBlock {
    public static final EnumProperty<Direction> ATTACHED = EnumProperty.create("attached", Direction.class, direction -> direction != Direction.DOWN);
    public static final EnumProperty<Direction> TRAVEL = EnumProperty.create("travel", Direction.class);
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Map.of(
        Direction.UP, Block.box(0.0, 14.0, 0.0, 16.0, 16.0, 16.0),
        Direction.NORTH, Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 2.0),
        Direction.SOUTH, Block.box(0.0, 0.0, 14.0, 16.0, 16.0, 16.0),
        Direction.WEST, Block.box(0.0, 0.0, 0.0, 2.0, 16.0, 16.0),
        Direction.EAST, Block.box(14.0, 0.0, 0.0, 16.0, 16.0, 16.0)
    ));
    private final boolean drives;

    public AttachedRailBlock(Properties properties, boolean drives) {
        super(properties);
        this.drives = drives;
        registerDefaultState(stateDefinition.any()
            .setValue(ATTACHED, Direction.NORTH)
            .setValue(TRAVEL, Direction.UP)
            .setValue(BlockStateProperties.WATERLOGGED, false));
    }

    /**
     * Whether this is a chain gear rail, moving carts at a fixed speed the way it travels.
     */
    public boolean drives() {
        return drives;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ATTACHED, TRAVEL, BlockStateProperties.WATERLOGGED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(ATTACHED));
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction attached = state.getValue(ATTACHED);
        BlockPos support = pos.relative(attached);
        return level.getBlockState(support).isFaceSturdy(level, support, attached.getOpposite());
    }

    // On the face clicked, running the way the player looks along that face.
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction attached = context.getClickedFace().getOpposite();
        if (attached == Direction.DOWN) {
            return null;
        }
        for (Direction direction : context.getNearestLookingDirections()) {
            if (direction.getAxis() != attached.getAxis()) {
                BlockState state = defaultBlockState().setValue(ATTACHED, attached).setValue(TRAVEL, direction);
                return state.canSurvive(context.getLevel(), context.getClickedPos()) ? Waterlogging.place(state, context) : null;
            }
        }
        return null;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        Waterlogging.scheduleTick(state, level, ticks, pos);
        return directionToNeighbour == state.getValue(ATTACHED) && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Waterlogging.isWet(state) ? Waterlogging.WATER : super.getFluidState(state);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(ATTACHED, rotation.rotate(state.getValue(ATTACHED))).setValue(TRAVEL, rotation.rotate(state.getValue(TRAVEL)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(ATTACHED, mirror.mirror(state.getValue(ATTACHED))).setValue(TRAVEL, mirror.mirror(state.getValue(TRAVEL)));
    }
}
