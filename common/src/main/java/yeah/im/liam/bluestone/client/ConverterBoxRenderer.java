package yeah.im.liam.bluestone.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.Bluestone;
import yeah.im.liam.bluestone.block.ConverterBoxBlock;
import yeah.im.liam.bluestone.block.ConverterBoxBlockEntity;
import yeah.im.liam.bluestone.signal.Channel;

/**
 * Draws a converter box while any of its lights is on, glowing as a whole, the way a chiseled bookshelf shows its books:
 * each face of the block is nine flat pieces, and a lit light is its piece (a side piece for a light at an edge, the
 * middle piece for the one in the middle) in the overlay of its colour instead. The world draws the box like any block,
 * lit or not; while it is lit the whole block is drawn again here at full brightness, over the world's, nudged towards
 * the camera so it covers it exactly: the frame (the corners of the faces) and every side and middle piece, unlit or
 * lit. With the world's block always there, nothing shows through the box while the world redraws it after a change
 * (an empty model for the lit box did). The pieces are models of their own (models/block/converter_box_frame, converter_box_light_* and
 * converter_box_center_*), loaded through the made-up block states of blockstates/converter_box_frame.json,
 * converter_box_light.json and converter_box_center.json the way the game loads its item frame models
 * (BlockStateDefinitionsMixin).
 */
public final class ConverterBoxRenderer implements BlockEntityRenderer<ConverterBoxBlockEntity, ConverterBoxRenderer.State> {
    public static final Identifier FRAME_ID = Bluestone.id("converter_box_frame");
    public static final Identifier LIGHT_ID = Bluestone.id("converter_box_light");
    public static final Identifier CENTER_ID = Bluestone.id("converter_box_center");
    private static final EnumProperty<Direction> FACE = EnumProperty.create("face", Direction.class);
    private static final EnumProperty<Direction> EDGE = EnumProperty.create("edge", Direction.class);
    private static final EnumProperty<Colour> COLOUR = EnumProperty.create("color", Colour.class);
    public static final StateDefinition<Block, BlockState> FRAME_DEFINITION = new StateDefinition.Builder<Block, BlockState>(Blocks.AIR)
        .create(Block::defaultBlockState, BlockState::new);
    public static final StateDefinition<Block, BlockState> LIGHT_DEFINITION = new StateDefinition.Builder<Block, BlockState>(Blocks.AIR)
        .add(FACE, EDGE, COLOUR)
        .create(Block::defaultBlockState, BlockState::new);
    public static final StateDefinition<Block, BlockState> CENTER_DEFINITION = new StateDefinition.Builder<Block, BlockState>(Blocks.AIR)
        .add(FACE, COLOUR)
        .create(Block::defaultBlockState, BlockState::new);
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int[] NO_TINTS = new int[0];

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

    public static final class State extends BlockEntityRenderState {
        private final List<BlockStateModelPart> parts = new ArrayList<>();
    }

    private final RandomSource random = RandomSource.create();

    public ConverterBoxRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ConverterBoxBlockEntity converter, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(converter, state, breakProgress);
        Level level = converter.getLevel();
        BlockState box = converter.getBlockState();
        if (level == null || !box.hasProperty(ConverterBoxBlock.LIT) || !box.getValue(ConverterBoxBlock.LIT)) {
            return;
        }
        Colour[][] lit = new Colour[6][6];
        Colour[] middle = new Colour[6];
        ConverterBoxBlock.lights(level, converter.getBlockPos(), (face, edge, colour) -> {
            if (edge == null) {
                middle[face.ordinal()] = Colour.of(colour);
            } else {
                lit[face.ordinal()][edge.ordinal()] = Colour.of(colour);
            }
        });
        BlockStateModelSet models = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        collect(models, FRAME_DEFINITION.any(), state.parts);
        for (Direction face : Direction.values()) {
            Colour center = middle[face.ordinal()];
            collect(models, CENTER_DEFINITION.any().setValue(FACE, face).setValue(COLOUR, center != null ? center : Colour.OFF), state.parts);
            for (Direction edge : Direction.values()) {
                if (edge.getAxis() != face.getAxis()) {
                    Colour colour = lit[face.ordinal()][edge.ordinal()];
                    collect(models, LIGHT_DEFINITION.any().setValue(FACE, face).setValue(EDGE, edge).setValue(COLOUR, colour != null ? colour : Colour.OFF), state.parts);
                }
            }
        }
    }

    private void collect(BlockStateModelSet models, BlockState piece, List<BlockStateModelPart> parts) {
        random.setSeed(42L);
        models.get(piece).collectParts(random, parts);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.parts.isEmpty()) {
            collector.submitBlockModel(poseStack, RenderTypes.entityCutoutZOffset(TextureAtlas.LOCATION_BLOCKS), state.parts, NO_TINTS,
                FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        }
    }

    // A lit box is drawn only here, so it is drawn as far off as the world draws blocks.
    @Override
    public int getViewDistance() {
        return 1024;
    }
}
