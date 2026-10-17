package yeah.im.liam.bluestone.mixin;

// Minecraft 1.20.6 and earlier, before the loaders could add blocks to a block entity type.
//#if MC < 1.21
//$$ import com.llamalad7.mixinextras.injector.ModifyReturnValue;
//$$ import net.minecraft.world.level.block.entity.BlockEntityType;
//$$ import net.minecraft.world.level.block.state.BlockState;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import yeah.im.liam.bluestone.registry.BluestoneBlocks;
//$$
//$$ /** Lets the bluestone comparator keep the vanilla comparator's block entity. */
//$$ @Mixin(BlockEntityType.class)
//$$ abstract class BlockEntityTypeMixin {
//$$     @ModifyReturnValue(method = "isValid", at = @At("RETURN"))
//$$     private boolean bluestone$comparator(boolean valid, BlockState state) {
//$$         return valid || (Object) this == BlockEntityType.COMPARATOR && state.is(BluestoneBlocks.comparator());
//$$     }
//$$ }
//#endif
