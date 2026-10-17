package yeah.im.liam.bluestone.client;

// Minecraft 26.1 and later pick each block's render layer by themselves.
//#if MC < 26.1
//$$ import java.util.List;
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
//$$     // Through the game's own table, which Fabric reads. NeoForge keeps a table of its own before 21.5 and sets
//$$     // these blocks through its setRenderLayer then (see BluestoneNeoForgeClient).
//$$     public static void useCutout() {
//$$         Map<Block, Object> layers = ItemBlockRenderTypesAccessor.bluestone$typeByBlock();
//$$         Object cutout = layers.get(Blocks.REDSTONE_WIRE);
//$$         blocks().forEach(block -> layers.put(block, cutout));
//$$     }
//$$
//$$     // The mod's blocks that aren't full cubes, so may have see-through gaps.
//$$     public static List<Block> blocks() {
//$$         return BuiltInRegistries.BLOCK.stream()
//$$             .filter(block -> BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(Bluestone.MOD_ID)
//$$                 && !Block.isShapeFullBlock(block.defaultBlockState().getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO)))
//$$             .toList();
//$$     }
//$$ }
//#endif
