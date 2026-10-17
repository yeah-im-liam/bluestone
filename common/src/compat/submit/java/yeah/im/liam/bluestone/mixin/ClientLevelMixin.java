package yeah.im.liam.bluestone.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Underwater, the glow of anything powered (redstone, bluestone, purplestone and greenstone dust, torches, repeaters,
 * ores) comes with bubbles: each dust particle in water brings a bubble with it.
 */
@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
    @Inject(method = "doAddParticle", at = @At("HEAD"))
    private void bluestone$bubbles(ParticleOptions particle, boolean overrideLimiter, boolean alwaysShow, double x, double y, double z,
                                   double xd, double yd, double zd, CallbackInfo ci) {
        ClientLevel level = (ClientLevel) (Object) this;
        if (particle.getType() == ParticleTypes.DUST && level.getFluidState(BlockPos.containing(x, y, z)).is(FluidTags.WATER)) {
            level.addParticle(ParticleTypes.BUBBLE, x, y, z, 0.0, 0.02, 0.0);
        }
    }
}
