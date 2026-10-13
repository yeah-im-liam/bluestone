package yeah.im.liam.bluestone.block;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.signal.Channel;

/**
 * A torch on the purplestone channel: redstone or bluestone can turn it off, but only purplestone dust, the
 * converter and plain blocks pick up its signal.
 */
public class PurplestoneTorchBlock extends BluestoneTorchBlock {
    // Redstone's particle colour run through the purplestone dust mix.
    static final DustParticleOptions PARTICLE = new DustParticleOptions(0x9900FF, 1.0F);

    public PurplestoneTorchBlock(Properties properties) {
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
        return PARTICLE;
    }
}
