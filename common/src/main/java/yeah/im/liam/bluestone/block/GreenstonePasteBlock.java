package yeah.im.liam.bluestone.block;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.signal.Channel;
import yeah.im.liam.bluestone.signal.SignalBlock;
import yeah.im.liam.bluestone.signal.Signals;

/**
 * Greenstone paste: redstone dust for greenstone that sticks to any face.
 */
public class GreenstonePasteBlock extends Block implements SignalBlock {
    public static final EnumProperty<Side> NORTH = EnumProperty.create("north", Side.class);
    public static final EnumProperty<Side> EAST = EnumProperty.create("east", Side.class);
    public static final EnumProperty<Side> SOUTH = EnumProperty.create("south", Side.class);
    public static final EnumProperty<Side> WEST = EnumProperty.create("west", Side.class);
    public static final EnumProperty<Side> UP = EnumProperty.create("up", Side.class);
    public static final EnumProperty<Side> DOWN = EnumProperty.create("down", Side.class);
    public static final EnumProperty<Shared> SHARED = EnumProperty.create("shared", Shared.class);
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    private static final Map<Direction, EnumProperty<Side>> SIDES = new EnumMap<>(Map.of(
        Direction.NORTH, NORTH, Direction.EAST, EAST, Direction.SOUTH, SOUTH, Direction.WEST, WEST, Direction.UP, UP, Direction.DOWN, DOWN));
    private static final int[] COLORS = ColoredWireBlock.colorsFor((red, green, blue) -> ColoredWireBlock.opaque(green, red, blue));
    private static final VoxelShape[][] OUTLINES = new VoxelShape[6][16];
    private static final Direction[] FACE_SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    static {
        AABB middle = new AABB(3.0, 0.0, 3.0, 13.0, 1.0, 13.0);
        AABB[] lines = {new AABB(3.0, 0.0, 0.0, 13.0, 1.0, 8.0), new AABB(8.0, 0.0, 3.0, 16.0, 1.0, 13.0),
            new AABB(3.0, 0.0, 8.0, 13.0, 1.0, 16.0), new AABB(0.0, 0.0, 3.0, 8.0, 1.0, 13.0)};
        for (Direction face : Direction.values()) {
            for (int drawn = 0; drawn < 16; drawn++) {
                VoxelShape shape = turn(middle, face);
                for (int i = 0; i < 4; i++) {
                    if ((drawn & 1 << i) != 0) {
                        shape = Shapes.or(shape, turn(lines[i], face));
                    }
                }
                OUTLINES[face.ordinal()][drawn] = shape;
            }
        }
    }

    private boolean shouldSignal = true;

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

    public enum Shared implements StringRepresentable {
        ALL("all"),
        FIRST("first"),
        LAST("last");

        private final String name;

