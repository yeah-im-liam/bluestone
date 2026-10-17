package yeah.im.liam.bluestone.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The redstone ore with blue particles.
 */
public class BluestoneOreBlock extends RedStoneOreBlock {
    public BluestoneOreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        interact(state, level, pos);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState onState, Entity entity) {
        if (!entity.isSteppingCarefully()) {
            interact(onState, level, pos);
        }
    }

    // Item use answers with an ItemInteractionResult in 1.20.5 to 1.21.1.
    //#if MC >= 1.21.2
    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
    //#else
    //$$ @Override
    //$$ protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
    //#endif
        if (level.isClientSide()) {
            spawnParticles(level, pos);
        } else {
            interact(state, level, pos);
        }
        //#if MC >= 1.21.2
        return itemStack.getItem() instanceof BlockItem && new BlockPlaceContext(player, hand, itemStack, hitResult).canPlace()
            ? InteractionResult.PASS
            : InteractionResult.SUCCESS;
        //#else
        //$$ return itemStack.getItem() instanceof BlockItem && new BlockPlaceContext(player, hand, itemStack, hitResult).canPlace()
        //$$     ? net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION
        //$$     : net.minecraft.world.ItemInteractionResult.SUCCESS;
        //#endif
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) {
            spawnParticles(level, pos);
        }
    }

    private static void interact(BlockState state, Level level, BlockPos pos) {
        spawnParticles(level, pos);
        if (!state.getValue(LIT)) {
            level.setBlockAndUpdate(pos, state.setValue(LIT, true));
        }
    }

    private static void spawnParticles(Level level, BlockPos pos) {
        RandomSource random = level.getRandom();
        for (Direction direction : Direction.values()) {
            BlockPos relative = pos.relative(direction);
            //#if MC >= 1.21.2
            if (!level.getBlockState(relative).isSolidRender()) {
            //#else
            //$$ if (!level.getBlockState(relative).isSolidRender(level, relative)) {
            //#endif
                Direction.Axis axis = direction.getAxis();
                double dx = axis == Direction.Axis.X ? 0.5 + 0.5625 * direction.getStepX() : random.nextFloat();
                double dy = axis == Direction.Axis.Y ? 0.5 + 0.5625 * direction.getStepY() : random.nextFloat();
                double dz = axis == Direction.Axis.Z ? 0.5 + 0.5625 * direction.getStepZ() : random.nextFloat();
                BluestoneDiodes.particle(level, pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
            }
        }
    }
}
