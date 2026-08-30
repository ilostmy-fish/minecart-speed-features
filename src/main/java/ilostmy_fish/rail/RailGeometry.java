package ilostmy_fish.rail;

import net.minecraft.block.enums.RailShape;
import net.minecraft.util.math.Vec3d;

/** Shared 1.21.1 rail-centerline geometry for traversal and boundary ownership” would reflect its current role. */
public final class RailGeometry {
    private static final double BOUNDARY_TOLERANCE = 1.0E-7;
    private static final double OWNERSHIP_PROBE_DISTANCE = 1.0E-4;

    private static final RailEndpoint WEST = new RailEndpoint(-1, 0, 0);
    private static final RailEndpoint EAST = new RailEndpoint(1, 0, 0);
    private static final RailEndpoint NORTH = new RailEndpoint(0, 0, -1);
    private static final RailEndpoint SOUTH = new RailEndpoint(0, 0, 1);
    private static final RailEndpoint WEST_DOWN = new RailEndpoint(-1, -1, 0);
    private static final RailEndpoint EAST_DOWN = new RailEndpoint(1, -1, 0);
    private static final RailEndpoint NORTH_DOWN = new RailEndpoint(0, -1, -1);
    private static final RailEndpoint SOUTH_DOWN = new RailEndpoint(0, -1, 1);

    private RailGeometry() {
    }

    public static Endpoints endpoints(RailShape shape) {
        return switch (shape) {
            case NORTH_SOUTH -> new Endpoints(NORTH, SOUTH);
            case EAST_WEST -> new Endpoints(WEST, EAST);
            case ASCENDING_EAST -> new Endpoints(WEST_DOWN, EAST);
            case ASCENDING_WEST -> new Endpoints(WEST, EAST_DOWN);
            case ASCENDING_NORTH -> new Endpoints(NORTH, SOUTH_DOWN);
            case ASCENDING_SOUTH -> new Endpoints(NORTH_DOWN, SOUTH);
            case SOUTH_EAST -> new Endpoints(SOUTH, EAST);
            case SOUTH_WEST -> new Endpoints(SOUTH, WEST);
            case NORTH_WEST -> new Endpoints(NORTH, WEST);
            case NORTH_EAST -> new Endpoints(NORTH, EAST);
        };
    }

    /** Selects the end toward which a rail-constrained horizontal movement is pointing. */
    public static RailEndpoint exitEndpoint(RailShape shape, double movementX, double movementZ) {
        Endpoints endpoints = endpoints(shape);
        double firstDot = movementX * endpoints.first.x() + movementZ * endpoints.first.z();
        double secondDot = movementX * endpoints.second.x() + movementZ * endpoints.second.z();
        return firstDot < secondDot ? endpoints.second : endpoints.first;
    }

    /**
     * Returns whether the cart has reached the face represented by an outward rail endpoint.
     *
     * <p>A coordinate exactly on a block's minimum face still floors to that block. Treating the
     * face geometrically, rather than relying on {@code floor}, keeps negative and positive exits
     * equivalent.</p>
     */
    public static boolean isAtExitBoundary(RailRef rail, Vec3d position, RailEndpoint exit) {
        if (exit.x() < 0) {
            return position.getX() <= rail.pos().getX() + BOUNDARY_TOLERANCE;
        }
        if (exit.x() > 0) {
            return position.getX() >= rail.pos().getX() + 1.0 - BOUNDARY_TOLERANCE;
        }
        if (exit.z() < 0) {
            return position.getZ() <= rail.pos().getZ() + BOUNDARY_TOLERANCE;
        }
        if (exit.z() > 0) {
            return position.getZ() >= rail.pos().getZ() + 1.0 - BOUNDARY_TOLERANCE;
        }
        return false;
    }

    /**
     * Samples block ownership immediately beyond a face without asking the entity to move there.
     * This distance is representational only and is never charged to the tick movement budget.
     */
    public static Vec3d probePastBoundary(Vec3d boundary, RailEndpoint exit) {
        double horizontal = Math.hypot(exit.x(), exit.z());
        if (horizontal == 0.0) {
            return boundary;
        }
        return boundary.add(
                exit.x() / horizontal * OWNERSHIP_PROBE_DISTANCE,
                0.0,
                exit.z() / horizontal * OWNERSHIP_PROBE_DISTANCE
        );
    }

    public record Endpoints(RailEndpoint first, RailEndpoint second) {
    }
}