        Shared(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    private record Remaining(LevelAccessor level, BlockPos pos, BlockState state) {
    }

    private static final ThreadLocal<@Nullable Remaining> REMAINING = new ThreadLocal<>();

    public GreenstonePasteBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(SHARED, Shared.ALL).setValue(POWER, 0);
        for (EnumProperty<Side> side : SIDES.values()) {
            state = state.setValue(side, Side.NONE);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, SHARED, POWER);
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

    private static VoxelShape turn(AABB box, Direction face) {
        Vec3 north = Vec3.atLowerCornerOf(toWorld(face, Direction.NORTH).getUnitVec3i());
        Vec3 east = Vec3.atLowerCornerOf(toWorld(face, Direction.EAST).getUnitVec3i());
        Vec3 up = Vec3.atLowerCornerOf(face.getOpposite().getUnitVec3i());
        Vec3 min = null;
        Vec3 max = null;
        for (double x : new double[]{box.minX, box.maxX}) {
            for (double y : new double[]{box.minY, box.maxY}) {
                for (double z : new double[]{box.minZ, box.maxZ}) {
                    Vec3 corner = new Vec3(0.5, 0.5, 0.5).add(east.scale(x / 16.0 - 0.5)).add(up.scale(y / 16.0 - 0.5)).add(north.scale(0.5 - z / 16.0));
                    min = min == null ? corner : new Vec3(Math.min(min.x, corner.x), Math.min(min.y, corner.y), Math.min(min.z, corner.z));
                    max = max == null ? corner : new Vec3(Math.max(max.x, corner.x), Math.max(max.y, corner.y), Math.max(max.z, corner.z));
                }
            }
        }
        return Shapes.box(min.x, min.y, min.z, max.x, max.y, max.z);
    }

    public static boolean draws(BlockState state, Direction face, Direction way) {
        Side side = state.getValue(SIDES.get(way));
        if (side != Side.LINE) {
            return side == Side.FACE;
        }
        Shared shared = state.getValue(SHARED);
        if (shared == Shared.ALL) {
            return true;
        }
        for (Direction other : Direction.values()) {
            if (other != face && other.getAxis() != way.getAxis() && hasFace(state, other)
                && (shared == Shared.FIRST ? other.ordinal() < face.ordinal() : other.ordinal() > face.ordinal())) {
                return false;
            }
        }
        return true;
    }

    private static VoxelShape outline(BlockState state, Direction face) {
        int drawn = 0;
        for (int i = 0; i < 4; i++) {
            if (draws(state, face, toWorld(face, FACE_SIDES[i]))) {
                drawn |= 1 << i;
            }
        }
        return OUTLINES[face.ordinal()][drawn];
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext entityContext && entityContext.getEntity() instanceof Player player) {
            Direction face = lookedAt(state, pos, player);
            if (face != null) {
                return outline(state, face);
            }
        }
        VoxelShape shape = Shapes.empty();
        for (Direction face : faces(state)) {
            shape = Shapes.or(shape, outline(state, face));
        }
        return shape;
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
        return face == null ? null : withConnections(context.getLevel(), context.getClickedPos(), state.setValue(SIDES.get(face), Side.FACE), false);
    }

