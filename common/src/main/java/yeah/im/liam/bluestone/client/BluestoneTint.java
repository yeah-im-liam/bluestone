package yeah.im.liam.bluestone.client;

import java.util.Set;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import yeah.im.liam.bluestone.block.ColoredWireBlock;

/**
 * Colours the grey dust textures by power level, like the game does for redstone dust.
 */
public final class BluestoneTint {
    private BluestoneTint() {
    }

    public static BlockTintSource wire(ColoredWireBlock wire) {
        return new BlockTintSource() {
            @Override
            public int color(BlockState state) {
                return wire.colorForPower(state.getValue(ColoredWireBlock.POWER));
            }

            @Override
            public Set<Property<?>> relevantProperties() {
                return Set.of(ColoredWireBlock.POWER);
            }
        };
    }
}
