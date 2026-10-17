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
//#if MC >= 1.20.5
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
//#endif
import yeah.im.liam.bluestone.Bluestone;
import yeah.im.liam.bluestone.client.BluestoneTint;
import yeah.im.liam.bluestone.client.CartViewScreen;
import yeah.im.liam.bluestone.client.ConverterBoxRenderer;
import yeah.im.liam.bluestone.client.ProgrammerArtExtras;
//#if MC < 26.1
//$$ import yeah.im.liam.bluestone.client.RenderLayers;
//#endif
import yeah.im.liam.bluestone.registry.BluestoneBlocks;

// Before 1.21 BluestoneNeoForge starts this on the client.
//#if MC >= 1.21
@Mod(value = Bluestone.MOD_ID, dist = Dist.CLIENT)
//#endif
public final class BluestoneNeoForgeClient {
    //#if MC >= 1.20.5
    private static net.minecraft.server.packs.PackLocationInfo programmerArt() {
        return new net.minecraft.server.packs.PackLocationInfo("mod/" + Bluestone.id("programmer_art"),
            net.minecraft.network.chat.Component.translatable("resourcePack.programmer_art.name"),
            net.minecraft.server.packs.repository.PackSource.BUILT_IN, java.util.Optional.empty());
    }
    //#endif

    public BluestoneNeoForgeClient(IEventBus modEventBus, ModContainer container) {
        // The settings screen factory is a ConfigScreenFactory record before 20.5.
        //#if MC >= 1.20.5
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new CartViewScreen(parent));
        //#else
        //$$ container.registerExtensionPoint(net.neoforged.neoforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
        //$$     () -> new net.neoforged.neoforge.client.ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new CartViewScreen(parent)));
        //#endif
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
        //$$ // The same layer as redstone dust. NeoForge reads its own table of layers, not the game's, before 21.5.
        //#if MC >= 1.21.5
        //$$ modEventBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(RenderLayers::useCutout));
        //#else
        //$$ modEventBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> RenderLayers.blocks().forEach(block ->
        //$$     net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(block,
        //$$         net.minecraft.client.renderer.ItemBlockRenderTypes.getChunkRenderType(net.minecraft.world.level.block.Blocks.REDSTONE_WIRE.defaultBlockState())))));
        //#endif
        //#endif
        // The mod's Programmer Art textures go with the game's Programmer Art pack, as Fabric API does it on Fabric.
        //#if MC >= 1.21.9
        ProgrammerArtExtras.use(() -> new net.neoforged.neoforge.resource.JarContentsPackResources(programmerArt(),
            container.getModInfo().getOwningFile().getFile().getContents(), "programmer_art"));
        //#elseif MC >= 1.20.5
        //$$ ProgrammerArtExtras.use(() -> new net.minecraft.server.packs.PathPackResources(programmerArt(),
        //$$     container.getModInfo().getOwningFile().getFile().findResource("programmer_art")));
        //#else
        //$$ ProgrammerArtExtras.use(() -> new net.minecraft.server.packs.PathPackResources("mod/" + Bluestone.id("programmer_art"),
        //$$     container.getModInfo().getOwningFile().getFile().findResource("programmer_art"), true));
        //#endif
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
            event.registerBlockEntityRenderer(BluestoneBlocks.converterBoxEntity(), ConverterBoxRenderer::new));
    }
}
