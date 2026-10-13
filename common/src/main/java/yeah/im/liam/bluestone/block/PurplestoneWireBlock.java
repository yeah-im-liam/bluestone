package yeah.im.liam.bluestone.block;

import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.signal.Channel;
import yeah.im.liam.bluestone.signal.Listener;
import yeah.im.liam.bluestone.signal.Signals;

/**
 * Purplestone dust takes power from both redstone and bluestone, but only at half strength, rounded down; both pick up
 * its own signal at full strength. Like redstone dust, it can't sit in water.
 */
public class PurplestoneWireBlock extends ColoredWireBlock {
    // Redstone and bluestone signals come in at half strength; purplestone and plain sources such as levers at full.
    private static final Listener LISTENER = new Listener() {
        @Override
        public boolean hears(Channel source) {
            return Channel.PURPLESTONE.hears(source);
        }

        @Override
        public int heard(Channel source, int signal) {
            return !hears(source) ? 0 : source == Channel.PURPLESTONE ? signal : signal / 2;
        }
    };

    public PurplestoneWireBlock(Properties properties) {
        super(properties, Channel.PURPLESTONE, (red, green, blue) -> ARGB.colorFromFloat(1.0F, red * 0.6F, green, red));
    }

    @Override
    protected Listener listener() {
        return LISTENER;
    }

    // Redstone or bluestone dust at 10 gives purplestone dust 5, instead of the 9 it would give its own kind.
    @Override
    protected int powerFromOtherDust(BlockState state) {
        if (state.getBlock() instanceof WallConverterBlock) {
            return Math.max(0, state.getValue(WallConverterBlock.POWER) - 1);
        }
        return Signals.isDust(state) && LISTENER.hears(Channel.ofSource(state)) ? state.getValue(POWER) / 2 : 0;
    }

    // Every kind of dust bends towards purplestone dust, since all of them can feed it.
    @Override
    public boolean connectsToWire(BlockState state, Channel wire, @Nullable Direction direction) {
        return true;
    }
}
