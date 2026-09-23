package com.example.poultryscanfinal;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Formats scan timestamps in the device's local timezone. */
public final class TimeUtils {

    private TimeUtils() {
    }

    /** e.g. "17 Sep 2026, 9:42 PM" */
    public static String formatDateTime(long timestampMillis) {
        return new SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
                .format(new Date(timestampMillis));
    }

    /** e.g. "17 September 2026" */
    public static String formatDate(long timestampMillis) {
        return new SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
                .format(new Date(timestampMillis));
    }

    /** e.g. "9:42 PM" */
    public static String formatTime(long timestampMillis) {
        return new SimpleDateFormat("h:mm a", Locale.getDefault())
                .format(new Date(timestampMillis));
    }
}
