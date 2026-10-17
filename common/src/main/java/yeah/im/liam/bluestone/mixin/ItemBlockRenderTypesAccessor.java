package yeah.im.liam.bluestone.mixin;

// Minecraft 26.1 and later pick each block's render layer by themselves.
//#if MC < 26.1
//$$ import java.util.Map;
//$$ import net.minecraft.client.renderer.ItemBlockRenderTypes;
//$$ import net.minecraft.world.level.block.Block;
//$$ import org.spongepowered.asm.mixin.Mixin;
//$$ import org.spongepowered.asm.mixin.gen.Accessor;
//$$
//$$ @Mixin(ItemBlockRenderTypes.class)
//$$ public interface ItemBlockRenderTypesAccessor {
//$$     // The value type changed from RenderType to ChunkSectionLayer in 1.21.6, so it is left as Object.
//$$     @Accessor("TYPE_BY_BLOCK")
//$$     static Map<Block, Object> bluestone$typeByBlock() {
//$$         throw new AssertionError();
//$$     }
//$$ }
//#endif
