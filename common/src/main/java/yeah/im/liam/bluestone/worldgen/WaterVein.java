package yeah.im.liam.bluestone.worldgen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A bluestone vein held back while the game works out its shape.
 */
public final class WaterVein {
    public static final float DRY_CHANCE = 0.25F;

    private final List<BlockPos> positions = new ArrayList<>();
    private final List<BlockState> states = new ArrayList<>();

    public void add(BlockPos pos, BlockState state) {
        positions.add(pos.immutable());
        states.add(state);
    }

    public void place(WorldGenLevel level, RandomSource random) {
        if (!touchesWater(level) && random.nextFloat() >= DRY_CHANCE) {
            return;
        }
        for (int i = 0; i < positions.size(); i++) {
            level.setBlock(positions.get(i), states.get(i), Block.UPDATE_CLIENTS);
        }
    }

    private boolean touchesWater(WorldGenLevel level) {
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
        for (BlockPos pos : positions) {
            for (Direction direction : Direction.values()) {
                if (level.getFluidState(neighbor.setWithOffset(pos, direction)).is(FluidTags.WATER)) {
                    return true;
                }
            }
        }
        return false;
    }
}
