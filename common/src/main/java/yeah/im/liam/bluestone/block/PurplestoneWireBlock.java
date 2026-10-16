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
 * its own signal at full strength.
 */
public class PurplestoneWireBlock extends ColoredWireBlock {
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

    @Override
    protected int powerFromOtherDust(BlockState state) {
        if (state.getBlock() instanceof ConverterBoxBlock) {
            return super.powerFromOtherDust(state);
        }
        return Signals.isDust(state) && LISTENER.hears(Channel.ofSource(state)) ? state.getValue(POWER) / 2 : 0;
    }

    @Override
    public boolean connectsToWire(BlockState state, Channel wire, @Nullable Direction direction) {
        return true;
    }
}
