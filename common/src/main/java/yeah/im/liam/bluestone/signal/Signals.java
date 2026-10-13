package yeah.im.liam.bluestone.signal;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.block.ColoredWireBlock;

/**
 * Tracks who is reading signals on the current thread. While a listener reads, sources it can't hear report no
 * signal.
 */
public final class Signals {
    private static final ThreadLocal<Listener> LISTENER = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> DUST_READING = ThreadLocal.withInitial(() -> false);
    private static final AtomicInteger READERS = new AtomicInteger();

    private Signals() {
    }

    /**
     * Reads signals as the given listener hears them, or everything for {@code null}.
     */
    public static int read(@Nullable Listener listener, IntSupplier reader) {
        return read(listener, false, reader);
    }

    /**
     * Reads signals for dust working out its own power: as {@link #read}, but dust of every colour stays silent. Like
     * the game's dust with its own kind, dust takes power from other dust only along the wire, one level weaker per
     * block, so redstone and purplestone dust side by side can't keep each other powered.
     */
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

    public static boolean hidden(BlockState source) {
        if (READERS.get() == 0) {
            return false;
        }
        if (DUST_READING.get() && isDust(source)) {
            return true;
        }
        Listener listener = LISTENER.get();
        if (listener == null) {
            return false;
        }
        Channel channel = Channel.ofSource(source);
        return channel != null && !listener.hears(channel);
    }

    public static boolean isDust(BlockState state) {
        return state.getBlock() == Blocks.REDSTONE_WIRE || state.getBlock() instanceof ColoredWireBlock;
    }
}
