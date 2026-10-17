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
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.block.ConverterBoxBlockEntity;

/**
 * Draws a converter box glowing while any of its lights is on, over the world's own drawing of it and nudged towards
 * the camera so it covers it exactly. Faces the world would cull, facing away or covered, are left out.
 */
public final class ConverterBoxRenderer implements BlockEntityRenderer<ConverterBoxBlockEntity, ConverterBoxRenderer.State> {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int[] NO_TINTS = new int[0];

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
        //#if MC >= 26.1
        BlockStateModelSet models = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        ConverterBoxPieces.collect(level, converter.getBlockPos(), converter.getBlockState(), cameraPosition, piece -> {
            random.setSeed(42L);
            models.get(piece).collectParts(random, state.parts);
        });
        //#else
        //$$ var models = Minecraft.getInstance().getBlockRenderer();
        //$$ ConverterBoxPieces.collect(level, converter.getBlockPos(), converter.getBlockState(), cameraPosition, piece -> state.parts.add(models.getBlockModel(piece)));
        //#endif
    }

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
