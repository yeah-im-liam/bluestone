package yeah.im.liam.bluestone.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

final class Waterlogging {
    static final FluidState WATER = Fluids.WATER.getSource(false);

    private Waterlogging() {
    }

    static @Nullable BlockState place(@Nullable BlockState state, BlockPlaceContext context) {
        if (state == null || !state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            return state;
        }
        boolean water = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
        return state.setValue(BlockStateProperties.WATERLOGGED, water);
    }

    static void scheduleTick(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos) {
        if (isWet(state)) {
            ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
    }

    static boolean isWet(BlockState state) {
        return state.getValueOrElse(BlockStateProperties.WATERLOGGED, false);
    }
}
