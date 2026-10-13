package yeah.im.liam.bluestone.signal;

/**
 * Which channels' sources a reader picks up. Every {@link Channel} is one; blocks that hear an unusual mix, like the
 * converter, make their own.
 */
@FunctionalInterface
public interface Listener {
    boolean hears(Channel source);

    /**
     * How strong a signal of the given strength from the given channel is to this listener.
     */
    default int heard(Channel source, int signal) {
        return hears(source) ? signal : 0;
    }
}
