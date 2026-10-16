package yeah.im.liam.bluestone.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yeah.im.liam.bluestone.block.BluestoneComparatorBlock;
import yeah.im.liam.bluestone.block.GreenstoneComparatorBlock;

@Mixin(Level.class)
abstract class LevelMixin {
    @WrapOperation(method = "updateNeighbourForOutputSignal", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z"))
    private boolean bluestone$alsoBluestoneComparator(BlockState state, Object block, Operation<Boolean> original) {
        return original.call(state, block) || block == Blocks.COMPARATOR && state.getBlock() instanceof BluestoneComparatorBlock;
    }

    @Inject(method = "updateNeighbourForOutputSignal", at = @At("TAIL"))
    private void bluestone$greenstoneComparators(BlockPos pos, Block block, CallbackInfo ci) {
        Level level = (Level) (Object) this;
        for (Direction direction : Direction.values()) {
            BlockPos next = pos.relative(direction);
            if (!level.hasChunkAt(next)) {
                continue;
            }
            BlockState state = level.getBlockState(next);
            if (state.getBlock() instanceof GreenstoneComparatorBlock) {
                level.neighborChanged(state, next, block, null, false);
            } else if (state.isRedstoneConductor(level, next)) {
                BlockPos further = next.relative(direction);
                BlockState furtherState = level.getBlockState(further);
                if (level.hasChunkAt(further) && furtherState.getBlock() instanceof GreenstoneComparatorBlock) {
                    level.neighborChanged(furtherState, further, block, null, false);
                }
            }
        }
    }
}
