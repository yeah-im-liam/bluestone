package yeah.im.liam.bluestone.mixin;

// Minecraft 1.20.6 and earlier, whose model bakery holds the definitions of block states that belong to no block;
// BlockStateModelLoaderMixin from 1.21.
//#if MC < 1.21
//$$ import com.google.common.collect.ImmutableMap;
//$$ import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
//$$ import java.util.Map;
//$$ import net.minecraft.client.resources.model.ModelBakery;
//$$ import net.minecraft.resources.Identifier;
//$$ import net.minecraft.world.level.block.Block;
//$$ import net.minecraft.world.level.block.state.BlockState;
//$$ import net.minecraft.world.level.block.state.StateDefinition;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.Unique;
//$$ import org.spongepowered.asm.mixin.injection.At;
//$$ import yeah.im.liam.bluestone.client.ConverterBoxPieces;
//$$
//$$ /**
//$$  * Loads the converter box's frame and light pieces alongside the item frames' (see ConverterBoxPieces): wherever the
//$$  * bakery reads its static definitions, it gets them with the pieces' added.
//$$  */
//$$ @Mixin(ModelBakery.class)
//$$ abstract class ModelBakeryMixin {
//$$     @Unique
//$$     private static Map<Identifier, StateDefinition<Block, BlockState>> bluestone$definitions;
//$$
//$$     @ModifyExpressionValue(method = {"<init>", "loadModel"}, at = @At(value = "FIELD",
//$$         target = "Lnet/minecraft/client/resources/model/ModelBakery;STATIC_DEFINITIONS:Ljava/util/Map;"))
//$$     private Map<Identifier, StateDefinition<Block, BlockState>> bluestone$addLights(Map<Identifier, StateDefinition<Block, BlockState>> definitions) {
//$$         if (bluestone$definitions == null) {
//$$             bluestone$definitions = ImmutableMap.<Identifier, StateDefinition<Block, BlockState>>builder().putAll(definitions)
//$$                 .put(ConverterBoxPieces.FRAME_ID, ConverterBoxPieces.FRAME_DEFINITION)
//$$                 .put(ConverterBoxPieces.LIGHT_ID, ConverterBoxPieces.LIGHT_DEFINITION)
//$$                 .put(ConverterBoxPieces.CENTER_ID, ConverterBoxPieces.CENTER_DEFINITION)
//$$                 .build();
//$$         }
//$$         return bluestone$definitions;
//$$     }
//$$ }
//#endif
