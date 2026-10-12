package yeah.im.liam.bluestone.signal;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.PoweredBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.block.BluestoneComponent;

/**
 * Redstone and bluestone parts only hear their own kind. Levers, buttons, observers and other
 * plain sources belong to no channel, so both kinds hear them.
 */
public enum Channel {
    REDSTONE,
    BLUESTONE;

    public static Channel of(Block block) {
        return block instanceof BluestoneComponent ? BLUESTONE : REDSTONE;
    }

    public static @Nullable Channel ofSource(Block block) {
        if (block instanceof BluestoneComponent) {
            return BLUESTONE;
        }
        if (block == Blocks.REDSTONE_WIRE
            || block instanceof DiodeBlock
            || block instanceof RedstoneTorchBlock
            || block instanceof PoweredBlock
            || block instanceof DetectorRailBlock) {
            return REDSTONE;
        }
        return null;
    }
}
