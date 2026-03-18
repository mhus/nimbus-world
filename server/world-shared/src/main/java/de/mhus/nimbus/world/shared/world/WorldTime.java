package de.mhus.nimbus.world.shared.world;

/**
 * Immutable snapshot of the current world time with all derived components.
 * Computed by WorldTimeService from WorldInfo settings.
 *
 * Time format as string: "@era, @year.month.day, hour:minute"
 * Parseable via {@link #parse(String)}.
 */
public record WorldTime(
        int era,
        int year,       // 1-based
        int month,      // 1-based
        int day,        // 1-based
        int hour,       // 0-based (0..hoursPerDay-1)
        int minute,     // 0-based (0..minutesPerHour-1)
        String daySection,  // "morning"|"day"|"evening"|"night"
        long totalMinutes   // raw world-minutes since era start
) {

    /**
     * Check if this time is after or equal to the given hour of day.
     */
    public boolean isHourAfterOrEqual(int h) {
        return hour >= h;
    }

    /**
     * Check if this time is before the given hour of day.
     */
    public boolean isHourBefore(int h) {
        return hour < h;
    }

    /**
     * Check if the current hour falls within [fromHour, toHour).
     * Handles wrap-around (e.g. fromHour=22, toHour=6 matches hours 22,23,0,1,2,3,4,5).
     */
    public boolean isHourInRange(int fromHour, int toHour) {
        if (fromHour < toHour) {
            return hour >= fromHour && hour < toHour;
        }
        // Wrap-around: e.g. 22..6 means 22,23,0,1,2,3,4,5
        return hour >= fromHour || hour < toHour;
    }

    /**
     * Check if this world time is after another world time (by totalMinutes).
     */
    public boolean isAfter(WorldTime other) {
        return totalMinutes > other.totalMinutes;
    }

    /**
     * Check if this world time is before another world time (by totalMinutes).
     */
    public boolean isBefore(WorldTime other) {
        return totalMinutes < other.totalMinutes;
    }

    /**
     * Check if the day section matches.
     */
    public boolean isDaySection(String section) {
        return daySection != null && daySection.equals(section);
    }

    public boolean isMorning() { return "morning".equals(daySection); }
    public boolean isDay()     { return "day".equals(daySection); }
    public boolean isEvening() { return "evening".equals(daySection); }
    public boolean isNight()   { return "night".equals(daySection); }

    /**
     * Format as string: "@era, @year.month.day, hour:minute"
     */
    @Override
    public String toString() {
        return "@" + era + ", @" + year + "." + month + "." + day + ", " + hour + ":" + String.format("%02d", minute);
    }

    /**
     * Parse a world time string: "@era, @year.month.day, hour:minute"
     *
     * @param text Formatted world time string
     * @return WorldTime with parsed components (daySection will be null, totalMinutes 0)
     * @throws IllegalArgumentException if the format is invalid
     */
    public static WorldTime parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("WorldTime string is null or blank");
        }

        try {
            // "@1, @2.3.15, 14:30"
            String[] parts = text.split(",");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Expected 3 comma-separated parts");
            }

            // Era: "@1"
            int era = Integer.parseInt(parts[0].trim().replace("@", ""));

            // Date: "@2.3.15"
            String datePart = parts[1].trim().replace("@", "");
            String[] dateParts = datePart.split("\\.");
            if (dateParts.length != 3) {
                throw new IllegalArgumentException("Expected year.month.day");
            }
            int year = Integer.parseInt(dateParts[0]);
            int month = Integer.parseInt(dateParts[1]);
            int day = Integer.parseInt(dateParts[2]);

            // Time: "14:30"
            String timePart = parts[2].trim();
            String[] timeParts = timePart.split(":");
            if (timeParts.length != 2) {
                throw new IllegalArgumentException("Expected hour:minute");
            }
            int hour = Integer.parseInt(timeParts[0]);
            int minute = Integer.parseInt(timeParts[1]);

            return new WorldTime(era, year, month, day, hour, minute, null, 0);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number in world time string: " + text, e);
        }
    }
}
