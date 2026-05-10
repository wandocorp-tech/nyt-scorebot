package com.wandocorp.nytscorebot.service;

/**
 * Shared time-formatting helper.
 *
 * <p>Renders a duration in seconds as {@code m:ss} for sub-hour and {@code h:mm:ss} for
 * durations of one hour or more. Used in both the stats report and PB-break announcements
 * so both render identically.
 */
public final class TimeFormatter {

    private TimeFormatter() {}

    public static String format(int totalSeconds) {
        if (totalSeconds >= 3600) {
            int h = totalSeconds / 3600;
            int m = (totalSeconds % 3600) / 60;
            int s = totalSeconds % 60;
            return String.format("%d:%02d:%02d", h, m, s);
        }
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return String.format("%d:%02d", m, s);
    }
}
