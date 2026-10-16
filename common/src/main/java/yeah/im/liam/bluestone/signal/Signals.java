package yeah.im.liam.bluestone.signal;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.block.ColoredWireBlock;
import yeah.im.liam.bluestone.block.GreenstonePasteBlock;

/**
 * Tracks who is reading signals on the current thread.
 */
public final class Signals {
    private static final ThreadLocal<Listener> LISTENER = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> DUST_READING = ThreadLocal.withInitial(() -> false);
    private static final AtomicInteger READERS = new AtomicInteger();

    private Signals() {
    }

    public static int read(@Nullable Listener listener, IntSupplier reader) {
        return read(listener, false, reader);
    }

    public static int readForDust(Listener listener, IntSupplier reader) {
        return read(listener, true, reader);
    }

    private static int read(@Nullable Listener listener, boolean dust, IntSupplier reader) {
        Listener previous = LISTENER.get();
        boolean previousDust = DUST_READING.get();
        LISTENER.set(listener);
        DUST_READING.set(dust);
        READERS.incrementAndGet();
        try {
            return reader.getAsInt();
        } finally {
            READERS.decrementAndGet();
            DUST_READING.set(previousDust);
            LISTENER.set(previous);
        }
    }

    public static boolean test(@Nullable Listener listener, BooleanSupplier reader) {
        return read(listener, () -> reader.getAsBoolean() ? 1 : 0) != 0;
    }

    public static int heard(BlockState source, int signal) {
        if (READERS.get() == 0) {
            return signal;
        }
        if (DUST_READING.get() && isDust(source)) {
            return 0;
        }
        Listener listener = LISTENER.get();
        Channel channel = Channel.ofSource(source);
        return listener == null || channel == null ? signal : listener.heard(channel, signal);
    }

    public static boolean isDust(BlockState state) {
        return state.getBlock() == Blocks.REDSTONE_WIRE || state.getBlock() instanceof ColoredWireBlock || state.getBlock() instanceof GreenstonePasteBlock;
    }
}
