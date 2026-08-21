package ilostmy_fish.rail;

public final class MoveResult {
    public final double remainingMovement;
    public final boolean reachedEndpoint;
    public final boolean stuck;
    public final int forwardDy;

    public MoveResult(double remainingMovement, boolean reachedEndpoint, boolean stuck, int forwardDy) {
        this.remainingMovement = remainingMovement;
        this.reachedEndpoint = reachedEndpoint;
        this.stuck = stuck;
        this.forwardDy = forwardDy;
    }
}
