package yeah.im.liam.bluestone.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yeah.im.liam.bluestone.block.BluestoneComparatorBlock;

@Mixin(Level.class)
abstract class LevelMixin {
    // Containers tell nearby comparators when their contents change; the game only looks for its own comparator.
    @WrapOperation(method = "updateNeighbourForOutputSignal", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z"))
    private boolean bluestone$alsoBluestoneComparator(BlockState state, Object block, Operation<Boolean> original) {
        return original.call(state, block) || block == Blocks.COMPARATOR && state.getBlock() instanceof BluestoneComparatorBlock;
    }
}
