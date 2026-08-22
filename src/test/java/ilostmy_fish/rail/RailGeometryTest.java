package ilostmy_fish.rail;

import net.minecraft.block.enums.RailShape;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RailGeometryTest {
    @Test
    void ascendingRailDistinguishesUpperAndLowerExit() {
        assertEquals(
                new RailEndpoint(1, 0, 0),
                RailGeometry.exitEndpoint(RailShape.ASCENDING_EAST, 1.0, 0.0)
        );
        assertEquals(
                new RailEndpoint(-1, -1, 0),
                RailGeometry.exitEndpoint(RailShape.ASCENDING_EAST, -1.0, 0.0)
        );
    }

    @Test
    void curvedRailSelectsTheEndpointInMovementDirection() {
        assertEquals(
                new RailEndpoint(1, 0, 0),
                RailGeometry.exitEndpoint(RailShape.SOUTH_EAST, 1.0, -1.0)
        );
        assertEquals(
                new RailEndpoint(0, 0, 1),
                RailGeometry.exitEndpoint(RailShape.SOUTH_EAST, -1.0, 1.0)
        );
    }

    @Test
    void exactMinimumAndMaximumFacesHaveDirectionalOwnership() {
        RailRef eastWest = new RailRef(
                new BlockPos(0, 0, 0), null, RailShape.EAST_WEST
        );
        RailEndpoint west = new RailEndpoint(-1, 0, 0);
        RailEndpoint east = new RailEndpoint(1, 0, 0);

        assertTrue(RailGeometry.isAtExitBoundary(
                eastWest, new Vec3d(0.0, 0.0, 0.5), west
        ));
        assertTrue(RailGeometry.isAtExitBoundary(
                eastWest, new Vec3d(1.0, 0.0, 0.5), east
        ));
        assertFalse(RailGeometry.isAtExitBoundary(
                eastWest, new Vec3d(0.01, 0.0, 0.5), west
        ));

        Vec3d westProbe = RailGeometry.probePastBoundary(
                new Vec3d(0.0, 0.0, 0.5), west
        );
        Vec3d eastProbe = RailGeometry.probePastBoundary(
                new Vec3d(1.0, 0.0, 0.5), east
        );
        assertEquals(-1, (int)Math.floor(westProbe.getX()));
        assertEquals(1, (int)Math.floor(eastProbe.getX()));
    }

    @Test
    void northAndSouthProbesCrossTheExpectedFace() {
        Vec3d northProbe = RailGeometry.probePastBoundary(
                new Vec3d(0.5, 0.0, 0.0), new RailEndpoint(0, 0, -1)
        );
        Vec3d southProbe = RailGeometry.probePastBoundary(
                new Vec3d(0.5, 0.0, 1.0), new RailEndpoint(0, 0, 1)
        );

        assertEquals(-1, (int)Math.floor(northProbe.getZ()));
        assertEquals(1, (int)Math.floor(southProbe.getZ()));
    }
}
