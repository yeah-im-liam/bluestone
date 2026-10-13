package yeah.im.liam.bluestone.mixin;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.RailState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yeah.im.liam.bluestone.block.AttachedRailBlock;
import yeah.im.liam.bluestone.rail.WallRails;

/**
 * Floor rails join sticky rails on walls (see {@link WallRails}): the game's rail logic counts a wall rail it can join
 * as a rail next to it, keeps that connection, and slopes a rail up to a wall rail at an inside corner.
 */
@Mixin(RailState.class)
abstract class RailStateMixin {
    @Shadow
    @Final
    private Level level;
    @Shadow
    @Final
    private BlockPos pos;
    @Shadow
    private BlockState state;
    @Shadow
    @Final
    private List<BlockPos> connections;

    @Inject(method = "hasNeighborRail", at = @At("RETURN"), cancellable = true)
    private void bluestone$wallRail(BlockPos railPos, CallbackInfoReturnable<Boolean> cir) {
        Direction way = WallRails.way(pos, railPos);
        if (!cir.getReturnValueZ() && way != null && WallRails.joins(level, pos, way)) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private void bluestone$slope() {
        BaseRailBlock rail = (BaseRailBlock) state.getBlock();
        RailShape shape = state.getValue(rail.getShapeProperty());
        RailShape sloped = WallRails.sloped(level, pos, shape);
        if (sloped != shape && level.getBlockState(pos) == state) {
            state = state.setValue(rail.getShapeProperty(), sloped);
            level.setBlockAndUpdate(pos, state);
        }
    }

    // The game drops connections that lead to no rail; one that leads to a wall rail stays.
    @Inject(method = "removeSoftConnections", at = @At("TAIL"))
    private void bluestone$keepWallRails(CallbackInfo ci) {
        RailShape shape = state.getValue(((BaseRailBlock) state.getBlock()).getShapeProperty());
        for (Direction way : WallRails.ends(shape)) {
            BlockPos to = pos.relative(way);
            if (connections.stream().noneMatch(connection -> connection.getX() == to.getX() && connection.getZ() == to.getZ())
                && WallRails.joins(level, pos, way)) {
                connections.add(to);
            }
        }
    }

    // A rail laid at the foot of a wall rail slopes up to it. A wall rail down the side of the block this rail sits on
    // isn't next to it, so it is told this rail is here.
    @Inject(method = "place", at = @At("TAIL"))
    private void bluestone$tellWallRails(boolean hasSignal, boolean first, RailShape defaultShape, CallbackInfoReturnable<RailState> cir) {
        bluestone$slope();
        for (Direction way : Direction.Plane.HORIZONTAL) {
            BlockPos below = pos.relative(way).below();
            if (level.getBlockState(below).getBlock() instanceof AttachedRailBlock) {
                level.neighborChanged(below, state.getBlock(), null);
            }
        }
    }
}
