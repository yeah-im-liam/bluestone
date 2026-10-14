package yeah.im.liam.bluestone.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
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
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.Bluestone;
import yeah.im.liam.bluestone.block.WallConverterBlock;
import yeah.im.liam.bluestone.block.WallConverterBlockEntity;
import yeah.im.liam.bluestone.signal.Channel;

/**
 * Draws the wall converter's lit lights over the block, glowing, like the books of a chiseled bookshelf. Each light is
 * a model of its own (models/block/wall_converter_light_*), loaded through the made-up block states of
 * blockstates/wall_converter_light.json the way the game loads its item frame models (BlockStateDefinitionsMixin).
 */
public final class WallConverterRenderer implements BlockEntityRenderer<WallConverterBlockEntity, WallConverterRenderer.State> {
    public static final Identifier LIGHT_ID = Bluestone.id("wall_converter_light");
    private static final EnumProperty<Direction> FACE = EnumProperty.create("face", Direction.class);
    private static final EnumProperty<Direction> EDGE = EnumProperty.create("edge", Direction.class);
    private static final EnumProperty<Colour> COLOUR = EnumProperty.create("color", Colour.class);
    public static final StateDefinition<Block, BlockState> LIGHT_DEFINITION = new StateDefinition.Builder<Block, BlockState>(Blocks.AIR)
        .add(FACE, EDGE, COLOUR)
        .create(Block::defaultBlockState, BlockState::new);
    private static final BlockDisplayContext DISPLAY = BlockDisplayContext.create();
    private static final int FULL_BRIGHT = 0xF000F0;

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
        private final List<BlockModelRenderState> lights = new ArrayList<>();
    }

    private final BlockModelResolver models;

    public WallConverterRenderer(BlockEntityRendererProvider.Context context) {
        models = context.blockModelResolver();
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
        WallConverterBlock.lights(level, converter.getBlockPos(), (face, edge, colour) -> {
            BlockState light = LIGHT_DEFINITION.any()
                .setValue(FACE, face)
                .setValue(EDGE, edge)
                .setValue(COLOUR, colour == Channel.GREENSTONE ? Colour.GREENSTONE : Colour.PURPLESTONE);
            BlockModelRenderState model = new BlockModelRenderState();
            models.update(model, light, DISPLAY);
            state.lights.add(model);
        });
    }

    // The light models sit just off the block's faces (see gen_resources.py).
    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        for (BlockModelRenderState light : state.lights) {
            light.submit(poseStack, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        }
    }
}
