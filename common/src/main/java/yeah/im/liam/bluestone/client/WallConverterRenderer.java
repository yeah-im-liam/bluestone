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
import yeah.im.liam.bluestone.block.WallConverterBlock;
import yeah.im.liam.bluestone.block.WallConverterBlockEntity;
import yeah.im.liam.bluestone.signal.Channel;

/**
 * Draws the wall converter's lit lights, glowing, the way a chiseled bookshelf shows its books: each face of the block
 * is made of nine flat pieces, and a lit light is its side piece again in the purplestone or the greenstone overlay, in
 * exactly the same place. The lit pieces are models of their own (models/block/wall_converter_light_*), loaded through
 * the made-up block states of blockstates/wall_converter_light.json the way the game loads its item frame models
 * (BlockStateDefinitionsMixin).
 */
public final class WallConverterRenderer implements BlockEntityRenderer<WallConverterBlockEntity, WallConverterRenderer.State> {
    public static final Identifier LIGHT_ID = Bluestone.id("wall_converter_light");
    private static final EnumProperty<Direction> FACE = EnumProperty.create("face", Direction.class);
    private static final EnumProperty<Direction> EDGE = EnumProperty.create("edge", Direction.class);
    private static final EnumProperty<Colour> COLOUR = EnumProperty.create("color", Colour.class);
    public static final StateDefinition<Block, BlockState> LIGHT_DEFINITION = new StateDefinition.Builder<Block, BlockState>(Blocks.AIR)
        .add(FACE, EDGE, COLOUR)
        .create(Block::defaultBlockState, BlockState::new);
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int[] NO_TINTS = new int[0];

    private enum Colour implements StringRepresentable {
        PURPLESTONE("purplestone"),
        GREENSTONE("greenstone");

        private final String name;

        Colour(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final class State extends BlockEntityRenderState {
        private final List<BlockStateModelPart> lights = new ArrayList<>();
    }

    private final RandomSource random = RandomSource.create();

    public WallConverterRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(WallConverterBlockEntity converter, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(converter, state, breakProgress);
        Level level = converter.getLevel();
        if (level == null) {
            return;
        }
        BlockStateModelSet models = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        WallConverterBlock.lights(level, converter.getBlockPos(), (face, edge, colour) -> {
            BlockState light = LIGHT_DEFINITION.any()
                .setValue(FACE, face)
                .setValue(EDGE, edge)
                .setValue(COLOUR, colour == Channel.GREENSTONE ? Colour.GREENSTONE : Colour.PURPLESTONE);
            random.setSeed(42L);
            models.get(light).collectParts(random, state.lights);
        });
    }

    // A lit piece lies in the plane of the unlit one under it, so it is drawn nudged towards the camera, as armour trims
    // are over armour: only which one is in front changes, not where it shows.
    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.lights.isEmpty()) {
            collector.submitBlockModel(poseStack, RenderTypes.entityCutoutZOffset(TextureAtlas.LOCATION_BLOCKS), state.lights, NO_TINTS,
                FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        }
    }
}
