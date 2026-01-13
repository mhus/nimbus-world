package de.mhus.nimbus.shared.service;

/**
 * Import mode for handling existing entities during import.
 */
public enum ImportMode {
    /**
     * Skip entities that already exist in the database.
     * Existing entities are not modified.
     */
    SKIP,

    /**
     * Overwrite existing entities with imported data.
     * Existing entities are replaced with the imported version.
     */
    OVERWRITE;

    /**
     * Parse import mode from string value.
     *
     * @param value the string value (case-insensitive)
     * @return the ImportMode, defaults to SKIP if invalid
     */
    public static ImportMode fromString(String value) {
        if (value == null) {
            return SKIP;
        }
        try {
            return ImportMode.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SKIP;
        }
    }
}
