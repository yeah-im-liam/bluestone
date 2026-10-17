package yeah.im.liam.bluestone.fabric.client;

//#if MC >= 26.1
import java.util.List;
//#endif
import net.fabricmc.api.ClientModInitializer;
//#if MC >= 26.1
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
//#else
//$$ import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
//$$ import yeah.im.liam.bluestone.client.RenderLayers;
//#endif
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import yeah.im.liam.bluestone.client.BluestoneTint;
import yeah.im.liam.bluestone.client.ConverterBoxRenderer;
import yeah.im.liam.bluestone.registry.BluestoneBlocks;

public final class BluestoneFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        //#if MC >= 26.1
        BluestoneBlocks.wires().forEach(wire -> BlockColorRegistry.register(List.of(BluestoneTint.wire(wire)), wire));
        BlockColorRegistry.register(List.of(BluestoneTint.paste(BluestoneBlocks.paste())), BluestoneBlocks.paste());
        //#else
        //$$ BluestoneBlocks.wires().forEach(wire -> ColorProviderRegistry.BLOCK.register(BluestoneTint.wire(wire), wire));
        //$$ ColorProviderRegistry.BLOCK.register(BluestoneTint.paste(BluestoneBlocks.paste()), BluestoneBlocks.paste());
        //$$ RenderLayers.useCutout();
        //#endif
        BlockEntityRendererRegistry.register(BluestoneBlocks.converterBoxEntity(), ConverterBoxRenderer::new);
    }
}
