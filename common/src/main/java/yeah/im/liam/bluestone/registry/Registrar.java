package yeah.im.liam.bluestone.registry;

import java.util.function.Function;
import net.minecraft.resources.ResourceKey;

public interface Registrar<T> {
    <V extends T> V register(String path, Function<ResourceKey<T>, V> factory);
}
