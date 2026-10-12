package yeah.im.liam.bluestone.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yeah.im.liam.bluestone.block.BluestoneOreBlock;
import yeah.im.liam.bluestone.worldgen.WaterVein;

/**
 * Bluestone veins use the game's ore feature; their blocks are held back until the whole vein is known, so that
 * {@link WaterVein} can favour veins that touch water.
 */
@Mixin(OreFeature.class)
abstract class OreFeatureMixin {
    @WrapOperation(method = "doPlace", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState bluestone$holdBack(LevelChunkSection section, int x, int y, int z, BlockState state, boolean useLocks, Operation<BlockState> original,
                                          @Local BlockPos.MutableBlockPos orePos, @Share("vein") LocalRef<WaterVein> vein) {
        if (!(state.getBlock() instanceof BluestoneOreBlock)) {
            return original.call(section, x, y, z, state, useLocks);
        }
        if (vein.get() == null) {
            vein.set(new WaterVein());
        }
        vein.get().add(orePos, state);
        return section.getBlockState(x, y, z);
    }

    @Inject(method = "doPlace", at = @At("RETURN"))
    private void bluestone$placeVein(CallbackInfoReturnable<Boolean> cir, @Local(argsOnly = true) WorldGenLevel level,
                                     @Local(argsOnly = true) RandomSource random, @Share("vein") LocalRef<WaterVein> vein) {
        if (vein.get() != null) {
            vein.get().place(level, random);
        }
    }
}
