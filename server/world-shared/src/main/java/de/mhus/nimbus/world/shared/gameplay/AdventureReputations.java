package de.mhus.nimbus.world.shared.gameplay;

import java.util.List;

/**
 * All reputation definitions for Adventure gameplay mode.
 * Reputations represent how a character is perceived by the world.
 *
 * <p>Values range from negative (bad) to positive (good).
 * A value of 0 means neutral/unknown.</p>
 *
 * @see Reputation
 */
public final class AdventureReputations {

    private AdventureReputations() {}

    // --- Social Reputations ---

    public static final Reputation RENOWNED = Reputation.of(
            "renowned", "Berühmt",
            "Allgemeine Bekanntheit und Ansehen in der Welt",
            "Sozial", 0, -1000, 1000);

    public static final Reputation RESPECTED = Reputation.of(
            "respected", "Respektiert",
            "Wird von anderen als ehrenwert und vertrauenswürdig angesehen",
            "Sozial", 0, -1000, 1000);

    public static final Reputation NOTORIOUS = Reputation.of(
            "notorious", "Berüchtigt",
            "Bekannt für zweifelhafte Taten und fragwürdige Methoden",
            "Sozial", 0, -1000, 1000);

    // --- Combat Reputations ---

    public static final Reputation FEARED = Reputation.of(
            "feared", "Gefürchtet",
            "Feinde weichen zurück, NPCs reagieren eingeschüchtert",
            "Kampf", 0, -1000, 1000);

    public static final Reputation INFAMOUS = Reputation.of(
            "infamous", "Verrufen",
            "Bekannt für rücksichtsloses und zerstörerisches Verhalten",
            "Kampf", 0, -1000, 1000);

    // --- Special Reputations ---

    public static final Reputation LEGENDARY = Reputation.of(
            "legendary", "Legendär",
            "Geschichten über die Taten dieses Charakters werden weitererzählt",
            "Besonders", 0, -1000, 1000);

    /**
     * All defined reputations as a list.
     */
    public static final List<Reputation> ALL = List.of(
            RENOWNED, RESPECTED, NOTORIOUS,
            FEARED, INFAMOUS,
            LEGENDARY
    );

    /**
     * Find a reputation definition by its technical name.
     *
     * @param name Reputation ID, e.g. "renowned"
     * @return Reputation or null if not found
     */
    public static Reputation byName(String name) {
        for (Reputation rep : ALL) {
            if (rep.getName().equals(name)) return rep;
        }
        return null;
    }
}
