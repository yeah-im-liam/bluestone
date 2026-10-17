package yeah.im.liam.bluestone.neoforge.client;

//#if MC >= 26.1
import java.util.List;
//#endif
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
//#if MC < 26.1
//$$ import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
//#endif
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import yeah.im.liam.bluestone.Bluestone;
import yeah.im.liam.bluestone.client.BluestoneTint;
import yeah.im.liam.bluestone.client.CartViewScreen;
import yeah.im.liam.bluestone.client.ConverterBoxRenderer;
//#if MC < 26.1
//$$ import yeah.im.liam.bluestone.client.RenderLayers;
//#endif
import yeah.im.liam.bluestone.registry.BluestoneBlocks;

// Before 1.21 BluestoneNeoForge starts this on the client.
//#if MC >= 1.21
@Mod(value = Bluestone.MOD_ID, dist = Dist.CLIENT)
//#endif
public final class BluestoneNeoForgeClient {
    public BluestoneNeoForgeClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new CartViewScreen(parent));
        //#if MC >= 26.1
        modEventBus.addListener((RegisterColorHandlersEvent.BlockTintSources event) -> {
            BluestoneBlocks.wires().forEach(wire -> event.register(List.of(BluestoneTint.wire(wire)), wire));
            event.register(List.of(BluestoneTint.paste(BluestoneBlocks.paste())), BluestoneBlocks.paste());
        });
        //#else
        //$$ modEventBus.addListener((RegisterColorHandlersEvent.Block event) -> {
        //$$     BluestoneBlocks.wires().forEach(wire -> event.register(BluestoneTint.wire(wire), wire));
        //$$     event.register(BluestoneTint.paste(BluestoneBlocks.paste()), BluestoneBlocks.paste());
        //$$ });
        //$$ modEventBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(RenderLayers::useCutout));
        //#endif
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
            event.registerBlockEntityRenderer(BluestoneBlocks.converterBoxEntity(), ConverterBoxRenderer::new));
    }
}
