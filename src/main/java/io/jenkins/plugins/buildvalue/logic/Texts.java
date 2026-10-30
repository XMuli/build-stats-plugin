package io.jenkins.plugins.buildvalue.logic;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class Texts {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private Texts() {}

    static String duration(long ms) {
        if (ms < 0) {
            ms = 0;
        }
        long totalSeconds = ms / 1000;
        long days = totalSeconds / 86_400;
        long hours = (totalSeconds % 86_400) / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;
        if (days > 0) {
            return hours == 0 ? days + "d" : days + "d " + hours + "h";
        }
        if (hours > 0) {
            return minutes == 0 ? hours + "h" : hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return seconds == 0 ? minutes + "m" : minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    static String percent(int numerator, int denominator) {
        if (denominator <= 0) {
            return "—";
        }
        return String.format(Locale.ROOT, "%.1f%%", (100.0 * numerator) / denominator);
    }

    static String points(double pp) {
        return String.format(Locale.ROOT, "%.1f", Math.abs(pp));
    }

    static String escape(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    static String resultLabel(String result) {
        return switch (result) {
            case "SUCCESS" -> "Success";
            case "FAILURE" -> "Failure";
            case "UNSTABLE" -> "Unstable";
            case "ABORTED" -> "Aborted";
            case "NOT_BUILT" -> "Not built";
            default -> "Running";
        };
    }

    static String resultClass(String result) {
        return switch (result) {
            case "SUCCESS" -> "is-ok";
            case "FAILURE" -> "is-bad";
            case "UNSTABLE" -> "is-warn";
            case "ABORTED", "NOT_BUILT" -> "is-muted";
            default -> "is-run";
        };
    }

    static String triggerLabel(String trigger) {
        return switch (trigger) {
            case "manual" -> "Manual";
            case "scm" -> "Commit";
            case "timer" -> "Timer";
            case "upstream" -> "Upstream";
            default -> "Other";
        };
    }

    static String day(long epochMs, ZoneId zone) {
        return DAY.format(Instant.ofEpochMilli(epochMs).atZone(zone));
    }

    static String when(long epochMs, ZoneId zone) {
        return WHEN.format(Instant.ofEpochMilli(epochMs).atZone(zone));
    }

    static String day(java.time.LocalDate date) {
        return DAY.format(date);
    }

    static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, Math.max(1, max - 1)) + "…";
    }

    static String num(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.05) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
