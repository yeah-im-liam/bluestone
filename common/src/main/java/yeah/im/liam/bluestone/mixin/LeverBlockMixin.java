package yeah.im.liam.bluestone.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yeah.im.liam.bluestone.block.LeverParticles;

@Mixin(LeverBlock.class)
abstract class LeverBlockMixin {
    @WrapOperation(method = "makeParticle", at = @At(value = "NEW", target = "(IF)Lnet/minecraft/core/particles/DustParticleOptions;"))
    private static DustParticleOptions bluestone$colour(int colour, float scale, Operation<DustParticleOptions> original,
                                                        @Local(argsOnly = true) BlockState state, @Local(argsOnly = true) LevelAccessor level,
                                                        @Local(argsOnly = true) BlockPos pos) {
        return original.call(LeverParticles.colour(level, pos, state, colour), scale);
    }
}
