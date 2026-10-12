package yeah.im.liam.bluestone.client;

import java.util.Set;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import yeah.im.liam.bluestone.block.BluestoneWireBlock;

/**
 * Colours the grey dust textures by power level, like the game does for redstone dust.
 */
public final class BluestoneTint {
    private BluestoneTint() {
    }

    public static BlockTintSource wire() {
        return new BlockTintSource() {
            @Override
            public int color(BlockState state) {
                return BluestoneWireBlock.getColorForPower(state.getValue(BluestoneWireBlock.POWER));
            }

            @Override
            public Set<Property<?>> relevantProperties() {
                return Set.of(BluestoneWireBlock.POWER);
            }
        };
    }
}
