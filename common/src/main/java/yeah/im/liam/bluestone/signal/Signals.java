package yeah.im.liam.bluestone.signal;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import net.minecraft.world.level.block.Block;

/**
 * Tracks which channel is reading signals on the current thread. While a channel reads,
 * sources of the other channel report no signal.
 */
public final class Signals {
    private static final ThreadLocal<Channel> LISTENER = new ThreadLocal<>();
    private static final AtomicInteger READERS = new AtomicInteger();

    private Signals() {
    }

    public static int read(Channel listener, IntSupplier reader) {
        Channel previous = LISTENER.get();
        LISTENER.set(listener);
        READERS.incrementAndGet();
        try {
            return reader.getAsInt();
        } finally {
            READERS.decrementAndGet();
            LISTENER.set(previous);
        }
    }

    public static boolean test(Channel listener, BooleanSupplier reader) {
        return read(listener, () -> reader.getAsBoolean() ? 1 : 0) != 0;
    }

    public static boolean hidden(Block source) {
        if (READERS.get() == 0) {
            return false;
        }
        Channel listener = LISTENER.get();
        if (listener == null) {
            return false;
        }
        Channel channel = Channel.ofSource(source);
        return channel != null && channel != listener;
    }
}
