package yeah.im.liam.bluestone.block;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.signal.Channel;
import yeah.im.liam.bluestone.signal.SignalBlock;
import yeah.im.liam.bluestone.signal.Signals;

/**
 * Greenstone paste: redstone dust for greenstone that sticks to any face. Unlike dust it doesn't climb blocks by
 * itself; each face is placed on its own, like glow lichen, and one space can have paste on several of its faces.
 * <p>
 * There is one property per direction: {@link Side#FACE} where paste lies on that face, {@link Side#LINE} where the
 * paste runs off that way, and {@link Side#NONE}. Each face draws dust lines towards the edges whose direction isn't
 * none. Paste joins the paste next to it on the same flat surface, paste round the edge of the block it lies on, and
 * the other faces of its own space, and loses one level per space. It hears only greenstone and plain switches, and
 * meets purplestone through the wall converter.
 */
public class GreenstonePasteBlock extends Block implements SignalBlock {
    public static final EnumProperty<Side> NORTH = EnumProperty.create("north", Side.class);
    public static final EnumProperty<Side> EAST = EnumProperty.create("east", Side.class);
    public static final EnumProperty<Side> SOUTH = EnumProperty.create("south", Side.class);
    public static final EnumProperty<Side> WEST = EnumProperty.create("west", Side.class);
    public static final EnumProperty<Side> UP = EnumProperty.create("up", Side.class);
    public static final EnumProperty<Side> DOWN = EnumProperty.create("down", Side.class);
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    private static final Map<Direction, EnumProperty<Side>> SIDES = new EnumMap<>(Map.of(
        Direction.NORTH, NORTH, Direction.EAST, EAST, Direction.SOUTH, SOUTH, Direction.WEST, WEST, Direction.UP, UP, Direction.DOWN, DOWN));
    // Redstone's colours with the red and green channels swapped.
    private static final int[] COLORS = ColoredWireBlock.colorsFor((red, green, blue) -> ARGB.colorFromFloat(1.0F, green, red, blue));
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Map.of(
        Direction.DOWN, Block.box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0),
        Direction.UP, Block.box(0.0, 15.0, 0.0, 16.0, 16.0, 16.0),
        Direction.NORTH, Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 1.0),
        Direction.SOUTH, Block.box(0.0, 0.0, 15.0, 16.0, 16.0, 16.0),
        Direction.WEST, Block.box(0.0, 0.0, 0.0, 1.0, 16.0, 16.0),
        Direction.EAST, Block.box(15.0, 0.0, 0.0, 16.0, 16.0, 16.0)
    ));
    // The outline for each set of faces, by the faces' bits (1 << ordinal).
    private static final VoxelShape[] OUTLINES = new VoxelShape[64];
    private boolean shouldSignal = true;

    static {
        for (int faces = 0; faces < OUTLINES.length; faces++) {
            VoxelShape shape = Shapes.empty();
            for (Direction face : Direction.values()) {
                if ((faces & 1 << face.ordinal()) != 0) {
                    shape = Shapes.or(shape, SHAPES.get(face));
                }
            }
            OUTLINES[faces] = shape;
        }
    }

    public enum Side implements StringRepresentable {
        NONE("none"),
        LINE("line"),
        FACE("face");

        private final String name;

        Side(String name) {
            this.name = name;
        }

        public boolean reaches() {
            return this != NONE;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public GreenstonePasteBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(POWER, 0);
        for (EnumProperty<Side> side : SIDES.values()) {
            state = state.setValue(side, Side.NONE);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, POWER);
    }

    public static EnumProperty<Side> side(Direction direction) {
        return SIDES.get(direction);
    }

    public static boolean hasFace(BlockState state, Direction face) {
        return state.getValue(SIDES.get(face)) == Side.FACE;
    }

    private static List<Direction> faces(BlockState state) {
        List<Direction> faces = new ArrayList<>(6);
        for (Direction direction : Direction.values()) {
            if (hasFace(state, direction)) {
                faces.add(direction);
            }
        }
        return faces;
    }

    /**
     * Where a direction on a face points in the world. On the floor they are the same; on the ceiling north stays
     * north and east becomes west, as seen from below; on a wall north is up and east is to the right, as seen from
     * in front.
     */
    public static Direction toWorld(Direction face, Direction side) {
        Direction north = face.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP;
        Vec3i cross = face.getUnitVec3i().cross(north.getUnitVec3i());
        Direction east = Direction.getApproximateNearest(cross.getX(), cross.getY(), cross.getZ());
        return switch (side) {
            case NORTH -> north;
            case SOUTH -> north.getOpposite();
            case EAST -> east;
            default -> east.getOpposite();
        };
    }

    public int colorForPower(int power) {
        return COLORS[power];
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int faces = 0;
        for (Direction face : faces(state)) {
            faces |= 1 << face.ordinal();
        }
        return OUTLINES[faces];
    }

    private static boolean supported(LevelReader level, BlockPos pos, Direction face) {
        BlockPos support = pos.relative(face);
        return level.getBlockState(support).isFaceSturdy(level, support, face.getOpposite());
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        List<Direction> faces = faces(state);
        return !faces.isEmpty() && faces.stream().allMatch(face -> supported(level, pos, face));
    }

    // Paste goes on the face clicked; clicking paste already there adds the nearest empty face the player looks at.
    private @Nullable Direction placingFace(BlockState state, BlockPlaceContext context) {
        List<Direction> faces = new ArrayList<>(7);
        if (!context.replacingClickedOnBlock()) {
            faces.add(context.getClickedFace().getOpposite());
        } else {
            faces.addAll(Arrays.asList(context.getNearestLookingDirections()));
        }
        for (Direction face : faces) {
            if (!(state.is(this) && hasFace(state, face)) && supported(context.getLevel(), context.getClickedPos(), face)) {
                return face;
            }
        }
        return null;
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return context.getItemInHand().is(asItem()) && placingFace(state, context) != null;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState existing = context.getLevel().getBlockState(context.getClickedPos());
        BlockState state = existing.is(this) ? existing : defaultBlockState();
        Direction face = placingFace(state, context);
        return face == null ? null : withConnections(context.getLevel(), context.getClickedPos(), state.setValue(SIDES.get(face), Side.FACE));
    }

    // A face that loses the block under it drops off on the next tick; with no faces left the paste is gone.
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (faces(state).isEmpty()) {
            return Blocks.AIR.defaultBlockState();
        }
        if (hasFace(state, directionToNeighbour) && !supported(level, pos, directionToNeighbour)) {
            if (faces(state).size() == 1) {
                return Blocks.AIR.defaultBlockState();
            }
            ticks.scheduleTick(pos, this, 1);
        }
        return withConnections(level, pos, state);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState updated = state;
        for (Direction face : faces(state)) {
            if (!supported(level, pos, face)) {
                updated = updated.setValue(SIDES.get(face), Side.NONE);
                Block.popResource(level, pos, new ItemStack(asItem()));
            }
        }
        if (updated != state) {
            level.setBlock(pos, faces(updated).isEmpty() ? Blocks.AIR.defaultBlockState() : withConnections(level, pos, updated), Block.UPDATE_ALL);
        }
    }

    // Paste round an edge connects too, and isn't next to this space, so it is told when this space changes.
    @Override
    protected void updateIndirectNeighbourShapes(BlockState state, LevelAccessor level, BlockPos pos, int flags, int updateLimit) {
        for (BlockPos other : diagonals(pos)) {
            BlockState otherState = level.getBlockState(other);
            if (otherState.is(this)) {
                Block.updateOrDestroy(otherState, withConnections(level, other, otherState), level, other, flags & ~Block.UPDATE_NEIGHBORS, updateLimit);
            }
        }
    }

    private static List<BlockPos> diagonals(BlockPos pos) {
        List<BlockPos> positions = new ArrayList<>(12);
        for (Direction first : Direction.values()) {
            for (Direction second : Direction.values()) {
                if (first.ordinal() < second.ordinal() && first.getAxis() != second.getAxis()) {
                    positions.add(pos.relative(first).relative(second));
                }
            }
        }
        return positions;
    }

    private BlockState withConnections(BlockGetter level, BlockPos pos, BlockState state) {
        List<Direction> faces = faces(state);
        Map<Direction, Side> sides = new EnumMap<>(Direction.class);
        for (Direction way : Direction.values()) {
            sides.put(way, faces.contains(way) ? Side.FACE : links(level, pos, faces, way) ? Side.LINE : Side.NONE);
        }
        // Like dust: a face with no lines is a cross, and a face with a line one way runs straight on through.
        Map<Direction, Side> drawn = new EnumMap<>(sides);
        for (Direction face : faces) {
            List<Direction> edges = new ArrayList<>(4);
            for (Direction way : Direction.values()) {
                if (way.getAxis() != face.getAxis() && sides.get(way).reaches()) {
                    edges.add(way);
                }
            }
            for (Direction way : Direction.values()) {
                if (way.getAxis() != face.getAxis() && !drawn.get(way).reaches()
                    && (edges.isEmpty() || edges.size() == 1 && edges.getFirst() == way.getOpposite())) {
                    drawn.put(way, Side.LINE);
                }
            }
        }
        for (Map.Entry<Direction, Side> entry : drawn.entrySet()) {
            state = state.setValue(SIDES.get(entry.getKey()), entry.getValue());
        }
        return state;
    }

    // Whether any face runs off the given way: into paste on the same flat surface, round the edge of the block it lies
    // on, or into something it bends towards.
    private boolean links(BlockGetter level, BlockPos pos, List<Direction> faces, Direction way) {
        boolean across = false;
        for (Direction face : faces) {
            if (face.getAxis() != way.getAxis()) {
                across = true;
                if (isPaste(level, pos.relative(way), face) || isPaste(level, pos.relative(way).relative(face), way.getOpposite())) {
                    return true;
                }
            }
        }
        return across && connectsTo(level.getBlockState(pos.relative(way)), faces, way);
    }

    private boolean isPaste(BlockGetter level, BlockPos pos, Direction face) {
        BlockState state = level.getBlockState(pos);
        return state.is(this) && hasFace(state, face);
    }

    // Greenstone parts and plain switches bend paste towards them, the way dust bends towards a lever or a torch.
    private static boolean connectsTo(BlockState state, List<Direction> faces, Direction way) {
        if (state.getBlock() instanceof WallConverterBlock) {
            return true;
        }
        if (state.getBlock() instanceof GreenstoneDiodeBlock) {
            return faces.contains(state.getValue(GreenstoneDiodeBlock.ATTACHED)) && state.getValue(GreenstoneDiodeBlock.OUTPUT).getAxis() == way.getAxis();
        }
        if (!state.isSignalSource()) {
            return false;
        }
        Channel channel = Channel.ofSource(state);
        return channel == null || channel == Channel.GREENSTONE;
    }

    /**
     * The paste this space passes power to and takes it from: on the same flat surface or round an edge.
     */
    private List<BlockPos> connectedPaste(Level level, BlockPos pos, BlockState state) {
        List<Direction> faces = faces(state);
        List<BlockPos> paste = new ArrayList<>();
        for (Direction face : faces) {
            for (Direction way : Direction.values()) {
                if (way.getAxis() == face.getAxis()) {
                    continue;
                }
                if (isPaste(level, pos.relative(way), face)) {
                    paste.add(pos.relative(way));
                }
                if (isPaste(level, pos.relative(way).relative(face), way.getOpposite())) {
                    paste.add(pos.relative(way).relative(face));
                }
            }
        }
        return paste;
    }

    // Like dust, it powers the blocks it lies on and the blocks its lines point into.
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (!shouldSignal) {
            return 0;
        }
        return state.getValue(SIDES.get(direction.getOpposite())).reaches() ? state.getValue(POWER) : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return shouldSignal;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (level.isClientSide()) {
            return;
        }
        if (!oldState.is(this)) {
            updatePower(level, pos, state);
            updateAround(level, pos);
        } else if (!faces(oldState).equals(faces(state))) {
            // A face added or dropped: what it lies on and points into changes.
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
        int power = Math.max(blockSignal(level, pos), wireSignal(level, pos, state));
        if (power != state.getValue(POWER)) {
            if (level.getBlockState(pos) == state) {
                level.setBlock(pos, state.setValue(POWER, power), Block.UPDATE_CLIENTS);
            }
            updateAround(level, pos);
        }
    }

    // Everything within two spaces, so paste round an edge hears about the change too.
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

    private int wireSignal(Level level, BlockPos pos, BlockState state) {
        int signal = 0;
        for (BlockPos other : connectedPaste(level, pos, state)) {
            signal = Math.max(signal, level.getBlockState(other).getValue(POWER) - 1);
        }
        for (Direction direction : Direction.values()) {
            BlockState neighbour = level.getBlockState(pos.relative(direction));
            if (neighbour.getBlock() instanceof WallConverterBlock) {
                signal = Math.max(signal, neighbour.getValue(WallConverterBlock.POWER) - 1);
            }
        }
        return Math.max(0, signal);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int power = state.getValue(POWER);
        List<Direction> faces = faces(state);
        if (power == 0 || faces.isEmpty() || random.nextFloat() > 0.2F) {
            return;
        }
        Direction face = faces.get(random.nextInt(faces.size()));
        double x = pos.getX() + 0.5 + 0.45 * face.getStepX() + (face.getStepX() == 0 ? (random.nextDouble() - 0.5) * 0.4 : 0.0);
        double y = pos.getY() + 0.5 + 0.45 * face.getStepY() + (face.getStepY() == 0 ? (random.nextDouble() - 0.5) * 0.4 : 0.0);
        double z = pos.getZ() + 0.5 + 0.45 * face.getStepZ() + (face.getStepZ() == 0 ? (random.nextDouble() - 0.5) * 0.4 : 0.0);
        level.addParticle(new DustParticleOptions(COLORS[power], 1.0F), x, y, z, 0.0, 0.0, 0.0);
    }

    @Override
    public Channel channel(BlockState state) {
        return Channel.GREENSTONE;
    }

    // No dust bends towards paste.
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
