package yeah.im.liam.bluestone.block;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import yeah.im.liam.bluestone.signal.Channel;
import yeah.im.liam.bluestone.signal.Signals;

/**
 * Colours a lever's particles after the stones it powers, picking one of them at random for each particle.
 */
public final class LeverParticles {
    private LeverParticles() {
    }

    public static int colour(LevelAccessor level, BlockPos pos, BlockState lever, int fallback) {
        Direction out = switch (lever.getValue(LeverBlock.FACE)) {
            case FLOOR -> Direction.UP;
            case CEILING -> Direction.DOWN;
            case WALL -> lever.getValue(LeverBlock.FACING);
        };
        Set<Channel> powered = EnumSet.noneOf(Channel.class);
        poweredAround(level, pos, null, powered);
        BlockPos support = pos.relative(out.getOpposite());
        if (level.getBlockState(support).isRedstoneConductor(level, support)) {
            poweredAround(level, support, pos, powered);
        }
        if (powered.isEmpty()) {
            return fallback;
        }
        List<Channel> channels = List.copyOf(powered);
        return colour(channels.get(level.getRandom().nextInt(channels.size())));
    }

    private static void poweredAround(LevelAccessor level, BlockPos source, @Nullable BlockPos skip, Set<Channel> powered) {
        for (Direction direction : Direction.values()) {
            BlockPos at = source.relative(direction);
            if (!at.equals(skip)) {
                Channel channel = reads(level.getBlockState(at), direction.getOpposite());
                if (channel != null) {
                    powered.add(channel);
                }
            }
        }
    }

    // The channel of a part that takes power from that side of it, or null.
    private static @Nullable Channel reads(BlockState state, Direction side) {
        Block block = state.getBlock();
        if (Signals.isDust(state) || block instanceof ConverterBoxBlock) {
            return Channel.ofSource(state);
        }
        if (block instanceof RedstoneTorchBlock) {
            Direction support = block instanceof RedstoneWallTorchBlock ? state.getValue(RedstoneWallTorchBlock.FACING).getOpposite() : Direction.DOWN;
            return side == support ? Channel.ofSource(state) : null;
        }
        if (block instanceof PurplestoneConverterBlock) {
            return side == state.getValue(DiodeBlock.FACING) ? state.getValue(PurplestoneConverterBlock.MODE).input() : null;
        }
        if (block instanceof DiodeBlock) {
            return side == state.getValue(DiodeBlock.FACING) ? Channel.ofSource(state) : null;
        }
        if (block instanceof GreenstoneDiodeBlock) {
            return side == GreenstoneDiodeBlock.input(state) ? Channel.GREENSTONE : null;
        }
        return RailPower.takesPower(state) ? RailPower.channel(state) : null;
    }

    private static int colour(Channel channel) {
        return switch (channel) {
            case REDSTONE -> 0xFF0000;
            case BLUESTONE -> 0x0000FF;
            case PURPLESTONE -> 0x9900FF;
            case GREENSTONE -> 0x00FF00;
        };
    }
}
