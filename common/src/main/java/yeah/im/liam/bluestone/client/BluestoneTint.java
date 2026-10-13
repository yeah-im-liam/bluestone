package yeah.im.liam.bluestone.client;

import java.util.Set;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import yeah.im.liam.bluestone.block.ColoredWireBlock;
import yeah.im.liam.bluestone.block.GreenstonePasteBlock;

/**
 * Colours the grey dust and paste textures by power level, like the game does for redstone dust.
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

    public static BlockTintSource paste(GreenstonePasteBlock paste) {
        return new BlockTintSource() {
            @Override
            public int color(BlockState state) {
                return paste.colorForPower(state.getValue(GreenstonePasteBlock.POWER));
            }

            @Override
            public Set<Property<?>> relevantProperties() {
                return Set.of(GreenstonePasteBlock.POWER);
            }
        };
    }
}
