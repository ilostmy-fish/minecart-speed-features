package ilostmy_fish.rail;

/**
 * One end of a rail centerline, expressed as an offset from the rail block.
 *
 * <p>The vertical component follows Minecraft 1.21.1's
 * {@code ADJACENT_RAIL_POSITIONS_BY_SHAPE} convention: the lower end of an ascending rail is
 * represented with {@code y == -1} and its upper end with {@code y == 0}.</p>
 */
public record RailEndpoint(int x, int y, int z) {
}
