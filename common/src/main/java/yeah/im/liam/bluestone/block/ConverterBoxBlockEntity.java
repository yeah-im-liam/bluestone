package yeah.im.liam.bluestone.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import yeah.im.liam.bluestone.registry.BluestoneBlocks;

/**
 * Holds nothing: it is there so the converter box's lights can be drawn (see {@link ConverterBoxBlock#lights}).
 */
public class ConverterBoxBlockEntity extends BlockEntity {
    public ConverterBoxBlockEntity(BlockPos pos, BlockState state) {
        super(BluestoneBlocks.converterBoxEntity(), pos, state);
    }
}
