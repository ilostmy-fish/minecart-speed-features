package ilostmy_fish.rail;

import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.RailShape;

public final class RailRef {
    public final BlockPos pos;
    public final BlockState state;
    public final RailShape shape;

    public RailRef(BlockPos pos, BlockState state, RailShape shape) {
        this.pos = pos;
        this.state = state;
        this.shape = shape;
    }
}
