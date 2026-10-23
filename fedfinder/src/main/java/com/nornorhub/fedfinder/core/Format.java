package com.nornorhub.fedfinder.core;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Plain-English pay and deadlines. */
public final class Format {

    /** Hours in a federal work year, used to turn hourly pay into a yearly figure. */
    static final int WORK_HOURS_PER_YEAR = 2087;

    private Format() {}

    public static String pay(Job job) {
        String code = job.payInterval == null ? "" : job.payInterval.toUpperCase(Locale.US);
        String name = job.payIntervalName == null ? "" : job.payIntervalName.toLowerCase(Locale.US);
        if (code.equals("WC") || name.contains("without compensation")) {
            return "Unpaid";
        }
        if (job.minPay <= 0 && job.maxPay <= 0) {
            return "Pay not listed";
        }
        boolean hourly = code.equals("PH") || name.contains("hour");
        if (hourly) {
            String range = range(job.minPay, job.maxPay, true) + "/hr";
            String yearly = thousands(job.minPay * WORK_HOURS_PER_YEAR);
            if (job.maxPay > job.minPay) {
                yearly += "–" + thousands(job.maxPay * WORK_HOURS_PER_YEAR);
            }
            return range + " (≈ " + yearly + "/yr)";
        }
        String suffix = code.equals("PA") || name.contains("year") ? "/yr"
                : name.isEmpty() ? "" : " " + name;
        return range(job.minPay, job.maxPay, false) + suffix;
    }

    private static String range(double min, double max, boolean cents) {
        String fmt = cents ? "$%,.2f" : "$%,.0f";
        if (max <= 0 || Math.abs(max - min) < 0.005) {
            return String.format(Locale.US, fmt, Math.max(min, max));
        }
        return String.format(Locale.US, fmt, min) + "–" + String.format(Locale.US, fmt, max);
    }

    /** 38,612.5 -> "$39k". */
    private static String thousands(double amount) {
        return "$" + Math.round(amount / 1000) + "k";
    }

    /** Days from {@code today} until the job closes; negative once closed; null if unknown. */
    public static Long daysLeft(Job job, LocalDate today) {
        if (job.closeDate == null) {
            return null;
        }
        try {
            return ChronoUnit.DAYS.between(today, LocalDate.parse(job.closeDate));
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static String deadline(Job job, LocalDate today) {
        Long days = daysLeft(job, today);
        if (days == null) {
            return "";
        }
        if (days < 0) {
            return "Closed";
        }
        if (days == 0) {
            return "⏰ Closes today";
        }
        if (days == 1) {
            return "⏰ Closes tomorrow";
        }
        return (days <= 7 ? "⏰ " : "") + "Closes in " + days + " days";
    }
}
