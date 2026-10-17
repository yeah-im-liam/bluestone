package yeah.im.liam.bluestone.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.signal.Channel;

/**
 * Bluestone dust: redstone dust that carries bluestone signals and can sit in water, coloured like redstone with red
 * and blue swapped.
 */
public class BluestoneWireBlock extends ColoredWireBlock implements BluestoneComponent, SimpleWaterloggedBlock {
    public BluestoneWireBlock(Properties properties) {
        super(properties, Channel.BLUESTONE, (red, green, blue) -> opaque(blue, green, red));
    }

    @Override
    public Channel channel(BlockState state) {
        return Channel.BLUESTONE;
    }

    @Override
    public boolean connectsToWire(BlockState state, Channel wire, @Nullable Direction direction) {
        return wire != Channel.REDSTONE;
    }
}