    //#if MC >= 1.21.2
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
    //#else
    //$$ @Override
    //$$ protected BlockState updateShape(BlockState state, Direction directionToNeighbour, BlockState neighbourState, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
    //$$     LevelAccessor ticks = level;
    //$$     RandomSource random = level.getRandom();
    //#endif
        if (faces(state).isEmpty()) {
            return Blocks.AIR.defaultBlockState();
        }
        if (hasFace(state, directionToNeighbour) && !supported(level, pos, directionToNeighbour)) {
            if (faces(state).size() == 1) {
                return Blocks.AIR.defaultBlockState();
            }
            ticks.scheduleTick(pos, this, 1);
        }
        return withConnections(level, pos, state, true);
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
            level.setBlock(pos, faces(updated).isEmpty() ? Blocks.AIR.defaultBlockState() : withConnections(level, pos, updated, true), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void updateIndirectNeighbourShapes(BlockState state, LevelAccessor level, BlockPos pos, int flags, int updateLimit) {
        for (BlockPos other : diagonals(pos)) {
            BlockState otherState = level.getBlockState(other);
            if (otherState.is(this)) {
                Block.updateOrDestroy(otherState, withConnections(level, other, otherState, true), level, other, flags & ~Block.UPDATE_NEIGHBORS, updateLimit);
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

    private static boolean isDot(BlockState state) {
        List<Direction> faces = faces(state);
        if (faces.size() != 1) {
            return false;
        }
        for (Direction way : Direction.values()) {
            if (way.getAxis() != faces.get(0).getAxis() && state.getValue(SIDES.get(way)) != Side.NONE) {
                return false;
            }
        }
        return true;
    }

    private BlockState withConnections(BlockGetter level, BlockPos pos, BlockState state, boolean keepDot) {
        boolean dot = keepDot && isDot(state);
        List<Direction> faces = faces(state);
        Map<Direction, Set<Direction>> draws = new EnumMap<>(Direction.class);
        for (Direction face : faces) {
            Set<Direction> ways = EnumSet.noneOf(Direction.class);
            List<Direction> edges = new ArrayList<>(4);
            for (Direction way : Direction.values()) {
                if (way.getAxis() == face.getAxis()) {
                    continue;
                }
                if (faces.contains(way)) {
                    edges.add(way);
                } else if (links(level, pos, face, way)) {
                    ways.add(way);
                    edges.add(way);
                }
            }
            for (Direction way : Direction.values()) {
                if (way.getAxis() != face.getAxis() && !faces.contains(way)
                    && (edges.isEmpty() && !dot || edges.size() == 1 && edges.get(0) == way.getOpposite())) {
                    ways.add(way);
                }
            }
            draws.put(face, ways);
        }
        Shared shared = null;
        boolean clash = false;
        for (Direction way : Direction.values()) {
            List<Direction> bordering = new ArrayList<>(4);
            List<Direction> drawing = new ArrayList<>(4);
            for (Direction face : faces) {
                if (face.getAxis() != way.getAxis()) {
                    bordering.add(face);
                    if (draws.get(face).contains(way)) {
                        drawing.add(face);
                    }
                }
            }
            Side side = faces.contains(way) ? Side.FACE : drawing.isEmpty() ? Side.NONE : Side.LINE;
            state = state.setValue(SIDES.get(way), side);
            if (side == Side.LINE && bordering.size() > 1) {
                Shared needed = drawing.size() == bordering.size() ? Shared.ALL
                    : drawing.equals(List.of(bordering.get(0))) ? Shared.FIRST
                    : drawing.equals(List.of(bordering.get(bordering.size() - 1))) ? Shared.LAST : Shared.ALL;
                clash |= shared != null && shared != needed;
                shared = needed;
            }
        }
        return state.setValue(SHARED, shared == null || clash ? Shared.ALL : shared);
    }

    private boolean links(BlockGetter level, BlockPos pos, Direction face, Direction way) {
        BlockPos ahead = pos.relative(way);
        return isPaste(level, ahead, face) || isPaste(level, ahead.relative(face), way.getOpposite())
            || connectsTo(level, ahead, level.getBlockState(ahead), face, way) || onBoxFace(level, ahead, face);
    }

    // The surface carries on onto the face of a converter box that nothing covers.
    private static boolean onBoxFace(BlockGetter level, BlockPos ahead, Direction face) {
        return level.getBlockState(ahead.relative(face)).getBlock() instanceof ConverterBoxBlock
            && !level.getBlockState(ahead).isFaceSturdy(level, ahead, face);
    }

    private boolean isPaste(BlockGetter level, BlockPos pos, Direction face) {
        BlockState state = level.getBlockState(pos);
        return state.is(this) && hasFace(state, face);
    }

    private static boolean connectsTo(BlockGetter level, BlockPos pos, BlockState state, Direction face, Direction way) {
        if (state.getBlock() instanceof ConverterBoxBlock) {
            return true;
        }
        if (state.getBlock() instanceof GreenstoneDiodeBlock) {
            return state.getValue(GreenstoneDiodeBlock.ATTACHED) == face
                && (state.getBlock() instanceof GreenstoneComparatorBlock || state.getValue(GreenstoneDiodeBlock.OUTPUT).getAxis() == way.getAxis());
        }
        if (!state.isSignalSource() || state.getBlock() instanceof GreenstonePasteBlock) {
            return false;
        }
        Channel channel = Channel.ofSource(state);
        if (channel != null && channel != Channel.GREENSTONE) {
            return false;
        }
        Direction support = support(level, pos, state);
        return support == null || support == face;
    }

    private static @Nullable Direction support(BlockGetter level, BlockPos pos, BlockState state) {
        if (Block.isShapeFullBlock(state.getShape(level, pos))) {
            return null;
        }
        if (state.hasProperty(BlockStateProperties.ATTACH_FACE) && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return switch (state.getValue(BlockStateProperties.ATTACH_FACE)) {
                case FLOOR -> Direction.DOWN;
                case CEILING -> Direction.UP;
                case WALL -> state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
            };
        }
        Block block = state.getBlock();
        if ((block instanceof WallTorchBlock || block instanceof RedstoneWallTorchBlock || block instanceof TripWireHookBlock)
            && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
        }
        if (block instanceof LightningRodBlock && state.hasProperty(BlockStateProperties.FACING)) {
            return state.getValue(BlockStateProperties.FACING).getOpposite();
        }
        return Direction.DOWN;
    }

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
            updatePower(level, pos, state);
            updateAround(level, pos);
        }
    }

    // Blocks are used through use() with any item before 1.20.5.
    //#if MC >= 1.20.5
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
    //#else
    //$$ @Override
    //$$ public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hitResult) {
    //#endif
        List<Direction> faces = faces(state);
        if (!player.getAbilities().mayBuild || faces.size() != 1) {
            return InteractionResult.PASS;
        }
        Direction face = faces.get(0);
        BlockState switched = state.setValue(SHARED, Shared.ALL);
        for (Direction way : Direction.values()) {
            if (way.getAxis() != face.getAxis()) {
                if (links(level, pos, face, way)) {
                    return InteractionResult.PASS;
                }
                switched = switched.setValue(SIDES.get(way), isDot(state) ? Side.LINE : Side.NONE);
            }
        }
        level.setBlock(pos, switched, Block.UPDATE_ALL);
        if (!level.isClientSide()) {
            updateAround(level, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        REMAINING.remove();
        if (faces(state).size() > 1) {
            Direction target = lookedAt(state, pos, player);
            if (target == null) {
                target = facing(state, player);
            }
            REMAINING.set(new Remaining(level, pos.immutable(), state.setValue(SIDES.get(target), Side.NONE)));
            state = defaultBlockState().setValue(SIDES.get(target), Side.FACE).setValue(POWER, state.getValue(POWER));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {
        Remaining remaining = REMAINING.get();
        if (remaining != null && remaining.level() == level && remaining.pos().equals(pos)) {
            REMAINING.remove();
            level.setBlock(pos, withConnections(level, pos, remaining.state(), true), Block.UPDATE_ALL);
        }
    }

    private static @Nullable Direction lookedAt(BlockState state, BlockPos pos, Player player) {
        Vec3 eye = player.getEyePosition();
        // Players have an interaction range attribute from 1.20.5; before, the reach is fixed.
        //#if MC >= 1.20.5
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(player.blockInteractionRange() + 1.0));
        //#else
        //$$ Vec3 end = eye.add(player.getViewVector(1.0F).scale((player.isCreative() ? 5.0 : 4.5) + 1.0));
        //#endif
        Direction best = null;
        double nearest = Double.MAX_VALUE;
        for (Direction face : faces(state)) {
            BlockHitResult hit = outline(state, face).clip(eye, end, pos);
            if (hit != null && hit.getLocation().distanceToSqr(eye) < nearest) {
                best = face;
                nearest = hit.getLocation().distanceToSqr(eye);
            }
        }
        return best;
    }

    private static Direction facing(BlockState state, Player player) {
        Vec3 look = player.getViewVector(1.0F);
        Direction best = null;
        double straightest = -Double.MAX_VALUE;
        for (Direction face : faces(state)) {
            double toward = look.dot(Vec3.atLowerCornerOf(face.getUnitVec3i()));
            if (toward > straightest) {
                best = face;
                straightest = toward;
            }
        }
        return best;
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
        if (!movedByPiston) {
            updateAround(level, pos);
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
            if (neighbour.getBlock() instanceof ConverterBoxBlock && state.getValue(SIDES.get(direction)).reaches()) {
                signal = Math.max(signal, neighbour.getValue(ConverterBoxBlock.POWER) - 1);
            }
        }
        for (Direction face : faces(state)) {
            for (Direction way : Direction.values()) {
                if (way.getAxis() != face.getAxis() && draws(state, face, way)) {
                    BlockState box = level.getBlockState(pos.relative(way).relative(face));
                    if (box.getBlock() instanceof ConverterBoxBlock) {
                        signal = Math.max(signal, box.getValue(ConverterBoxBlock.POWER) - 1);
                    }
                }
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
        level.addParticle(Particles.dust(COLORS[power], 1.0F), x, y, z, 0.0, 0.0, 0.0);
    }

    @Override
    public Channel channel(BlockState state) {
        return Channel.GREENSTONE;
    }

    @Override
    public boolean connectsToWire(BlockState state, Channel wire, @Nullable Direction direction) {
        return false;
    }

    //#if MC >= 26.1
    @Override
    //#endif
    protected boolean shouldRedstoneWireConnectTo(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return false;
    }

    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return false;
    }
}
