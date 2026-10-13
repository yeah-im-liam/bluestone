package yeah.im.liam.bluestone.signal;

/**
 * Which channels' sources a reader picks up. Every {@link Channel} is one; blocks that hear an unusual mix, like the
 * converter, make their own.
 */
@FunctionalInterface
public interface Listener {
    boolean hears(Channel source);
}
