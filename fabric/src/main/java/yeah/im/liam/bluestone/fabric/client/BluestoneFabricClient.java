package yeah.im.liam.bluestone.fabric.client;

import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import yeah.im.liam.bluestone.client.BluestoneTint;
import yeah.im.liam.bluestone.registry.BluestoneBlocks;

public final class BluestoneFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BluestoneBlocks.wires().forEach(wire -> BlockColorRegistry.register(List.of(BluestoneTint.wire(wire)), wire));
        BlockColorRegistry.register(List.of(BluestoneTint.paste(BluestoneBlocks.paste())), BluestoneBlocks.paste());
    }
}
