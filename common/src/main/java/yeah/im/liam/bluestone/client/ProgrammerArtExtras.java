package yeah.im.liam.bluestone.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.server.packs.PackResources;
import org.jspecify.annotations.Nullable;

/**
 * Makes the mod's Programmer Art textures (programmer_art/) part of the game's Programmer Art pack on NeoForge, as
 * Fabric API does on Fabric: PackRepositoryMixin adds them right after that pack.
 */
public final class ProgrammerArtExtras {
    private static @Nullable Supplier<PackResources> resources;

    private ProgrammerArtExtras() {
    }

    public static void use(Supplier<PackResources> supplier) {
        resources = supplier;
    }

    public static List<PackResources> addTo(List<PackResources> packs) {
        if (resources == null) {
            return packs;
        }
        for (int i = 0; i < packs.size(); i++) {
            if (packs.get(i).packId().equals("programmer_art")) {
                List<PackResources> added = new ArrayList<>(packs);
                added.add(i + 1, resources.get());
                return added;
            }
        }
        return packs;
    }
}
