package yeah.im.liam.bluestone.mixin;

// Minecraft 1.21.4 and earlier; BlockStateDefinitionsMixin from 1.21.5.
//#if MC < 1.21.5
//$$ import com.llamalad7.mixinextras.injector.ModifyReturnValue;
//$$ import java.util.function.Function;
//$$ import net.minecraft.client.resources.model.BlockStateModelLoader;
//$$ import net.minecraft.resources.Identifier;
//$$ import net.minecraft.world.level.block.Block;
//$$ import net.minecraft.world.level.block.state.BlockState;
//$$ import net.minecraft.world.level.block.state.StateDefinition;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import yeah.im.liam.bluestone.client.ConverterBoxPieces;

//$$ /**
 //$$ * Loads the converter box's frame and light pieces from blockstates/converter_box_frame.json, converter_box_light.json
 //$$ * and converter_box_center.json, on block states that belong to no block, as the game does for item frames (see
 //$$ * ConverterBoxPieces).
 //$$ */
//$$ @Mixin(BlockStateModelLoader.class)
//$$ abstract class BlockStateModelLoaderMixin {
    //#if MC >= 1.21.2
    //$$ @ModifyReturnValue(method = "definitionLocationToBlockMapper", at = @At("RETURN"))
    //$$ private static Function<Identifier, StateDefinition<Block, BlockState>> bluestone$addLights(Function<Identifier, StateDefinition<Block, BlockState>> definitions) {
        //$$ return id -> ConverterBoxPieces.LIGHT_ID.equals(id) ? ConverterBoxPieces.LIGHT_DEFINITION
            //$$ : ConverterBoxPieces.CENTER_ID.equals(id) ? ConverterBoxPieces.CENTER_DEFINITION
            //$$ : ConverterBoxPieces.FRAME_ID.equals(id) ? ConverterBoxPieces.FRAME_DEFINITION
            //$$ : definitions.apply(id);
    //$$ }
    //#else
    //$$ // Before 1.21.2 the loader loads the item frames' definitions and every block's one by one.
    //$$ @org.spongepowered.asm.mixin.Shadow
    //$$ private void loadBlockStateDefinitions(Identifier id, StateDefinition<Block, BlockState> definition) {
    //$$     throw new AssertionError();
    //$$ }
    //$$
    //$$ @org.spongepowered.asm.mixin.injection.Inject(method = "loadAllBlockStates", at = @At("TAIL"))
    //$$ private void bluestone$addLights(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
    //$$     loadBlockStateDefinitions(ConverterBoxPieces.FRAME_ID, ConverterBoxPieces.FRAME_DEFINITION);
    //$$     loadBlockStateDefinitions(ConverterBoxPieces.LIGHT_ID, ConverterBoxPieces.LIGHT_DEFINITION);
    //$$     loadBlockStateDefinitions(ConverterBoxPieces.CENTER_ID, ConverterBoxPieces.CENTER_DEFINITION);
    //$$ }
    //#endif
//$$ }
//#endif
