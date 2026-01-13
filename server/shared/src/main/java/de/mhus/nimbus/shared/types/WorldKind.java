package de.mhus.nimbus.shared.types;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Repräsentiert eine Welt-Referenz nach dem Schema: <worldId>[$<zone>][:<branch>]
 * Beispiele:
 *   earth
 *   earth$europe
 *   earth:main
 *   earth$europe:main
 *   earth$europe:dev-feature
 */
public final class WorldKind {

    private static final Pattern PART = Pattern.compile("[a-zA-Z0-9-_]+", Pattern.UNICODE_CASE);

    private final String worldId;   // Pflicht
    private final String zone;      // optional (früher subWorld)
    private final String branch;    // optional

    private WorldKind(String worldId, String zone, String branch) {
        this.worldId = worldId;
        this.zone = zone;
        this.branch = branch;
    }

    public String worldId() { return worldId; }
    public String zone() { return zone; }
    public String branch() { return branch; }

    public boolean hasZone() { return zone != null; }
    public boolean hasBranch() { return branch != null; }

    // Neue Convenience-Methoden
    public boolean isMain() { return zone == null && branch == null; }
    public boolean isZone() { return zone != null && branch == null; }
    public boolean isBranch() { return branch != null; }

    // Deprecated alte API
    @Deprecated public String subWorld() { return zone(); }
    @Deprecated public boolean hasSubWorld() { return hasZone(); }
    @Deprecated public boolean isSubworld() { return isZone(); }

    /**
     * Parst einen String nach Schema: world[$zone][:branch]
     * @param value Eingabestring
     * @return WorldKind Instanz
     * @throws IllegalArgumentException bei ungültiger Struktur
     */
    public static WorldKind of(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("value is blank");
        String trimmed = value.trim();

        String worldPart = trimmed;
        String branchPart = null;

        int colonPos = trimmed.indexOf(':');
        if (colonPos >= 0) {
            worldPart = trimmed.substring(0, colonPos);
            branchPart = trimmed.substring(colonPos + 1);
            if (branchPart.isBlank()) branchPart = null; // leeres Branch ignorieren
        }

        String zonePart = null;
        int dollarPos = worldPart.indexOf('$');
        if (dollarPos >= 0) {
            zonePart = worldPart.substring(dollarPos + 1);
            worldPart = worldPart.substring(0, dollarPos);
            if (zonePart.isBlank()) throw new IllegalArgumentException("zone is blank");
        }

        // Validieren Teile
        if (!PART.matcher(worldPart).matches()) throw new IllegalArgumentException("Invalid worldId: " + worldPart);
        if (zonePart != null && !PART.matcher(zonePart).matches()) throw new IllegalArgumentException("Invalid zone: " + zonePart);
        if (branchPart != null && !PART.matcher(branchPart).matches()) throw new IllegalArgumentException("Invalid branch: " + branchPart);

        return new WorldKind(worldPart, zonePart, branchPart);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(worldId);
        if (hasZone()) sb.append('$').append(zone);
        if (hasBranch()) sb.append(':').append(branch);
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorldKind wk)) return false;
        return Objects.equals(worldId, wk.worldId) && Objects.equals(zone, wk.zone) && Objects.equals(branch, wk.branch);
    }

    @Override
    public int hashCode() { return Objects.hash(worldId, zone, branch); }
}
