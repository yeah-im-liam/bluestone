package yeah.im.liam.bluestone.client;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import yeah.im.liam.bluestone.Bluestone;
import yeah.im.liam.bluestone.block.ConverterBoxBlock;
import yeah.im.liam.bluestone.signal.Channel;

/**
 * The pieces a glowing converter box is drawn from: its frame, edge lights and middle lights, loaded from
 * blockstates/converter_box_frame.json, converter_box_light.json and converter_box_center.json on block states that
 * belong to no block, as the game does for item frames.
 */
public final class ConverterBoxPieces {
    public static final Identifier FRAME_ID = Bluestone.id("converter_box_frame");
    public static final Identifier LIGHT_ID = Bluestone.id("converter_box_light");
    public static final Identifier CENTER_ID = Bluestone.id("converter_box_center");
    private static final EnumProperty<Direction> FACE = EnumProperty.create("face", Direction.class);
    private static final EnumProperty<Direction> EDGE = EnumProperty.create("edge", Direction.class);
    private static final EnumProperty<Colour> COLOUR = EnumProperty.create("color", Colour.class);
    public static final StateDefinition<Block, BlockState> FRAME_DEFINITION = new StateDefinition.Builder<Block, BlockState>(Blocks.AIR)
        .add(FACE)
        .create(Block::defaultBlockState, BlockState::new);
    public static final StateDefinition<Block, BlockState> LIGHT_DEFINITION = new StateDefinition.Builder<Block, BlockState>(Blocks.AIR)
        .add(FACE, EDGE, COLOUR)
        .create(Block::defaultBlockState, BlockState::new);
    public static final StateDefinition<Block, BlockState> CENTER_DEFINITION = new StateDefinition.Builder<Block, BlockState>(Blocks.AIR)
        .add(FACE, COLOUR)
        .create(Block::defaultBlockState, BlockState::new);

    private enum Colour implements StringRepresentable {
        OFF("off"),
        REDSTONE("redstone"),
        BLUESTONE("bluestone"),
        PURPLESTONE("purplestone"),
        GREENSTONE("greenstone");

        private final String name;

        Colour(String name) {
            this.name = name;
        }

        static Colour of(Channel channel) {
            return switch (channel) {
                case REDSTONE -> REDSTONE;
                case BLUESTONE -> BLUESTONE;
                case PURPLESTONE -> PURPLESTONE;
                case GREENSTONE -> GREENSTONE;
            };
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    private ConverterBoxPieces() {
    }

    // Nothing while all lights are off; otherwise every piece of each face the world would draw, facing the camera and
    // not covered.
    public static void collect(Level level, BlockPos pos, BlockState box, Vec3 camera, Consumer<BlockState> pieces) {
        Colour[][] lit = new Colour[6][6];
        Colour[] middle = new Colour[6];
        boolean[] any = {false};
        ConverterBoxBlock.lights(level, pos, (face, edge, colour) -> {
            any[0] = true;
            if (edge == null) {
                middle[face.ordinal()] = Colour.of(colour);
            } else {
                lit[face.ordinal()][edge.ordinal()] = Colour.of(colour);
            }
        });
        if (!any[0]) {
            return;
        }
        for (Direction face : Direction.values()) {
            //#if MC >= 1.21.2
            if (!facesCamera(pos, face, camera) || !Block.shouldRenderFace(box, level.getBlockState(pos.relative(face)), face)) {
            //#else
            //$$ if (!facesCamera(pos, face, camera) || !Block.shouldRenderFace(box, level, pos, face, pos.relative(face))) {
            //#endif
                continue;
            }
            pieces.accept(FRAME_DEFINITION.any().setValue(FACE, face));
            Colour center = middle[face.ordinal()];
            pieces.accept(CENTER_DEFINITION.any().setValue(FACE, face).setValue(COLOUR, center != null ? center : Colour.OFF));
            for (Direction edge : Direction.values()) {
                if (edge.getAxis() != face.getAxis()) {
                    Colour colour = lit[face.ordinal()][edge.ordinal()];
                    pieces.accept(LIGHT_DEFINITION.any().setValue(FACE, face).setValue(EDGE, edge).setValue(COLOUR, colour != null ? colour : Colour.OFF));
                }
            }
        }
    }

    // The blockstate file a piece comes from.
    public static Identifier id(BlockState piece) {
        return piece.hasProperty(EDGE) ? LIGHT_ID : piece.hasProperty(COLOUR) ? CENTER_ID : FRAME_ID;
    }

    private static boolean facesCamera(BlockPos pos, Direction face, Vec3 camera) {
        double plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? pos.get(face.getAxis()) + 1 : pos.get(face.getAxis());
        return (camera.get(face.getAxis()) - plane) * face.getAxisDirection().getStep() > 0;
    }
}
