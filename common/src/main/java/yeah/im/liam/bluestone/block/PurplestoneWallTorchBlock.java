package yeah.im.liam.bluestone.block;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.signal.Channel;

/**
 * The wall-mounted {@link PurplestoneTorchBlock}.
 */
public class PurplestoneWallTorchBlock extends BluestoneWallTorchBlock {
    public PurplestoneWallTorchBlock(Properties properties) {
        super(properties);
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
    protected ParticleOptions particle() {
        return PurplestoneTorchBlock.PARTICLE;
    }
}
