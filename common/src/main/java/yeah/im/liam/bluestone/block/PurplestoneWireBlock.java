package yeah.im.liam.bluestone.block;

import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.signal.Channel;

/**
 * Purplestone dust takes power from both redstone and bluestone. Only purplestone parts, the converter and plain
 * blocks such as lamps and pistons pick up its own signal, so it never passes power straight between redstone and
 * bluestone. Like redstone dust, it can't sit in water.
 */
public class PurplestoneWireBlock extends ColoredWireBlock {
    public PurplestoneWireBlock(Properties properties) {
        super(properties, Channel.PURPLESTONE, (red, green, blue) -> ARGB.colorFromFloat(1.0F, red * 0.6F, green, red));
    }

    // Every kind of dust bends towards purplestone dust, since all of them can feed it.
    @Override
    public boolean connectsToWire(BlockState state, Channel wire, @Nullable Direction direction) {
        return true;
    }
}
