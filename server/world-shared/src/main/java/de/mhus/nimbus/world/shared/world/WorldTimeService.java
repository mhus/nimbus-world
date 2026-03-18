package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.WorldInfo;
import de.mhus.nimbus.generated.types.WorldInfoSettingsDTOWorldTimeDTO;
import de.mhus.nimbus.generated.types.WorldInfoSettingsDTOWorldTimeDTODaySectionsDTO;
import org.springframework.stereotype.Service;

/**
 * Server-side world time calculation.
 * Mirrors the logic from EnvironmentService.ts on the client.
 *
 * World time is measured in @Minutes since the start of the current era.
 * The formula: linuxEpocheDeltaMinutes + (System.currentTimeMillis() / 60000) * minuteScaling
 */
@Service
public class WorldTimeService {

    // Defaults matching EnvironmentService.ts
    private static final int DEFAULT_MINUTE_SCALING = 10;
    private static final int DEFAULT_MINUTES_PER_HOUR = 60;
    private static final int DEFAULT_HOURS_PER_DAY = 24;
    private static final int DEFAULT_DAYS_PER_MONTH = 30;
    private static final int DEFAULT_MONTHS_PER_YEAR = 12;

    private static final int DEFAULT_MORNING_START = 6;
    private static final int DEFAULT_DAY_START = 12;
    private static final int DEFAULT_EVENING_START = 18;

    /**
     * Calculate the current world minute since the start of the current era.
     */
    public long getCurrentWorldMinute(WorldInfo worldInfo) {
        var wt = getWorldTime(worldInfo);
        int minuteScaling = wt != null && wt.getMinuteScaling() != null ? wt.getMinuteScaling() : DEFAULT_MINUTE_SCALING;
        long delta = wt != null && wt.getLinuxEpocheDeltaMinutes() != null ? wt.getLinuxEpocheDeltaMinutes() : 0L;

        long nowMinutes = System.currentTimeMillis() / 60_000L;
        return delta + nowMinutes * minuteScaling;
    }

    /**
     * Get a full WorldTime snapshot for the current moment.
     */
    public WorldTime getCurrentWorldTime(WorldInfo worldInfo) {
        long worldMinute = getCurrentWorldMinute(worldInfo);
        return toWorldTime(worldInfo, worldMinute);
    }

    /**
     * Convert a raw world-minute value into a WorldTime snapshot.
     */
    public WorldTime toWorldTime(WorldInfo worldInfo, long worldMinute) {
        var wt = getWorldTime(worldInfo);
        int minutesPerHour = wt != null && wt.getMinutesPerHour() != null ? wt.getMinutesPerHour() : DEFAULT_MINUTES_PER_HOUR;
        int hoursPerDay = wt != null && wt.getHoursPerDay() != null ? wt.getHoursPerDay() : DEFAULT_HOURS_PER_DAY;
        int daysPerMonth = wt != null && wt.getDaysPerMonth() != null ? wt.getDaysPerMonth() : DEFAULT_DAYS_PER_MONTH;
        int monthsPerYear = wt != null && wt.getMonthsPerYear() != null ? wt.getMonthsPerYear() : DEFAULT_MONTHS_PER_YEAR;
        int era = wt != null && wt.getCurrentEra() != null ? wt.getCurrentEra() : 1;

        long remaining = worldMinute;

        int minute = (int) (remaining % minutesPerHour);
        remaining /= minutesPerHour;

        int hour = (int) (remaining % hoursPerDay);
        remaining /= hoursPerDay;

        int day = (int) (remaining % daysPerMonth) + 1; // 1-based
        remaining /= daysPerMonth;

        int month = (int) (remaining % monthsPerYear) + 1; // 1-based
        remaining /= monthsPerYear;

        int year = (int) remaining + 1; // 1-based

        String daySection = calculateDaySection(worldInfo, hour);

        return new WorldTime(era, year, month, day, hour, minute, daySection, worldMinute);
    }

    /**
     * Get the current hour of the world day (0..hoursPerDay-1).
     */
    public int getCurrentHourOfDay(WorldInfo worldInfo) {
        return getCurrentWorldTime(worldInfo).hour();
    }

    /**
     * Get the current day section: "morning", "day", "evening", or "night".
     */
    public String getCurrentDaySection(WorldInfo worldInfo) {
        return getCurrentWorldTime(worldInfo).daySection();
    }

    /**
     * Calculate world-minutes from time components.
     * Useful for computing totalMinutes for a parsed WorldTime.
     */
    public long toWorldMinute(WorldInfo worldInfo, int year, int month, int day, int hour, int minute) {
        var wt = getWorldTime(worldInfo);
        int minutesPerHour = wt != null && wt.getMinutesPerHour() != null ? wt.getMinutesPerHour() : DEFAULT_MINUTES_PER_HOUR;
        int hoursPerDay = wt != null && wt.getHoursPerDay() != null ? wt.getHoursPerDay() : DEFAULT_HOURS_PER_DAY;
        int daysPerMonth = wt != null && wt.getDaysPerMonth() != null ? wt.getDaysPerMonth() : DEFAULT_DAYS_PER_MONTH;
        int monthsPerYear = wt != null && wt.getMonthsPerYear() != null ? wt.getMonthsPerYear() : DEFAULT_MONTHS_PER_YEAR;

        long total = (long) (year - 1) * monthsPerYear;
        total = (total + (month - 1)) * daysPerMonth;
        total = (total + (day - 1)) * hoursPerDay;
        total = (total + hour) * minutesPerHour;
        total += minute;
        return total;
    }

    private String calculateDaySection(WorldInfo worldInfo, int hourOfDay) {
        var wt = getWorldTime(worldInfo);
        var ds = wt != null ? wt.getDaySections() : null;

        int morningStart = ds != null && ds.getMorningStart() != null ? ds.getMorningStart() : DEFAULT_MORNING_START;
        int dayStart = ds != null && ds.getDayStart() != null ? ds.getDayStart() : DEFAULT_DAY_START;
        int eveningStart = ds != null && ds.getEveningStart() != null ? ds.getEveningStart() : DEFAULT_EVENING_START;

        if (hourOfDay >= morningStart && hourOfDay < dayStart) {
            return "morning";
        } else if (hourOfDay >= dayStart && hourOfDay < eveningStart) {
            return "day";
        } else if (hourOfDay >= eveningStart) {
            return "evening";
        } else {
            return "night";
        }
    }

    private WorldInfoSettingsDTOWorldTimeDTO getWorldTime(WorldInfo worldInfo) {
        if (worldInfo == null || worldInfo.getSettings() == null) return null;
        return worldInfo.getSettings().getWorldTime();
    }
}
