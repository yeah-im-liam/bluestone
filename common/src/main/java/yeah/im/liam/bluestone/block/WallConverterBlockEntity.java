package yeah.im.liam.bluestone.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import yeah.im.liam.bluestone.registry.BluestoneBlocks;

/**
 * Holds nothing: it is there so the wall converter's lights can be drawn (see {@link WallConverterBlock#lights}).
 */
public class WallConverterBlockEntity extends BlockEntity {
    public WallConverterBlockEntity(BlockPos pos, BlockState state) {
        super(BluestoneBlocks.wallConverterEntity(), pos, state);
    }
}
