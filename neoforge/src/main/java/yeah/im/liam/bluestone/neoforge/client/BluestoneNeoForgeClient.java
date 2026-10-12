package yeah.im.liam.bluestone.neoforge.client;

import java.util.List;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import yeah.im.liam.bluestone.Bluestone;
import yeah.im.liam.bluestone.client.BluestoneTint;
import yeah.im.liam.bluestone.registry.BluestoneBlocks;

@Mod(value = Bluestone.MOD_ID, dist = Dist.CLIENT)
public final class BluestoneNeoForgeClient {
    public BluestoneNeoForgeClient(IEventBus modEventBus) {
        modEventBus.addListener((RegisterColorHandlersEvent.BlockTintSources event) ->
            BluestoneBlocks.wires().forEach(wire -> event.register(List.of(BluestoneTint.wire(wire)), wire)));
    }
}
