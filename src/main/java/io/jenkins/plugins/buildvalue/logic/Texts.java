package io.jenkins.plugins.buildvalue.logic;

import io.jenkins.plugins.buildvalue.Messages;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;

public final class Texts {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private Texts() {}

    /**
     * True when the current request asks for Chinese.
     *
     * <p>Jenkins resolves the locale from the user's own choice, falling back to the browser's
     * {@code Accept-Language}. Reading it here means text and artwork are picked before the
     * response is written, so nothing swaps after the page loads.
     */
    static boolean chineseRequest() {
        StaplerRequest2 request = Stapler.getCurrentRequest2();
        if (request == null || request.getLocale() == null) {
            return false;
        }
        return "zh".equals(request.getLocale().getLanguage());
    }

    /**
     * Localized result word. Accepts either a Jenkins result key ({@code SUCCESS}) or the English
     * label an older row may carry ({@code Success}), so both call sites behave the same.
     */
    public static String stateText(String result) {
        return switch (key(result)) {
            case "RUNNING" -> Messages.state_run();
            case "SUCCESS" -> Messages.state_ok();
            case "FAILURE" -> Messages.state_fail();
            case "UNSTABLE" -> Messages.state_unstable();
            case "ABORTED" -> Messages.state_abort();
            case "NOT_BUILT" -> Messages.state_none();
            default -> "";
        };
    }

    /** Badge modifier: ok, run, bad, warn, stop or muted. */
    public static String stateClass(String result) {
        return switch (key(result)) {
            case "RUNNING" -> "run";
            case "SUCCESS" -> "ok";
            case "FAILURE" -> "bad";
            case "UNSTABLE" -> "warn";
            case "ABORTED", "NOT_BUILT" -> "stop";
            default -> "muted";
        };
    }

    /** Normalize a result key or its English label to the key form. */
    private static String key(String result) {
        if (result == null || result.isEmpty()) {
            return "";
        }
        return result.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    /**
     * One string from the plugin's bundle, for the locale Jenkins resolved for this request.
     *
     * <p>{@code Messages} is generated from {@code Messages.properties} and picks the locale from
     * Stapler's current request, so this is the same lookup the generated accessors do, by key.
     * An unknown key returns the key itself rather than throwing: a missing translation should
     * show up as an odd label on the page, not as a 500.
     */
    public static String message(String key, Object... arguments) {
        if (key == null || key.isBlank()) {
            return "";
        }
        try {
            return org.jvnet.localizer.ResourceBundleHolder.get(Messages.class).format(key, arguments);
        } catch (RuntimeException ex) {
            return key;
        }
    }

    /** One value in the request's language, picking from a packed English/Chinese pair. */
    public static String localized(String packed) {
        if (packed == null) {
            return "";
        }
        int split = packed.indexOf(LANG_SPLIT);
        if (split < 0) {
            return packed;
        }
        return chineseRequest() ? packed.substring(split + 1) : packed.substring(0, split);
    }

    static final char LANG_SPLIT = '\u001f';

    static String duration(long ms) {
        return duration(ms, false);
    }

    /** English and Chinese text for one value, so the page can switch language. */
    static String durations(long ms) {
        return both(duration(ms, false), duration(ms, true));
    }

    static String duration(long ms, boolean chinese) {
        if (ms < 0) {
            ms = 0;
        }
        long totalSeconds = ms / 1000;
        long days = totalSeconds / 86_400;
        long hours = (totalSeconds % 86_400) / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;
        if (chinese) {
            if (days > 0) {
                return hours == 0 ? days + " 天" : days + " 天 " + hours + " 小时";
            }
            if (hours > 0) {
                return minutes == 0 ? hours + " 小时" : hours + " 小时 " + minutes + " 分";
            }
            if (minutes > 0) {
                return seconds == 0 ? minutes + " 分" : minutes + " 分 " + seconds + " 秒";
            }
            return seconds + " 秒";
        }
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

    static String both(String english, String chinese) {
        if (english == null) {
            english = "";
        }
        if (chinese == null || chinese.isEmpty() || chinese.equals(english)) {
            return english;
        }
        return english + LANG_SPLIT + chinese;
    }

    static String chinese(String packed) {
        if (packed == null) {
            return "";
        }
        int split = packed.indexOf(LANG_SPLIT);
        return split < 0 ? "" : packed.substring(split + 1);
    }

    static String english(String packed) {
        if (packed == null) {
            return "";
        }
        int split = packed.indexOf(LANG_SPLIT);
        return split < 0 ? packed : packed.substring(0, split);
    }

    static String bilingualAttrs(String packed) {
        if (packed == null) {
            return "";
        }
        int split = packed.indexOf(LANG_SPLIT);
        if (split < 0) {
            return "";
        }
        return " data-en=\"" + escape(packed.substring(0, split)) + "\" data-zh=\"" + escape(packed.substring(split + 1)) + "\"";
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
