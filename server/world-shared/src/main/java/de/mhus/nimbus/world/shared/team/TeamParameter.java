package de.mhus.nimbus.world.shared.team;

/**
 * Defines well-known team parameter keys stored in {@link WTeam#getParameters()}.
 * Values are stored as strings; numeric values should be parsed/incremented atomically
 * via {@link WTeamService#incrementParameterAtomic(String, String, long)}.
 */
public final class TeamParameter {

    private TeamParameter() {}

    /** Number of unique entity kills credited to the team. */
    public static final String KILLS = "kills";

    /** Number of objectives/areas occupied by the team. */
    public static final String OCCUPIED = "occupied";

    /** Total damage dealt by team members. */
    public static final String DAMAGE = "damage";

    /** Number of team members that have died. */
    public static final String DEATHS = "deaths";

    /** Points scored by the team (generic scoring). */
    public static final String SCORE = "score";
}
