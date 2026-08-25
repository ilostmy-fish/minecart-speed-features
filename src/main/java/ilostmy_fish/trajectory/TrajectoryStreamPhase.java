package ilostmy_fish.trajectory;

/** Marks an authoritative trajectory's place in one burst of server updates. */
public enum TrajectoryStreamPhase {
    START(0),
    CONTINUE(1),
    END(2);

    private final int wireValue;

    TrajectoryStreamPhase(int wireValue) {
        this.wireValue = wireValue;
    }

    public int wireValue() {
        return this.wireValue;
    }

    public static TrajectoryStreamPhase fromWireValue(int wireValue) {
        return switch (wireValue) {
            case 0 -> START;
            case 1 -> CONTINUE;
            case 2 -> END;
            default -> throw new IllegalArgumentException(
                    "Invalid trajectory stream phase: " + wireValue
            );
        };
    }
}
