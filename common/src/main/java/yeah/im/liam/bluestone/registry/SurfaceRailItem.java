package yeah.im.liam.bluestone.registry;

import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Places a rail on the top of a block as the floor rail, and on its side or underside as the attached rail.
 */
public class SurfaceRailItem extends BlockItem {
    private final Block attached;

    public SurfaceRailItem(Block floor, Block attached, Properties properties) {
        super(floor, properties);
        this.attached = attached;
    }

    @Override
    protected @Nullable BlockState getPlacementState(BlockPlaceContext context) {
        Block block = context.getClickedFace() == Direction.UP ? getBlock() : attached;
        BlockState state = block.getStateForPlacement(context);
        return state != null && canPlace(context, state) ? state : null;
    }

    @Override
    public void registerBlocks(Map<Block, Item> map, Item item) {
        super.registerBlocks(map, item);
        map.put(attached, item);
    }
}
