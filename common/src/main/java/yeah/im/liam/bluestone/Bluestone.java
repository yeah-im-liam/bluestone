package yeah.im.liam.bluestone;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

public final class Bluestone {
    public static final String MOD_ID = "bluestone";

    private Bluestone() {
    }

    public static Identifier id(String path) {
        return id(MOD_ID, path);
    }

    public static Identifier vanilla(String path) {
        return id("minecraft", path);
    }

    // Ids come from factory methods from 1.21 and from the constructor before.
    public static Identifier id(String namespace, String path) {
        //#if MC >= 1.21
        return Identifier.fromNamespaceAndPath(namespace, path);
        //#else
        //$$ return new Identifier(namespace, path);
        //#endif
    }

    public static <T> ResourceKey<T> key(ResourceKey<? extends Registry<T>> registry, String path) {
        return ResourceKey.create(registry, id(path));
    }
}
