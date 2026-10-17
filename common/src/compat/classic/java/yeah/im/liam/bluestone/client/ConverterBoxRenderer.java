package yeah.im.liam.bluestone.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
//#if MC < 1.21.5
//$$ import net.minecraft.client.renderer.block.BlockModelShaper;
//#endif
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import yeah.im.liam.bluestone.block.ConverterBoxBlockEntity;

/**
 * Draws a converter box glowing while any of its lights is on, over the world's own drawing of it and nudged towards
 * the camera so it covers it exactly. Faces the world would cull, facing away or covered, are left out.
 */
public final class ConverterBoxRenderer implements BlockEntityRenderer<ConverterBoxBlockEntity> {
    private static final int FULL_BRIGHT = 0xF000F0;

    public ConverterBoxRenderer(BlockEntityRendererProvider.Context context) {
    }

    //#if MC >= 1.21.5
    @Override
    public void render(ConverterBoxBlockEntity converter, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay, Vec3 camera) {
    //#else
    //$$ @Override
    //$$ public void render(ConverterBoxBlockEntity converter, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
    //$$     Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
    //#endif
        Level level = converter.getLevel();
        if (level == null) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderTypes.entityCutoutNoCullZOffset(TextureAtlas.LOCATION_BLOCKS));
        //#if MC >= 1.21.5
        var models = Minecraft.getInstance().getBlockRenderer();
        ConverterBoxPieces.collect(level, converter.getBlockPos(), converter.getBlockState(), camera, piece ->
            ModelBlockRenderer.renderModel(poseStack.last(), consumer, models.getBlockModel(piece), 1.0F, 1.0F, 1.0F, FULL_BRIGHT, OverlayTexture.NO_OVERLAY));
        //#else
        //$$ // Before 1.21.5 the pieces' baked models are looked up by blockstate file and variant.
        //$$ var models = Minecraft.getInstance().getModelManager();
        //$$ ModelBlockRenderer renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        //$$ ConverterBoxPieces.collect(level, converter.getBlockPos(), converter.getBlockState(), camera, piece ->
        //$$     renderer.renderModel(poseStack.last(), consumer, null, models.getModel(BlockModelShaper.stateToModelLocation(ConverterBoxPieces.id(piece), piece)),
        //$$         1.0F, 1.0F, 1.0F, FULL_BRIGHT, OverlayTexture.NO_OVERLAY));
        //#endif
    }

    @Override
    public int getViewDistance() {
        return 1024;
    }
}
