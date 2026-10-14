package yeah.im.liam.bluestone.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.function.Function;
import net.minecraft.client.resources.model.BlockStateDefinitions;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import yeah.im.liam.bluestone.client.ConverterBoxRenderer;

/**
 * Loads the converter box's frame and light pieces from blockstates/converter_box_frame.json and
 * converter_box_light.json, on block states that belong to no block, as the game does for item frames (see
 * ConverterBoxRenderer).
 */
@Mixin(BlockStateDefinitions.class)
abstract class BlockStateDefinitionsMixin {
    @ModifyReturnValue(method = "definitionLocationToBlockStateMapper", at = @At("RETURN"))
    private static Function<Identifier, StateDefinition<Block, BlockState>> bluestone$addLights(Function<Identifier, StateDefinition<Block, BlockState>> definitions) {
        return id -> ConverterBoxRenderer.LIGHT_ID.equals(id) ? ConverterBoxRenderer.LIGHT_DEFINITION
            : ConverterBoxRenderer.FRAME_ID.equals(id) ? ConverterBoxRenderer.FRAME_DEFINITION
            : definitions.apply(id);
    }
}
