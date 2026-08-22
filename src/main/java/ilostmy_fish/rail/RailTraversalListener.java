package ilostmy_fish.rail;

import net.minecraft.util.math.Vec3d;

/** Optional feature hooks around the traversal solver; these are not pseudo-tick callbacks. */
public interface RailTraversalListener {
    void minecartspeedfeatures$beginRailTraversal(Vec3d tickVelocity);

    void minecartspeedfeatures$leaveRail(RailRef rail, RailEndpoint exitEndpoint);
}
