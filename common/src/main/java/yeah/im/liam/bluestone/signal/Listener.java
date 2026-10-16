package yeah.im.liam.bluestone.signal;

/**
 * Which channels' sources a reader picks up.
 */
@FunctionalInterface
public interface Listener {
    boolean hears(Channel source);

    default int heard(Channel source, int signal) {
        return hears(source) ? signal : 0;
    }
}
