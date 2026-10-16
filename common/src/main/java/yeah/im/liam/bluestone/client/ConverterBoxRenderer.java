package yeah.im.liam.bluestone.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
//#if MC >= 26.1
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
//#else
//$$ import net.minecraft.client.renderer.block.model.BlockStateModel;
//#endif
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
//#if MC >= 26.1
import net.minecraft.client.renderer.state.level.CameraRenderState;
//#else
//$$ import net.minecraft.client.renderer.state.CameraRenderState;
//#endif
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
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
 * Draws a converter box glowing while any of its lights is on, over the world's own drawing of it and nudged towards
 * the camera so it covers it exactly. Faces the world would cull, facing away or covered, are left out.
 */
public final class ConverterBoxRenderer implements BlockEntityRenderer<ConverterBoxBlockEntity, ConverterBoxRenderer.State> {
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
        //#if MC >= 26.1
        private final List<BlockStateModelPart> parts = new ArrayList<>();
        //#else
        //$$ private final List<BlockStateModel> parts = new ArrayList<>();
        //#endif
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
        if (level == null) {
            return;
        }
        Colour[][] lit = new Colour[6][6];
        Colour[] middle = new Colour[6];
        boolean[] any = {false};
        ConverterBoxBlock.lights(level, converter.getBlockPos(), (face, edge, colour) -> {
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
        BlockPos pos = converter.getBlockPos();
        BlockState box = converter.getBlockState();
        //#if MC >= 26.1
        BlockStateModelSet models = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        //#else
        //$$ var models = Minecraft.getInstance().getBlockRenderer();
        //#endif
        for (Direction face : Direction.values()) {
            if (!facesCamera(pos, face, cameraPosition) || !Block.shouldRenderFace(box, level.getBlockState(pos.relative(face)), face)) {
                continue;
            }
            collect(models, FRAME_DEFINITION.any().setValue(FACE, face), state.parts);
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

    private static boolean facesCamera(BlockPos pos, Direction face, Vec3 camera) {
        double plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? pos.get(face.getAxis()) + 1 : pos.get(face.getAxis());
        return (camera.get(face.getAxis()) - plane) * face.getAxisDirection().getStep() > 0;
    }

    //#if MC >= 26.1
    private void collect(BlockStateModelSet models, BlockState piece, List<BlockStateModelPart> parts) {
        random.setSeed(42L);
        models.get(piece).collectParts(random, parts);
    }
    //#else
    //$$ private void collect(net.minecraft.client.renderer.block.BlockRenderDispatcher models, BlockState piece, List<BlockStateModel> parts) {
    //$$     parts.add(models.getBlockModel(piece));
    //$$ }
    //#endif

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        //#if MC >= 26.1
        if (!state.parts.isEmpty()) {
            collector.submitBlockModel(poseStack, RenderTypes.entityCutoutZOffset(TextureAtlas.LOCATION_BLOCKS), state.parts, NO_TINTS,
                FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        }
        //#else
        //$$ for (BlockStateModel model : state.parts) {
        //$$     collector.submitBlockModel(poseStack, RenderTypes.entityCutoutNoCullZOffset(TextureAtlas.LOCATION_BLOCKS), model, 1.0F, 1.0F, 1.0F,
        //$$         FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        //$$ }
        //#endif
    }

    @Override
    public int getViewDistance() {
        return 1024;
    }
}
