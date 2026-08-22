package ilostmy_fish.rail;

import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.RailShape;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** Resolves the actual rail block/state at an authoritative minecart position. */
public final class RailResolver {
    private RailResolver() {
    }

    @Nullable
    public static RailRef from(BlockPos pos, BlockState state) {
        if (!AbstractRailBlock.isRail(state)) {
            return null;
        }

        Block block = state.getBlock();
        if (!(block instanceof AbstractRailBlock railBlock)) {
            return null;
        }

        RailShape shape = state.get(railBlock.getShapeProperty());
        return new RailRef(pos.toImmutable(), state, shape);
    }

    /** Matches the below-first lookup used by the vanilla 1.21.1 minecart tick. */
    @Nullable
    public static RailRef atCart(World world, Vec3d cartPosition) {
        BlockPos cartBlock = BlockPos.ofFloored(cartPosition);
        BlockPos below = cartBlock.down();
        BlockState belowState = world.getBlockState(below);
        RailRef belowRail = from(below, belowState);
        if (belowRail != null) {
            return belowRail;
        }

        return from(cartBlock, world.getBlockState(cartBlock));
    }

    /** Resolves ownership on the far side of a completed rail face without moving the entity. */
    @Nullable
    public static RailRef afterBoundary(
            World world,
            RailRef current,
            Vec3d boundaryPosition,
            RailEndpoint exit
    ) {
        RailRef result = atCart(world, RailGeometry.probePastBoundary(boundaryPosition, exit));
        return result == null || result.pos().equals(current.pos()) ? null : result;
    }

    /**
     * Disambiguates a cart that begins a tick exactly on a rail's minimum block face.
     * Positive faces naturally floor into the next block; negative faces need the travel direction
     * to decide whether the old rail still owns the cart.
     */
    @Nullable
    public static RailRef atTraversalStart(
            World world,
            RailRef vanillaRail,
            Vec3d cartPosition,
            Vec3d velocity
    ) {
        if (Math.hypot(velocity.getX(), velocity.getZ()) <= RailMovementBudget.TIME_EPSILON) {
            return vanillaRail;
        }

        RailEndpoint exit = RailGeometry.exitEndpoint(
                vanillaRail.shape(),
                velocity.getX(),
                velocity.getZ()
        );
        if (!RailGeometry.isAtExitBoundary(vanillaRail, cartPosition, exit)) {
            return vanillaRail;
        }
        return afterBoundary(world, vanillaRail, cartPosition, exit);
    }
}
