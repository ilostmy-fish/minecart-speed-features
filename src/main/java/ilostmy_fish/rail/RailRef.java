package ilostmy_fish.rail;

import net.minecraft.block.BlockState;
import net.minecraft.block.enums.RailShape;
import net.minecraft.util.math.BlockPos;

/** The exact world position and state supplied to one {@code moveOnRail} invocation. */
public record RailRef(BlockPos pos, BlockState state, RailShape shape) {
}
