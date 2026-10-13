package yeah.im.liam.bluestone.block;

import net.minecraft.world.level.block.RailBlock;

/**
 * The sticky rail on a floor: an ordinary rail that doesn't move carts itself. The same item places it on walls and
 * ceilings as an {@link AttachedRailBlock}.
 */
public class StickyRailBlock extends RailBlock {
    public StickyRailBlock(Properties properties) {
        super(properties);
    }
}
