package yeah.im.liam.bluestone.mixin;

// Minecraft 1.21.1 and earlier, which match multipart blockstate conditions against the definition of the state's
// block. The converter box's pieces (see ConverterBoxPieces) belong to air, so their own definition is handed in.
//#if MC < 1.21.2
//$$ import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
//$$ import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
//$$ import com.llamalad7.mixinextras.sugar.Local;
//$$ import net.minecraft.world.level.block.Block;
//$$ import net.minecraft.world.level.block.state.BlockState;
//$$ import net.minecraft.world.level.block.state.StateDefinition;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import yeah.im.liam.bluestone.client.ConverterBoxPieces;
//$$
//#if MC >= 1.21
//$$ @Mixin(targets = "net.minecraft.client.resources.model.BlockStateModelLoader$ModelGroupKey")
//#else
//$$ @Mixin(targets = "net.minecraft.client.resources.model.ModelBakery$ModelGroupKey")
//#endif
//$$ abstract class ModelGroupKeyMixin {
//#if MC >= 1.21
//$$     @WrapOperation(method = "create(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/model/multipart/MultiPart;Ljava/util/Collection;)Lnet/minecraft/client/resources/model/BlockStateModelLoader$ModelGroupKey;",
//#else
//$$     @WrapOperation(method = "create(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/model/multipart/MultiPart;Ljava/util/Collection;)Lnet/minecraft/client/resources/model/ModelBakery$ModelGroupKey;",
//#endif
//$$         at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getStateDefinition()Lnet/minecraft/world/level/block/state/StateDefinition;"))
//$$     private static StateDefinition<Block, BlockState> bluestone$pieceDefinition(Block block, Operation<StateDefinition<Block, BlockState>> original,
//$$                                                                              @Local(argsOnly = true) BlockState state) {
//$$         StateDefinition<Block, BlockState> piece = ConverterBoxPieces.definition(state);
//$$         return piece != null ? piece : original.call(block);
//$$     }
//$$ }
//#endif
