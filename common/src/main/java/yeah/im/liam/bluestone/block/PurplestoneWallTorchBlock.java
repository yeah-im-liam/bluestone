package yeah.im.liam.bluestone.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.signal.Channel;
import yeah.im.liam.bluestone.signal.SignalBlock;

/**
 * The wall-mounted {@link PurplestoneTorchBlock}.
 */
public class PurplestoneWallTorchBlock extends RedstoneWallTorchBlock implements SignalBlock {
    public PurplestoneWallTorchBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT)) {
            Direction opposite = state.getValue(FACING).getOpposite();
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.2 + 0.27 * opposite.getStepX();
            double y = pos.getY() + 0.7 + (random.nextDouble() - 0.5) * 0.2 + 0.22;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.2 + 0.27 * opposite.getStepZ();
            level.addParticle(PurplestoneTorchBlock.PARTICLE, x, y, z, 0.0, 0.0, 0.0);
        }
    }

    @Override
    public Channel channel(BlockState state) {
        return Channel.PURPLESTONE;
    }

    @Override
    public boolean connectsToWire(BlockState state, Channel wire, @Nullable Direction direction) {
        return wire == Channel.PURPLESTONE && direction != null;
    }

    @Override
    protected boolean shouldRedstoneWireConnectTo(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return connectsToWire(state, Channel.REDSTONE, direction);
    }

    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return connectsToWire(state, Channel.REDSTONE, direction);
    }
}
