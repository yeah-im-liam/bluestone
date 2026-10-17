package yeah.im.liam.bluestone.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Underwater, the glow of anything powered (redstone, bluestone, purplestone and greenstone dust, torches, repeaters,
 * ores) comes with bubbles: each dust particle in water brings a bubble with it.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Shadow
    private ClientLevel level;

    @Inject(method = "addParticleInternal(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("HEAD"))
    private void bluestone$bubbles(ParticleOptions particle, boolean overrideLimiter, boolean alwaysShow, double x, double y, double z,
                                   double xd, double yd, double zd, CallbackInfoReturnable<Particle> cir) {
        if (particle.getType() == ParticleTypes.DUST && level != null && level.getFluidState(BlockPos.containing(x, y, z)).is(FluidTags.WATER)) {
            level.addParticle(ParticleTypes.BUBBLE, x, y, z, 0.0, 0.02, 0.0);
        }
    }
}
