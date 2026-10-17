package yeah.im.liam.bluestone.client;

// Minecraft 26.1 and later pick each block's render layer by themselves.
//#if MC < 26.1
//$$ import java.util.Map;
//$$ import net.minecraft.core.registries.BuiltInRegistries;
//$$ import net.minecraft.world.level.block.Block;
//$$ import net.minecraft.world.level.block.Blocks;
//$$ import yeah.im.liam.bluestone.Bluestone;
//$$ import yeah.im.liam.bluestone.mixin.ItemBlockRenderTypesAccessor;
//$$
//$$ /**
//$$  * Draws the mod's dust, paste, torches, diodes and rails with see-through gaps, like the game's redstone parts.
//$$  */
//$$ public final class RenderLayers {
//$$     private RenderLayers() {
//$$     }
//$$
//$$     public static void useCutout() {
//$$         Map<Block, Object> layers = ItemBlockRenderTypesAccessor.bluestone$typeByBlock();
//$$         Object cutout = layers.get(Blocks.REDSTONE_WIRE);
//$$         for (Block block : BuiltInRegistries.BLOCK) {
//$$             if (BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(Bluestone.MOD_ID) && !block.defaultBlockState().canOcclude()) {
//$$                 layers.put(block, cutout);
//$$             }
//$$         }
//$$     }
//$$ }
//#endif
