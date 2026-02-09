package de.mhus.nimbus.world.generator.genesis;

/**
 * Phases of Day3Generation workflow.
 * Defines the sequential steps for hex grid generation and export.
 */
public enum Day3Phase {
    CREATE_ALL("createAll", "Create all hex grids"),
    GROUND_ALL("groundAll", "Apply ground manipulation to all grids"),
    BLENDER_ALL("blenderAll", "Apply blender manipulation to all grids"),
    TERRAIN_ALL("terrainAll", "Apply terrain manipulation to all grids"),
    FILLER_ALL("fillerAll", "Apply filler manipulation to all grids"),
    EXPORT_ALL("exportAll", "Export all grids to layers"),
    IMAGES_ALL("imagesAll", "Export all grid images"),
    COMPOSITE_IMAGES("compositeImages", "Create composite images of entire world");

    private final String phaseName;
    private final String description;

    Day3Phase(String phaseName, String description) {
        this.phaseName = phaseName;
        this.description = description;
    }

    public String getPhaseName() {
        return phaseName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Get phase by phase name (case-insensitive).
     */
    public static Day3Phase fromPhaseName(String phaseName) {
        if (phaseName == null || phaseName.isBlank()) {
            return null;
        }
        for (Day3Phase phase : values()) {
            if (phase.phaseName.equalsIgnoreCase(phaseName)) {
                return phase;
            }
        }
        return null;
    }

    /**
     * Get next phase in sequence, or null if this is the last phase.
     */
    public Day3Phase next() {
        int nextOrdinal = this.ordinal() + 1;
        if (nextOrdinal >= values().length) {
            return null;
        }
        return values()[nextOrdinal];
    }

    /**
     * Check if this phase should continue to the next phase based on target.
     */
    public boolean shouldContinue(Day3Phase targetPhase) {
        if (targetPhase == null) {
            return true; // No limit, continue to end
        }
        return this.ordinal() < targetPhase.ordinal();
    }
}
