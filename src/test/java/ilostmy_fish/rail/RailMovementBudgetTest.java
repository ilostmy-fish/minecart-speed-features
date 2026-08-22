package ilostmy_fish.rail;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RailMovementBudgetTest {
    private static final double TOLERANCE = 1.0E-9;

    @Test
    void constantVelocityCrossesEachRailWithoutMultiplyingDisplacement() {
        RailMovementBudget budget = new RailMovementBudget();
        List<Integer> visitedRailBlocks = new ArrayList<>();
        double x = 0.2;
        double start = x;

        while (budget.hasTimeRemaining()) {
            int railX = (int)Math.floor(x);
            visitedRailBlocks.add(railX);
            RailMovementBudget.MovementLimit limit = budget.limit(
                    x, 0.5, railX, 0, 3.4, 0.0
            );
            double movementX = limit.scaleX();
            x += movementX;
            RailMovementBudget.MovementCompletion completion = budget.complete(
                    limit, movementX, 0.0
            );
            if (!completion.reachedBoundary()) {
                break;
            }
        }

        assertEquals(List.of(0, 1, 2, 3), visitedRailBlocks);
        assertEquals(3.4, x - start, TOLERANCE);
        assertEquals(0.0, budget.remainingTime(), TOLERANCE);
    }

    @Test
    void nextRailVelocityUsesOnlyTheRemainingTickFraction() {
        RailMovementBudget budget = new RailMovementBudget();
        double x = 0.25;

        RailMovementBudget.MovementLimit first = budget.limit(x, 0.5, 0, 0, 2.0, 0.0);
        x += first.scaleX();
        RailMovementBudget.MovementCompletion firstCompletion = budget.complete(
                first, first.scaleX(), 0.0
        );
        assertTrue(firstCompletion.reachedBoundary());

        // A rail effect halves the full-tick velocity. It applies to the time left after reaching
        // the first boundary; it does not grant a fresh one-block displacement.
        RailMovementBudget.MovementLimit second = budget.limit(x, 0.5, 1, 0, 1.0, 0.0);
        x += second.scaleX();
        RailMovementBudget.MovementCompletion secondCompletion = budget.complete(
                second, second.scaleX(), 0.0
        );

        assertFalse(secondCompletion.reachedBoundary());
        assertEquals(1.625, x, TOLERANCE);
        assertEquals(0.0, budget.remainingTime(), TOLERANCE);
    }

    @Test
    void orderedRailVelocityChangesCarryIntoLaterSegments() {
        RailMovementBudget budget = new RailMovementBudget();
        double x = 0.2;

        // Full-tick movement proposed by ordinary -> powered -> copper -> braking rail calls.
        // These values include the normal repeated slowdown behavior retained by the solver.
        double[] requestedMovementByRail = {3.4, 3.264, 3.19344, 1.7828512};
        List<Integer> visitedRailBlocks = new ArrayList<>();
        for (double requestedMovement : requestedMovementByRail) {
            int railX = (int)Math.floor(x);
            visitedRailBlocks.add(railX);
            RailMovementBudget.MovementLimit limit = budget.limit(
                    x, 0.5, railX, 0, requestedMovement, 0.0
            );
            double movementX = limit.scaleX();
            x += movementX;
            RailMovementBudget.MovementCompletion completion = budget.complete(
                    limit, movementX, 0.0
            );
            if (!completion.reachedBoundary()) {
                break;
            }
        }

        assertEquals(List.of(0, 1, 2, 3), visitedRailBlocks);
        assertEquals(3.2588546480885814, x, TOLERANCE);
        assertEquals(0.0, budget.remainingTime(), TOLERANCE);
    }

    @Test
    void negativeTravelEndsOnTheMathematicalBlockFace() {
        RailMovementBudget budget = new RailMovementBudget();
        double x = 2.8;

        RailMovementBudget.MovementLimit limit = budget.limit(x, 0.5, 2, 0, -3.4, 0.0);
        double movementX = limit.scaleX();
        x += movementX;
        RailMovementBudget.MovementCompletion completion = budget.complete(
                limit, movementX, 0.0
        );

        assertTrue(completion.reachedBoundary());
        assertEquals(2.0, x, TOLERANCE);
    }

    @Test
    void exactHalfBlockCopperStartupReachesBothFacesSymmetrically() {
        RailMovementBudget positiveBudget = new RailMovementBudget();
        RailMovementBudget.MovementLimit positive = positiveBudget.limit(
                0.5, 0.5, 0, 0, 0.5, 0.0
        );
        RailMovementBudget.MovementCompletion positiveCompletion = positiveBudget.complete(
                positive, positive.scaleX(), 0.0
        );

        RailMovementBudget negativeBudget = new RailMovementBudget();
        RailMovementBudget.MovementLimit negative = negativeBudget.limit(
                0.5, 0.5, 0, 0, -0.5, 0.0
        );
        RailMovementBudget.MovementCompletion negativeCompletion = negativeBudget.complete(
                negative, negative.scaleX(), 0.0
        );

        assertTrue(positiveCompletion.reachedBoundary());
        assertTrue(negativeCompletion.reachedBoundary());
        assertEquals(1.0, 0.5 + positive.scaleX(), TOLERANCE);
        assertEquals(0.0, 0.5 + negative.scaleX(), TOLERANCE);
        assertEquals(0.0, positiveBudget.remainingTime(), TOLERANCE);
        assertEquals(0.0, negativeBudget.remainingTime(), TOLERANCE);
    }

    @Test
    void clippedEntityMovementStopsTheSegmentAtACollision() {
        RailMovementBudget budget = new RailMovementBudget();
        RailMovementBudget.MovementLimit limit = budget.limit(0.2, 0.5, 0, 0, 3.4, 0.0);

        RailMovementBudget.MovementCompletion completion = budget.complete(limit, 0.3, 0.0);

        assertTrue(completion.blocked());
        assertFalse(completion.reachedBoundary());
        assertEquals(1.0 - 0.3 / 3.4, budget.remainingTime(), TOLERANCE);
    }
}
