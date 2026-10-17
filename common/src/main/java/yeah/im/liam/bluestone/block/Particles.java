package yeah.im.liam.bluestone.block;

import net.minecraft.core.particles.DustParticleOptions;
//#if MC < 1.21.2
//$$ import net.minecraft.world.phys.Vec3;
//#endif

final class Particles {
    private Particles() {
    }

    // Dust takes an RGB number from 1.21.2 and a colour vector before.
    static DustParticleOptions dust(int color, float scale) {
        //#if MC >= 1.21.2
        return new DustParticleOptions(color, scale);
        //#else
        //$$ return new DustParticleOptions(Vec3.fromRGB24(color).toVector3f(), scale);
        //#endif
    }
}
